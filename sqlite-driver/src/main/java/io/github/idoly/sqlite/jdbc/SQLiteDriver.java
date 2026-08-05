package io.github.idoly.sqlite.jdbc;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.Properties;
import java.util.logging.Logger;

/** JDBC driver for SQLite databases backed by the packaged JDK FFM binding. */
public final class SQLiteDriver implements Driver {
    /** Connection property for the SQLite busy timeout in milliseconds. */
    public static final String BUSY_TIMEOUT_PROPERTY = "busy_timeout";
    /** Connection property controlling SQLite foreign-key enforcement. */
    public static final String FOREIGN_KEYS_PROPERTY = "foreign_keys";
    /** Connection property requesting a read-only database handle. */
    public static final String READ_ONLY_PROPERTY = "read_only";
    /** Connection property selecting the manual-commit transaction mode. */
    public static final String TRANSACTION_MODE_PROPERTY = "transaction_mode";

    private static final int MAJOR_VERSION = 0;
    private static final int MINOR_VERSION = 1;
    private static final String VERSION = java.util.Optional.ofNullable(
                    SQLiteDriver.class.getPackage().getImplementationVersion())
            .orElse("development");

    static {
        try {
            DriverManager.registerDriver(new SQLiteDriver());
        } catch (SQLException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    /** Creates a driver instance. */
    public SQLiteDriver() {}

    @Override
    public Connection connect(String url, Properties properties) throws SQLException {
        if (!acceptsURL(url)) {
            return null;
        }

        SQLiteJdbcUrl jdbcUrl = SQLiteJdbcUrl.parse(url);
        SQLiteConfig config = SQLiteConfig.fromProperties(properties);
        NativeDatabase database = null;
        try {
            int openFlags = (config.readOnly()
                            ? NativeDatabase.OPEN_READONLY
                            : NativeDatabase.OPEN_READWRITE | NativeDatabase.OPEN_CREATE)
                    | NativeDatabase.OPEN_URI;
            database = NativeDatabase.open(jdbcUrl.filename(), openFlags);
            database.setBusyTimeoutMillis(config.busyTimeoutMillis());
            database.execute("PRAGMA foreign_keys = " + (config.foreignKeys() ? "ON" : "OFF"));
            return new SQLiteConnection(database, url, config.readOnly(), config.transactionMode());
        } catch (NativeException error) {
            closeAfterInitializationFailure(database, error);
            throw SqlExceptionMapper.map(error);
        } catch (RuntimeException | LinkageError error) {
            closeAfterInitializationFailure(database, error);
            throw new SQLException("Could not initialize the SQLite native backend", "08001", error);
        }
    }

    @Override
    public boolean acceptsURL(String url) {
        return SQLiteJdbcUrl.accepts(url);
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties properties) {
        DriverPropertyInfo busyTimeoutProperty = new DriverPropertyInfo(
                BUSY_TIMEOUT_PROPERTY, Integer.toString(SQLiteConfig.DEFAULT_BUSY_TIMEOUT_MILLIS));
        busyTimeoutProperty.description = "Milliseconds SQLite waits for a locked database";
        DriverPropertyInfo foreignKeysProperty = new DriverPropertyInfo(FOREIGN_KEYS_PROPERTY, "true");
        foreignKeysProperty.description = "Enable PRAGMA foreign_keys for new connections";
        foreignKeysProperty.choices = new String[] {"true", "false"};
        DriverPropertyInfo readOnlyProperty = new DriverPropertyInfo(READ_ONLY_PROPERTY, "false");
        readOnlyProperty.description = "Open the SQLite database in read-only mode";
        readOnlyProperty.choices = new String[] {"true", "false"};
        DriverPropertyInfo transactionModeProperty =
                new DriverPropertyInfo(TRANSACTION_MODE_PROPERTY, "DEFERRED");
        transactionModeProperty.description = "SQLite BEGIN mode for manual-commit transactions";
        transactionModeProperty.choices = new String[] {"DEFERRED", "IMMEDIATE", "EXCLUSIVE"};
        return new DriverPropertyInfo[] {
            busyTimeoutProperty, foreignKeysProperty, readOnlyProperty, transactionModeProperty
        };
    }

    @Override
    public int getMajorVersion() {
        return MAJOR_VERSION;
    }

    @Override
    public int getMinorVersion() {
        return MINOR_VERSION;
    }

    static String driverVersion() {
        return VERSION;
    }

    @Override
    public boolean jdbcCompliant() {
        return false;
    }

    @Override
    public Logger getParentLogger() {
        return Logger.getLogger("io.github.idoly.sqlite");
    }

    private static void closeAfterInitializationFailure(NativeDatabase database, Throwable failure) {
        if (database == null || !database.isOpen()) return;
        try {
            database.close();
        } catch (RuntimeException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }

}
