package io.github.idoly.sqlite;

import io.github.idoly.sqlite.SQLiteConfig.TransactionMode;
import io.github.idoly.sqlite.core.CoreDatabaseMetaData;
import io.github.idoly.sqlite.core.DB;
import io.github.idoly.sqlite.core.NativeDB;
import io.github.idoly.sqlite.internal.DatabaseMetaDataImpl;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Executor;

public abstract class SQLiteConnection implements Connection {
    private static final String RESOURCE_NAME_PREFIX = ":resource:";
    private final DB db;
    private CoreDatabaseMetaData meta = null;
    private final SQLiteConnectionConfig connectionConfig;

    private TransactionMode currentTransactionMode;
    private boolean firstStatementExecuted = false;

    /** Connection constructor for reusing an existing DB handle */
    public SQLiteConnection(DB db) {
        this.db = db;
        connectionConfig = db.getConfig().newConnectionConfig();
    }

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
        DB newDB = null;
        try {
            this.db = newDB = open(url, fileName, prop);
            SQLiteConfig config = this.db.getConfig();
            this.connectionConfig = this.db.getConfig().newConnectionConfig();
            config.apply(this);
            this.currentTransactionMode = this.getDatabase().getConfig().getTransactionMode();
            // connection starts in "clean" state (even though some PRAGMA statements were executed)
            this.firstStatementExecuted = false;
        } catch (Throwable t) {
            try {
                if (newDB != null) {
                    newDB.close();
                }
            } catch (Exception e) {
                t.addSuppressed(e);
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

    public CoreDatabaseMetaData getSQLiteDatabaseMetaData() throws SQLException {
        checkOpen();

        if (meta == null) {
            meta = new DatabaseMetaDataImpl(this);
        }

        return meta;
    }

    @Override
    public DatabaseMetaData getMetaData() throws SQLException {
        return (DatabaseMetaData) getSQLiteDatabaseMetaData();
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
                getDatabase().exec("PRAGMA read_uncommitted = false;", getAutoCommit());
                break;
            case java.sql.Connection.TRANSACTION_READ_UNCOMMITTED:
                getDatabase().exec("PRAGMA read_uncommitted = true;", getAutoCommit());
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
    private static DB open(String url, String origFileName, Properties props) throws SQLException {
        // Create a copy of the given properties
        Properties newProps = new Properties();
        newProps.putAll(props);

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
                ClassLoader contextCL = Thread.currentThread().getContextClassLoader();
                URL resourceAddr = contextCL.getResource(resourceName);
                if (resourceAddr == null) {
                    try {
                        resourceAddr = new URL(resourceName);
                    } catch (MalformedURLException e) {
                        throw new SQLException(
                                String.format("resource %s not found: %s", resourceName, e));
                    }
                }

                try {
                    fileName = extractResource(resourceAddr).getAbsolutePath();
                } catch (IOException e) {
                    throw new SQLException(String.format("failed to load %s: %s", resourceName, e));
                }
            } else {
                fileName = new File(fileName).getAbsolutePath();
            }
        }

        // load the native DB
        DB db = null;
        try {
            NativeDB.load();
            db = new NativeDB(url, fileName, config);
        } catch (Exception e) {
            SQLException err = new SQLException("Error opening connection");
            err.initCause(e);
            throw err;
        }
        db.open(fileName, config.getOpenModeFlags());
        return db;
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
            } catch (URISyntaxException e) {
                throw new IOException(e.getMessage());
            }
        }

        String tempFolder = new File(System.getProperty("java.io.tmpdir")).getAbsolutePath();
        String dbFileName = String.format("sqlite-jdbc-tmp-%s.db", UUID.randomUUID());
        File dbFile = new File(tempFolder, dbFileName);

        if (dbFile.exists()) {
            long resourceLastModified = resourceAddr.openConnection().getLastModified();
            long tmpFileLastModified = dbFile.lastModified();
            if (resourceLastModified < tmpFileLastModified) {
                return dbFile;
            } else {
                // remove the old DB file
                boolean deletionSucceeded = dbFile.delete();
                if (!deletionSucceeded) {
                    throw new IOException(
                            "failed to remove existing DB file: " + dbFile.getAbsolutePath());
                }
            }

            //
            //            if (md5sum1.equals(md5sum2))
            //                return dbFile; // no need to extract the DB file
            //            else
            //            {
            //            }
        }

        URLConnection conn = resourceAddr.openConnection();
        // Disable caches to avoid keeping unnecessary file references after the single-use copy
        conn.setUseCaches(false);
        try (InputStream reader = conn.getInputStream()) {
            Files.copy(reader, dbFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return dbFile;
        }
    }

    public DB getDatabase() {
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
        if (meta != null) meta.close();

        db.close();
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
     * Add a listener for DB update events, see https://www.sqlite.org/c3ref/update_hook.html
     *
     * @param listener The listener to receive update events
     */
    public void addUpdateListener(SQLiteUpdateListener listener) {
        db.addUpdateListener(listener);
    }

    /**
     * Remove a listener registered for DB update events.
     *
     * @param listener The listener to no longer receive update events
     */
    public void removeUpdateListener(SQLiteUpdateListener listener) {
        db.removeUpdateListener(listener);
    }

    /**
     * Add a listener for DB commit/rollback events, see
     * https://www.sqlite.org/c3ref/commit_hook.html
     *
     * @param listener The listener to receive commit events
     */
    public void addCommitListener(SQLiteCommitListener listener) {
        db.addCommitListener(listener);
    }

    /**
     * Remove a listener registered for DB commit/rollback events.
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
        String[] parameters = filename.substring(parameterDelimiter + 1).split("&");
        for (int i = 0; i < parameters.length; i++) {
            // process parameters in reverse-order, last specified pragma value wins
            String parameter = parameters[parameters.length - 1 - i].trim();

            if (parameter.isEmpty()) {
                // duplicated &&& sequence, drop
                continue;
            }

            String[] kvp = parameter.split("=");
            String key = kvp[0].trim().toLowerCase(Locale.ROOT);
            if (SQLiteConfig.pragmaSet.contains(key)) {
                if (kvp.length == 1) {
                    throw new SQLException(
                            String.format(
                                    "Please specify a value for PRAGMA %s in URL %s", key, url));
                }
                String value = kvp[1].trim();
                if (!value.isEmpty()) {
                    if (prop.containsKey(key)) {
                        //
                        // IGNORE
                        //
                        // this allows DriverManager.getConnection(String, Properties)
                        // to override URL parameters programmatically.
                        //
                        // It also ignores duplicate pragma keys in the URL. The reversed
                        // processing order ensures the last-supplied pragma value is used.
                    } else {
                        prop.setProperty(key, value);
                    }
                }
            } else {
                // not a Pragma, retain as part of filename
                sb.append(nonPragmaCount == 0 ? '?' : '&');
                sb.append(parameter);
                nonPragmaCount++;
            }
        }

        final String newFilename = sb.toString();
        return newFilename;
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
}
