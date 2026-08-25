package io.github.idoly.sqlite.internal;

import io.github.idoly.sqlite.SQLiteConfig;
import io.github.idoly.sqlite.SQLiteConfig.TransactionMode;
import io.github.idoly.sqlite.SQLiteConnection;
import io.github.idoly.sqlite.SQLiteOpenMode;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLWarning;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class BaseConnection extends SQLiteConnection {
    private final AtomicInteger savePoint = new AtomicInteger(0);
    private Map<String, Class<?>> typeMap;

    private boolean readOnly = false;

    protected BaseConnection(String url, String fileName, Properties prop) throws SQLException {
        super(url, fileName, prop);
    }

    /**
     * This will try to enforce the transaction mode if SQLiteConfig#isExplicitReadOnly is true and
     * auto commit is disabled.
     *
     * <ul>
     *   <li>If this connection is read only, the PRAGMA query_only will be set
     *   <li>If this connection is not read only:
     *       <ul>
     *         <li>if no statement has been executed, PRAGMA query_only will be set to false, and an
     *             IMMEDIATE transaction will be started
     *         <li>if a statement has already been executed, an exception is thrown
     *       </ul>
     * </ul>
     *
     * @throws SQLException if a statement has already been executed on this connection, then the
     *     transaction cannot be upgraded to write
     */
    @SuppressWarnings("deprecation")
    public void tryEnforceTransactionMode() throws SQLException {
        // important note: read-only mode is only supported when auto-commit is disabled
        if (getDatabase().getConfig().isExplicitReadOnly()
                && !this.getAutoCommit()
                && this.getCurrentTransactionMode() != null) {
            if (isReadOnly()) {
                // this is a read-only transaction, make sure all writing operations are rejected by
                // the SQLiteDatabase
                // (note: this pragma is evaluated on a per-transaction basis by SQLite)
                getDatabase()._exec("PRAGMA query_only = true;");
            } else {
                if (getCurrentTransactionMode() == TransactionMode.DEFERRED) {
                    if (isFirstStatementExecuted()) {
                        // first statement was already executed; cannot upgrade to write
                        // transaction!
                        throw new SQLException(
                                "A statement has already been executed on this connection; cannot upgrade to write transaction");
                    } else {
                        // this is the first statement in the transaction; close and create an
                        // immediate one
                        getDatabase()._exec("commit; /* need to explicitly upgrade transaction */");

                        // start the write transaction
                        getDatabase()._exec("PRAGMA query_only = false;");
                        getDatabase()
                                ._exec("BEGIN IMMEDIATE; /* explicitly upgrade transaction */");
                        setCurrentTransactionMode(TransactionMode.IMMEDIATE);
                    }
                }
            }
        }
    }

    public String getCatalog() throws SQLException {
        checkOpen();
        return null;
    }

    public void setCatalog(String catalog) throws SQLException {
        checkOpen();
    }

    public int getHoldability() throws SQLException {
        checkOpen();
        return ResultSet.CLOSE_CURSORS_AT_COMMIT;
    }

    public void setHoldability(int h) throws SQLException {
        checkOpen();
        if (h != ResultSet.CLOSE_CURSORS_AT_COMMIT) {
            throw new SQLException("SQLite only supports CLOSE_CURSORS_AT_COMMIT");
        }
    }

    public Map<String, Class<?>> getTypeMap() throws SQLException {
        synchronized (this) {
            if (this.typeMap == null) {
                this.typeMap = new HashMap<String, Class<?>>();
            }

            return this.typeMap;
        }
    }

    public void setTypeMap(Map map) throws SQLException {
        synchronized (this) {
            this.typeMap = map;
        }
    }

    public boolean isReadOnly() {
        SQLiteConfig config = getDatabase().getConfig();
        return (
        // the entire database is read-only
        ((config.getOpenModeFlags() & SQLiteOpenMode.READONLY.flag) != 0)
                // the flag was set explicitly by the user on this connection
                || (config.isExplicitReadOnly() && this.readOnly));
    }

    public void setReadOnly(boolean ro) throws SQLException {
        if (getDatabase().getConfig().isExplicitReadOnly()) {
            if (ro != readOnly && isFirstStatementExecuted()) {
                throw new SQLException(
                        "Cannot change Read-Only status of this connection: the first statement was"
                                + " already executed and the transaction is open.");
            }
        } else {
            // trying to change read-only flag
            if (ro != isReadOnly()) {
                throw new SQLException(
                        "Cannot change read-only flag after establishing a connection."
                                + " Use SQLiteConfig#setReadOnly and SQLiteConfig.createConnection().");
            }
        }
        this.readOnly = ro;
    }

    public String nativeSQL(String sql) {
        return sql;
    }

    public void clearWarnings() throws SQLException {}

    public SQLWarning getWarnings() throws SQLException {
        return null;
    }

    public Statement createStatement() throws SQLException {
        return createStatement(
                ResultSet.TYPE_FORWARD_ONLY,
                ResultSet.CONCUR_READ_ONLY,
                ResultSet.CLOSE_CURSORS_AT_COMMIT);
    }

    public Statement createStatement(int rsType, int rsConcurr) throws SQLException {
        return createStatement(rsType, rsConcurr, ResultSet.CLOSE_CURSORS_AT_COMMIT);
    }

    public abstract Statement createStatement(int rst, int rsc, int rsh) throws SQLException;

    public CallableStatement prepareCall(String sql) throws SQLException {
        return prepareCall(
                sql,
                ResultSet.TYPE_FORWARD_ONLY,
                ResultSet.CONCUR_READ_ONLY,
                ResultSet.CLOSE_CURSORS_AT_COMMIT);
    }

    public CallableStatement prepareCall(String sql, int rst, int rsc) throws SQLException {
        return prepareCall(sql, rst, rsc, ResultSet.CLOSE_CURSORS_AT_COMMIT);
    }

    public CallableStatement prepareCall(String sql, int rst, int rsc, int rsh)
            throws SQLException {
        throw new SQLException("SQLite does not support Stored Procedures");
    }

    public PreparedStatement prepareStatement(String sql) throws SQLException {
        return prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
    }

    public PreparedStatement prepareStatement(String sql, int autoC) throws SQLException {
        return prepareStatement(sql);
    }

    public PreparedStatement prepareStatement(String sql, int[] colInds) throws SQLException {
        return prepareStatement(sql);
    }

    public PreparedStatement prepareStatement(String sql, String[] colNames) throws SQLException {
        return prepareStatement(sql);
    }

    public PreparedStatement prepareStatement(String sql, int rst, int rsc) throws SQLException {
        return prepareStatement(sql, rst, rsc, ResultSet.CLOSE_CURSORS_AT_COMMIT);
    }

    public abstract PreparedStatement prepareStatement(String sql, int rst, int rsc, int rsh)
            throws SQLException;

    public Savepoint setSavepoint() throws SQLException {
        checkSavepointMode();
        SavepointImpl savepoint = new SavepointImpl(this, savePoint.incrementAndGet());
        getDatabase().exec("SAVEPOINT " + quoteIdentifier(savepoint.sqliteName()), false);
        return savepoint;
    }

    public Savepoint setSavepoint(String name) throws SQLException {
        checkSavepointMode();
        if (name == null) throw new SQLException("savepoint name must not be null");
        SavepointImpl savepoint = new SavepointImpl(this, savePoint.incrementAndGet(), name);
        getDatabase().exec("SAVEPOINT " + quoteIdentifier(savepoint.sqliteName()), false);
        return savepoint;
    }

    public void releaseSavepoint(Savepoint savepoint) throws SQLException {
        checkOpen();
        if (getAutoCommit()) {
            throw new SQLException("database in auto-commit mode");
        }
        SavepointImpl sqliteSavepoint = requireSavepoint(savepoint);
        getDatabase()
                .exec("RELEASE SAVEPOINT " + quoteIdentifier(sqliteSavepoint.sqliteName()), false);
    }

    public void rollback(Savepoint savepoint) throws SQLException {
        checkOpen();
        if (getAutoCommit()) {
            throw new SQLException("database in auto-commit mode");
        }
        SavepointImpl sqliteSavepoint = requireSavepoint(savepoint);
        getDatabase()
                .exec(
                        "ROLLBACK TO SAVEPOINT " + quoteIdentifier(sqliteSavepoint.sqliteName()),
                        getAutoCommit());
    }

    private void checkSavepointMode() throws SQLException {
        checkOpen();
        if (getAutoCommit()) throw new SQLException("database in auto-commit mode");
    }

    private SavepointImpl requireSavepoint(Savepoint savepoint) throws SQLException {
        if (!(savepoint instanceof SavepointImpl sqliteSavepoint)
                || !sqliteSavepoint.belongsTo(this)) {
            throw new SQLException("savepoint does not belong to this connection");
        }
        return sqliteSavepoint;
    }

    private static String quoteIdentifier(String identifier) {
        return '"' + identifier.replace("\"", "\"\"") + '"';
    }

    public Struct createStruct(String t, Object[] attr) throws SQLException {
        throw new SQLFeatureNotSupportedException("not implemented by SQLite JDBC driver");
    }
}
