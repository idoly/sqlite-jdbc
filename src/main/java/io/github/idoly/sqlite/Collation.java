/*
 * Copyright (c) 2021 Gauthier Roebroeck <gauthier.roebroeck@gmail.com>
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
 * ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
 * OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */
package io.github.idoly.sqlite;

import io.github.idoly.sqlite.core.Codes;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Provides an interface for creating SQLite user-defined collations.
 *
 * <p>A subclass of <code>io.github.idoly.sqlite.Collation</code> can be registered with <code>
 * Collation.create()</code> and called by the name it was given. All collations must implement
 * <code>xCompare(String, String)</code>, which is called when SQLite compares two strings using the
 * custom collation. Eg.
 *
 * <pre>
 *      Class.forName("io.github.idoly.sqlite.JDBC");
 *      Connection conn = DriverManager.getConnection("jdbc:sqlite:");
 *
 *      Collation.create(conn, "REVERSE", new Collation() {
 *          protected int xCompare(String str1, String str2) {
 *              return str1.compareTo(str2) * -1;
 *          }
 *      });
 *
 *      conn.createStatement().execute("select c1 from t order by c1 collate REVERSE;");
 *  </pre>
 */
public abstract class Collation {

    /**
     * Registers a given collation with the connection.
     *
     * @param connection The connection.
     * @param name The name of the collation.
     * @param collation The collation to register.
     */
    public static void create(Connection connection, String name, Collation collation)
            throws SQLException {
        SQLiteConnection sqliteConnection = requireSQLiteConnection(connection);
        if (name == null || name.isEmpty()) {
            throw new SQLException("collation name must not be empty");
        }
        if (collation == null) throw new SQLException("collation must not be null");

        if (sqliteConnection.getDatabase().create_collation(name, collation) != Codes.SQLITE_OK) {
            throw new SQLException("error creating collation");
        }
    }

    /**
     * Removes a named collation from the given connection.
     *
     * @param connection The connection to remove the collation from.
     * @param name The name of the collation.
     */
    public static void destroy(Connection connection, String name) throws SQLException {
        SQLiteConnection sqliteConnection = requireSQLiteConnection(connection);
        if (name == null || name.isEmpty()) {
            throw new SQLException("collation name must not be empty");
        }
        sqliteConnection.getDatabase().destroy_collation(name);
    }

    private static SQLiteConnection requireSQLiteConnection(Connection connection)
            throws SQLException {
        if (!(connection instanceof SQLiteConnection sqliteConnection)) {
            throw new SQLException("connection must be a SQLite connection");
        }
        if (connection.isClosed()) throw new SQLException("connection closed");
        return sqliteConnection;
    }

    /**
     * Called by SQLite as a custom collation to compare two strings.
     *
     * @param str1 the first string in the comparison
     * @param str2 the second string in the comparison
     * @return an integer that is negative, zero, or positive if the first string is less than,
     *     equal to, or greater than the second, respectively
     */
    protected abstract int xCompare(String str1, String str2);

    /** Internal FFM dispatch entry point. */
    public final int invokeCompare(String first, String second) {
        return xCompare(first, second);
    }
}
