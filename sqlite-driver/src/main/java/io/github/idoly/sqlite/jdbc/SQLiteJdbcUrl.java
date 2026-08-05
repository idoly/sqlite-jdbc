package io.github.idoly.sqlite.jdbc;

import java.sql.SQLException;

final class SQLiteJdbcUrl {
    static final String PREFIX = "jdbc:sqlite:";

    private final String filename;

    private SQLiteJdbcUrl(String filename) {
        this.filename = filename;
    }

    static boolean accepts(String url) {
        return url != null && url.startsWith(PREFIX);
    }

    static SQLiteJdbcUrl parse(String url) throws SQLException {
        if (!accepts(url)) {
            throw new SQLException("Not a SQLite JDBC URL: " + url, "08001");
        }
        String filename = url.substring(PREFIX.length());
        if (filename.indexOf('\0') >= 0) {
            throw new SQLException("SQLite JDBC URL cannot contain a NUL character", "08001");
        }
        return new SQLiteJdbcUrl(filename);
    }

    String filename() {
        return filename;
    }
}
