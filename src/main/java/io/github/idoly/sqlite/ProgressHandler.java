package io.github.idoly.sqlite;

import java.sql.Connection;
import java.sql.SQLException;

/** https://www.sqlite.org/c3ref/progress_handler.html */
public abstract class ProgressHandler {
    /**
     * Sets a progress handler for the connection.
     *
     * @param connection the SQLite connection
     * @param virtualMachineCalls the approximate number of virtual machine instructions that are
     *     evaluated between successive invocations of the progressHandler
     * @param handler the progressHandler
     */
    public static void setHandler(
            Connection connection, int virtualMachineCalls, ProgressHandler handler)
            throws SQLException {
        SQLiteConnection sqliteConnection = requireSQLiteConnection(connection);
        if (handler == null) {
            sqliteConnection.getDatabase().clear_progress_handler();
        } else {
            if (virtualMachineCalls < 1) {
                throw new SQLException("virtualMachineCalls must be >= 1");
            }
            sqliteConnection.getDatabase().register_progress_handler(virtualMachineCalls, handler);
        }
    }

    /**
     * Clears any progress handler registered with the connection.
     *
     * @param connection the SQLite connection
     */
    public static void clearHandler(Connection connection) throws SQLException {
        requireSQLiteConnection(connection).getDatabase().clear_progress_handler();
    }

    private static SQLiteConnection requireSQLiteConnection(Connection connection)
            throws SQLException {
        if (!(connection instanceof SQLiteConnection sqliteConnection)) {
            throw new SQLException("connection must be a SQLite connection");
        }
        if (connection.isClosed()) throw new SQLException("connection closed");
        return sqliteConnection;
    }

    protected abstract int progress() throws SQLException;

    /** Internal FFM dispatch entry point. */
    public final int invokeProgress() throws SQLException {
        return progress();
    }
}
