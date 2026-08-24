package io.github.idoly.sqlite.internal;

import io.github.idoly.sqlite.SQLiteConnection;
import java.sql.SQLException;
import java.sql.Statement;

public class StatementImpl extends BaseStatement implements Statement {
    public StatementImpl(SQLiteConnection conn) {
        super(conn);
    }

    // JDBC 4
    public <T> T unwrap(Class<T> iface) throws ClassCastException {
        return iface.cast(this);
    }

    public boolean isWrapperFor(Class<?> iface) {
        return iface.isInstance(this);
    }

    private boolean closed = false;

    @Override
    public void close() throws SQLException {
        super.close();
        closed = true; // isClosed() should only return true when close() happened
    }

    public boolean isClosed() {
        return closed;
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

    public void setPoolable(boolean poolable) throws SQLException {
        // TODO Auto-generated method stub

    }

    public boolean isPoolable() throws SQLException {
        // TODO Auto-generated method stub
        return false;
    }
}
