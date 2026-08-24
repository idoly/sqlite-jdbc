package io.github.idoly.sqlite.internal;

import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLXML;
import java.sql.Statement;
import java.util.Properties;

public class ConnectionImpl extends BaseConnection {

    public ConnectionImpl(String url, String fileName, Properties prop) throws SQLException {
        super(url, fileName, prop);
    }

    public Statement createStatement(int rst, int rsc, int rsh) throws SQLException {
        checkOpen();
        checkCursor(rst, rsc, rsh);

        return new StatementImpl(this);
    }

    public PreparedStatement prepareStatement(String sql, int rst, int rsc, int rsh)
            throws SQLException {
        checkOpen();
        checkCursor(rst, rsc, rsh);

        return new PreparedStatementImpl(this, sql);
    }

    // JDBC 4
    public boolean isClosed() throws SQLException {
        return super.isClosed();
    }

    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (!isWrapperFor(iface)) throw new SQLException("not a wrapper for " + iface.getName());
        return iface.cast(this);
    }

    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        if (iface == null) throw new SQLException("interface must not be null");
        return iface.isInstance(this);
    }

    public Clob createClob() throws SQLException {
        throw unsupported("CLOB");
    }

    public Blob createBlob() throws SQLException {
        throw unsupported("BLOB");
    }

    public NClob createNClob() throws SQLException {
        throw unsupported("NCLOB");
    }

    public SQLXML createSQLXML() throws SQLException {
        throw unsupported("SQLXML");
    }

    public boolean isValid(int timeout) throws SQLException {
        if (timeout < 0) throw new SQLException("timeout must be >= 0");
        if (isClosed()) {
            return false;
        }
        Statement statement = createStatement();
        try {
            return statement.execute("select 1");
        } finally {
            statement.close();
        }
    }

    public void setClientInfo(String name, String value) throws SQLClientInfoException {
        requireOpenForClientInfo();
    }

    public void setClientInfo(Properties properties) throws SQLClientInfoException {
        requireOpenForClientInfo();
    }

    public String getClientInfo(String name) throws SQLException {
        checkOpen();
        return null;
    }

    public Properties getClientInfo() throws SQLException {
        checkOpen();
        return new Properties();
    }

    public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
        throw unsupported("SQL ARRAY");
    }

    private void requireOpenForClientInfo() throws SQLClientInfoException {
        try {
            checkOpen();
        } catch (SQLException error) {
            throw new SQLClientInfoException(error.getMessage(), null, error);
        }
    }

    private static SQLFeatureNotSupportedException unsupported(String type) {
        return new SQLFeatureNotSupportedException(type + " is not supported by SQLite");
    }
}
