package com.termux.terminal;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * A terminal session, consisting of a {@link TerminalBackend} (a local process, an SSH channel, ...) coupled to a
 * terminal emulator.
 * <p>
 * The backend is started when the size is made known by a call to {@link #updateSize(int, int, int, int)}, after which
 * threads are spawned to handle the backend I/O. All terminal emulation and callback methods are performed on the
 * main thread.
 * <p>
 * The backend may be ended forcefully by using the {@link #finishIfRunning()} method.
 * <p>
 * NOTE: The terminal session may outlive the EmulatorView, so be careful with callbacks!
 */
public final class TerminalSession extends TerminalOutput {

    private static final int MSG_NEW_INPUT = 1;
    private static final int MSG_STARTED = 2;
    private static final int MSG_START_FAILED = 3;
    private static final int MSG_PROCESS_EXITED = 4;

    /** Exit status reported when the backend could not be started. */
    public static final int EXIT_STATUS_START_FAILED = Integer.MIN_VALUE;

    private static final int STATE_NEW = 0;
    private static final int STATE_STARTING = 1;
    private static final int STATE_RUNNING = 2;
    private static final int STATE_FINISHED = 3;

    /** How long to wait for remaining output to be read after the backend has ended. */
    private static final long OUTPUT_DRAIN_TIMEOUT_MS = 500;

    public final String mHandle = UUID.randomUUID().toString();

    TerminalEmulator mEmulator;

    /**
     * A queue written to from a separate thread when the backend outputs, and read by main thread to process by
     * terminal emulator.
     */
    final ByteQueue mProcessToTerminalIOQueue = new ByteQueue(4096);
    /**
     * A queue written to from the main thread due to user interaction, and read by another thread which forwards by
     * writing to the backend.
     */
    final ByteQueue mTerminalToProcessIOQueue = new ByteQueue(4096);
    /** Buffer to write translate code points into utf8 before writing to mTerminalToProcessIOQueue */
    private final byte[] mUtf8InputBuffer = new byte[5];

    /** Callback which gets notified when a session finishes or changes title. */
    TerminalSessionClient mClient;

    private final TerminalBackend mBackend;

    private int mState = STATE_NEW;

    /** The exit status of the backend. Only valid if the session is finished. */
    private int mExitStatus;

    /** Set by the application for user identification of session, not by terminal. */
    public String mSessionName;

    final Handler mMainThreadHandler = new MainThreadHandler();

    private final Integer mTranscriptRows;

    /** The latest cell size in pixels, accessed only on the main thread. */
    private int mCellWidthPixels, mCellHeightPixels;

    private static final String LOG_TAG = "TerminalSession";

    public TerminalSession(TerminalBackend backend, Integer transcriptRows, TerminalSessionClient client) {
        this.mBackend = backend;
        this.mTranscriptRows = transcriptRows;
        this.mClient = client;
    }

    /**
     * @param client The {@link TerminalSessionClient} interface implementation to allow
     *               for communication between {@link TerminalSession} and its client.
     */
    public void updateTerminalSessionClient(TerminalSessionClient client) {
        mClient = client;

        if (mEmulator != null)
            mEmulator.updateTerminalSessionClient(client);
    }

    public TerminalBackend getBackend() {
        return mBackend;
    }

    /** Inform the backend of the new size and reflow or initialize the emulator. */
    public void updateSize(int columns, int rows, int cellWidthPixels, int cellHeightPixels) {
        mCellWidthPixels = cellWidthPixels;
        mCellHeightPixels = cellHeightPixels;
        if (mEmulator == null) {
            initializeEmulator(columns, rows, cellWidthPixels, cellHeightPixels);
        } else {
            if (getState() == STATE_RUNNING) mBackend.resize(columns, rows, cellWidthPixels, cellHeightPixels);
            mEmulator.resize(columns, rows, cellWidthPixels, cellHeightPixels);
        }
    }

    /** The terminal title as set through escape sequences or null if none set. */
    public String getTitle() {
        return (mEmulator == null) ? null : mEmulator.getTitle();
    }

    /**
     * Set the terminal emulator's window size and start the backend.
     *
     * @param columns The number of columns in the terminal window.
     * @param rows    The number of rows in the terminal window.
     */
    public void initializeEmulator(int columns, int rows, int cellWidthPixels, int cellHeightPixels) {
        mEmulator = new TerminalEmulator(this, columns, rows, cellWidthPixels, cellHeightPixels, mTranscriptRows, mClient);
        mCellWidthPixels = cellWidthPixels;
        mCellHeightPixels = cellHeightPixels;
        synchronized (this) {
            if (mState != STATE_NEW) return;
            mState = STATE_STARTING;
        }

        new Thread("TermSession[" + mHandle + "]") {
            @Override
            public void run() {
                try {
                    mBackend.start(columns, rows, cellWidthPixels, cellHeightPixels, text -> {
                        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
                        if (mProcessToTerminalIOQueue.write(bytes, 0, bytes.length))
                            mMainThreadHandler.sendEmptyMessage(MSG_NEW_INPUT);
                    });
                } catch (Throwable e) {
                    Logger.logStackTraceWithMessage(mClient, LOG_TAG, "Failed to start session", e);
                    String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    mMainThreadHandler.sendMessage(mMainThreadHandler.obtainMessage(MSG_START_FAILED, message));
                    return;
                }

                synchronized (TerminalSession.this) {
                    if (mState == STATE_STARTING) mState = STATE_RUNNING;
                }
                mMainThreadHandler.sendEmptyMessage(MSG_STARTED);

                Thread reader = startReader(mBackend.getInputStream());
                startWriter(mBackend.getOutputStream());

                int exitStatus = mBackend.waitFor();
                try {
                    reader.join(OUTPUT_DRAIN_TIMEOUT_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                mMainThreadHandler.sendMessage(mMainThreadHandler.obtainMessage(MSG_PROCESS_EXITED, exitStatus));
            }
        }.start();
    }

    private Thread startReader(InputStream input) {
        Thread reader = new Thread("TermSessionInputReader[" + mHandle + "]") {
            @Override
            public void run() {
                final byte[] buffer = new byte[4096];
                try {
                    while (true) {
                        int read = input.read(buffer);
                        if (read == -1) return;
                        if (!mProcessToTerminalIOQueue.write(buffer, 0, read)) return;
                        mMainThreadHandler.sendEmptyMessage(MSG_NEW_INPUT);
                    }
                } catch (Exception e) {
                    // Ignore, just shutting down.
                }
            }
        };
        reader.start();
        return reader;
    }

    private void startWriter(OutputStream output) {
        new Thread("TermSessionOutputWriter[" + mHandle + "]") {
            @Override
            public void run() {
                final byte[] buffer = new byte[4096];
                try {
                    while (true) {
                        int bytesToWrite = mTerminalToProcessIOQueue.read(buffer, true);
                        if (bytesToWrite == -1) return;
                        output.write(buffer, 0, bytesToWrite);
                        output.flush();
                    }
                } catch (Exception e) {
                    // Ignore, just shutting down.
                }
            }
        }.start();
    }

    /** Write data to the backend. Input is dropped while the backend is not running. */
    @Override
    public void write(byte[] data, int offset, int count) {
        if (getState() == STATE_RUNNING) mTerminalToProcessIOQueue.write(data, offset, count);
    }

    /** Write the Unicode code point to the terminal encoded in UTF-8. */
    public void writeCodePoint(boolean prependEscape, int codePoint) {
        if (codePoint > 1114111 || (codePoint >= 0xD800 && codePoint <= 0xDFFF)) {
            // 1114111 (= 2**16 + 1024**2 - 1) is the highest code point, [0xD800,0xDFFF] is the surrogate range.
            throw new IllegalArgumentException("Invalid code point: " + codePoint);
        }

        int bufferPosition = 0;
        if (prependEscape) mUtf8InputBuffer[bufferPosition++] = 27;

        if (codePoint <= /* 7 bits */0b1111111) {
            mUtf8InputBuffer[bufferPosition++] = (byte) codePoint;
        } else if (codePoint <= /* 11 bits */0b11111111111) {
            /* 110xxxxx leading byte with leading 5 bits */
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b11000000 | (codePoint >> 6));
            /* 10xxxxxx continuation byte with following 6 bits */
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | (codePoint & 0b111111));
        } else if (codePoint <= /* 16 bits */0b1111111111111111) {
            /* 1110xxxx leading byte with leading 4 bits */
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b11100000 | (codePoint >> 12));
            /* 10xxxxxx continuation byte with following 6 bits */
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | ((codePoint >> 6) & 0b111111));
            /* 10xxxxxx continuation byte with following 6 bits */
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | (codePoint & 0b111111));
        } else { /* We have checked codePoint <= 1114111 above, so we have max 21 bits = 0b111111111111111111111 */
            /* 11110xxx leading byte with leading 3 bits */
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b11110000 | (codePoint >> 18));
            /* 10xxxxxx continuation byte with following 6 bits */
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | ((codePoint >> 12) & 0b111111));
            /* 10xxxxxx continuation byte with following 6 bits */
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | ((codePoint >> 6) & 0b111111));
            /* 10xxxxxx continuation byte with following 6 bits */
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | (codePoint & 0b111111));
        }
        write(mUtf8InputBuffer, 0, bufferPosition);
    }

    public TerminalEmulator getEmulator() {
        return mEmulator;
    }

    /** Notify the {@link #mClient} that the screen has changed. */
    protected void notifyScreenUpdate() {
        mClient.onTextChanged(this);
    }

    /** Reset state for terminal emulator state. */
    public void reset() {
        mEmulator.reset();
        notifyScreenUpdate();
    }

    /** Finish this terminal session by asking the backend to end. */
    public void finishIfRunning() {
        if (isRunning()) mBackend.terminate();
    }

    /** Cleanup resources when the backend has ended. */
    void cleanupResources(int exitStatus) {
        synchronized (this) {
            mState = STATE_FINISHED;
            mExitStatus = exitStatus;
        }

        // Stop the reader and writer threads, and close the I/O streams
        mTerminalToProcessIOQueue.close();
        mProcessToTerminalIOQueue.close();
        mBackend.close();
    }

    @Override
    public void titleChanged(String oldTitle, String newTitle) {
        mClient.onTitleChanged(this);
    }

    private synchronized int getState() {
        return mState;
    }

    /** True until the backend has ended (also while it is still starting). */
    public synchronized boolean isRunning() {
        return mState != STATE_FINISHED;
    }

    /** True while the backend is still starting, e.g. connecting to a remote host. */
    public synchronized boolean isStarting() {
        return mState == STATE_NEW || mState == STATE_STARTING;
    }

    /** Only valid if not {@link #isRunning()}. {@link #EXIT_STATUS_START_FAILED} if the backend never started. */
    public synchronized int getExitStatus() {
        return mExitStatus;
    }

    @Override
    public void onCopyTextToClipboard(String text) {
        mClient.onCopyTextToClipboard(this, text);
    }

    @Override
    public void onPasteTextFromClipboard() {
        mClient.onPasteTextFromClipboard(this);
    }

    @Override
    public void onBell() {
        mClient.onBell(this);
    }

    @Override
    public void onColorsChanged() {
        mClient.onColorsChanged(this);
    }

    public int getPid() {
        return mBackend.getPid();
    }

    /** Returns the shell's working directory or null if it was unavailable. */
    public String getCwd() {
        return mBackend.getCwd();
    }

    private void appendToTerminal(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        mEmulator.append(bytes, bytes.length);
        notifyScreenUpdate();
    }

    @SuppressLint("HandlerLeak")
    class MainThreadHandler extends Handler {

        final byte[] mReceiveBuffer = new byte[4 * 1024];

        MainThreadHandler() {
            super(Looper.getMainLooper());
        }

        @Override
        public void handleMessage(Message msg) {
            int bytesRead = mProcessToTerminalIOQueue.read(mReceiveBuffer, false);
            if (bytesRead > 0) {
                mEmulator.append(mReceiveBuffer, bytesRead);
                notifyScreenUpdate();
            }

            if (msg.what == MSG_STARTED) {
                // The size may have changed while the backend was starting.
                mBackend.resize(mEmulator.mColumns, mEmulator.mRows, mCellWidthPixels, mCellHeightPixels);
                mClient.onTitleChanged(TerminalSession.this);
            } else if (msg.what == MSG_START_FAILED) {
                cleanupResources(EXIT_STATUS_START_FAILED);
                appendToTerminal("\r\n\u001b[31m" + msg.obj + "\u001b[0m\r\n[Press Enter to retry]");
                mClient.onSessionFinished(TerminalSession.this);
            } else if (msg.what == MSG_PROCESS_EXITED) {
                int exitStatus = (Integer) msg.obj;
                // Process any output that arrived after the first read above.
                int remaining;
                while ((remaining = mProcessToTerminalIOQueue.read(mReceiveBuffer, false)) > 0) {
                    mEmulator.append(mReceiveBuffer, remaining);
                }
                cleanupResources(exitStatus);
                appendToTerminal(mBackend.describeExit(exitStatus));
                mClient.onSessionFinished(TerminalSession.this);
            }
        }

    }

}
