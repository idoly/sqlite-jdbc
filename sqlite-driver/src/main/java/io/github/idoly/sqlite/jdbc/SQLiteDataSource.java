package io.github.idoly.sqlite.jdbc;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Objects;
import java.util.logging.Logger;
import javax.sql.DataSource;

/** Configurable SQLite {@link DataSource} for environments that do not use {@code DriverManager}. */
public final class SQLiteDataSource implements DataSource {
    private final SQLiteDriver driver = new SQLiteDriver();
    private volatile String url;
    private volatile SQLiteConfig config;
    private volatile PrintWriter logWriter;

    /** Creates a data source using {@code jdbc:sqlite:} and default connection settings. */
    public SQLiteDataSource() {
        this(SQLiteJdbcUrl.PREFIX, SQLiteConfig.defaults());
    }

    /**
     * Creates a fully configured data source.
     *
     * @param url SQLite JDBC URL
     * @param config immutable connection settings
     */
    public SQLiteDataSource(String url, SQLiteConfig config) {
        this.url = Objects.requireNonNull(url, "url");
        this.config = Objects.requireNonNull(config, "config");
    }

    /** @return configured JDBC URL */
    public String getUrl() {
        return url;
    }

    /** @param url SQLite JDBC URL */
    public void setUrl(String url) {
        this.url = Objects.requireNonNull(url, "url");
    }

    /** @return an immutable snapshot of the connection settings */
    public SQLiteConfig getConfig() {
        return config;
    }

    /** @param config immutable connection settings */
    public synchronized void setConfig(SQLiteConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    /** @return configured busy timeout in milliseconds */
    public int getBusyTimeoutMillis() {
        return config.busyTimeoutMillis();
    }

    /** @param timeoutMillis non-negative busy timeout in milliseconds */
    public synchronized void setBusyTimeoutMillis(int timeoutMillis) {
        config = config.toBuilder().busyTimeoutMillis(timeoutMillis).build();
    }

    /** @return whether new connections enable foreign-key enforcement */
    public boolean isForeignKeys() {
        return config.foreignKeys();
    }

    /** @param enabled whether new connections enable foreign-key enforcement */
    public synchronized void setForeignKeys(boolean enabled) {
        config = config.toBuilder().foreignKeys(enabled).build();
    }

    /** @return whether new connections are opened read-only */
    public boolean isReadOnly() {
        return config.readOnly();
    }

    /** @param enabled whether new connections should be opened read-only */
    public synchronized void setReadOnly(boolean enabled) {
        config = config.toBuilder().readOnly(enabled).build();
    }

    /** @return transaction mode used when auto-commit is disabled */
    public SQLiteTransactionMode getTransactionMode() {
        return config.transactionMode();
    }

    /** @param mode transaction mode used when auto-commit is disabled */
    public synchronized void setTransactionMode(SQLiteTransactionMode mode) {
        config = config.toBuilder().transactionMode(mode).build();
    }

    @Override
    public Connection getConnection() throws SQLException {
        SQLiteConfig snapshot = config;
        return driver.connect(url, snapshot.toProperties());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        if (username != null || password != null) {
            throw new SQLFeatureNotSupportedException("SQLite does not use usernames or passwords", "0A000");
        }
        return getConnection();
    }

    @Override
    public PrintWriter getLogWriter() {
        return logWriter;
    }

    @Override
    public void setLogWriter(PrintWriter writer) {
        logWriter = writer;
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        if (seconds != 0) {
            throw new SQLFeatureNotSupportedException("Login timeouts are not supported", "0A000");
        }
    }

    @Override
    public int getLoginTimeout() {
        return 0;
    }

    @Override
    public Logger getParentLogger() {
        return Logger.getLogger("io.github.idoly.sqlite");
    }

    @Override
    public <T> T unwrap(Class<T> type) throws SQLException {
        if (type != null && type.isInstance(this)) return type.cast(this);
        throw new SQLException("DataSource does not wrap " + type, "HY000");
    }

    @Override
    public boolean isWrapperFor(Class<?> type) {
        return type != null && type.isInstance(this);
    }
}
