/*
 * Pseudo-terminal process support for com.termux.terminal.JNI.
 *
 * All Java strings are converted to C memory before fork() so that the child only performs
 * async-signal-safe system calls between fork() and execve().
 */

#define _GNU_SOURCE

#include <errno.h>
#include <fcntl.h>
#include <jni.h>
#include <signal.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/resource.h>
#include <sys/wait.h>
#include <termios.h>
#include <unistd.h>

#define CHILD_EXEC_FAILED 127

static void throw_runtime_exception(JNIEnv *env, const char *message, int error) {
    char buffer[256];
    if (error != 0) {
        strlcpy(buffer, message, sizeof(buffer));
        strlcat(buffer, ": ", sizeof(buffer));
        strlcat(buffer, strerror(error), sizeof(buffer));
        message = buffer;
    }
    jclass exception_class = (*env)->FindClass(env, "java/lang/RuntimeException");
    if (exception_class != NULL) (*env)->ThrowNew(env, exception_class, message);
}

/* Encodes a Java string as proper UTF-8 (unlike GetStringUTFChars, which produces modified UTF-8). */
static char *to_utf8(JNIEnv *env, jstring string) {
    jsize length = string == NULL ? 0 : (*env)->GetStringLength(env, string);
    jchar *utf16 = malloc((size_t) length * sizeof(jchar) + 1);
    char *utf8 = malloc((size_t) length * 3 + 1);
    if (utf16 == NULL || utf8 == NULL) {
        free(utf16);
        free(utf8);
        throw_runtime_exception(env, "Out of memory", 0);
        return NULL;
    }
    (*env)->GetStringRegion(env, string, 0, length, utf16);

    size_t out = 0;
    for (jsize i = 0; i < length; i++) {
        uint32_t c = utf16[i];
        if (c >= 0xD800 && c <= 0xDBFF && i + 1 < length && utf16[i + 1] >= 0xDC00 && utf16[i + 1] <= 0xDFFF) {
            c = 0x10000 + ((c - 0xD800) << 10) + (utf16[i + 1] - 0xDC00);
            i++;
        } else if (c >= 0xD800 && c <= 0xDFFF) {
            c = 0xFFFD; // Unpaired surrogate.
        }

        if (c < 0x80) {
            utf8[out++] = (char) c;
        } else if (c < 0x800) {
            utf8[out++] = (char) (0xC0 | (c >> 6));
            utf8[out++] = (char) (0x80 | (c & 0x3F));
        } else if (c < 0x10000) {
            utf8[out++] = (char) (0xE0 | (c >> 12));
            utf8[out++] = (char) (0x80 | ((c >> 6) & 0x3F));
            utf8[out++] = (char) (0x80 | (c & 0x3F));
        } else {
            utf8[out++] = (char) (0xF0 | (c >> 18));
            utf8[out++] = (char) (0x80 | ((c >> 12) & 0x3F));
            utf8[out++] = (char) (0x80 | ((c >> 6) & 0x3F));
            utf8[out++] = (char) (0x80 | (c & 0x3F));
        }
    }
    utf8[out] = '\0';
    free(utf16);
    return utf8;
}

static void free_string_array(char **array) {
    if (array == NULL) return;
    for (char **entry = array; *entry != NULL; entry++) free(*entry);
    free(array);
}

/* Converts a Java String[] into a NULL terminated array of C strings. */
static char **to_string_array(JNIEnv *env, jobjectArray array) {
    jsize size = array == NULL ? 0 : (*env)->GetArrayLength(env, array);
    char **result = calloc((size_t) size + 1, sizeof(char *));
    if (result == NULL) {
        throw_runtime_exception(env, "Out of memory", 0);
        return NULL;
    }
    for (jsize i = 0; i < size; i++) {
        jstring element = (jstring) (*env)->GetObjectArrayElement(env, array, i);
        if (element == NULL) {
            free_string_array(result);
            throw_runtime_exception(env, "Null element in string array", 0);
            return NULL;
        }
        result[i] = to_utf8(env, element);
        (*env)->DeleteLocalRef(env, element);
        if (result[i] == NULL) {
            free_string_array(result);
            return NULL;
        }
    }
    return result;
}

static void set_window_size(int fd, jint rows, jint columns, jint cell_width, jint cell_height) {
    struct winsize size = {
        .ws_row = (unsigned short) rows,
        .ws_col = (unsigned short) columns,
        .ws_xpixel = (unsigned short) (columns * cell_width),
        .ws_ypixel = (unsigned short) (rows * cell_height),
    };
    ioctl(fd, TIOCSWINSZ, &size);
}

static void write_all(int fd, const char *text) {
    size_t remaining = strlen(text);
    while (remaining > 0) {
        ssize_t written = write(fd, text, remaining);
        if (written < 0) {
            if (errno == EINTR) continue;
            return;
        }
        text += written;
        remaining -= (size_t) written;
    }
}

static void close_descriptors_from(int lowest) {
    int highest = 65536;
    struct rlimit limit;
    if (getrlimit(RLIMIT_NOFILE, &limit) == 0 && limit.rlim_cur != RLIM_INFINITY && limit.rlim_cur < (rlim_t) highest) {
        highest = (int) limit.rlim_cur;
    }
    for (int fd = lowest; fd < highest; fd++) close(fd);
}

__attribute__((noreturn))
static void run_child(const char *pts_name, const char *cmd, const char *cwd, char *const argv[], char *const envp[]) {
    // The Android runtime blocks and ignores signals the shell expects to be able to receive.
    struct sigaction default_action;
    memset(&default_action, 0, sizeof(default_action));
    default_action.sa_handler = SIG_DFL;
    for (int signal_number = 1; signal_number < 32; signal_number++) {
        if (signal_number != SIGKILL && signal_number != SIGSTOP) sigaction(signal_number, &default_action, NULL);
    }
    sigset_t all_signals;
    sigfillset(&all_signals);
    sigprocmask(SIG_UNBLOCK, &all_signals, NULL);

    // Become a session leader so that opening the slave makes it our controlling terminal.
    if (setsid() < 0) _exit(CHILD_EXEC_FAILED);
    int pts = open(pts_name, O_RDWR);
    if (pts < 0) _exit(CHILD_EXEC_FAILED);
    ioctl(pts, TIOCSCTTY, 0);
    dup2(pts, STDIN_FILENO);
    dup2(pts, STDOUT_FILENO);
    dup2(pts, STDERR_FILENO);
    close_descriptors_from(STDERR_FILENO + 1);

    if (cwd[0] == '\0' || chdir(cwd) != 0) chdir("/");

    execve(cmd, argv, envp);

    int error = errno;
    write_all(STDERR_FILENO, "\r\nFailed to start ");
    write_all(STDERR_FILENO, cmd);
    write_all(STDERR_FILENO, ": ");
    write_all(STDERR_FILENO, strerror(error));
    write_all(STDERR_FILENO, "\r\n");
    _exit(CHILD_EXEC_FAILED);
}

JNIEXPORT jint JNICALL
Java_com_termux_terminal_JNI_createSubprocess(JNIEnv *env, jclass clazz, jstring cmd, jstring cwd,
                                              jobjectArray args, jobjectArray env_vars, jintArray process_id_array,
                                              jint rows, jint columns, jint cell_width, jint cell_height) {
    (void) clazz;
    jint result = -1;
    int ptm = -1;
    char *cmd_utf8 = NULL;
    char *cwd_utf8 = NULL;
    char **argv = NULL;
    char **envp = NULL;

    if ((cmd_utf8 = to_utf8(env, cmd)) == NULL) goto cleanup;
    if ((cwd_utf8 = to_utf8(env, cwd)) == NULL) goto cleanup;
    if ((argv = to_string_array(env, args)) == NULL) goto cleanup;
    if ((envp = to_string_array(env, env_vars)) == NULL) goto cleanup;
    if (argv[0] == NULL) {
        free(argv);
        argv = calloc(2, sizeof(char *));
        if (argv == NULL || (argv[0] = strdup(cmd_utf8)) == NULL) {
            throw_runtime_exception(env, "Out of memory", 0);
            goto cleanup;
        }
    }

    ptm = open("/dev/ptmx", O_RDWR | O_NOCTTY | O_CLOEXEC);
    if (ptm < 0) {
        throw_runtime_exception(env, "Cannot open /dev/ptmx", errno);
        goto cleanup;
    }

    char pts_name[64];
    if (grantpt(ptm) != 0 || unlockpt(ptm) != 0 || ptsname_r(ptm, pts_name, sizeof(pts_name)) != 0) {
        throw_runtime_exception(env, "Cannot set up pseudo-terminal", errno);
        goto cleanup;
    }

    // UTF-8 input processing, and no XON/XOFF flow control so that Ctrl+S cannot freeze the display.
    struct termios attributes;
    if (tcgetattr(ptm, &attributes) == 0) {
        attributes.c_iflag |= IUTF8;
        attributes.c_iflag &= ~(tcflag_t) (IXON | IXOFF);
        tcsetattr(ptm, TCSANOW, &attributes);
    }
    set_window_size(ptm, rows, columns, cell_width, cell_height);

    pid_t pid = fork();
    if (pid < 0) {
        throw_runtime_exception(env, "Cannot fork", errno);
        goto cleanup;
    }
    if (pid == 0) run_child(pts_name, cmd_utf8, cwd_utf8, argv, envp);

    jint pid_value = (jint) pid;
    (*env)->SetIntArrayRegion(env, process_id_array, 0, 1, &pid_value);
    result = ptm;
    ptm = -1;

cleanup:
    if (ptm >= 0) close(ptm);
    free(cmd_utf8);
    free(cwd_utf8);
    free_string_array(argv);
    free_string_array(envp);
    return result;
}

JNIEXPORT void JNICALL
Java_com_termux_terminal_JNI_setPtyWindowSize(JNIEnv *env, jclass clazz, jint fd, jint rows, jint columns,
                                              jint cell_width, jint cell_height) {
    (void) env;
    (void) clazz;
    set_window_size(fd, rows, columns, cell_width, cell_height);
}

JNIEXPORT jint JNICALL
Java_com_termux_terminal_JNI_waitFor(JNIEnv *env, jclass clazz, jint pid) {
    (void) env;
    (void) clazz;
    int status;
    while (waitpid(pid, &status, 0) < 0) {
        // ECHILD means the child has already been reaped elsewhere, and its status is lost.
        if (errno != EINTR) return 0;
    }
    if (WIFEXITED(status)) return WEXITSTATUS(status);
    if (WIFSIGNALED(status)) return -WTERMSIG(status);
    return 0;
}
