package io.github.idoly.sqlite.jdbc;

import java.sql.SQLFeatureNotSupportedException;

/** Rejects PreparedStatement operations until a concrete implementation opts in. */
@SuppressWarnings("deprecation")
abstract class PreparedStatementAdapter extends SQLiteStatement implements java.sql.PreparedStatement {
    PreparedStatementAdapter(SQLiteConnection connection) {
        super(connection);
    }

    @Override
    public void addBatch() throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void clearParameters() throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public java.sql.ResultSet executeQuery() throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public int executeUpdate() throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public boolean execute() throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public java.sql.ResultSetMetaData getMetaData() throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public java.sql.ParameterMetaData getParameterMetaData() throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void setArray(int unused1, java.sql.Array unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void setAsciiStream(int unused1, java.io.InputStream unused2, int unused3) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void setAsciiStream(int unused1, java.io.InputStream unused2, long unused3) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void setAsciiStream(int unused1, java.io.InputStream unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void setBigDecimal(int unused1, java.math.BigDecimal unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void setBinaryStream(int unused1, java.io.InputStream unused2, int unused3) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void setBinaryStream(int unused1, java.io.InputStream unused2, long unused3) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void setBinaryStream(int unused1, java.io.InputStream unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void setBlob(int unused1, java.io.InputStream unused2, long unused3) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void setBlob(int unused1, java.io.InputStream unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void setBlob(int unused1, java.sql.Blob unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void setBoolean(int unused1, boolean unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void setByte(int unused1, byte unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void setBytes(int unused1, byte[] unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void setCharacterStream(int unused1, java.io.Reader unused2, int unused3) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void setCharacterStream(int unused1, java.io.Reader unused2, long unused3) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void setCharacterStream(int unused1, java.io.Reader unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void setClob(int unused1, java.io.Reader unused2, long unused3) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void setClob(int unused1, java.io.Reader unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void setClob(int unused1, java.sql.Clob unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void setDate(int unused1, java.sql.Date unused2, java.util.Calendar unused3) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void setDate(int unused1, java.sql.Date unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void setDouble(int unused1, double unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void setFloat(int unused1, float unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void setInt(int unused1, int unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void setLong(int unused1, long unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void setNCharacterStream(int unused1, java.io.Reader unused2, long unused3) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void setNCharacterStream(int unused1, java.io.Reader unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void setNClob(int unused1, java.io.Reader unused2, long unused3) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void setNClob(int unused1, java.io.Reader unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void setNClob(int unused1, java.sql.NClob unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void setNString(int unused1, java.lang.String unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void setNull(int unused1, int unused2, java.lang.String unused3) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void setNull(int unused1, int unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void setObject(int unused1, java.lang.Object unused2, int unused3, int unused4) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void setObject(int unused1, java.lang.Object unused2, int unused3) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void setObject(int unused1, java.lang.Object unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void setRef(int unused1, java.sql.Ref unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void setRowId(int unused1, java.sql.RowId unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void setSQLXML(int unused1, java.sql.SQLXML unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void setShort(int unused1, short unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void setString(int unused1, java.lang.String unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void setTime(int unused1, java.sql.Time unused2, java.util.Calendar unused3) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void setTime(int unused1, java.sql.Time unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void setTimestamp(int unused1, java.sql.Timestamp unused2, java.util.Calendar unused3) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void setTimestamp(int unused1, java.sql.Timestamp unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void setURL(int unused1, java.net.URL unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void setUnicodeStream(int unused1, java.io.InputStream unused2, int unused3) throws java.sql.SQLException {
        throw unsupported();
    }

    static SQLFeatureNotSupportedException unsupported() {
        return new SQLFeatureNotSupportedException("JDBC operation is not supported", "0A000");
    }
}
