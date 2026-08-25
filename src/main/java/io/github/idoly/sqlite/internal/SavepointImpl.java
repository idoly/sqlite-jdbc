package io.github.idoly.sqlite.internal;

import io.github.idoly.sqlite.SQLiteConnection;
import java.sql.SQLException;
import java.sql.Savepoint;

public final class SavepointImpl implements Savepoint {
    private final SQLiteConnection connection;
    private final int id;
    private final String name;

    public SavepointImpl(SQLiteConnection connection, int id) {
        this.connection = connection;
        this.id = id;
        name = null;
    }

    public SavepointImpl(SQLiteConnection connection, int id, String name) {
        this.connection = connection;
        this.id = id;
        this.name = name;
    }

    @Override
    public int getSavepointId() throws SQLException {
        if (name != null) throw new SQLException("named savepoint has no numeric ID");
        return id;
    }

    @Override
    public String getSavepointName() throws SQLException {
        if (name == null) throw new SQLException("unnamed savepoint has no name");
        return name;
    }

    public boolean belongsTo(SQLiteConnection candidate) {
        return connection == candidate;
    }

    public String sqliteName() {
        return name == null ? "SQLITE_SAVEPOINT_" + id : name;
    }
}
