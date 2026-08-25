package io.github.idoly.sqlite.internal;

import static io.github.idoly.sqlite.core.SQLiteResultCodes.*;

import io.github.idoly.sqlite.SQLiteConnection;
import io.github.idoly.sqlite.SQLiteConnectionConfig;
import io.github.idoly.sqlite.core.SQLiteDatabase;
import io.github.idoly.sqlite.core.StatementHandle;
import io.github.idoly.sqlite.internal.BackupRestoreCommand.Command;
import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.Arrays;
import java.util.regex.Pattern;

public class StatementImpl implements Statement {
    public final SQLiteConnection conn;
    protected final SQLiteDatabase database;
    protected final ResultSetImpl rs;

    public StatementHandle pointer;
    protected String sql = null;

    protected int batchPos;
    protected Object[] batch = null;
    protected boolean resultsWaiting = false;

    private Statement generatedKeysStat = null;
    private ResultSet generatedKeysRs = null;

    // pattern for matching insert statements of the general format starting with INSERT or REPLACE.
    // CTEs used prior to the insert or replace keyword are also be permitted.
    private static final Pattern INSERT_PATTERN =
            Pattern.compile(
                    "^\\s*(?:with\\s+.+\\(.+?\\))*\\s*(?:insert|replace)\\s*",
                    Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    public StatementImpl(SQLiteConnection connection, SQLiteDatabase database) {
        this.conn = connection;
        this.database = database;
        this.rs = new ResultSetImpl(this);
        this.queryTimeout = 0;
    }

    SQLiteDatabase getDatabase() {
        return database;
    }

    protected final void prepareStatement() throws SQLException {
        if (pointer != null) pointer.close();
        pointer = database.prepareStatement(sql);
    }

    public SQLiteConnectionConfig getConnectionConfig() {
        return conn.getConnectionConfig();
    }

    /**
     * @throws SQLException If the database is not opened.
     */
    protected final void checkOpen() throws SQLException {
        if (pointer.isClosed()) throw new SQLException("statement is not executing");
    }

    /**
     * @return True if the database is opened; false otherwise.
     */
    boolean isOpen() throws SQLException {
        return !pointer.isClosed();
    }

    /**
     * Calls sqlite3_step() and sets up results. Expects a clean stmt.
     *
     * @return True if the ResultSet has at least one row; false otherwise.
     * @throws SQLException If the given SQL statement is null or no database is open.
     */
    protected boolean exec() throws SQLException {
        if (sql == null) throw new SQLException("SQLiteJDBC internal error: sql==null");
        if (rs.isOpen()) throw new SQLException("SQLite JDBC internal error: rs.isOpen() on exec.");

        conn.tryEnforceTransactionMode();

        boolean success = false;
        boolean rc = false;
        try {
            rc = database.execute(pointer, null, conn.getAutoCommit());
            success = true;
        } finally {
            notifyFirstStatementExecuted();
            resultsWaiting = rc;
            if (!success) {
                this.pointer.close();
            }
        }

        return pointer.safeRunInt(SQLiteDatabase::column_count) != 0;
    }

    /**
     * Executes SQL statement and throws SQLExceptions if the given SQL statement is null or no
     * database is open.
     *
     * @param sql SQL statement.
     * @return True if the ResultSet has at least one row; false otherwise.
     * @throws SQLException If the given SQL statement is null or no database is open.
     */
    protected boolean exec(String sql) throws SQLException {
        if (sql == null) throw new SQLException("SQLiteJDBC internal error: sql==null");
        if (rs.isOpen()) throw new SQLException("SQLite JDBC internal error: rs.isOpen() on exec.");

        conn.tryEnforceTransactionMode();

        boolean rc = false;
        boolean success = false;
        try {
            rc = database.execute(sql, conn.getAutoCommit());
            success = true;
        } finally {
            notifyFirstStatementExecuted();
            resultsWaiting = rc;
            if (!success && pointer != null) {
                pointer.close();
            }
        }

        return pointer.safeRunInt(SQLiteDatabase::column_count) != 0;
    }

    protected void internalClose() throws SQLException {
        if (this.pointer != null && !this.pointer.isClosed()) {
            if (conn.isClosed())
                throw SQLiteDatabase.newSQLException(SQLITE_ERROR, "Connection is closed");

            rs.close();

            batch = null;
            batchPos = 0;
            int resp = this.pointer.close();

            if (resp != SQLITE_OK && resp != SQLITE_MISUSE) database.throwex(resp);
        }
    }

    protected void notifyFirstStatementExecuted() {
        conn.setFirstStatementExecuted(true);
    }

    protected void checkIndex(int index) throws SQLException {
        if (batch == null) {
            throw new SQLException("No parameter has been set yet");
        }
        if (index < 1 || index > batch.length) {
            throw new SQLException("Parameter index is invalid");
        }
    }

    protected void clearGeneratedKeys() throws SQLException {
        if (generatedKeysRs != null && !generatedKeysRs.isClosed()) {
            generatedKeysRs.close();
        }
        generatedKeysRs = null;
        if (generatedKeysStat != null && !generatedKeysStat.isClosed()) {
            generatedKeysStat.close();
        }
        generatedKeysStat = null;
    }

    /**
     * SQLite's last_insert_rowid() function is SQLiteDatabase-specific. However, in this
     * implementation we ensure the Generated Key result set is statement-specific by executing the
     * query immediately after an insert operation is performed. The caller is simply responsible
     * for calling updateGeneratedKeys on the statement object right after execute in a
     * synchronized(connection) block.
     */
    public void updateGeneratedKeys() throws SQLException {
        if (conn.getConnectionConfig().isGetGeneratedKeys()) {
            clearGeneratedKeys();
            if (sql != null && INSERT_PATTERN.matcher(sql).find()) {
                generatedKeysStat = conn.createStatement();
                generatedKeysRs = generatedKeysStat.executeQuery("SELECT last_insert_rowid();");
            }
        }
    }

    /**
     * This implementation uses SQLite's last_insert_rowid function to obtain the row ID. It cannot
     * provide multiple values when inserting multiple rows. Suggestion is to use a <a
     * href=https://www.sqlite.org/lang_returning.html>RETURNING</a> clause instead.
     *
     * @see java.sql.Statement#getGeneratedKeys()
     */
    public ResultSet getGeneratedKeys() throws SQLException {
        // getGeneratedKeys is required to return an EmptyResult set if the statement
        // did not generate any keys. Thus, if the generateKeysResultSet is NULL, spin
        // up a new result set without any contents by issuing a query with a false where condition
        if (generatedKeysRs == null) {
            generatedKeysStat = conn.createStatement();
            generatedKeysRs = generatedKeysStat.executeQuery("SELECT 1 WHERE 1 = 2;");
        }
        return generatedKeysRs;
    }

    private int queryTimeout; // in seconds, as per the JDBC spec
    protected long updateCount;
    protected boolean exhaustedResults = false;

    public boolean execute(final String sql) throws SQLException {
        internalClose();

        return this.withConnectionTimeout(
                () -> {
                    Command ext = BackupRestoreCommand.parse(sql);
                    if (ext != null) {
                        ext.execute(database);

                        return false;
                    }

                    this.sql = sql;
                    synchronized (conn) {
                        prepareStatement();
                        boolean result = exec();
                        updateGeneratedKeys();
                        updateCount = getDatabase().changes();
                        exhaustedResults = false;
                        return result;
                    }
                });
    }

    public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
        return execute(sql);
    }

    /**
     * @param closeStmt Whether to close this statement when the resultset is closed.
     * @see java.sql.Statement#executeQuery(java.lang.String)
     */
    public ResultSet executeQuery(String sql, boolean closeStmt) throws SQLException {
        rs.closeStmt = closeStmt;

        return executeQuery(sql);
    }

    public ResultSet executeQuery(String sql) throws SQLException {
        internalClose();
        this.sql = sql;

        return this.withConnectionTimeout(
                () -> {
                    prepareStatement();

                    if (!exec()) {
                        internalClose();
                        throw new SQLException(
                                "query does not return ResultSet", "SQLITE_DONE", SQLITE_DONE);
                    }
                    exhaustedResults = false;

                    return getResultSet();
                });
    }

    public int executeUpdate(final String sql) throws SQLException {
        return (int) executeLargeUpdate(sql);
    }

    public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
        return executeUpdate(sql);
    }

    public long executeLargeUpdate(String sql) throws SQLException {
        internalClose();
        this.sql = sql;

        return this.withConnectionTimeout(
                () -> {
                    SQLiteDatabase db = database;
                    long changes = 0;
                    Command ext = BackupRestoreCommand.parse(sql);
                    if (ext != null) {
                        // execute extended command
                        ext.execute(db);
                    } else {
                        try {
                            synchronized (db) {
                                changes = db.total_changes();
                                // directly invokes the exec API to support multiple SQL statements
                                int statusCode = db._exec(sql);
                                if (statusCode != SQLITE_OK)
                                    throw SQLiteDatabase.newSQLException(statusCode, "");
                                updateGeneratedKeys();
                                changes = db.total_changes() - changes;
                            }

                        } finally {
                            internalClose();
                        }
                    }
                    return changes;
                });
    }

    public long executeLargeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
        return executeLargeUpdate(sql);
    }

    public ResultSet getResultSet() throws SQLException {
        checkOpen();

        if (exhaustedResults) return null;

        if (rs.isOpen()) {
            throw new SQLException("ResultSet already requested");
        }

        if (pointer.safeRunInt(SQLiteDatabase::column_count) == 0) {
            return null;
        }

        if (rs.colsMeta == null) {
            rs.colsMeta = pointer.safeRun(SQLiteDatabase::column_names);
        }

        rs.cols = rs.colsMeta;
        rs.emptyResultSet = !resultsWaiting;
        rs.open = true;
        resultsWaiting = false;

        return (ResultSet) rs;
    }

    /**
     * This function has a complex behaviour best understood by carefully reading the JavaDoc for
     * getMoreResults() and considering the test StatementTest.execute().
     *
     * @see java.sql.Statement#getUpdateCount()
     */
    public int getUpdateCount() throws SQLException {
        return (int) getLargeUpdateCount();
    }

    /**
     * This function has a complex behaviour best understood by carefully reading the JavaDoc for
     * getMoreResults() and considering the test StatementTest.execute().
     *
     * @see java.sql.Statement#getLargeUpdateCount()
     */
    public long getLargeUpdateCount() throws SQLException {
        if (!pointer.isClosed()
                && !rs.isOpen()
                && !resultsWaiting
                && pointer.safeRunInt(SQLiteDatabase::column_count) == 0) return updateCount;
        return -1;
    }

    public void addBatch(String sql) throws SQLException {
        internalClose();
        if (batch == null || batchPos + 1 >= batch.length) {
            Object[] nb = new Object[Math.max(10, batchPos * 2)];
            if (batch != null) System.arraycopy(batch, 0, nb, 0, batch.length);
            batch = nb;
        }
        batch[batchPos++] = sql;
    }

    public void clearBatch() throws SQLException {
        batchPos = 0;
        if (batch != null) for (int i = 0; i < batch.length; i++) batch[i] = null;
    }

    public int[] executeBatch() throws SQLException {
        return Arrays.stream(executeLargeBatch()).mapToInt(l -> (int) l).toArray();
    }

    public long[] executeLargeBatch() throws SQLException {
        internalClose();
        if (batch == null || batchPos == 0) return new long[] {};

        long[] changes = new long[batchPos];
        SQLiteDatabase db = database;
        synchronized (db) {
            try {
                for (int i = 0; i < changes.length; i++) {
                    try {
                        this.sql = (String) batch[i];
                        prepareStatement();
                        changes[i] = db.executeUpdate(pointer, null, conn.getAutoCommit());
                    } catch (SQLException e) {
                        // don't use the constructor with long because of
                        // https://github.com/xerial/sqlite-jdbc/issues/1378
                        throw new BatchUpdateException(
                                "batch entry " + i + ": " + e.getMessage(),
                                null,
                                0,
                                Arrays.stream(changes).mapToInt(l -> (int) l).toArray(),
                                e);
                    } finally {
                        if (pointer != null) pointer.close();
                    }
                }
            } finally {
                clearBatch();
            }
        }

        return changes;
    }

    public void setCursorName(String name) {}

    public SQLWarning getWarnings() throws SQLException {
        return null;
    }

    public void clearWarnings() throws SQLException {}

    public Connection getConnection() throws SQLException {
        return conn;
    }

    public void cancel() throws SQLException {
        database.interrupt();
    }

    public int getQueryTimeout() throws SQLException {
        return queryTimeout;
    }

    public void setQueryTimeout(int seconds) throws SQLException {
        if (seconds < 0) {
            throw new SQLException("query timeout must be >= 0");
        }
        this.queryTimeout = seconds;
    }

    public int getMaxRows() throws SQLException {
        // checkOpen();
        return (int) rs.maxRows;
    }

    public long getLargeMaxRows() throws SQLException {
        // checkOpen();
        return rs.maxRows;
    }

    public void setMaxRows(int max) throws SQLException {
        setLargeMaxRows(max);
    }

    public void setLargeMaxRows(long max) throws SQLException {
        // checkOpen();
        if (max < 0) throw new SQLException("max row count must be >= 0");
        rs.maxRows = max;
    }

    public int getMaxFieldSize() throws SQLException {
        return 0;
    }

    public void setMaxFieldSize(int max) throws SQLException {
        if (max < 0) throw new SQLException("max field size " + max + " cannot be negative");
    }

    public int getFetchSize() throws SQLException {
        return ((ResultSet) rs).getFetchSize();
    }

    public void setFetchSize(int r) throws SQLException {
        ((ResultSet) rs).setFetchSize(r);
    }

    public int getFetchDirection() throws SQLException {
        return ResultSet.FETCH_FORWARD;
    }

    public void setFetchDirection(int direction) throws SQLException {
        switch (direction) {
            case ResultSet.FETCH_FORWARD:
            case ResultSet.FETCH_REVERSE:
            case ResultSet.FETCH_UNKNOWN:
                // No-op: SQLite does not support a value other than FETCH_FORWARD
                break;
            default:
                throw new SQLException(
                        "Unknown fetch direction "
                                + direction
                                + ". "
                                + "Must be one of FETCH_FORWARD, FETCH_REVERSE, or FETCH_UNKNOWN in java.sql.ResultSet");
        }
    }

    /**
     * SQLite does not support multiple results from execute().
     *
     * @see java.sql.Statement#getMoreResults()
     */
    public boolean getMoreResults() throws SQLException {
        return getMoreResults(Statement.CLOSE_CURRENT_RESULT);
    }

    public boolean getMoreResults(int current) throws SQLException {
        checkOpen();

        if (current == Statement.KEEP_CURRENT_RESULT || current == Statement.CLOSE_ALL_RESULTS) {
            throw new SQLFeatureNotSupportedException(
                    "Argument not supported: Statement.KEEP_CURRENT_RESULT or Statement.CLOSE_ALL_RESULTS");
        }
        if (current != Statement.CLOSE_CURRENT_RESULT) {
            throw new SQLException("Invalid argument");
        }

        // we support a single result set, close it
        rs.close();

        // as we don't have more result, change the update count to -1
        updateCount = -1;
        exhaustedResults = true;

        // always return false as we never have more results
        return false;
    }

    public int getResultSetConcurrency() throws SQLException {
        return ResultSet.CONCUR_READ_ONLY;
    }

    public int getResultSetHoldability() throws SQLException {
        return ResultSet.CLOSE_CURSORS_AT_COMMIT;
    }

    public int getResultSetType() throws SQLException {
        return ResultSet.TYPE_FORWARD_ONLY;
    }

    public void setEscapeProcessing(boolean enable) {
        // no-op
        // This previously threw a SQLException as unsupported (added in
        // 44e559b74d53e2ca006a4638f57a4e6662d0f2c0),
        // but it's not allowed to do that according to the method documentation.
        // This had impacts when using CachedRowSet for example (Github #224).
        // The default value according to the JDBC spec is true, so changing this should not have
        // any impact.
    }

    protected SQLException unsupported() {
        return new SQLFeatureNotSupportedException("not implemented by SQLite JDBC driver");
    }

    // Statement ////////////////////////////////////////////////////

    public boolean execute(String sql, int[] colinds) throws SQLException {
        throw unsupported();
    }

    public boolean execute(String sql, String[] colnames) throws SQLException {
        throw unsupported();
    }

    public int executeUpdate(String sql, int[] colinds) throws SQLException {
        throw unsupported();
    }

    public int executeUpdate(String sql, String[] cols) throws SQLException {
        throw unsupported();
    }

    public long executeLargeUpdate(String sql, int[] colinds) throws SQLException {
        throw unsupported();
    }

    public long executeLargeUpdate(String sql, String[] cols) throws SQLException {
        throw unsupported();
    }

    protected <T> T withConnectionTimeout(SQLCallable<T> callable) throws SQLException {
        int origBusyTimeout = conn.getBusyTimeout();
        if (queryTimeout > 0) {
            // SQLite handles busy timeout in milliseconds, JDBC in seconds
            conn.setBusyTimeout(1000 * queryTimeout);
        }
        try {
            return callable.call();
        } finally {
            if (queryTimeout > 0) {
                // reset connection timeout to the original value
                conn.setBusyTimeout(origBusyTimeout);
            }
        }
    }

    @FunctionalInterface
    protected interface SQLCallable<T> {

        T call() throws SQLException;
    }

    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (!isWrapperFor(iface)) throw new SQLException("not a wrapper for " + iface.getName());
        return iface.cast(this);
    }

    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        if (iface == null) throw new SQLException("interface must not be null");
        return iface.isInstance(this);
    }

    private boolean closed = false;

    @Override
    public void close() throws SQLException {
        try {
            clearGeneratedKeys();
            internalClose();
        } finally {
            closed = true;
        }
    }

    public boolean isClosed() throws SQLException {
        return closed || conn.isClosed();
    }

    boolean closeOnCompletion;

    public void closeOnCompletion() throws SQLException {
        if (closed) throw new SQLException("statement is closed");
        closeOnCompletion = true;
    }

    public boolean isCloseOnCompletion() throws SQLException {
        if (closed) throw new SQLException("statement is closed");
        return closeOnCompletion;
    }

    public void setPoolable(boolean poolable) throws SQLException {}

    public boolean isPoolable() throws SQLException {
        return false;
    }
}
