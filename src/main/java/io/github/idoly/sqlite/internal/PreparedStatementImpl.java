package io.github.idoly.sqlite.internal;

import io.github.idoly.sqlite.SQLiteConnection;
import io.github.idoly.sqlite.SQLiteConnectionConfig;
import io.github.idoly.sqlite.core.SQLiteDatabase;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.JDBCType;
import java.sql.NClob;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLXML;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Arrays;
import java.util.Calendar;

public final class PreparedStatementImpl extends StatementImpl
        implements PreparedStatement, ParameterMetaData {
    protected int columnCount;
    protected int paramCount;
    protected int batchQueryCount;

    /**
     * Constructs a prepared statement on a provided connection.
     *
     * @param conn Connection on which to create the prepared statement.
     * @param sql The SQL script to prepare.
     */
    public PreparedStatementImpl(SQLiteConnection connection, SQLiteDatabase database, String sql)
            throws SQLException {
        super(connection, database);

        this.sql = sql;
        prepareStatement();
        rs.colsMeta = pointer.safeRun(SQLiteDatabase::column_names);
        columnCount = pointer.safeRunInt(SQLiteDatabase::column_count);
        paramCount = pointer.safeRunInt(SQLiteDatabase::bind_parameter_count);
        batchQueryCount = 0;
        batch = null;
        batchPos = 0;
    }

    /**
     * @see io.github.idoly.sqlite.internal.StatementImpl#executeBatch()
     */
    @Override
    public int[] executeBatch() throws SQLException {
        return Arrays.stream(executeLargeBatch()).mapToInt(l -> (int) l).toArray();
    }

    /**
     * @see io.github.idoly.sqlite.internal.StatementImpl#executeLargeBatch()
     */
    @Override
    public long[] executeLargeBatch() throws SQLException {
        if (batchQueryCount == 0) {
            return new long[] {};
        }

        conn.tryEnforceTransactionMode();

        return this.withConnectionTimeout(
                () -> {
                    try {
                        return database.executeBatch(
                                pointer, batchQueryCount, batch, conn.getAutoCommit());
                    } finally {
                        clearBatch();
                    }
                });
    }

    /**
     * @see io.github.idoly.sqlite.internal.StatementImpl#clearBatch() ()
     */
    @Override
    public void clearBatch() throws SQLException {
        super.clearBatch();
        batchQueryCount = 0;
    }

    // PARAMETER FUNCTIONS //////////////////////////////////////////

    /** Assigns the object value to the element at the specific position of array batch. */
    protected void batch(int pos, Object value) throws SQLException {
        checkOpen();
        if (batch == null) {
            batch = new Object[paramCount];
        }
        batch[batchPos + pos - 1] = value;
    }

    /** Store the date in the user's preferred format (text, int, or real) */
    protected void setDateByMilliseconds(int pos, Long value, Calendar calendar)
            throws SQLException {
        SQLiteConnectionConfig config = conn.getConnectionConfig();
        switch (config.getDateClass()) {
            case TEXT:
                batch(pos, config.formatDate(new Date(value), calendar.getTimeZone()));
                break;

            case REAL:
                // long to Julian date
                batch(pos, new Double((value / 86400000.0) + 2440587.5));
                break;

            default: // INTEGER:
                batch(pos, new Long(value / config.getDateMultiplier()));
        }
    }

    public void clearParameters() throws SQLException {
        checkOpen();
        pointer.safeRunConsume(SQLiteDatabase::clearBindings);
        if (batch != null) for (int i = batchPos; i < batchPos + paramCount; i++) batch[i] = null;
    }

    public boolean execute() throws SQLException {
        checkOpen();
        rs.close();
        pointer.safeRunConsume(SQLiteDatabase::reset);
        exhaustedResults = false;

        conn.tryEnforceTransactionMode();

        return this.withConnectionTimeout(
                () -> {
                    boolean success = false;
                    try {
                        synchronized (conn) {
                            resultsWaiting = database.execute(pointer, batch, conn.getAutoCommit());
                            updateGeneratedKeys();
                            success = true;
                            updateCount = getDatabase().changes();
                        }
                        return 0 != columnCount;
                    } finally {
                        if (!success && !pointer.isClosed())
                            pointer.safeRunConsume(SQLiteDatabase::reset);
                    }
                });
    }

    public ResultSet executeQuery() throws SQLException {
        checkOpen();

        if (columnCount == 0) {
            throw new SQLException("Query does not return results");
        }

        rs.close();
        pointer.safeRunConsume(SQLiteDatabase::reset);
        exhaustedResults = false;

        conn.tryEnforceTransactionMode();

        return this.withConnectionTimeout(
                () -> {
                    boolean success = false;
                    try {
                        resultsWaiting = database.execute(pointer, batch, conn.getAutoCommit());
                        success = true;
                    } finally {
                        if (!success && !pointer.isClosed()) {
                            pointer.safeRunInt(SQLiteDatabase::reset);
                        }
                    }
                    return getResultSet();
                });
    }

    public int executeUpdate() throws SQLException {
        return (int) executeLargeUpdate();
    }

    public long executeLargeUpdate() throws SQLException {
        checkOpen();

        if (columnCount != 0) {
            throw new SQLException("Query returns results");
        }

        rs.close();
        pointer.safeRunConsume(SQLiteDatabase::reset);
        exhaustedResults = false;

        conn.tryEnforceTransactionMode();

        return this.withConnectionTimeout(
                () -> {
                    synchronized (conn) {
                        long rc = database.executeUpdate(pointer, batch, conn.getAutoCommit());
                        updateGeneratedKeys();
                        return rc;
                    }
                });
    }

    public void addBatch() throws SQLException {
        checkOpen();
        batchPos += paramCount;
        batchQueryCount++;
        if (batch == null) {
            batch = new Object[paramCount];
        }
        if (batchPos + paramCount > batch.length) {
            Object[] nb = new Object[batch.length * 2];
            System.arraycopy(batch, 0, nb, 0, batch.length);
            batch = nb;
        }
        System.arraycopy(batch, batchPos - paramCount, batch, batchPos, paramCount);
    }

    // ParameterMetaData FUNCTIONS //////////////////////////////////

    public ParameterMetaData getParameterMetaData() {
        return (ParameterMetaData) this;
    }

    public int getParameterCount() throws SQLException {
        checkOpen();
        return paramCount;
    }

    public String getParameterClassName(int param) throws SQLException {
        checkOpen();
        return "java.lang.String";
    }

    public String getParameterTypeName(int pos) throws SQLException {
        checkIndex(pos);
        return JDBCType.valueOf(getParameterType(pos)).getName();
    }

    public int getParameterType(int pos) throws SQLException {
        checkIndex(pos);
        Object paramValue = batch[pos - 1];

        if (paramValue == null) {
            return Types.NULL;
        } else if (paramValue instanceof Integer
                || paramValue instanceof Short
                || paramValue instanceof Boolean) {
            return Types.INTEGER;
        } else if (paramValue instanceof Long) {
            return Types.BIGINT;
        } else if (paramValue instanceof Double || paramValue instanceof Float) {
            return Types.REAL;
        } else {
            return Types.VARCHAR;
        }
    }

    public int getParameterMode(int pos) {
        return ParameterMetaData.parameterModeIn;
    }

    public int getPrecision(int pos) {
        return 0;
    }

    public int getScale(int pos) {
        return 0;
    }

    public int isNullable(int pos) {
        return ParameterMetaData.parameterNullable;
    }

    public boolean isSigned(int pos) {
        return true;
    }

    /**
     * @return
     */
    public Statement getStatement() {
        return this;
    }

    public void setBigDecimal(int pos, BigDecimal value) throws SQLException {
        batch(pos, value == null ? null : value.toString());
    }

    /**
     * Reads given number of bytes from an input stream.
     *
     * @param istream The input stream.
     * @param length The number of bytes to read.
     * @return byte array.
     */
    private byte[] readBytes(InputStream istream, int length) throws SQLException {
        if (length < 0) {
            throw new SQLException("Error reading stream. Length should be non-negative");
        }

        byte[] bytes = new byte[length];

        try {
            int bytesRead;
            int totalBytesRead = 0;

            while (totalBytesRead < length) {
                bytesRead = istream.read(bytes, totalBytesRead, length - totalBytesRead);
                if (bytesRead == -1) {
                    throw new IOException("End of stream has been reached");
                }
                totalBytesRead += bytesRead;
            }

            return bytes;
        } catch (IOException cause) {
            SQLException exception = new SQLException("Error reading stream");

            exception.initCause(cause);
            throw exception;
        }
    }

    public void setBinaryStream(int pos, InputStream istream, int length) throws SQLException {
        if (istream == null && length == 0) {
            setBytes(pos, null);
        }

        setBytes(pos, readBytes(istream, length));
    }

    public void setAsciiStream(int pos, InputStream istream, int length) throws SQLException {
        setUnicodeStream(pos, istream, length);
    }

    public void setUnicodeStream(int pos, InputStream istream, int length) throws SQLException {
        if (istream == null && length == 0) {
            setString(pos, null);
        }

        try {
            setString(pos, new String(readBytes(istream, length), "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            SQLException exception = new SQLException("UTF-8 is not supported");

            exception.initCause(e);
            throw exception;
        }
    }

    public void setBoolean(int pos, boolean value) throws SQLException {
        setInt(pos, value ? 1 : 0);
    }

    public void setByte(int pos, byte value) throws SQLException {
        setInt(pos, value);
    }

    public void setBytes(int pos, byte[] value) throws SQLException {
        batch(pos, value);
    }

    public void setDouble(int pos, double value) throws SQLException {
        batch(pos, new Double(value));
    }

    public void setFloat(int pos, float value) throws SQLException {
        batch(pos, new Float(value));
    }

    public void setInt(int pos, int value) throws SQLException {
        batch(pos, new Integer(value));
    }

    public void setLong(int pos, long value) throws SQLException {
        batch(pos, new Long(value));
    }

    public void setNull(int pos, int u1) throws SQLException {
        setNull(pos, u1, null);
    }

    public void setNull(int pos, int u1, String u2) throws SQLException {
        batch(pos, null);
    }

    public void setObject(int pos, Object value) throws SQLException {
        if (value == null) {
            batch(pos, null);
        } else if (value instanceof java.util.Date) {
            setDateByMilliseconds(pos, ((java.util.Date) value).getTime(), Calendar.getInstance());
        } else if (value instanceof Long) {
            batch(pos, value);
        } else if (value instanceof Integer) {
            batch(pos, value);
        } else if (value instanceof Short) {
            batch(pos, new Integer(((Short) value).intValue()));
        } else if (value instanceof Float) {
            batch(pos, value);
        } else if (value instanceof Double) {
            batch(pos, value);
        } else if (value instanceof Boolean) {
            setBoolean(pos, ((Boolean) value).booleanValue());
        } else if (value instanceof byte[]) {
            batch(pos, value);
        } else if (value instanceof BigDecimal) {
            setBigDecimal(pos, (BigDecimal) value);
        } else {
            batch(pos, value.toString());
        }
    }

    public void setObject(int p, Object v, int t) throws SQLException {
        setObject(p, v);
    }

    public void setObject(int p, Object v, int t, int s) throws SQLException {
        setObject(p, v);
    }

    public void setShort(int pos, short value) throws SQLException {
        setInt(pos, value);
    }

    public void setString(int pos, String value) throws SQLException {
        batch(pos, value);
    }

    public void setCharacterStream(int pos, Reader reader, int length) throws SQLException {
        try {
            // copy chars from reader to StringBuilder
            StringBuilder sb = new StringBuilder();
            char[] cbuf = new char[8192];
            int cnt;

            while ((cnt = reader.read(cbuf, 0, Math.min(length, cbuf.length))) > 0) {
                sb.append(cbuf, 0, cnt);
                length -= cnt;
            }

            // set as string
            setString(pos, sb.toString());
        } catch (IOException e) {
            throw new SQLException(
                    "Cannot read from character stream, exception message: " + e.getMessage());
        }
    }

    public void setDate(int pos, Date x) throws SQLException {
        setDate(pos, x, Calendar.getInstance());
    }

    public void setDate(int pos, Date x, Calendar cal) throws SQLException {
        if (x == null) {
            setObject(pos, null);
        } else {
            setDateByMilliseconds(pos, x.getTime(), cal);
        }
    }

    public void setTime(int pos, Time x) throws SQLException {
        setTime(pos, x, Calendar.getInstance());
    }

    public void setTime(int pos, Time x, Calendar cal) throws SQLException {
        if (x == null) {
            setObject(pos, null);
        } else {
            setDateByMilliseconds(pos, x.getTime(), cal);
        }
    }

    public void setTimestamp(int pos, Timestamp x) throws SQLException {
        setTimestamp(pos, x, Calendar.getInstance());
    }

    public void setTimestamp(int pos, Timestamp x, Calendar cal) throws SQLException {
        if (x == null) {
            setObject(pos, null);
        } else {
            setDateByMilliseconds(pos, x.getTime(), cal);
        }
    }

    public ResultSetMetaData getMetaData() throws SQLException {
        checkOpen();
        return (ResultSetMetaData) rs;
    }

    protected SQLException unsupported() {
        return new SQLFeatureNotSupportedException("not implemented by SQLite JDBC driver");
    }

    protected SQLException invalid() {
        return new SQLException("method cannot be called on a PreparedStatement");
    }

    // PreparedStatement ////////////////////////////////////////////

    public void setArray(int i, Array x) throws SQLException {
        throw unsupported();
    }

    public void setBlob(int i, Blob x) throws SQLException {
        throw unsupported();
    }

    public void setClob(int i, Clob x) throws SQLException {
        throw unsupported();
    }

    public void setRef(int i, Ref x) throws SQLException {
        throw unsupported();
    }

    public void setURL(int pos, URL x) throws SQLException {
        throw unsupported();
    }

    /**
     * @see io.github.idoly.sqlite.internal.StatementImpl#exec(java.lang.String)
     */
    @Override
    public boolean execute(String sql) throws SQLException {
        throw invalid();
    }

    public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
        throw invalid();
    }

    public boolean execute(String sql, int[] colinds) throws SQLException {
        throw invalid();
    }

    public boolean execute(String sql, String[] colnames) throws SQLException {
        throw invalid();
    }

    /**
     * @see io.github.idoly.sqlite.internal.StatementImpl#exec(java.lang.String)
     */
    @Override
    public int executeUpdate(String sql) throws SQLException {
        throw invalid();
    }

    public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
        throw invalid();
    }

    public int executeUpdate(String sql, int[] colinds) throws SQLException {
        throw invalid();
    }

    public int executeUpdate(String sql, String[] cols) throws SQLException {
        throw invalid();
    }

    public long executeLargeUpdate(String sql) throws SQLException {
        throw invalid();
    }

    public long executeLargeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
        throw invalid();
    }

    public long executeLargeUpdate(String sql, int[] colinds) throws SQLException {
        throw invalid();
    }

    public long executeLargeUpdate(String sql, String[] cols) throws SQLException {
        throw invalid();
    }

    /**
     * @see io.github.idoly.sqlite.internal.StatementImpl#exec(String)
     */
    @Override
    public ResultSet executeQuery(String sql) throws SQLException {
        throw invalid();
    }

    @Override
    public void addBatch(String sql) throws SQLException {
        throw invalid();
    }

    @Override
    public String toString() {
        return sql + " \n parameters=" + Arrays.toString(batch);
    }

    // JDBC 4
    public void setRowId(int parameterIndex, RowId x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    public void setNString(int parameterIndex, String value) throws SQLException {
        setString(parameterIndex, value);
    }

    public void setNCharacterStream(int parameterIndex, Reader value, long length)
            throws SQLException {
        setCharacterStream(parameterIndex, value, length);
    }

    public void setNClob(int parameterIndex, NClob value) throws SQLException {
        if (value == null) {
            setString(parameterIndex, null);
        } else {
            setNCharacterStream(parameterIndex, value.getCharacterStream(), value.length());
        }
    }

    public void setClob(int parameterIndex, Reader reader, long length) throws SQLException {
        requireLengthIsPositiveInt(length);
        setCharacterStream(parameterIndex, reader, (int) length);
    }

    private void requireLengthIsPositiveInt(long length) throws SQLFeatureNotSupportedException {
        if (length > Integer.MAX_VALUE || length < 0) {
            throw new SQLFeatureNotSupportedException(
                    "Data must have a length between 0 and Integer.MAX_VALUE");
        }
    }

    public void setBlob(int parameterIndex, InputStream inputStream, long length)
            throws SQLException {
        requireLengthIsPositiveInt(length);
        setBinaryStream(parameterIndex, inputStream, (int) length);
    }

    public void setNClob(int parameterIndex, Reader reader, long length) throws SQLException {
        setNCharacterStream(parameterIndex, reader, length);
    }

    public void setSQLXML(int parameterIndex, SQLXML xmlObject) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    public void setAsciiStream(int parameterIndex, InputStream x, long length) throws SQLException {
        requireLengthIsPositiveInt(length);
        setAsciiStream(parameterIndex, x, (int) length);
    }

    public void setBinaryStream(int parameterIndex, InputStream x, long length)
            throws SQLException {
        requireLengthIsPositiveInt(length);
        setBinaryStream(parameterIndex, x, (int) length);
    }

    public void setCharacterStream(int parameterIndex, Reader reader, long length)
            throws SQLException {
        requireLengthIsPositiveInt(length);
        setCharacterStream(parameterIndex, reader, (int) length);
    }

    public void setAsciiStream(int parameterIndex, InputStream x) throws SQLException {
        byte[] bytes = readBytes(x);
        setAsciiStream(parameterIndex, new ByteArrayInputStream(bytes), bytes.length);
    }

    /**
     * Reads given number of bytes from an input stream.
     *
     * @param istream The input stream.
     * @param length The number of bytes to read.
     * @return byte array.
     */
    private byte[] readBytes(InputStream istream) throws SQLException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] bytes = new byte[8192];

        try {
            int bytesRead;
            while ((bytesRead = istream.read(bytes)) > 0) {
                baos.write(bytes, 0, bytesRead);
            }
            return baos.toByteArray();
        } catch (IOException cause) {
            SQLException exception = new SQLException("Error reading stream");

            exception.initCause(cause);
            throw exception;
        }
    }

    public void setBinaryStream(int parameterIndex, InputStream x) throws SQLException {
        setBytes(parameterIndex, readBytes(x));
    }

    public void setCharacterStream(int parameterIndex, Reader reader) throws SQLException {
        setCharacterStream(parameterIndex, reader, Integer.MAX_VALUE);
    }

    public void setNCharacterStream(int parameterIndex, Reader value) throws SQLException {
        setCharacterStream(parameterIndex, value);
    }

    public void setClob(int parameterIndex, Reader reader) throws SQLException {
        setCharacterStream(parameterIndex, reader, Integer.MAX_VALUE);
    }

    public void setBlob(int parameterIndex, InputStream inputStream) throws SQLException {
        setBytes(parameterIndex, readBytes(inputStream));
    }

    public void setNClob(int parameterIndex, Reader reader) throws SQLException {
        setNCharacterStream(parameterIndex, reader);
    }
}
