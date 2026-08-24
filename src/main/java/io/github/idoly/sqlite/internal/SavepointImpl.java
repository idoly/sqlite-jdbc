package io.github.idoly.sqlite.internal;

import java.sql.SQLException;
import java.sql.Savepoint;

final class SavepointImpl implements Savepoint {
    private final BaseConnection connection;
    private final int id;
    private final String name;

    SavepointImpl(BaseConnection connection, int id) {
        this.connection = connection;
        this.id = id;
        name = null;
    }

    SavepointImpl(BaseConnection connection, int id, String name) {
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

    boolean belongsTo(BaseConnection candidate) {
        return connection == candidate;
    }

    String sqliteName() {
        return name == null ? "SQLITE_SAVEPOINT_" + id : name;
    }
}
