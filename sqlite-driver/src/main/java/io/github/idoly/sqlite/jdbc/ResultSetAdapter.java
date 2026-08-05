package io.github.idoly.sqlite.jdbc;

import java.sql.SQLFeatureNotSupportedException;

/** Rejects ResultSet operations until a concrete implementation opts in. */
@SuppressWarnings("deprecation")
abstract class ResultSetAdapter implements java.sql.ResultSet {
    @Override
    public boolean absolute(int unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void afterLast() throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void beforeFirst() throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void cancelRowUpdates() throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void clearWarnings() throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void close() throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void deleteRow() throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public int findColumn(java.lang.String unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public boolean first() throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public java.sql.Array getArray(java.lang.String unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public java.sql.Array getArray(int unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public java.io.InputStream getAsciiStream(java.lang.String unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public java.io.InputStream getAsciiStream(int unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public java.math.BigDecimal getBigDecimal(java.lang.String unused1, int unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public java.math.BigDecimal getBigDecimal(java.lang.String unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public java.math.BigDecimal getBigDecimal(int unused1, int unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public java.math.BigDecimal getBigDecimal(int unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public java.io.InputStream getBinaryStream(java.lang.String unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public java.io.InputStream getBinaryStream(int unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public java.sql.Blob getBlob(java.lang.String unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public java.sql.Blob getBlob(int unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public boolean getBoolean(java.lang.String unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public boolean getBoolean(int unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public byte getByte(java.lang.String unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public byte getByte(int unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public byte[] getBytes(java.lang.String unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public byte[] getBytes(int unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public java.io.Reader getCharacterStream(java.lang.String unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public java.io.Reader getCharacterStream(int unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public java.sql.Clob getClob(java.lang.String unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public java.sql.Clob getClob(int unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public int getConcurrency() throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public java.lang.String getCursorName() throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public java.sql.Date getDate(java.lang.String unused1, java.util.Calendar unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public java.sql.Date getDate(java.lang.String unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public java.sql.Date getDate(int unused1, java.util.Calendar unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public java.sql.Date getDate(int unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public double getDouble(java.lang.String unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public double getDouble(int unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public int getFetchDirection() throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public int getFetchSize() throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public float getFloat(java.lang.String unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public float getFloat(int unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public int getHoldability() throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public int getInt(java.lang.String unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public int getInt(int unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public long getLong(java.lang.String unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public long getLong(int unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public java.sql.ResultSetMetaData getMetaData() throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public java.io.Reader getNCharacterStream(java.lang.String unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public java.io.Reader getNCharacterStream(int unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public java.sql.NClob getNClob(java.lang.String unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public java.sql.NClob getNClob(int unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public java.lang.String getNString(java.lang.String unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public java.lang.String getNString(int unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public <T> T getObject(java.lang.String unused1, java.lang.Class<T> unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public java.lang.Object getObject(java.lang.String unused1, java.util.Map<java.lang.String, java.lang.Class<?>> unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public java.lang.Object getObject(java.lang.String unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public <T> T getObject(int unused1, java.lang.Class<T> unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public java.lang.Object getObject(int unused1, java.util.Map<java.lang.String, java.lang.Class<?>> unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public java.lang.Object getObject(int unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public java.sql.Ref getRef(java.lang.String unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public java.sql.Ref getRef(int unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public java.sql.RowId getRowId(java.lang.String unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public java.sql.RowId getRowId(int unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public int getRow() throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public java.sql.SQLXML getSQLXML(java.lang.String unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public java.sql.SQLXML getSQLXML(int unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public short getShort(java.lang.String unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public short getShort(int unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public java.sql.Statement getStatement() throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public java.lang.String getString(java.lang.String unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public java.lang.String getString(int unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public java.sql.Time getTime(java.lang.String unused1, java.util.Calendar unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public java.sql.Time getTime(java.lang.String unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public java.sql.Time getTime(int unused1, java.util.Calendar unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public java.sql.Time getTime(int unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public java.sql.Timestamp getTimestamp(java.lang.String unused1, java.util.Calendar unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public java.sql.Timestamp getTimestamp(java.lang.String unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public java.sql.Timestamp getTimestamp(int unused1, java.util.Calendar unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public java.sql.Timestamp getTimestamp(int unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public int getType() throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public java.net.URL getURL(java.lang.String unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public java.net.URL getURL(int unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public java.io.InputStream getUnicodeStream(java.lang.String unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public java.io.InputStream getUnicodeStream(int unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public java.sql.SQLWarning getWarnings() throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void insertRow() throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public boolean isAfterLast() throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public boolean isBeforeFirst() throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public boolean isClosed() throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public boolean isFirst() throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public boolean isLast() throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public boolean isWrapperFor(java.lang.Class<?> unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public boolean last() throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void moveToCurrentRow() throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void moveToInsertRow() throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public boolean next() throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public boolean previous() throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void refreshRow() throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public boolean relative(int unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public boolean rowDeleted() throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public boolean rowInserted() throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public boolean rowUpdated() throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void setFetchDirection(int unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void setFetchSize(int unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public <T> T unwrap(java.lang.Class<T> unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateArray(java.lang.String unused1, java.sql.Array unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateArray(int unused1, java.sql.Array unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateAsciiStream(java.lang.String unused1, java.io.InputStream unused2, int unused3) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateAsciiStream(java.lang.String unused1, java.io.InputStream unused2, long unused3) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateAsciiStream(java.lang.String unused1, java.io.InputStream unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateAsciiStream(int unused1, java.io.InputStream unused2, int unused3) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateAsciiStream(int unused1, java.io.InputStream unused2, long unused3) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateAsciiStream(int unused1, java.io.InputStream unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateBigDecimal(java.lang.String unused1, java.math.BigDecimal unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateBigDecimal(int unused1, java.math.BigDecimal unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateBinaryStream(java.lang.String unused1, java.io.InputStream unused2, int unused3) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateBinaryStream(java.lang.String unused1, java.io.InputStream unused2, long unused3) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateBinaryStream(java.lang.String unused1, java.io.InputStream unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateBinaryStream(int unused1, java.io.InputStream unused2, int unused3) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateBinaryStream(int unused1, java.io.InputStream unused2, long unused3) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateBinaryStream(int unused1, java.io.InputStream unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateBlob(java.lang.String unused1, java.io.InputStream unused2, long unused3) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateBlob(java.lang.String unused1, java.io.InputStream unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateBlob(java.lang.String unused1, java.sql.Blob unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateBlob(int unused1, java.io.InputStream unused2, long unused3) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateBlob(int unused1, java.io.InputStream unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateBlob(int unused1, java.sql.Blob unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateBoolean(java.lang.String unused1, boolean unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateBoolean(int unused1, boolean unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateByte(java.lang.String unused1, byte unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateByte(int unused1, byte unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateBytes(java.lang.String unused1, byte[] unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateBytes(int unused1, byte[] unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateCharacterStream(java.lang.String unused1, java.io.Reader unused2, int unused3) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateCharacterStream(java.lang.String unused1, java.io.Reader unused2, long unused3) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateCharacterStream(java.lang.String unused1, java.io.Reader unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateCharacterStream(int unused1, java.io.Reader unused2, int unused3) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateCharacterStream(int unused1, java.io.Reader unused2, long unused3) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateCharacterStream(int unused1, java.io.Reader unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateClob(java.lang.String unused1, java.io.Reader unused2, long unused3) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateClob(java.lang.String unused1, java.io.Reader unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateClob(java.lang.String unused1, java.sql.Clob unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateClob(int unused1, java.io.Reader unused2, long unused3) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateClob(int unused1, java.io.Reader unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateClob(int unused1, java.sql.Clob unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateDate(java.lang.String unused1, java.sql.Date unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateDate(int unused1, java.sql.Date unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateDouble(java.lang.String unused1, double unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateDouble(int unused1, double unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateFloat(java.lang.String unused1, float unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateFloat(int unused1, float unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateInt(java.lang.String unused1, int unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateInt(int unused1, int unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateLong(java.lang.String unused1, long unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateLong(int unused1, long unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateNCharacterStream(java.lang.String unused1, java.io.Reader unused2, long unused3) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateNCharacterStream(java.lang.String unused1, java.io.Reader unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateNCharacterStream(int unused1, java.io.Reader unused2, long unused3) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateNCharacterStream(int unused1, java.io.Reader unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateNClob(java.lang.String unused1, java.io.Reader unused2, long unused3) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateNClob(java.lang.String unused1, java.io.Reader unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateNClob(java.lang.String unused1, java.sql.NClob unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateNClob(int unused1, java.io.Reader unused2, long unused3) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateNClob(int unused1, java.io.Reader unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateNClob(int unused1, java.sql.NClob unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateNString(java.lang.String unused1, java.lang.String unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateNString(int unused1, java.lang.String unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateNull(java.lang.String unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateNull(int unused1) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateObject(java.lang.String unused1, java.lang.Object unused2, int unused3) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateObject(java.lang.String unused1, java.lang.Object unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateObject(int unused1, java.lang.Object unused2, int unused3) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateObject(int unused1, java.lang.Object unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateRef(java.lang.String unused1, java.sql.Ref unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateRef(int unused1, java.sql.Ref unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateRowId(java.lang.String unused1, java.sql.RowId unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateRowId(int unused1, java.sql.RowId unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateRow() throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateSQLXML(java.lang.String unused1, java.sql.SQLXML unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateSQLXML(int unused1, java.sql.SQLXML unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateShort(java.lang.String unused1, short unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateShort(int unused1, short unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateString(java.lang.String unused1, java.lang.String unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateString(int unused1, java.lang.String unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateTime(java.lang.String unused1, java.sql.Time unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateTime(int unused1, java.sql.Time unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateTimestamp(java.lang.String unused1, java.sql.Timestamp unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public void updateTimestamp(int unused1, java.sql.Timestamp unused2) throws java.sql.SQLException {
        throw unsupported();
    }

    @Override
    public boolean wasNull() throws java.sql.SQLException {
        throw unsupported();
    }

    static SQLFeatureNotSupportedException unsupported() {
        return new SQLFeatureNotSupportedException("JDBC operation is not supported", "0A000");
    }
}
