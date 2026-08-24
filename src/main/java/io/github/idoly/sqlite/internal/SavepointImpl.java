package io.github.idoly.sqlite.internal;

import java.sql.SQLException;
import java.sql.Savepoint;

public class SavepointImpl implements Savepoint {

    final int id;

    final String name;

    SavepointImpl(int id) {
        this.id = id;
        this.name = null;
    }

    SavepointImpl(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public int getSavepointId() throws SQLException {
        return id;
    }

    public String getSavepointName() throws SQLException {
        return name == null ? String.format("SQLITE_SAVEPOINT_%s", id) : name;
    }
}
