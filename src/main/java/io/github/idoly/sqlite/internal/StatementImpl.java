package io.github.idoly.sqlite.internal;

import io.github.idoly.sqlite.SQLiteConnection;
import java.sql.SQLException;
import java.sql.Statement;

public class StatementImpl extends BaseStatement implements Statement {
    public StatementImpl(SQLiteConnection conn) {
        super(conn);
    }

    // JDBC 4
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (!isWrapperFor(iface)) throw new SQLException("not a wrapper for " + iface.getName());
        return iface.cast(this);
    }

    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        if (iface == null) throw new SQLException("interface must not be null");
        return iface.isInstance(this);
    }

    private boolean closed = false;

    @Override
    public void close() throws SQLException {
        try {
            super.close();
        } finally {
            closed = true;
        }
    }

    public boolean isClosed() throws SQLException {
        return closed || conn.isClosed();
    }

    boolean closeOnCompletion;

    public void closeOnCompletion() throws SQLException {
        if (closed) throw new SQLException("statement is closed");
        closeOnCompletion = true;
    }

    public boolean isCloseOnCompletion() throws SQLException {
        if (closed) throw new SQLException("statement is closed");
        return closeOnCompletion;
    }

    public void setPoolable(boolean poolable) throws SQLException {}

    public boolean isPoolable() throws SQLException {
        return false;
    }
}
