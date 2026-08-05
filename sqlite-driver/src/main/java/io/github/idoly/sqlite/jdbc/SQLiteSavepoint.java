package io.github.idoly.sqlite.jdbc;

import java.sql.SQLException;
import java.sql.Savepoint;

final class SQLiteSavepoint implements Savepoint {
    private final SQLiteConnection connection;
    private final int savepointId;
    private final String savepointName;
    private boolean released;

    SQLiteSavepoint(SQLiteConnection connection, int savepointId, String savepointName) {
        this.connection = connection;
        this.savepointId = savepointId;
        this.savepointName = savepointName;
    }

    @Override
    public int getSavepointId() throws SQLException {
        ensureActive();
        if (savepointName != null) throw new SQLException("Named savepoint has no numeric ID", "3B000");
        return savepointId;
    }

    @Override
    public String getSavepointName() throws SQLException {
        ensureActive();
        if (savepointName == null) throw new SQLException("Unnamed savepoint has no name", "3B000");
        return savepointName;
    }

    String sqlIdentifier() {
        return "jdbc_savepoint_" + savepointId;
    }

    int sequenceNumber() {
        return savepointId;
    }

    void ensureOwnedBy(SQLiteConnection expectedConnection) throws SQLException {
        ensureActive();
        if (connection != expectedConnection) throw new SQLException("Savepoint belongs to another connection", "3B001");
    }

    void release() {
        released = true;
    }

    private void ensureActive() throws SQLException {
        if (released) throw new SQLException("Savepoint is no longer active", "3B001");
    }
}
