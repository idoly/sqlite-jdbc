package io.github.idoly.sqlite.jdbc;

import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Applies JDBC timeout and native-exception mapping around a statement operation. */
final class ExecutionController {
    private static final ScheduledExecutorService TIMEOUT_SCHEDULER = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "sqlite-jdbc-timeout");
        thread.setDaemon(true);
        return thread;
    });

    private final SQLiteConnection connection;
    private int timeoutSeconds;

    ExecutionController(SQLiteConnection connection) {
        this.connection = connection;
    }

    int timeoutSeconds() {
        return timeoutSeconds;
    }

    void setTimeoutSeconds(int seconds) {
        timeoutSeconds = seconds;
    }

    <T> T run(SqlCallable<T> operation) throws SQLException {
        if (timeoutSeconds == 0) return invoke(operation);

        AtomicBoolean expired = new AtomicBoolean();
        ScheduledFuture<?> timeoutTask = TIMEOUT_SCHEDULER.schedule(() -> {
            expired.set(true);
            connection.interrupt();
        }, timeoutSeconds, TimeUnit.SECONDS);
        try {
            T result = invoke(operation);
            if (expired.get()) throw timeoutException(null, 0);
            return result;
        } catch (SQLException error) {
            if (expired.get()) throw timeoutException(error, error.getErrorCode());
            throw error;
        } catch (RuntimeException error) {
            if (expired.get()) throw timeoutException(error, 0);
            throw error;
        } finally {
            timeoutTask.cancel(false);
        }
    }

    private static <T> T invoke(SqlCallable<T> operation) throws SQLException {
        try {
            return operation.call();
        } catch (NativeException error) {
            throw SqlExceptionMapper.map(error);
        }
    }

    private static SQLTimeoutException timeoutException(Throwable cause, int vendorCode) {
        return cause == null
                ? new SQLTimeoutException("SQLite statement timed out", "HYT00", vendorCode)
                : new SQLTimeoutException("SQLite statement timed out", "HYT00", vendorCode, cause);
    }

    @FunctionalInterface
    interface SqlCallable<T> {
        T call() throws SQLException;
    }
}
