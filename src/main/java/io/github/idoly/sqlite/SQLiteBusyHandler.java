package io.github.idoly.sqlite;

import java.sql.Connection;
import java.sql.SQLException;

/** https://www.sqlite.org/c3ref/busy_handler.html */
public abstract class SQLiteBusyHandler {

    /**
     * commit the busy handler for the connection.
     *
     * @param connection the SQLite connection
     * @param handler the busyHandler
     */
    private static void installHandler(Connection connection, SQLiteBusyHandler handler)
            throws SQLException {
        if (!(connection instanceof SQLiteConnection sqliteConnection)) {
            throw new SQLException("connection must be a SQLite connection");
        }
        if (connection.isClosed()) throw new SQLException("connection closed");
        sqliteConnection.database().busy_handler(handler);
    }

    /**
     * Sets a busy handler for the connection.
     *
     * @param connection the SQLite connection
     * @param handler the busyHandler
     */
    public static void setHandler(Connection connection, SQLiteBusyHandler handler)
            throws SQLException {
        installHandler(connection, handler);
    }

    /**
     * Clears any busy handler registered with the connection.
     *
     * @param connection the SQLite connection
     */
    public static void clearHandler(Connection connection) throws SQLException {
        installHandler(connection, null);
    }

    /**
     * https://www.sqlite.org/c3ref/busy_handler.html
     *
     * @param previousInvocations number of times that the busy handler has been invoked previously
     *     for the same locking event
     * @return If the busy callback returns 0, then no additional attempts are made to access the
     *     database and SQLITE_BUSY is returned to the application. If the callback returns
     *     non-zero, then another attempt is made to access the database and the cycle repeats.
     */
    protected abstract int callback(int previousInvocations) throws SQLException;

    /** Internal FFM dispatch entry point. */
    public final int invokeCallback(int previousInvocations) throws SQLException {
        return callback(previousInvocations);
    }
}
