package io.github.idoly.sqlite;

import io.github.idoly.sqlite.SQLiteConfig.TransactionMode;
import io.github.idoly.sqlite.core.SQLiteDatabase;
import io.github.idoly.sqlite.ffm.FfmDatabase;
import io.github.idoly.sqlite.internal.DatabaseMetaDataImpl;
import io.github.idoly.sqlite.internal.PreparedStatementImpl;
import io.github.idoly.sqlite.internal.StatementImpl;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

public final class SQLiteConnection implements Connection {
    private static final String RESOURCE_NAME_PREFIX = ":resource:";
    private final SQLiteDatabase db;
    private DatabaseMetaDataImpl meta = null;
    private final SQLiteConnectionConfig connectionConfig;

    private TransactionMode currentTransactionMode;
    private boolean firstStatementExecuted = false;

    /**
     * Constructor to create a connection to a database at the given location.
     *
     * @param url The location of the database.
     * @param fileName The database.
     */
    public SQLiteConnection(String url, String fileName) throws SQLException {
        this(url, fileName, new Properties());
    }

    /**
     * Constructor to create a pre-configured connection to a database at the given location.
     *
     * @param url The location of the database file.
     * @param fileName The database.
     * @param prop The configurations to apply.
     */
    public SQLiteConnection(String url, String fileName, Properties prop) throws SQLException {
        SQLiteDatabase newDatabase = null;
        try {
            this.db = newDatabase = open(url, fileName, prop);
            SQLiteConfig config = this.db.getConfig();
            this.connectionConfig = this.db.getConfig().newConnectionConfig();
            config.apply(this);
            this.currentTransactionMode = db.getConfig().getTransactionMode();
            // connection starts in "clean" state (even though some PRAGMA statements were executed)
            this.firstStatementExecuted = false;
        } catch (Throwable t) {
            try {
                if (newDatabase != null) {
                    newDatabase.close();
                }
            } catch (Throwable closeError) {
                t.addSuppressed(closeError);
            }
            throw t;
        }
    }

    public TransactionMode getCurrentTransactionMode() {
        return this.currentTransactionMode;
    }

    public void setCurrentTransactionMode(final TransactionMode currentTransactionMode) {
        this.currentTransactionMode = currentTransactionMode;
    }

    public void setFirstStatementExecuted(final boolean firstStatementExecuted) {
        this.firstStatementExecuted = firstStatementExecuted;
    }

    public boolean isFirstStatementExecuted() {
        return firstStatementExecuted;
    }

    public SQLiteConnectionConfig getConnectionConfig() {
        return connectionConfig;
    }

    private DatabaseMetaDataImpl databaseMetaData() throws SQLException {
        checkOpen();
        if (meta == null) meta = new DatabaseMetaDataImpl(this);
        return meta;
    }

    @Override
    public DatabaseMetaData getMetaData() throws SQLException {
        return databaseMetaData();
    }

    public String getUrl() {
        return db.getUrl();
    }

    public void setSchema(String schema) throws SQLException {
        checkOpen();
        throw new SQLFeatureNotSupportedException("SQLite has no current schema");
    }

    public String getSchema() throws SQLException {
        checkOpen();
        return null;
    }

    public void abort(Executor executor) throws SQLException {
        if (executor == null) throw new SQLException("executor must not be null");
        if (isClosed()) return;
        try {
            executor.execute(
                    () -> {
                        try {
                            close();
                        } catch (SQLException error) {
                            throw new IllegalStateException(
                                    "Could not abort SQLite connection", error);
                        }
                    });
        } catch (RuntimeException error) {
            throw new SQLException("Could not schedule SQLite connection abort", error);
        }
    }

    public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
        checkOpen();
        if (executor == null) throw new SQLException("executor must not be null");
        if (milliseconds < 0) throw new SQLException("milliseconds must be >= 0");
        if (milliseconds != 0) {
            throw new SQLFeatureNotSupportedException(
                    "SQLite does not use a network connection timeout");
        }
    }

    public int getNetworkTimeout() throws SQLException {
        checkOpen();
        return 0;
    }

    /**
     * Checks whether the type, concurrency, and holdability settings for a {@link ResultSet} are
     * supported by the SQLite interface. Supported settings are:
     *
     * <ul>
     *   <li>type: {@link ResultSet#TYPE_FORWARD_ONLY}
     *   <li>concurrency: {@link ResultSet#CONCUR_READ_ONLY})
     *   <li>holdability: {@link ResultSet#CLOSE_CURSORS_AT_COMMIT}
     * </ul>
     *
     * @param rst the type setting.
     * @param rsc the concurrency setting.
     * @param rsh the holdability setting.
     */
    protected void checkCursor(int rst, int rsc, int rsh) throws SQLException {
        if (rst != ResultSet.TYPE_FORWARD_ONLY)
            throw new SQLException("SQLite only supports TYPE_FORWARD_ONLY cursors");
        if (rsc != ResultSet.CONCUR_READ_ONLY)
            throw new SQLException("SQLite only supports CONCUR_READ_ONLY cursors");
        if (rsh != ResultSet.CLOSE_CURSORS_AT_COMMIT)
            throw new SQLException("SQLite only supports closing cursors at commit");
    }

    /**
     * Sets the mode that will be used to start transactions on this connection.
     *
     * @param mode One of {@link SQLiteConfig.TransactionMode}
     * @see <a
     *     href="https://www.sqlite.org/lang_transaction.html">https://www.sqlite.org/lang_transaction.html</a>
     */
    protected void setTransactionMode(SQLiteConfig.TransactionMode mode) {
        connectionConfig.setTransactionMode(mode);
    }

    @Override
    public int getTransactionIsolation() {
        return connectionConfig.getTransactionIsolation();
    }

    public void setTransactionIsolation(int level) throws SQLException {
        checkOpen();

        switch (level) {
            case java.sql.Connection.TRANSACTION_READ_COMMITTED:
            case java.sql.Connection.TRANSACTION_REPEATABLE_READ:
            // Fall-through: Spec allows upgrading isolation to a higher level
            case java.sql.Connection.TRANSACTION_SERIALIZABLE:
                db.exec("PRAGMA read_uncommitted = false;", getAutoCommit());
                break;
            case java.sql.Connection.TRANSACTION_READ_UNCOMMITTED:
                db.exec("PRAGMA read_uncommitted = true;", getAutoCommit());
                break;
            default:
                throw new SQLException(
                        "Unsupported transaction isolation level: "
                                + level
                                + ". "
                                + "Must be one of TRANSACTION_READ_UNCOMMITTED, TRANSACTION_READ_COMMITTED, "
                                + "TRANSACTION_REPEATABLE_READ, or TRANSACTION_SERIALIZABLE in java.sql.Connection");
        }
        connectionConfig.setTransactionIsolation(level);
    }

    /**
     * Opens a connection to the database using an SQLite library. * @throws SQLException
     *
     * @see <a
     *     href="https://www.sqlite.org/c3ref/c_open_autoproxy.html">https://www.sqlite.org/c3ref/c_open_autoproxy.html</a>
     */
    private static SQLiteDatabase open(String url, String origFileName, Properties props)
            throws SQLException {
        // Create a copy of the given properties
        Properties newProps = new Properties();
        if (props != null) newProps.putAll(props);

        // Extract pragma as properties
        String fileName = extractPragmasFromFilename(url, origFileName, newProps);
        SQLiteConfig config = new SQLiteConfig(newProps);

        // check the path to the file exists
        if (!fileName.isEmpty()
                && !":memory:".equals(fileName)
                && !fileName.startsWith("file:")
                && !fileName.contains("mode=memory")) {
            if (fileName.startsWith(RESOURCE_NAME_PREFIX)) {
                String resourceName = fileName.substring(RESOURCE_NAME_PREFIX.length());

                // search the class path
                ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
                URL resourceAddr =
                        contextClassLoader == null
                                ? SQLiteConnection.class.getResource("/" + resourceName)
                                : contextClassLoader.getResource(resourceName);
                if (resourceAddr == null) {
                    try {
                        resourceAddr = new URI(resourceName).toURL();
                    } catch (MalformedURLException | URISyntaxException error) {
                        throw new SQLException("resource " + resourceName + " not found", error);
                    }
                }

                try {
                    fileName = extractResource(resourceAddr).getAbsolutePath();
                } catch (IOException error) {
                    throw new SQLException("failed to load " + resourceName, error);
                }
            } else {
                fileName = new File(fileName).getAbsolutePath();
            }
        }

        try {
            FfmDatabase.load();
        } catch (ExceptionInInitializerError | NoClassDefFoundError error) {
            throw new SQLException("Could not initialize the packaged SQLite library", error);
        }
        SQLiteDatabase db = new FfmDatabase(url, fileName, config);
        try {
            db.open(fileName, config.getOpenModeFlags());
            return db;
        } catch (SQLException | RuntimeException | Error error) {
            try {
                db.close();
            } catch (Throwable closeError) {
                error.addSuppressed(closeError);
            }
            throw error;
        }
    }

    /**
     * Returns a file name from the given resource address.
     *
     * @param resourceAddr The resource address.
     * @return The extracted file name.
     */
    private static File extractResource(URL resourceAddr) throws IOException {
        if (resourceAddr.getProtocol().equals("file")) {
            try {
                return new File(resourceAddr.toURI());
            } catch (URISyntaxException error) {
                throw new IOException("Invalid file resource URI: " + resourceAddr, error);
            }
        }

        var databaseFile = Files.createTempFile("sqlite-jdbc-resource-", ".db");
        databaseFile.toFile().deleteOnExit();
        URLConnection connection = resourceAddr.openConnection();
        connection.setUseCaches(false);
        try (InputStream input = connection.getInputStream()) {
            Files.copy(input, databaseFile, StandardCopyOption.REPLACE_EXISTING);
            return databaseFile.toFile();
        } catch (IOException | RuntimeException error) {
            try {
                Files.deleteIfExists(databaseFile);
            } catch (IOException | RuntimeException deleteError) {
                error.addSuppressed(deleteError);
            }
            throw error;
        }
    }

    SQLiteDatabase database() {
        return db;
    }

    @Override
    public boolean getAutoCommit() throws SQLException {
        checkOpen();

        return connectionConfig.isAutoCommit();
    }

    @Override
    public void setAutoCommit(boolean ac) throws SQLException {
        checkOpen();
        if (connectionConfig.isAutoCommit() == ac) return;

        if (ac) {
            db.exec("commit;", true);
            connectionConfig.setAutoCommit(true);
            currentTransactionMode = null;
        } else {
            db.exec(transactionPrefix(), false);
            connectionConfig.setAutoCommit(false);
            currentTransactionMode = connectionConfig.getTransactionMode();
        }
    }

    /**
     * @return The busy timeout value for the connection.
     * @see <a
     *     href="https://www.sqlite.org/c3ref/busy_timeout.html">https://www.sqlite.org/c3ref/busy_timeout.html</a>
     */
    public int getBusyTimeout() {
        return db.getConfig().getBusyTimeout();
    }

    /**
     * Sets the timeout value for the connection. A timeout value less than or equal to zero turns
     * off all busy handlers.
     *
     * @see <a
     *     href="https://www.sqlite.org/c3ref/busy_timeout.html">https://www.sqlite.org/c3ref/busy_timeout.html</a>
     * @param timeoutMillis The timeout value in milliseconds.
     */
    public void setBusyTimeout(int timeoutMillis) throws SQLException {
        db.busy_timeout(timeoutMillis);
        db.getConfig().setBusyTimeout(timeoutMillis);
    }

    public void setLimit(SQLiteLimits limit, int value) throws SQLException {
        if (limit == null) throw new SQLException("limit must not be null");
        if (value >= 0) db.limit(limit.getId(), value);
    }

    public int getLimit(SQLiteLimits limit) throws SQLException {
        if (limit == null) throw new SQLException("limit must not be null");
        return db.limit(limit.getId(), -1);
    }

    @Override
    public boolean isClosed() throws SQLException {
        return db.isClosed();
    }

    @Override
    public void close() throws SQLException {
        if (isClosed()) return;

        SQLException failure = null;
        DatabaseMetaDataImpl metadata = meta;
        meta = null;
        if (metadata != null) {
            try {
                metadata.close();
            } catch (SQLException error) {
                failure = error;
            }
        }
        try {
            db.close();
        } catch (SQLException error) {
            if (failure == null) failure = error;
            else failure.addSuppressed(error);
        }
        if (failure != null) throw failure;
    }

    /** Whether an SQLite library interface to the database has been established. */
    protected void checkOpen() throws SQLException {
        if (isClosed()) throw new SQLException("database connection closed");
    }

    /**
     * @return Compile-time library version numbers.
     * @see <a
     *     href="https://www.sqlite.org/c3ref/c_source_id.html">https://www.sqlite.org/c3ref/c_source_id.html</a>
     */
    public String libversion() throws SQLException {
        checkOpen();

        return db.libversion();
    }

    @Override
    public void commit() throws SQLException {
        checkOpen();
        if (connectionConfig.isAutoCommit()) throw new SQLException("database in auto-commit mode");
        db.exec("commit;", getAutoCommit());
        db.exec(this.transactionPrefix(), getAutoCommit());
        this.firstStatementExecuted = false;
        this.setCurrentTransactionMode(this.getConnectionConfig().getTransactionMode());
    }

    @Override
    public void rollback() throws SQLException {
        checkOpen();
        if (connectionConfig.isAutoCommit()) throw new SQLException("database in auto-commit mode");
        db.exec("rollback;", getAutoCommit());
        db.exec(this.transactionPrefix(), getAutoCommit());
        this.firstStatementExecuted = false;
        this.setCurrentTransactionMode(this.getConnectionConfig().getTransactionMode());
    }

    /**
     * Add a listener for database update events, see https://www.sqlite.org/c3ref/update_hook.html
     *
     * @param listener The listener to receive update events
     */
    public void addUpdateListener(SQLiteUpdateListener listener) {
        db.addUpdateListener(listener);
    }

    /**
     * Remove a listener registered for database update events.
     *
     * @param listener The listener to no longer receive update events
     */
    public void removeUpdateListener(SQLiteUpdateListener listener) {
        db.removeUpdateListener(listener);
    }

    /**
     * Add a listener for database commit/rollback events, see
     * https://www.sqlite.org/c3ref/commit_hook.html
     *
     * @param listener The listener to receive commit events
     */
    public void addCommitListener(SQLiteCommitListener listener) {
        db.addCommitListener(listener);
    }

    /**
     * Remove a listener registered for database commit/rollback events.
     *
     * @param listener The listener to no longer receive commit/rollback events.
     */
    public void removeCommitListener(SQLiteCommitListener listener) {
        db.removeCommitListener(listener);
    }

    /**
     * Extracts PRAGMA values from the filename and sets them into the Properties object which will
     * be used to build the SQLConfig. The sanitized filename is returned.
     *
     * @return a PRAGMA-sanitized filename
     */
    protected static String extractPragmasFromFilename(String url, String filename, Properties prop)
            throws SQLException {
        int parameterDelimiter = filename.indexOf('?');
        if (parameterDelimiter == -1) {
            // nothing to extract
            return filename;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(filename.substring(0, parameterDelimiter));

        int nonPragmaCount = 0;
        Set<String> propertyNames = Set.copyOf(prop.stringPropertyNames());
        String[] parameters = filename.substring(parameterDelimiter + 1).split("&");
        for (String rawParameter : parameters) {
            String parameter = rawParameter.trim();

            if (parameter.isEmpty()) {
                // duplicated &&& sequence, drop
                continue;
            }

            String[] kvp = parameter.split("=", 2);
            String key = kvp[0].trim().toLowerCase(Locale.ROOT);
            if (SQLiteConfig.isPragma(key)) {
                String value = kvp.length == 1 ? "" : kvp[1].trim();
                if (value.isEmpty()) {
                    throw new SQLException(
                            String.format(
                                    "Please specify a value for PRAGMA %s in URL %s", key, url));
                }
                if (!propertyNames.contains(key)) prop.setProperty(key, value);
            } else {
                // not a Pragma, retain as part of filename
                sb.append(nonPragmaCount == 0 ? '?' : '&');
                sb.append(parameter);
                nonPragmaCount++;
            }
        }

        return sb.toString();
    }

    protected String transactionPrefix() {
        return this.connectionConfig.transactionPrefix();
    }

    /**
     * Returns a byte array representing the schema content. This method is intended for in-memory
     * schemas. Serialized databases are limited to 2gb.
     *
     * @param schema The schema to serialize
     * @return A byte[] holding the database content
     */
    public byte[] serialize(String schema) throws SQLException {
        return db.serialize(schema);
    }

    /**
     * Deserialize the schema using the given byte array. This method is intended for in-memory
     * database. The call will replace the content of an existing schema. To make sure there is an
     * existing schema, first execute ATTACH ':memory:' AS schema_name
     *
     * @param schema The schema to serialize
     * @param buff The buffer to deserialize
     */
    public void deserialize(String schema, byte[] buff) throws SQLException {
        db.deserialize(schema, buff);
    }

    private final AtomicInteger savePoint = new AtomicInteger(0);
    private Map<String, Class<?>> typeMap;

    private boolean readOnly = false;

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
    public void tryEnforceTransactionMode() throws SQLException {
        // important note: read-only mode is only supported when auto-commit is disabled
        if (db.getConfig().isExplicitReadOnly()
                && !this.getAutoCommit()
                && this.getCurrentTransactionMode() != null) {
            if (isReadOnly()) {
                // this is a read-only transaction, make sure all writing operations are rejected by
                // the SQLiteDatabase
                // (note: this pragma is evaluated on a per-transaction basis by SQLite)
                db._exec("PRAGMA query_only = true;");
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
                        db._exec("commit; /* need to explicitly upgrade transaction */");

                        // start the write transaction
                        db._exec("PRAGMA query_only = false;");
                        db._exec("BEGIN IMMEDIATE; /* explicitly upgrade transaction */");
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
                this.typeMap = new HashMap<>();
            }

            return this.typeMap;
        }
    }

    public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
        synchronized (this) {
            this.typeMap = map;
        }
    }

    public boolean isReadOnly() {
        SQLiteConfig config = db.getConfig();
        return (
        // the entire database is read-only
        ((config.getOpenModeFlags() & SQLiteOpenMode.READONLY.flag()) != 0)
                // the flag was set explicitly by the user on this connection
                || (config.isExplicitReadOnly() && this.readOnly));
    }

    public void setReadOnly(boolean ro) throws SQLException {
        if (db.getConfig().isExplicitReadOnly()) {
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

    public Savepoint setSavepoint() throws SQLException {
        checkSavepointMode();
        SavepointImpl savepoint = new SavepointImpl(this, savePoint.incrementAndGet(), null);
        db.exec("SAVEPOINT " + quoteIdentifier(savepoint.sqliteName()), false);
        return savepoint;
    }

    public Savepoint setSavepoint(String name) throws SQLException {
        checkSavepointMode();
        if (name == null) throw new SQLException("savepoint name must not be null");
        SavepointImpl savepoint = new SavepointImpl(this, savePoint.incrementAndGet(), name);
        db.exec("SAVEPOINT " + quoteIdentifier(savepoint.sqliteName()), false);
        return savepoint;
    }

    public void releaseSavepoint(Savepoint savepoint) throws SQLException {
        checkOpen();
        if (getAutoCommit()) {
            throw new SQLException("database in auto-commit mode");
        }
        SavepointImpl sqliteSavepoint = requireSavepoint(savepoint);
        db.exec("RELEASE SAVEPOINT " + quoteIdentifier(sqliteSavepoint.sqliteName()), false);
    }

    public void rollback(Savepoint savepoint) throws SQLException {
        checkOpen();
        if (getAutoCommit()) {
            throw new SQLException("database in auto-commit mode");
        }
        SavepointImpl sqliteSavepoint = requireSavepoint(savepoint);
        db.exec(
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

    private record SavepointImpl(SQLiteConnection connection, int id, String name)
            implements Savepoint {
        @Override
        public int getSavepointId() throws SQLException {
            if (name != null) throw new SQLException("named savepoint has no numeric ID");
            return id;
        }

        @Override
        public String getSavepointName() throws SQLException {
            if (name == null) throw new SQLException("unnamed savepoint has no name");
            return name;
        }

        boolean belongsTo(SQLiteConnection candidate) {
            return connection == candidate;
        }

        String sqliteName() {
            return name == null ? "SQLITE_SAVEPOINT_" + id : name;
        }
    }

    public Struct createStruct(String t, Object[] attr) throws SQLException {
        throw new SQLFeatureNotSupportedException("not implemented by SQLite JDBC driver");
    }

    public Statement createStatement(int rst, int rsc, int rsh) throws SQLException {
        checkOpen();
        checkCursor(rst, rsc, rsh);

        return new StatementImpl(this, db);
    }

    public PreparedStatement prepareStatement(String sql, int rst, int rsc, int rsh)
            throws SQLException {
        checkOpen();
        checkCursor(rst, rsc, rsh);

        return new PreparedStatementImpl(this, db, sql);
    }

    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (!isWrapperFor(iface)) throw new SQLException("not a wrapper for " + iface.getName());
        return iface.cast(this);
    }

    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        if (iface == null) throw new SQLException("interface must not be null");
        return iface.isInstance(this);
    }

    public Clob createClob() throws SQLException {
        throw unsupported("CLOB");
    }

    public Blob createBlob() throws SQLException {
        throw unsupported("BLOB");
    }

    public NClob createNClob() throws SQLException {
        throw unsupported("NCLOB");
    }

    public SQLXML createSQLXML() throws SQLException {
        throw unsupported("SQLXML");
    }

    public boolean isValid(int timeout) throws SQLException {
        if (timeout < 0) throw new SQLException("timeout must be >= 0");
        if (isClosed()) {
            return false;
        }
        Statement statement = createStatement();
        try {
            return statement.execute("select 1");
        } finally {
            statement.close();
        }
    }

    public void setClientInfo(String name, String value) throws SQLClientInfoException {
        requireOpenForClientInfo();
    }

    public void setClientInfo(Properties properties) throws SQLClientInfoException {
        requireOpenForClientInfo();
    }

    public String getClientInfo(String name) throws SQLException {
        checkOpen();
        return null;
    }

    public Properties getClientInfo() throws SQLException {
        checkOpen();
        return new Properties();
    }

    public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
        throw unsupported("SQL ARRAY");
    }

    private void requireOpenForClientInfo() throws SQLClientInfoException {
        try {
            checkOpen();
        } catch (SQLException error) {
            throw new SQLClientInfoException(error.getMessage(), null, error);
        }
    }

    private static SQLFeatureNotSupportedException unsupported(String type) {
        return new SQLFeatureNotSupportedException(type + " is not supported by SQLite");
    }
}
