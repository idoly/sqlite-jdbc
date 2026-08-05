package io.github.idoly.sqlite.jdbc;

import java.sql.SQLException;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;

/**
 * Immutable SQLite connection settings.
 *
 * <p>Use {@link #builder()} when configuring an application directly. JDBC connection properties
 * use the equivalent names exposed by {@link SQLiteDriver}.
 */
public final class SQLiteConfig {
    /** Default busy timeout in milliseconds. */
    public static final int DEFAULT_BUSY_TIMEOUT_MILLIS = 5_000;

    private static final SQLiteConfig DEFAULTS = builder().build();

    private final int busyTimeoutMillis;
    private final boolean foreignKeys;
    private final boolean readOnly;
    private final SQLiteTransactionMode transactionMode;

    private SQLiteConfig(Builder builder) {
        busyTimeoutMillis = builder.busyTimeoutMillis;
        foreignKeys = builder.foreignKeys;
        readOnly = builder.readOnly;
        transactionMode = builder.transactionMode;
    }

    /** @return the default connection settings */
    public static SQLiteConfig defaults() {
        return DEFAULTS;
    }

    /** @return a builder initialized with driver defaults */
    public static Builder builder() {
        return new Builder();
    }

    /** @return a builder initialized from this configuration */
    public Builder toBuilder() {
        return new Builder(this);
    }

    /** @return SQLite lock wait in milliseconds */
    public int busyTimeoutMillis() {
        return busyTimeoutMillis;
    }

    /** @return whether foreign-key enforcement is enabled for writable connections */
    public boolean foreignKeys() {
        return foreignKeys;
    }

    /** @return whether the database is opened read-only */
    public boolean readOnly() {
        return readOnly;
    }

    /** @return transaction mode used when auto-commit is disabled */
    public SQLiteTransactionMode transactionMode() {
        return transactionMode;
    }

    /** @return a defensive JDBC properties representation */
    public Properties toProperties() {
        Properties properties = new Properties();
        properties.setProperty(SQLiteDriver.BUSY_TIMEOUT_PROPERTY, Integer.toString(busyTimeoutMillis));
        properties.setProperty(SQLiteDriver.FOREIGN_KEYS_PROPERTY, Boolean.toString(foreignKeys));
        properties.setProperty(SQLiteDriver.READ_ONLY_PROPERTY, Boolean.toString(readOnly));
        properties.setProperty(SQLiteDriver.TRANSACTION_MODE_PROPERTY, transactionMode.name());
        return properties;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SQLiteConfig config)) return false;
        return busyTimeoutMillis == config.busyTimeoutMillis
                && foreignKeys == config.foreignKeys
                && readOnly == config.readOnly
                && transactionMode == config.transactionMode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(busyTimeoutMillis, foreignKeys, readOnly, transactionMode);
    }

    @Override
    public String toString() {
        return "SQLiteConfig[busyTimeoutMillis=" + busyTimeoutMillis
                + ", foreignKeys=" + foreignKeys
                + ", readOnly=" + readOnly
                + ", transactionMode=" + transactionMode + ']';
    }

    static SQLiteConfig fromProperties(Properties properties) throws SQLException {
        Properties resolvedProperties = properties == null ? new Properties() : properties;
        return builder()
                .busyTimeoutMillis(readNonNegativeIntProperty(
                        resolvedProperties, SQLiteDriver.BUSY_TIMEOUT_PROPERTY, DEFAULT_BUSY_TIMEOUT_MILLIS))
                .foreignKeys(readBooleanProperty(resolvedProperties, SQLiteDriver.FOREIGN_KEYS_PROPERTY, true))
                .readOnly(readBooleanProperty(resolvedProperties, SQLiteDriver.READ_ONLY_PROPERTY, false))
                .transactionMode(readTransactionModeProperty(resolvedProperties))
                .build();
    }

    private static int readNonNegativeIntProperty(Properties properties, String propertyName, int defaultValue) throws SQLException {
        String value = properties.getProperty(propertyName);
        if (value == null) return defaultValue;
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 0) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException error) {
            throw new SQLException("Invalid non-negative integer property " + propertyName + ": " + value, "08001", error);
        }
    }

    private static boolean readBooleanProperty(Properties properties, String propertyName, boolean defaultValue) throws SQLException {
        String value = properties.getProperty(propertyName);
        if (value == null) return defaultValue;
        if (value.equalsIgnoreCase("true")) return true;
        if (value.equalsIgnoreCase("false")) return false;
        throw new SQLException("Invalid boolean property " + propertyName + ": " + value, "08001");
    }

    private static SQLiteTransactionMode readTransactionModeProperty(Properties properties) throws SQLException {
        String value = properties.getProperty(
                SQLiteDriver.TRANSACTION_MODE_PROPERTY, SQLiteTransactionMode.DEFERRED.name());
        try {
            return SQLiteTransactionMode.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new SQLException("Invalid transaction mode: " + value, "08001", error);
        }
    }

    /** Fluent builder for {@link SQLiteConfig}. */
    public static final class Builder {
        private int busyTimeoutMillis = DEFAULT_BUSY_TIMEOUT_MILLIS;
        private boolean foreignKeys = true;
        private boolean readOnly;
        private SQLiteTransactionMode transactionMode = SQLiteTransactionMode.DEFERRED;

        private Builder() {}

        private Builder(SQLiteConfig source) {
            busyTimeoutMillis = source.busyTimeoutMillis;
            foreignKeys = source.foreignKeys;
            readOnly = source.readOnly;
            transactionMode = source.transactionMode;
        }

        /**
         * @param timeout non-negative SQLite lock wait
         * @return this builder
         */
        public Builder busyTimeout(Duration timeout) {
            Objects.requireNonNull(timeout, "timeout");
            try {
                long timeoutMillis = timeout.toMillis();
                if (timeout.isNegative() || timeoutMillis > Integer.MAX_VALUE) {
                    throw new IllegalArgumentException(
                            "Busy timeout must be between zero and 2147483647 milliseconds");
                }
                return busyTimeoutMillis((int) timeoutMillis);
            } catch (ArithmeticException error) {
                throw new IllegalArgumentException("Busy timeout is too large", error);
            }
        }

        /**
         * @param timeoutMillis non-negative SQLite lock wait in milliseconds
         * @return this builder
         */
        public Builder busyTimeoutMillis(int timeoutMillis) {
            if (timeoutMillis < 0) throw new IllegalArgumentException("Busy timeout cannot be negative");
            busyTimeoutMillis = timeoutMillis;
            return this;
        }

        /**
         * @param enabled whether writable connections enable foreign-key enforcement
         * @return this builder
         */
        public Builder foreignKeys(boolean enabled) {
            foreignKeys = enabled;
            return this;
        }

        /**
         * @param enabled whether the database is opened read-only
         * @return this builder
         */
        public Builder readOnly(boolean enabled) {
            readOnly = enabled;
            return this;
        }

        /**
         * @param mode transaction mode used when auto-commit is disabled
         * @return this builder
         */
        public Builder transactionMode(SQLiteTransactionMode mode) {
            transactionMode = Objects.requireNonNull(mode, "mode");
            return this;
        }

        /** @return an immutable validated configuration */
        public SQLiteConfig build() {
            return new SQLiteConfig(this);
        }
    }
}
