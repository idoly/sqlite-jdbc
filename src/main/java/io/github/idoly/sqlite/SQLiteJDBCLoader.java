package io.github.idoly.sqlite;

import io.github.idoly.sqlite.core.NativeDB;
import java.io.IOException;
import java.net.URL;
import java.util.Properties;

/** Initializes the SQLite FFM backend and exposes the driver version. */
public final class SQLiteJDBCLoader {
    private SQLiteJDBCLoader() {}

    /** Initializes the FFM symbol table. */
    public static boolean initialize() {
        return NativeDB.load();
    }

    /** Returns the major version of the SQLite JDBC driver. */
    public static int getMajorVersion() {
        String[] components = getVersion().split("\\.");
        return components.length > 0 ? Integer.parseInt(components[0]) : 1;
    }

    /** Returns the minor version of the SQLite JDBC driver. */
    public static int getMinorVersion() {
        String[] components = getVersion().split("\\.");
        return components.length > 1 ? Integer.parseInt(components[1]) : 0;
    }

    /** Returns the version of the SQLite JDBC driver. */
    public static String getVersion() {
        return VersionHolder.VERSION;
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
