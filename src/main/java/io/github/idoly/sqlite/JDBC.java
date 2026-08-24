package io.github.idoly.sqlite;

import io.github.idoly.sqlite.internal.ConnectionImpl;
import java.sql.*;
import java.util.Properties;

public final class JDBC implements Driver {
    public static final String PREFIX = "jdbc:sqlite:";

    public JDBC() {}

    static {
        try {
            DriverManager.registerDriver(new JDBC());
        } catch (SQLException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    public int getMajorVersion() {
        return SQLiteJDBCLoader.getMajorVersion();
    }

    public int getMinorVersion() {
        return SQLiteJDBCLoader.getMinorVersion();
    }

    public boolean jdbcCompliant() {
        return false;
    }

    public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("getParentLogger");
    }

    public boolean acceptsURL(String url) {
        return isValidURL(url);
    }

    /**
     * Validates a URL
     *
     * @return true if the URL is valid, false otherwise
     */
    public static boolean isValidURL(String url) {
        return url != null && url.regionMatches(true, 0, PREFIX, 0, PREFIX.length());
    }

    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
        return SQLiteConfig.getDriverPropertyInfo();
    }

    public Connection connect(String url, Properties info) throws SQLException {
        if (!isValidURL(url)) {
            return null;
        }
        return createConnection(url, info);
    }

    /**
     * Gets the location to the database from a given URL.
     *
     * @param url The URL to extract the location from.
     * @return The location to the database.
     */
    static String extractAddress(String url) {
        return url.substring(PREFIX.length());
    }

    /**
     * Creates a new database connection to a given URL.
     *
     * <p>Unlike {@link #connect(String, Properties)}, this method throws {@link SQLException} when
     * the URL is not a {@code jdbc:sqlite:} address, rather than returning {@code null}.
     *
     * @param url the URL
     * @param prop the properties
     * @return a Connection object that represents a connection to the URL
     * @throws SQLException if the URL is not a valid SQLite JDBC URL, or if a database access error
     *     occurs
     */
    public static SQLiteConnection createConnection(String url, Properties prop)
            throws SQLException {
        if (!isValidURL(url)) {
            throw new SQLException("invalid database address: " + url);
        }

        url = url.trim();
        return new ConnectionImpl(url, extractAddress(url), prop);
    }
}
