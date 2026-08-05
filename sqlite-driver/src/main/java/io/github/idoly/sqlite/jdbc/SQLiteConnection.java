package io.github.idoly.sqlite.jdbc;

import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Executor;
import javax.sql.rowset.serial.SerialBlob;
import javax.sql.rowset.serial.SerialClob;

/**
 * JDBC connection state machine.
 *
 * <p>Transactions start lazily, statements are tracked by identity, and close operations aggregate
 * failures while preserving every suppressed cause.
 */
final class SQLiteConnection implements Connection {
    private final NativeDatabase database;
    private final String url;
    private final boolean readOnly;
    private final SQLiteTransactionMode transactionMode;
    private final Set<SQLiteSavepoint> activeSavepoints = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<SQLiteStatement> openStatements = Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean autoCommit = true;
    private int savepointSequence;

    SQLiteConnection(NativeDatabase database) {
        this(database, SQLiteJdbcUrl.PREFIX, false, SQLiteTransactionMode.DEFERRED);
    }

    SQLiteConnection(NativeDatabase database, String url) {
        this(database, url, false, SQLiteTransactionMode.DEFERRED);
    }

    SQLiteConnection(NativeDatabase database, String url, boolean readOnly) {
        this(database, url, readOnly, SQLiteTransactionMode.DEFERRED);
    }

    SQLiteConnection(NativeDatabase database, String url, boolean readOnly, SQLiteTransactionMode transactionMode) {
        this.database = database;
        this.url = url;
        this.readOnly = readOnly;
        this.transactionMode = transactionMode;
    }

    @Override
    public synchronized void close() throws SQLException {
        if (isClosed()) {
            return;
        }

        SQLException failure = null;
        for (SQLiteStatement statement : Set.copyOf(openStatements)) {
            try {
                statement.close();
            } catch (SQLException error) {
                failure = appendSuppressed(failure, error);
            }
        }
        try {
            if (!database.isAutoCommit()) {
                executeTransaction("ROLLBACK");
            }
        } catch (SQLException error) {
            failure = appendSuppressed(failure, error);
        }

        clearSavepoints();
        try {
            database.close();
        } catch (NativeException error) {
            SQLException closeFailure = toSqlException(error);
            failure = appendSuppressed(failure, closeFailure);
        }

        if (failure != null) {
            throw failure;
        }
    }

    @Override
    public boolean isClosed() {
        return !database.isOpen();
    }

    @Override
    public boolean isValid(int timeout) throws SQLException {
        if (timeout < 0) {
            throw new SQLException("Timeout cannot be negative", "HY092");
        }
        return !isClosed();
    }

    @Override
    public String nativeSQL(String sql) throws SQLException {
        ensureOpen();
        if (sql == null) {
            throw new SQLException("SQL cannot be null", "HY009");
        }
        return sql;
    }

    @Override
    public synchronized boolean getAutoCommit() throws SQLException {
        ensureOpen();
        return autoCommit;
    }

    @Override
    public synchronized void setAutoCommit(boolean autoCommit) throws SQLException {
        ensureOpen();
        if (this.autoCommit == autoCommit) {
            return;
        }
        if (autoCommit && !database.isAutoCommit()) {
            closeOpenResults();
            executeTransaction("COMMIT");
            clearSavepoints();
        }
        this.autoCommit = autoCommit;
    }

    @Override
    public synchronized void commit() throws SQLException {
        ensureManualCommit();
        closeOpenResults();
        if (!database.isAutoCommit()) {
            executeTransaction("COMMIT");
        }
        clearSavepoints();
    }

    @Override
    public synchronized void rollback() throws SQLException {
        ensureManualCommit();
        closeOpenResults();
        if (!database.isAutoCommit()) {
            executeTransaction("ROLLBACK");
        }
        clearSavepoints();
    }

    @Override
    public boolean isReadOnly() throws SQLException {
        ensureOpen();
        return readOnly;
    }

    @Override
    public void setReadOnly(boolean readOnly) throws SQLException {
        ensureOpen();
        if (this.readOnly != readOnly) {
            throw unsupported("Read-only mode must be selected when the connection is opened");
        }
    }

    @Override
    public void setCatalog(String catalog) throws SQLException {
        ensureOpen();
        if (catalog != null) {
            throw unsupported("SQLite does not support catalogs");
        }
    }

    @Override
    public String getCatalog() throws SQLException {
        ensureOpen();
        return null;
    }

    @Override
    public void setSchema(String schema) throws SQLException {
        ensureOpen();
        if (schema != null) {
            throw unsupported("SQLite does not support JDBC schemas");
        }
    }

    @Override
    public String getSchema() throws SQLException {
        ensureOpen();
        return null;
    }

    @Override
    public void setTransactionIsolation(int level) throws SQLException {
        ensureOpen();
        if (level != TRANSACTION_SERIALIZABLE) {
            throw unsupported("Only TRANSACTION_SERIALIZABLE is currently reported");
        }
    }

    @Override
    public int getTransactionIsolation() throws SQLException {
        ensureOpen();
        return TRANSACTION_SERIALIZABLE;
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
    public Map<String, Class<?>> getTypeMap() throws SQLException {
        ensureOpen();
        return Collections.emptyMap();
    }

    @Override
    public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
        ensureOpen();
        if (map != null && !map.isEmpty()) {
            throw unsupported("Custom type maps are not supported");
        }
    }

    @Override
    public void setHoldability(int holdability) throws SQLException {
        ensureOpen();
        if (holdability != ResultSet.CLOSE_CURSORS_AT_COMMIT) {
            throw unsupported("Only CLOSE_CURSORS_AT_COMMIT is supported");
        }
    }

    @Override
    public int getHoldability() throws SQLException {
        ensureOpen();
        return ResultSet.CLOSE_CURSORS_AT_COMMIT;
    }

    @Override
    public Properties getClientInfo() throws SQLException {
        ensureOpen();
        return new Properties();
    }

    @Override
    public String getClientInfo(String name) throws SQLException {
        ensureOpen();
        return null;
    }

    @Override
    public void setClientInfo(String name, String value) throws SQLClientInfoException {
        if (value != null) {
            throw new SQLClientInfoException();
        }
    }

    @Override
    public void setClientInfo(Properties properties) throws SQLClientInfoException {
        if (properties != null && !properties.isEmpty()) {
            throw new SQLClientInfoException();
        }
    }

    @Override
    public void abort(Executor executor) throws SQLException {
        if (executor == null) {
            throw new SQLException("Executor cannot be null", "HY009");
        }
        close();
    }

    @Override
    public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
        ensureOpen();
        if (executor == null || milliseconds < 0) {
            throw new SQLException("Invalid network timeout arguments", "HY092");
        }
        if (milliseconds != 0) {
            throw unsupported("Network timeouts are not supported");
        }
    }

    @Override
    public int getNetworkTimeout() throws SQLException {
        ensureOpen();
        return 0;
    }

    @Override
    public <T> T unwrap(Class<T> type) throws SQLException {
        if (type != null && type.isInstance(this)) {
            return type.cast(this);
        }
        throw new SQLException("Connection does not wrap " + type, "HY000");
    }

    @Override
    public boolean isWrapperFor(Class<?> type) {
        return type != null && type.isInstance(this);
    }

    @Override
    public Statement createStatement() throws SQLException {
        return createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY, ResultSet.CLOSE_CURSORS_AT_COMMIT);
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
        return createStatement(resultSetType, resultSetConcurrency, ResultSet.CLOSE_CURSORS_AT_COMMIT);
    }

    @Override
    public synchronized Statement createStatement(
            int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        ensureOpen();
        JdbcSupport.validateResultSetMode(resultSetType, resultSetConcurrency, resultSetHoldability);
        return registerStatement(new SQLiteStatement(this));
    }

    @Override
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        return prepareStatement(
                sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY, ResultSet.CLOSE_CURSORS_AT_COMMIT);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency)
            throws SQLException {
        return prepareStatement(sql, resultSetType, resultSetConcurrency, ResultSet.CLOSE_CURSORS_AT_COMMIT);
    }

    @Override
    public synchronized PreparedStatement prepareStatement(
            String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        ensureOpen();
        JdbcSupport.validateResultSetMode(resultSetType, resultSetConcurrency, resultSetHoldability);
        return registerStatement(new SQLitePreparedStatement(this, sql));
    }

    @Override
    public synchronized PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
        boolean generatedKeys = JdbcSupport.generatedKeysRequested(autoGeneratedKeys);
        ensureOpen();
        return registerStatement(new SQLitePreparedStatement(this, sql, generatedKeys));
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
        return prepareStatement(sql, JdbcSupport.generatedKeysRequested(columnIndexes)
                ? Statement.RETURN_GENERATED_KEYS
                : Statement.NO_GENERATED_KEYS);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
        return prepareStatement(sql, JdbcSupport.generatedKeysRequested(columnNames)
                ? Statement.RETURN_GENERATED_KEYS
                : Statement.NO_GENERATED_KEYS);
    }

    @Override
    public CallableStatement prepareCall(String sql) throws SQLException {
        ensureOpen();
        throw unsupported("SQLite does not support stored procedures");
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        return prepareCall(sql);
    }

    @Override
    public CallableStatement prepareCall(
            String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        return prepareCall(sql);
    }

    @Override
    public DatabaseMetaData getMetaData() throws SQLException {
        ensureOpen();
        return new SQLiteDatabaseMetaData(this, url);
    }

    @Override
    public synchronized Savepoint setSavepoint() throws SQLException {
        return createSavepoint(null);
    }

    @Override
    public synchronized Savepoint setSavepoint(String name) throws SQLException {
        if (name == null) throw new SQLException("Savepoint name cannot be null", "HY009");
        return createSavepoint(name);
    }

    @Override
    public synchronized void rollback(Savepoint savepoint) throws SQLException {
        SQLiteSavepoint sqliteSavepoint = validateSavepoint(savepoint);
        executeTransaction("ROLLBACK TO SAVEPOINT \"" + sqliteSavepoint.sqlIdentifier() + "\"");
        for (SQLiteSavepoint candidate : Set.copyOf(activeSavepoints)) {
            if (candidate.sequenceNumber() > sqliteSavepoint.sequenceNumber()) {
                candidate.release();
                activeSavepoints.remove(candidate);
            }
        }
    }

    @Override
    public synchronized void releaseSavepoint(Savepoint savepoint) throws SQLException {
        SQLiteSavepoint sqliteSavepoint = validateSavepoint(savepoint);
        executeTransaction("RELEASE SAVEPOINT \"" + sqliteSavepoint.sqlIdentifier() + "\"");
        for (SQLiteSavepoint candidate : Set.copyOf(activeSavepoints)) {
            if (candidate.sequenceNumber() >= sqliteSavepoint.sequenceNumber()) {
                candidate.release();
                activeSavepoints.remove(candidate);
            }
        }
    }

    @Override
    public Clob createClob() throws SQLException {
        ensureOpen();
        return new SerialClob(new char[0]);
    }

    @Override
    public Blob createBlob() throws SQLException {
        ensureOpen();
        return new SerialBlob(new byte[0]);
    }

    @Override
    public NClob createNClob() throws SQLException {
        ensureOpen();
        return new SQLiteNClob();
    }

    @Override
    public SQLXML createSQLXML() throws SQLException {
        ensureOpen();
        return new SQLiteSQLXML();
    }

    @Override
    public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
        ensureOpen();
        throw unsupported("SQL array values are not supported");
    }

    @Override
    public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
        ensureOpen();
        throw unsupported("SQL struct values are not supported");
    }

    synchronized int executeUpdateSql(String sql) throws SQLException {
        try (NativeStatement statement = prepareForExecution(sql)) {
            if (statement.columnCount() != 0) {
                throw new SQLException("SQL produces a result set", "07000");
            }
            return statement.executeUpdate();
        } catch (NativeException error) {
            throw toSqlException(error);
        }
    }

    synchronized NativeStatement prepareForExecution(String sql) throws SQLException {
        beginTransactionIfNeeded();
        return prepareForReuse(sql);
    }

    synchronized NativeStatement prepareForReuse(String sql) throws SQLException {
        ensureOpen();
        if (sql == null) {
            throw new SQLException("SQL cannot be null", "HY009");
        }
        try {
            return database.prepare(sql);
        } catch (NativeException error) {
            throw toSqlException(error);
        }
    }

    void interrupt() {
        database.interrupt();
    }

    synchronized void unregisterStatement(SQLiteStatement statement) {
        openStatements.remove(statement);
    }

    private synchronized <T extends SQLiteStatement> T registerStatement(T statement) {
        openStatements.add(statement);
        return statement;
    }

    private void closeOpenResults() throws SQLException {
        SQLException failure = null;
        for (SQLiteStatement statement : Set.copyOf(openStatements)) {
            try {
                statement.closeResultsAtTransactionBoundary();
            } catch (SQLException error) {
                failure = appendSuppressed(failure, error);
            }
        }
        if (failure != null) throw failure;
    }

    synchronized void beginTransactionIfNeeded() throws SQLException {
        ensureOpen();
        if (!autoCommit && database.isAutoCommit()) {
            executeTransaction(transactionMode.beginSql());
        }
    }

    private SQLiteSavepoint createSavepoint(String savepointName) throws SQLException {
        ensureManualCommit();
        beginTransactionIfNeeded();
        SQLiteSavepoint savepoint = new SQLiteSavepoint(this, ++savepointSequence, savepointName);
        executeTransaction("SAVEPOINT \"" + savepoint.sqlIdentifier() + "\"");
        activeSavepoints.add(savepoint);
        return savepoint;
    }

    private SQLiteSavepoint validateSavepoint(Savepoint savepoint) throws SQLException {
        ensureManualCommit();
        if (!(savepoint instanceof SQLiteSavepoint sqliteSavepoint)) {
            throw new SQLException("Savepoint was not created by this driver", "3B001");
        }
        sqliteSavepoint.ensureOwnedBy(this);
        if (!activeSavepoints.contains(sqliteSavepoint)) {
            throw new SQLException("Savepoint is no longer active", "3B001");
        }
        return sqliteSavepoint;
    }

    private void clearSavepoints() {
        for (SQLiteSavepoint savepoint : activeSavepoints) savepoint.release();
        activeSavepoints.clear();
    }

    private void ensureManualCommit() throws SQLException {
        ensureOpen();
        if (autoCommit) {
            throw new SQLException("Cannot complete a transaction while auto-commit is enabled", "25000");
        }
    }

    private void executeTransaction(String sql) throws SQLException {
        try {
            database.execute(sql);
        } catch (NativeException error) {
            throw toSqlException(error);
        }
    }

    private void ensureOpen() throws SQLException {
        if (isClosed()) {
            throw new SQLException("Connection is closed", "08003");
        }
    }

    private static SQLException toSqlException(NativeException error) {
        return SqlExceptionMapper.map(error);
    }

    private static SQLException appendSuppressed(SQLException current, SQLException additional) {
        if (current == null) return additional;
        current.addSuppressed(additional);
        return current;
    }

    private static SQLFeatureNotSupportedException unsupported(String message) {
        return JdbcSupport.unsupported(message);
    }
}
