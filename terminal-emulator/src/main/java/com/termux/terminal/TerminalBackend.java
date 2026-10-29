package com.termux.terminal;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * The transport a {@link TerminalSession} is connected to, e.g. a local process running on a
 * pseudo-terminal or a remote shell running over SSH.
 * <p>
 * Threading contract:
 * <ul>
 *     <li>{@link #start} and {@link #waitFor} are called on a dedicated background thread owned by the session.</li>
 *     <li>{@link #resize} and {@link #close} are called on the main thread and must not block.</li>
 *     <li>{@link #terminate} may be called from any thread, at any time (also while {@link #start} is still running),
 *     and must be idempotent.</li>
 * </ul>
 */
public interface TerminalBackend {

    /** Receives human readable progress text (e.g. "Connecting to example.com...") while {@link #start} runs. */
    interface StatusSink {
        void onStatus(String text);
    }

    /**
     * Start the process or open the connection. Blocking.
     *
     * @throws Exception if starting failed. The exception message is shown in the terminal.
     */
    void start(int columns, int rows, int cellWidthPixels, int cellHeightPixels, StatusSink status) throws Exception;

    /** Output produced by the process or remote end. Only valid after {@link #start} returned normally. */
    InputStream getInputStream();

    /** Input to the process or remote end. Only valid after {@link #start} returned normally. */
    OutputStream getOutputStream();

    /** Inform the backend of a new terminal size. */
    void resize(int columns, int rows, int cellWidthPixels, int cellHeightPixels);

    /**
     * Block until the process or connection has ended.
     *
     * @return the exit status if >= 0, or a negated signal number if the process was killed by a signal.
     */
    int waitFor();

    /** Ask the process or connection to end. */
    void terminate();

    /** Release all resources. Called exactly once, after the session has finished. */
    void close();

    /** Text appended to the terminal when the session ends with the given status from {@link #waitFor}. */
    String describeExit(int exitStatus);

    /** The id of the local process, or -1 if there is none. */
    int getPid();

    /** The current working directory of the local process, or null if unknown. */
    String getCwd();

}
