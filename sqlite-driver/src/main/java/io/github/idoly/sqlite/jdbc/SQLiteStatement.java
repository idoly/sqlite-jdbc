package io.github.idoly.sqlite.jdbc;

import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Forward-only statement and base execution template for prepared statements.
 *
 * <p>Result ownership, generated keys, batch state, and close-on-completion live here; timeout and
 * native-exception behavior are delegated to {@link ExecutionController}.
 */
class SQLiteStatement implements Statement {
    final SQLiteConnection connection;

    private final ExecutionController execution;
    private final List<String> batchSql = new ArrayList<>();
    private boolean closed;
    private boolean poolable;
    private boolean closeOnCompletion;
    SQLiteResultSet currentResultSet;
    int updateCount = -1;
    private Long generatedKey;
    private SQLiteResultSet generatedKeysResultSet;
    private int maxRows;
    private int fetchSize;

    SQLiteStatement(SQLiteConnection connection) {
        this.connection = connection;
        execution = new ExecutionController(connection);
    }

    @Override
    public ResultSet executeQuery(String sql) throws SQLException {
        ensureOpen();
        resetExecution();
        NativeStatement nativeStatement = connection.prepareForExecution(sql);
        if (nativeStatement.columnCount() == 0) {
            nativeStatement.close();
            throw new SQLException("SQL does not produce a result set", "07000");
        }
        try {
            currentResultSet = new SQLiteResultSet(this, nativeStatement);
            updateCount = -1;
            return currentResultSet;
        } catch (SQLException | RuntimeException error) {
            closeAfterFailure(nativeStatement, error);
            throw error;
        }
    }

    @Override
    public int executeUpdate(String sql) throws SQLException {
        ensureOpen();
        resetExecution();
        updateCount = execution.run(() -> connection.executeUpdateSql(sql));
        return updateCount;
    }

    @Override
    public void close() throws SQLException {
        if (closed) return;
        closeCurrentResultSet();
        closeGeneratedKeys();
        closed = true;
        batchSql.clear();
        updateCount = -1;
        connection.unregisterStatement(this);
    }

    @Override
    public int getMaxFieldSize() throws SQLException {
        ensureOpen();
        return 0;
    }

    @Override
    public void setMaxFieldSize(int max) throws SQLException {
        ensureOpen();
        requireNonNegative(max, "Maximum field size");
        if (max != 0) {
            throw unsupported("Maximum field size is not supported");
        }
    }

    @Override
    public int getMaxRows() throws SQLException {
        ensureOpen();
        return maxRows;
    }

    @Override
    public void setMaxRows(int max) throws SQLException {
        ensureOpen();
        requireNonNegative(max, "Maximum rows");
        maxRows = max;
    }

    @Override
    public void setEscapeProcessing(boolean enable) throws SQLException {
        ensureOpen();
        if (enable) {
            throw unsupported("JDBC escape processing is not supported");
        }
    }

    @Override
    public int getQueryTimeout() throws SQLException {
        ensureOpen();
        return execution.timeoutSeconds();
    }

    @Override
    public void setQueryTimeout(int seconds) throws SQLException {
        ensureOpen();
        requireNonNegative(seconds, "Query timeout");
        execution.setTimeoutSeconds(seconds);
    }

    @Override
    public void cancel() throws SQLException {
        ensureOpen();
        connection.interrupt();
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        ensureOpen();
        return null;
    }

    @Override
    public void clearWarnings() throws SQLException {
        ensureOpen();
    }

    @Override
    public void setCursorName(String name) throws SQLException {
        ensureOpen();
        throw unsupported("Named cursors are not supported");
    }

    @Override
    public boolean execute(String sql) throws SQLException {
        ensureOpen();
        resetExecution();
        NativeStatement nativeStatement = connection.prepareForExecution(sql);
        if (nativeStatement.columnCount() > 0) {
            try {
                currentResultSet = new SQLiteResultSet(this, nativeStatement);
                updateCount = -1;
                return true;
            } catch (SQLException | RuntimeException error) {
                closeAfterFailure(nativeStatement, error);
                throw error;
            }
        }
        try (nativeStatement) {
            updateCount = runUpdate(nativeStatement);
            return false;
        } catch (NativeException error) {
            throw SqlExceptionMapper.map(error);
        }
    }

    @Override
    public ResultSet getResultSet() throws SQLException {
        ensureOpen();
        return currentResultSet;
    }

    @Override
    public int getUpdateCount() throws SQLException {
        ensureOpen();
        return updateCount;
    }

    @Override
    public long getLargeUpdateCount() throws SQLException {
        return getUpdateCount();
    }

    @Override
    public boolean getMoreResults() throws SQLException {
        return getMoreResults(CLOSE_CURRENT_RESULT);
    }

    @Override
    public void setFetchDirection(int direction) throws SQLException {
        ensureOpen();
        if (direction != ResultSet.FETCH_FORWARD) {
            throw unsupported("Only FETCH_FORWARD is supported");
        }
    }

    @Override
    public int getFetchDirection() throws SQLException {
        ensureOpen();
        return ResultSet.FETCH_FORWARD;
    }

    @Override
    public void setFetchSize(int rows) throws SQLException {
        ensureOpen();
        requireNonNegative(rows, "Fetch size");
        fetchSize = rows;
    }

    @Override
    public int getFetchSize() throws SQLException {
        ensureOpen();
        return fetchSize;
    }

    @Override
    public int getResultSetConcurrency() throws SQLException {
        ensureOpen();
        return ResultSet.CONCUR_READ_ONLY;
    }

    @Override
    public int getResultSetType() throws SQLException {
        ensureOpen();
        return ResultSet.TYPE_FORWARD_ONLY;
    }

    @Override
    public void addBatch(String sql) throws SQLException {
        ensureOpen();
        if (sql == null) {
            throw new SQLException("SQL cannot be null", "HY009");
        }
        batchSql.add(sql);
    }

    @Override
    public void clearBatch() throws SQLException {
        ensureOpen();
        batchSql.clear();
    }

    @Override
    public int[] executeBatch() throws SQLException {
        ensureOpen();
        resetExecution();
        int[] counts = new int[batchSql.size()];
        int completed = 0;
        try {
            for (; completed < batchSql.size(); completed++) {
                int batchIndex = completed;
                counts[completed] = execution.run(() -> connection.executeUpdateSql(batchSql.get(batchIndex)));
            }
            updateCount = -1;
            return counts;
        } catch (SQLException error) {
            int[] successful = java.util.Arrays.copyOf(counts, completed);
            throw new BatchUpdateException(
                    error.getMessage(), error.getSQLState(), error.getErrorCode(), successful, error);
        } finally {
            batchSql.clear();
        }
    }

    @Override
    public long[] executeLargeBatch() throws SQLException {
        int[] counts = executeBatch();
        long[] largeCounts = new long[counts.length];
        for (int index = 0; index < counts.length; index++) largeCounts[index] = counts[index];
        return largeCounts;
    }

    @Override
    public Connection getConnection() throws SQLException {
        ensureOpen();
        return connection;
    }

    @Override
    public boolean getMoreResults(int current) throws SQLException {
        ensureOpen();
        if (current != CLOSE_CURRENT_RESULT && current != CLOSE_ALL_RESULTS && current != KEEP_CURRENT_RESULT) {
            throw new SQLException("Invalid result disposition: " + current, "HY092");
        }
        if (current != KEEP_CURRENT_RESULT) closeCurrentResultSet();
        updateCount = -1;
        return false;
    }

    @Override
    public ResultSet getGeneratedKeys() throws SQLException {
        ensureOpen();
        closeGeneratedKeys();
        String query = generatedKey == null
                ? "SELECT CAST(NULL AS INTEGER) AS GENERATED_KEY WHERE 0"
                : "SELECT CAST(" + generatedKey + " AS INTEGER) AS GENERATED_KEY";
        generatedKeysResultSet = new SQLiteResultSet(this, connection.prepareForReuse(query));
        return generatedKeysResultSet;
    }

    @Override
    public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
        boolean generatedKeys = JdbcSupport.generatedKeysRequested(autoGeneratedKeys);
        ensureOpen();
        resetExecution();
        updateCount = execution.run(() -> connection.executeUpdateSql(sql));
        captureGeneratedKey(generatedKeys, sql);
        return updateCount;
    }

    @Override
    public int executeUpdate(String sql, int[] columnIndexes) throws SQLException {
        return executeUpdate(sql, JdbcSupport.generatedKeysRequested(columnIndexes)
                ? RETURN_GENERATED_KEYS
                : NO_GENERATED_KEYS);
    }

    @Override
    public int executeUpdate(String sql, String[] columnNames) throws SQLException {
        return executeUpdate(sql, JdbcSupport.generatedKeysRequested(columnNames)
                ? RETURN_GENERATED_KEYS
                : NO_GENERATED_KEYS);
    }

    @Override
    public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
        boolean generatedKeys = JdbcSupport.generatedKeysRequested(autoGeneratedKeys);
        boolean hasResultSet = execute(sql);
        if (!hasResultSet) captureGeneratedKey(generatedKeys, sql);
        return hasResultSet;
    }

    @Override
    public boolean execute(String sql, int[] columnIndexes) throws SQLException {
        return execute(sql, JdbcSupport.generatedKeysRequested(columnIndexes)
                ? RETURN_GENERATED_KEYS
                : NO_GENERATED_KEYS);
    }

    @Override
    public boolean execute(String sql, String[] columnNames) throws SQLException {
        return execute(sql, JdbcSupport.generatedKeysRequested(columnNames)
                ? RETURN_GENERATED_KEYS
                : NO_GENERATED_KEYS);
    }

    @Override
    public long executeLargeUpdate(String sql) throws SQLException {
        return executeUpdate(sql);
    }

    @Override
    public long executeLargeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
        return executeUpdate(sql, autoGeneratedKeys);
    }

    @Override
    public long executeLargeUpdate(String sql, int[] columnIndexes) throws SQLException {
        return executeUpdate(sql, columnIndexes);
    }

    @Override
    public long executeLargeUpdate(String sql, String[] columnNames) throws SQLException {
        return executeUpdate(sql, columnNames);
    }

    @Override
    public long getLargeMaxRows() throws SQLException {
        return getMaxRows();
    }

    @Override
    public void setLargeMaxRows(long max) throws SQLException {
        ensureOpen();
        if (max < 0 || max > Integer.MAX_VALUE) {
            throw new SQLException("Maximum rows must be between 0 and " + Integer.MAX_VALUE, "HY092");
        }
        setMaxRows((int) max);
    }

    @Override
    public int getResultSetHoldability() throws SQLException {
        ensureOpen();
        return ResultSet.CLOSE_CURSORS_AT_COMMIT;
    }

    @Override
    public boolean isClosed() {
        return closed || connection.isClosed();
    }

    @Override
    public void setPoolable(boolean poolable) throws SQLException {
        ensureOpen();
        this.poolable = poolable;
    }

    @Override
    public boolean isPoolable() throws SQLException {
        ensureOpen();
        return poolable;
    }

    @Override
    public void closeOnCompletion() throws SQLException {
        ensureOpen();
        closeOnCompletion = true;
    }

    @Override
    public boolean isCloseOnCompletion() throws SQLException {
        ensureOpen();
        return closeOnCompletion;
    }

    @Override
    public <T> T unwrap(Class<T> type) throws SQLException {
        if (type != null && type.isInstance(this)) {
            return type.cast(this);
        }
        throw new SQLException("Statement does not wrap " + type, "HY000");
    }

    @Override
    public boolean isWrapperFor(Class<?> type) {
        return type != null && type.isInstance(this);
    }

    void onResultSetClosed(SQLiteResultSet resultSet) throws SQLException {
        boolean dependentResult = false;
        if (currentResultSet == resultSet) {
            currentResultSet = null;
            dependentResult = true;
        }
        if (generatedKeysResultSet == resultSet) {
            generatedKeysResultSet = null;
            dependentResult = true;
        }
        if (dependentResult && closeOnCompletion && currentResultSet == null
                && generatedKeysResultSet == null && !closed) {
            close();
        }
    }

    void ensureOpen() throws SQLException {
        if (isClosed()) {
            throw new SQLException("Statement is closed", "07000");
        }
    }

    final void closeResultsAtTransactionBoundary() throws SQLException {
        closeCurrentResultSet();
        closeGeneratedKeys();
    }

    final void resetExecution() throws SQLException {
        closeCurrentResultSet();
        closeGeneratedKeys();
        generatedKey = null;
    }

    final void closeCurrentResultSet() throws SQLException {
        SQLiteResultSet resultSet = currentResultSet;
        currentResultSet = null;
        if (resultSet != null && !resultSet.isClosed()) resultSet.close();
    }

    private void closeGeneratedKeys() throws SQLException {
        SQLiteResultSet resultSet = generatedKeysResultSet;
        generatedKeysResultSet = null;
        if (resultSet != null && !resultSet.isClosed()) resultSet.close();
    }

    final void captureGeneratedKey(boolean requested, String sql) throws SQLException {
        generatedKey = requested && JdbcSupport.mayGenerateKey(sql) ? connection.lastInsertRowId() : null;
    }

    final StepResult step(NativeStatement statement) throws SQLException {
        return execution.run(statement::step);
    }

    final int runUpdate(NativeStatement statement) throws SQLException {
        return execution.run(statement::executeUpdate);
    }

    static SQLFeatureNotSupportedException unsupported(String message) {
        return JdbcSupport.unsupported(message);
    }

    private static void requireNonNegative(int value, String label) throws SQLException {
        if (value < 0) {
            throw new SQLException(label + " cannot be negative", "HY092");
        }
    }

    private static void closeAfterFailure(NativeStatement statement, Throwable failure) {
        try {
            statement.close();
        } catch (RuntimeException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }

}
