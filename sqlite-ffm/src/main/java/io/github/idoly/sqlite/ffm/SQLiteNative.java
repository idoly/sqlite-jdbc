package io.github.idoly.sqlite.ffm;

/**
 * Stable Java boundary for the SQLite calls implemented with JDK FFM.
 *
 * <p>The binding owns temporary native memory. Callers own returned database and statement handles
 * and must close or finalize them.
 */
public interface SQLiteNative {
    /** Driver result used when non-comment SQL follows the first statement. */
    int DRIVER_MULTIPLE_STATEMENTS = -1;

    /** Open an existing database without write access. */
    int OPEN_READONLY = 0x00000001;
    /** Open an existing database with read and write access. */
    int OPEN_READWRITE = 0x00000002;
    /** Create a database when it does not exist. */
    int OPEN_CREATE = 0x00000004;
    /** Interpret the filename as a SQLite URI. */
    int OPEN_URI = 0x00000040;

    /** @return the version reported by the packaged SQLite library */
    String libraryVersion();

    /**
     * Opens a SQLite database.
     *
     * @param filename database filename or SQLite URI
     * @param openFlags bitwise combination of the {@code OPEN_*} constants
     * @return native result code and database handle
     */
    OpenResult open(String filename, int openFlags);

    /**
     * @param database database to close
     * @return SQLite result code
     */
    int close(DatabaseHandle database);

    /**
     * Executes SQL that does not return rows.
     *
     * @param database target database
     * @param sql SQL text
     * @return SQLite result code
     */
    int execute(DatabaseHandle database, String sql);

    /**
     * @param database target database
     * @return whether SQLite is currently in auto-commit mode
     */
    boolean isAutoCommit(DatabaseHandle database);

    /**
     * @param database target database
     * @param timeoutMillis maximum lock wait in milliseconds
     * @return SQLite result code
     */
    int setBusyTimeoutMillis(DatabaseHandle database, int timeoutMillis);

    /** @param database database whose active operation should be interrupted */
    void interrupt(DatabaseHandle database);

    /**
     * @param database target database
     * @return number of rows changed directly by the most recently completed statement
     */
    int changes(DatabaseHandle database);

    /**
     * @param database target database
     * @return rowid from the most recent successful INSERT
     */
    long lastInsertRowId(DatabaseHandle database);

    /**
     * Prepares exactly one SQL statement, allowing only whitespace or comments after it.
     *
     * @param database target database
     * @param sql SQL text
     * @return native result code and statement handle
     */
    PrepareResult prepare(DatabaseHandle database, String sql);

    /**
     * @param statement statement to advance
     * @return SQLite row, done, or error result code
     */
    int step(StatementHandle statement);

    /**
     * Resets a statement so it can be executed again.
     *
     * @param statement statement to reset
     * @return result code from the statement's most recent evaluation, as defined by {@code sqlite3_reset}
     */
    int reset(StatementHandle statement);

    /**
     * @param statement statement whose parameters should be cleared
     * @return SQLite result code
     */
    int clearBindings(StatementHandle statement);

    /**
     * @param statement prepared statement
     * @return number of bind parameters
     */
    int parameterCount(StatementHandle statement);

    /**
     * @param statement prepared statement
     * @param parameterIndex one-based parameter index
     * @return SQLite result code
     */
    int bindNull(StatementHandle statement, int parameterIndex);

    /**
     * @param statement prepared statement
     * @param parameterIndex one-based parameter index
     * @param value value to bind
     * @return SQLite result code
     */
    int bindLong(StatementHandle statement, int parameterIndex, long value);

    /**
     * @param statement prepared statement
     * @param parameterIndex one-based parameter index
     * @param value value to bind
     * @return SQLite result code
     */
    int bindDouble(StatementHandle statement, int parameterIndex, double value);

    /**
     * @param statement prepared statement
     * @param parameterIndex one-based parameter index
     * @param value value to copy into SQLite
     * @return SQLite result code
     */
    int bindText(StatementHandle statement, int parameterIndex, String value);

    /**
     * @param statement prepared statement
     * @param parameterIndex one-based parameter index
     * @param value value to copy into SQLite
     * @return SQLite result code
     */
    int bindBlob(StatementHandle statement, int parameterIndex, byte[] value);

    /**
     * @param statement prepared statement
     * @return number of result columns
     */
    int columnCount(StatementHandle statement);

    /**
     * @param statement prepared statement positioned on a row
     * @param columnIndex zero-based column index
     * @return SQLite runtime storage-class code
     */
    int storageClass(StatementHandle statement, int columnIndex);

    /**
     * @param statement prepared statement positioned on a row
     * @param columnIndex zero-based column index
     * @return column value converted to a long
     */
    long columnLong(StatementHandle statement, int columnIndex);

    /**
     * @param statement prepared statement positioned on a row
     * @param columnIndex zero-based column index
     * @return column value converted to a double
     */
    double columnDouble(StatementHandle statement, int columnIndex);

    /**
     * @param statement prepared statement positioned on a row
     * @param columnIndex zero-based column index
     * @return copied UTF-8 column value, or {@code null}
     */
    String columnText(StatementHandle statement, int columnIndex);

    /**
     * @param statement prepared statement positioned on a row
     * @param columnIndex zero-based column index
     * @return copied BLOB value
     */
    byte[] columnBlob(StatementHandle statement, int columnIndex);

    /**
     * @param statement prepared statement
     * @param columnIndex zero-based column index
     * @return result column name
     */
    String columnName(StatementHandle statement, int columnIndex);

    /**
     * @param statement prepared statement
     * @param columnIndex zero-based column index
     * @return declared SQL type, or {@code null}
     */
    String declaredType(StatementHandle statement, int columnIndex);

    /**
     * @param statement statement to finalize
     * @return SQLite result code
     */
    int finalizeStatement(StatementHandle statement);

    /**
     * @param database target database
     * @return the current SQLite error message
     */
    String errorMessage(DatabaseHandle database);
}
