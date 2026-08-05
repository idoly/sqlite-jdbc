package io.github.idoly.sqlite.jdbc;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLNonTransientException;
import java.sql.SQLTransientException;

/** Maps SQLite primary result codes to the closest JDBC exception category and SQLState. */
final class SqlExceptionMapper {
    private static final int SQLITE_BUSY = 5;
    private static final int SQLITE_LOCKED = 6;
    private static final int SQLITE_READ_ONLY = 8;
    private static final int SQLITE_INTERRUPT = 9;
    private static final int SQLITE_CANNOT_OPEN = 14;
    private static final int SQLITE_CONSTRAINT = 19;

    private SqlExceptionMapper() {}

    static SQLException map(NativeException error) {
        int primaryCode = error.resultCode() & 0xff;
        return switch (primaryCode) {
            case SQLITE_BUSY, SQLITE_LOCKED -> new SQLTransientException(
                    error.getMessage(), "40001", error.resultCode(), error);
            case SQLITE_READ_ONLY -> new SQLNonTransientException(
                    error.getMessage(), "25006", error.resultCode(), error);
            case SQLITE_INTERRUPT -> new SQLNonTransientException(
                    error.getMessage(), "57014", error.resultCode(), error);
            case SQLITE_CANNOT_OPEN -> new SQLNonTransientConnectionException(
                    error.getMessage(), "08001", error.resultCode(), error);
            case SQLITE_CONSTRAINT -> new SQLIntegrityConstraintViolationException(
                    error.getMessage(), "23000", error.resultCode(), error);
            default -> new SQLException(error.getMessage(), "HY000", error.resultCode(), error);
        };
    }
}
