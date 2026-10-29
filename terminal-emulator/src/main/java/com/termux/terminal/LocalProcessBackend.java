package com.termux.terminal;

import android.os.ParcelFileDescriptor;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** A {@link TerminalBackend} running a local process on a pseudo-terminal. */
public final class LocalProcessBackend implements TerminalBackend {

    private final String mExecutable;
    private final String mCwd;
    private final String[] mArgs;
    private final String[] mEnv;

    /** The pid of the process. 0 if not started and -1 if finished running. */
    private volatile int mPid;
    private volatile boolean mTerminateRequested;
    private ParcelFileDescriptor mPty;
    private InputStream mInputStream;
    private OutputStream mOutputStream;

    /**
     * @param executable Absolute path of the program to run.
     * @param cwd        Initial working directory.
     * @param args       Full argument vector, including argv[0].
     * @param env        Complete environment as "NAME=value" entries.
     */
    public LocalProcessBackend(String executable, String cwd, String[] args, String[] env) {
        mExecutable = executable;
        mCwd = cwd;
        mArgs = args;
        mEnv = env;
    }

    @Override
    public void start(int columns, int rows, int cellWidthPixels, int cellHeightPixels, StatusSink status) {
        int[] processId = new int[1];
        int fd = JNI.createSubprocess(mExecutable, mCwd, mArgs, mEnv, processId, rows, columns, cellWidthPixels, cellHeightPixels);
        // The streams do not own the descriptor; it is closed exactly once through mPty in close().
        ParcelFileDescriptor pty = ParcelFileDescriptor.adoptFd(fd);
        synchronized (this) {
            mPty = pty;
            mPid = processId[0];
            mInputStream = new FileInputStream(pty.getFileDescriptor());
            mOutputStream = new FileOutputStream(pty.getFileDescriptor());
        }
        if (mTerminateRequested) terminate();
    }

    @Override
    public synchronized InputStream getInputStream() {
        return mInputStream;
    }

    @Override
    public synchronized OutputStream getOutputStream() {
        return mOutputStream;
    }

    @Override
    public synchronized void resize(int columns, int rows, int cellWidthPixels, int cellHeightPixels) {
        if (mPty != null) JNI.setPtyWindowSize(mPty.getFd(), rows, columns, cellWidthPixels, cellHeightPixels);
    }

    @Override
    public int waitFor() {
        int status = JNI.waitFor(mPid);
        mPid = -1;
        return status;
    }

    @Override
    public void terminate() {
        mTerminateRequested = true;
        int pid = mPid;
        if (pid > 0) {
            try {
                Os.kill(pid, OsConstants.SIGKILL);
            } catch (ErrnoException ignored) {
                // Already gone.
            }
        }
    }

    @Override
    public synchronized void close() {
        if (mPty != null) {
            try {
                mPty.close();
            } catch (IOException ignored) {
                // Nothing left to release.
            }
            mPty = null;
        }
    }

    @Override
    public String describeExit(int exitStatus) {
        StringBuilder builder = new StringBuilder("\r\n[Process completed");
        if (exitStatus > 0) {
            builder.append(" (code ").append(exitStatus).append(')');
        } else if (exitStatus < 0) {
            builder.append(" (signal ").append(-exitStatus).append(')');
        }
        return builder.append(" - press Enter]").toString();
    }

    @Override
    public int getPid() {
        int pid = mPid;
        return pid > 0 ? pid : -1;
    }

    @Override
    public String getCwd() {
        int pid = mPid;
        if (pid < 1) return null;
        try {
            String cwdSymlink = "/proc/" + pid + "/cwd/";
            String outputPath = new File(cwdSymlink).getCanonicalPath();
            String withTrailingSlash = outputPath.endsWith("/") ? outputPath : outputPath + '/';
            return cwdSymlink.equals(withTrailingSlash) ? null : outputPath;
        } catch (IOException | SecurityException e) {
            return null;
        }
    }

}
