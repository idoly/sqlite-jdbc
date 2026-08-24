package io.github.idoly.sqlite.internal;

import javax.sql.PooledConnection;
import javax.sql.StatementEventListener;

public abstract class PooledConnectionImpl implements PooledConnection {

    public void addStatementEventListener(StatementEventListener listener) {
        // TODO impl
    }

    public void removeStatementEventListener(StatementEventListener listener) {
        // TODO impl
    }
}
