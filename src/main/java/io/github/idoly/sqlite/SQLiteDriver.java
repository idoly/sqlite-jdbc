package io.github.idoly.sqlite;

import java.io.IOException;
import java.net.URL;
import java.sql.*;
import java.util.Properties;

public final class SQLiteDriver implements Driver {
    public static final String PREFIX = "jdbc:sqlite:";

    public SQLiteDriver() {}

    static {
        try {
            DriverManager.registerDriver(new SQLiteDriver());
        } catch (SQLException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    public int getMajorVersion() {
        return versionComponent(0, 1);
    }

    public int getMinorVersion() {
        return versionComponent(1, 0);
    }

    public static String getVersion() {
        return VersionHolder.VERSION;
    }

    private static int versionComponent(int index, int fallback) {
        String[] components = getVersion().split("\\.");
        return components.length > index ? Integer.parseInt(components[index]) : fallback;
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
        return new SQLiteConnection(url, extractAddress(url), prop);
    }

    /** Holds version data so native-image can initialize it at build time. */
    public static final class VersionHolder {
        private static final String VERSION = loadVersion();

        private VersionHolder() {}

        private static String loadVersion() {
            URL versionFile = VersionHolder.class.getResource("/sqlite-jdbc.properties");
            String version = "unknown";
            try {
                if (versionFile != null) {
                    Properties versionData = new Properties();
                    try (var input = versionFile.openStream()) {
                        versionData.load(input);
                    }
                    version = versionData.getProperty("version", version);
                    version = version.trim().replaceAll("[^0-9\\.]", "");
                }
            } catch (IOException error) {
                throw new IllegalStateException(
                        "Could not read SQLite JDBC version from " + versionFile, error);
            }
            return version;
        }
    }
}
