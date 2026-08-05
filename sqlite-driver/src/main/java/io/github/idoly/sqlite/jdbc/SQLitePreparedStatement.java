package io.github.idoly.sqlite.jdbc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.BatchUpdateException;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.NClob;
import java.sql.ParameterMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLType;
import java.sql.SQLXML;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

/**
 * Reusable prepared statement with canonical parameter values and positional binding.
 *
 * <p>The native handle survives successful executions; reset clears execution state but preserves
 * bindings, matching SQLite and JDBC prepared-statement reuse semantics.
 */
final class SQLitePreparedStatement extends PreparedStatementAdapter {
    private final NativeStatement nativeStatement;
    private final Object[] parameterValues;
    private final boolean[] parametersBound;
    private final List<Object[]> batchParameterValues = new ArrayList<>();
    private final List<boolean[]> batchParametersBound = new ArrayList<>();
    private final boolean generatedKeys;
    private final String sql;
    private boolean preparedClosed;

    SQLitePreparedStatement(SQLiteConnection connection, String sql) throws SQLException {
        this(connection, sql, false);
    }

    SQLitePreparedStatement(SQLiteConnection connection, String sql, boolean generatedKeys) throws SQLException {
        super(connection);
        this.generatedKeys = generatedKeys;
        this.sql = sql;
        this.nativeStatement = connection.prepareForReuse(sql);
        this.parameterValues = new Object[nativeStatement.parameterCount()];
        this.parametersBound = new boolean[parameterValues.length];
    }

    @Override
    public ResultSet executeQuery() throws SQLException {
        ensureOpen();
        resetExecution();
        ensureParametersBound();
        if (nativeStatement.columnCount() == 0) {
            throw new SQLException("SQL does not produce a result set", "07000");
        }
        connection.beginTransactionIfNeeded();
        try {
            currentResultSet = new SQLiteResultSet(this, nativeStatement, true);
            updateCount = -1;
            return currentResultSet;
        } catch (SQLException | RuntimeException error) {
            resetAfterFailure(error);
            throw error;
        }
    }

    @Override
    public int executeUpdate() throws SQLException {
        ensureOpen();
        resetExecution();
        ensureParametersBound();
        if (nativeStatement.columnCount() != 0) {
            throw new SQLException("SQL produces a result set", "07000");
        }
        connection.beginTransactionIfNeeded();
        try {
            updateCount = runUpdate(nativeStatement);
            captureGeneratedKey(generatedKeys, sql);
            return updateCount;
        } catch (NativeException error) {
            throw sqlException(error);
        } finally {
            resetNativeStatement();
        }
    }

    @Override
    public boolean execute() throws SQLException {
        ensureOpen();
        resetExecution();
        ensureParametersBound();
        connection.beginTransactionIfNeeded();
        if (nativeStatement.columnCount() > 0) {
            try {
                currentResultSet = new SQLiteResultSet(this, nativeStatement, true);
                updateCount = -1;
                return true;
            } catch (SQLException | RuntimeException error) {
                resetAfterFailure(error);
                throw error;
            }
        }
        try {
            updateCount = runUpdate(nativeStatement);
            captureGeneratedKey(generatedKeys, sql);
            return false;
        } catch (NativeException error) {
            throw sqlException(error);
        } finally {
            resetNativeStatement();
        }
    }

    @Override
    public void close() throws SQLException {
        if (preparedClosed) return;
        SQLException failure = null;
        try {
            super.close();
        } catch (SQLException error) {
            failure = error;
        }
        try {
            nativeStatement.close();
        } catch (NativeException error) {
            SQLException nativeFailure = sqlException(error);
            if (failure == null) failure = nativeFailure;
            else failure.addSuppressed(nativeFailure);
        }
        preparedClosed = true;
        if (failure != null) throw failure;
    }

    @Override
    public boolean isClosed() {
        return preparedClosed || super.isClosed() || !nativeStatement.isOpen();
    }

    @Override
    public void clearParameters() throws SQLException {
        ensureOpen();
        try {
            nativeStatement.clearBindings();
            Arrays.fill(parameterValues, null);
            Arrays.fill(parametersBound, false);
        } catch (NativeException error) {
            throw sqlException(error);
        }
    }

    @Override
    public void setNull(int parameterIndex, int sqlType) throws SQLException {
        setValue(parameterIndex, null);
    }

    @Override
    public void setNull(int parameterIndex, int sqlType, String typeName) throws SQLException {
        setNull(parameterIndex, sqlType);
    }

    @Override
    public void setBoolean(int parameterIndex, boolean value) throws SQLException {
        setValue(parameterIndex, value ? 1L : 0L);
    }

    @Override
    public void setByte(int parameterIndex, byte value) throws SQLException { setValue(parameterIndex, (long) value); }
    @Override
    public void setShort(int parameterIndex, short value) throws SQLException { setValue(parameterIndex, (long) value); }
    @Override
    public void setInt(int parameterIndex, int value) throws SQLException { setValue(parameterIndex, (long) value); }
    @Override
    public void setLong(int parameterIndex, long value) throws SQLException { setValue(parameterIndex, value); }
    @Override
    public void setFloat(int parameterIndex, float value) throws SQLException { setValue(parameterIndex, (double) value); }
    @Override
    public void setDouble(int parameterIndex, double value) throws SQLException { setValue(parameterIndex, value); }

    @Override
    public void setBigDecimal(int parameterIndex, BigDecimal value) throws SQLException {
        setValue(parameterIndex, value == null ? null : value.toPlainString());
    }

    @Override
    public void setString(int parameterIndex, String value) throws SQLException { setValue(parameterIndex, value); }

    @Override
    public void setBytes(int parameterIndex, byte[] value) throws SQLException {
        setValue(parameterIndex, value == null ? null : value.clone());
    }

    @Override
    public void setDate(int parameterIndex, Date value) throws SQLException {
        setValue(parameterIndex, value == null ? null : value.toString());
    }

    @Override
    public void setTime(int parameterIndex, Time value) throws SQLException {
        setValue(parameterIndex, value == null ? null : value.toString());
    }

    @Override
    public void setTimestamp(int parameterIndex, Timestamp value) throws SQLException {
        setValue(parameterIndex, value == null ? null : value.toString());
    }

    @Override
    public void setDate(int parameterIndex, Date value, Calendar calendar) throws SQLException {
        if (value == null) {
            setNull(parameterIndex, Types.DATE);
            return;
        }
        Calendar effectiveCalendar = calendarOrDefault(calendar);
        effectiveCalendar.setTimeInMillis(value.getTime());
        setString(parameterIndex, String.format(java.util.Locale.ROOT, "%04d-%02d-%02d",
                effectiveCalendar.get(Calendar.YEAR), effectiveCalendar.get(Calendar.MONTH) + 1,
                effectiveCalendar.get(Calendar.DAY_OF_MONTH)));
    }

    @Override
    public void setTime(int parameterIndex, Time value, Calendar calendar) throws SQLException {
        if (value == null) {
            setNull(parameterIndex, Types.TIME);
            return;
        }
        Calendar effectiveCalendar = calendarOrDefault(calendar);
        effectiveCalendar.setTimeInMillis(value.getTime());
        setString(parameterIndex, String.format(java.util.Locale.ROOT, "%02d:%02d:%02d",
                effectiveCalendar.get(Calendar.HOUR_OF_DAY), effectiveCalendar.get(Calendar.MINUTE),
                effectiveCalendar.get(Calendar.SECOND)));
    }

    @Override
    public void setTimestamp(int parameterIndex, Timestamp value, Calendar calendar) throws SQLException {
        if (value == null) {
            setNull(parameterIndex, Types.TIMESTAMP);
            return;
        }
        Calendar effectiveCalendar = calendarOrDefault(calendar);
        effectiveCalendar.setTimeInMillis(value.getTime());
        java.time.LocalDateTime localDateTime = java.time.LocalDateTime.of(
                effectiveCalendar.get(Calendar.YEAR), effectiveCalendar.get(Calendar.MONTH) + 1,
                effectiveCalendar.get(Calendar.DAY_OF_MONTH), effectiveCalendar.get(Calendar.HOUR_OF_DAY),
                effectiveCalendar.get(Calendar.MINUTE), effectiveCalendar.get(Calendar.SECOND), value.getNanos());
        setString(parameterIndex, Timestamp.valueOf(localDateTime).toString());
    }

    @Override
    public void setURL(int parameterIndex, URL value) throws SQLException {
        setString(parameterIndex, value == null ? null : value.toExternalForm());
    }

    @Override
    public void setNString(int parameterIndex, String value) throws SQLException { setString(parameterIndex, value); }

    @Override
    public void setObject(int parameterIndex, Object value) throws SQLException {
        if (value == null) setNull(parameterIndex, Types.NULL);
        else if (value instanceof Boolean booleanValue) setBoolean(parameterIndex, booleanValue);
        else if (value instanceof Byte byteValue) setByte(parameterIndex, byteValue);
        else if (value instanceof Short shortValue) setShort(parameterIndex, shortValue);
        else if (value instanceof Integer intValue) setInt(parameterIndex, intValue);
        else if (value instanceof Long longValue) setLong(parameterIndex, longValue);
        else if (value instanceof Float floatValue) setFloat(parameterIndex, floatValue);
        else if (value instanceof Double doubleValue) setDouble(parameterIndex, doubleValue);
        else if (value instanceof BigDecimal decimal) setBigDecimal(parameterIndex, decimal);
        else if (value instanceof String text) setString(parameterIndex, text);
        else if (value instanceof byte[] bytes) setBytes(parameterIndex, bytes);
        else if (value instanceof Date date) setDate(parameterIndex, date);
        else if (value instanceof Time time) setTime(parameterIndex, time);
        else if (value instanceof Timestamp timestamp) setTimestamp(parameterIndex, timestamp);
        else if (value instanceof URL url) setURL(parameterIndex, url);
        else if (value instanceof java.time.LocalDate date) setDate(parameterIndex, Date.valueOf(date));
        else if (value instanceof java.time.LocalTime time) setTime(parameterIndex, Time.valueOf(time));
        else if (value instanceof java.time.LocalDateTime dateTime) {
            setTimestamp(parameterIndex, Timestamp.valueOf(dateTime));
        }
        else throw new SQLException("Unsupported parameter type: " + value.getClass().getName(), "07006");
    }

    @Override
    public void setObject(int parameterIndex, Object value, int targetSqlType) throws SQLException {
        setObject(parameterIndex, value, targetSqlType, 0);
    }

    @Override
    public void setObject(int parameterIndex, Object value, SQLType targetSqlType) throws SQLException {
        setObject(parameterIndex, value, sqlTypeNumber(targetSqlType), 0);
    }

    @Override
    public void setObject(int parameterIndex, Object value, SQLType targetSqlType, int scaleOrLength)
            throws SQLException {
        setObject(parameterIndex, value, sqlTypeNumber(targetSqlType), scaleOrLength);
    }

    @Override
    public void setObject(int parameterIndex, Object value, int targetSqlType, int scaleOrLength) throws SQLException {
        if (value == null) {
            setNull(parameterIndex, targetSqlType);
            return;
        }
        try {
            switch (targetSqlType) {
                case Types.BIT, Types.BOOLEAN -> setBoolean(parameterIndex, asBoolean(value));
                case Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT ->
                    setLong(parameterIndex, asNumber(value).longValue());
                case Types.REAL, Types.FLOAT, Types.DOUBLE -> setDouble(parameterIndex, asNumber(value).doubleValue());
                case Types.NUMERIC, Types.DECIMAL -> setBigDecimal(parameterIndex, new BigDecimal(value.toString()));
                case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> {
                    if (!(value instanceof byte[] bytes)) throw conversionError(value, targetSqlType);
                    setBytes(parameterIndex, bytes);
                }
                case Types.DATE -> setDate(
                        parameterIndex, value instanceof Date date ? date : Date.valueOf(value.toString()));
                case Types.TIME -> setTime(
                        parameterIndex, value instanceof Time time ? time : Time.valueOf(value.toString()));
                case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> setTimestamp(parameterIndex,
                        value instanceof Timestamp timestamp ? timestamp : Timestamp.valueOf(value.toString()));
                default -> setString(parameterIndex, value.toString());
            }
        } catch (IllegalArgumentException error) {
            throw new SQLException(
                    "Cannot convert " + value.getClass().getName() + " to SQL type " + targetSqlType,
                    "22018", error);
        }
    }

    @Override
    public void setAsciiStream(int parameterIndex, InputStream stream) throws SQLException {
        byte[] bytes = readBytes(stream, -1);
        setString(parameterIndex, bytes == null ? null : new String(bytes, StandardCharsets.US_ASCII));
    }

    @Override
    public void setAsciiStream(int parameterIndex, InputStream stream, int length) throws SQLException {
        setAsciiStream(parameterIndex, stream, (long) length);
    }

    @Override
    public void setAsciiStream(int parameterIndex, InputStream stream, long length) throws SQLException {
        byte[] bytes = readBytes(stream, length);
        setString(parameterIndex, bytes == null ? null : new String(bytes, StandardCharsets.US_ASCII));
    }

    @Override
    @Deprecated
    public void setUnicodeStream(int parameterIndex, InputStream stream, int length) throws SQLException {
        byte[] bytes = readBytes(stream, length);
        setString(parameterIndex, bytes == null ? null : new String(bytes, StandardCharsets.UTF_8));
    }

    @Override
    public void setBinaryStream(int parameterIndex, InputStream stream) throws SQLException {
        setBytes(parameterIndex, readBytes(stream, -1));
    }

    @Override
    public void setBinaryStream(int parameterIndex, InputStream stream, int length) throws SQLException {
        setBinaryStream(parameterIndex, stream, (long) length);
    }

    @Override
    public void setBinaryStream(int parameterIndex, InputStream stream, long length) throws SQLException {
        setBytes(parameterIndex, readBytes(stream, length));
    }

    @Override
    public void setCharacterStream(int parameterIndex, Reader reader) throws SQLException {
        setString(parameterIndex, readCharacters(reader, -1));
    }

    @Override
    public void setCharacterStream(int parameterIndex, Reader reader, int length) throws SQLException {
        setCharacterStream(parameterIndex, reader, (long) length);
    }

    @Override
    public void setCharacterStream(int parameterIndex, Reader reader, long length) throws SQLException {
        setString(parameterIndex, readCharacters(reader, length));
    }

    @Override
    public void setNCharacterStream(int parameterIndex, Reader reader) throws SQLException {
        setCharacterStream(parameterIndex, reader);
    }

    @Override
    public void setNCharacterStream(int parameterIndex, Reader reader, long length) throws SQLException {
        setCharacterStream(parameterIndex, reader, length);
    }

    @Override
    public void setBlob(int parameterIndex, Blob blob) throws SQLException {
        if (blob == null) setNull(parameterIndex, Types.BLOB);
        else {
            long length = blob.length();
            if (length > Integer.MAX_VALUE) throw new SQLException("BLOB is too large", "22001");
            setBytes(parameterIndex, blob.getBytes(1, (int) length));
        }
    }

    @Override
    public void setBlob(int parameterIndex, InputStream stream) throws SQLException {
        setBinaryStream(parameterIndex, stream);
    }

    @Override
    public void setBlob(int parameterIndex, InputStream stream, long length) throws SQLException {
        setBinaryStream(parameterIndex, stream, length);
    }

    @Override
    public void setNClob(int parameterIndex, NClob clob) throws SQLException {
        setClob(parameterIndex, clob);
    }

    @Override
    public void setNClob(int parameterIndex, Reader reader) throws SQLException {
        setCharacterStream(parameterIndex, reader);
    }

    @Override
    public void setNClob(int parameterIndex, Reader reader, long length) throws SQLException {
        setCharacterStream(parameterIndex, reader, length);
    }

    @Override
    public void setSQLXML(int parameterIndex, SQLXML xml) throws SQLException {
        setString(parameterIndex, xml == null ? null : xml.getString());
    }

    @Override
    public void setClob(int parameterIndex, Clob clob) throws SQLException {
        if (clob == null) setNull(parameterIndex, Types.CLOB);
        else {
            long length = clob.length();
            if (length > Integer.MAX_VALUE) throw new SQLException("CLOB is too large", "22001");
            setString(parameterIndex, clob.getSubString(1, (int) length));
        }
    }

    @Override
    public void setClob(int parameterIndex, Reader reader) throws SQLException {
        setCharacterStream(parameterIndex, reader);
    }

    @Override
    public void setClob(int parameterIndex, Reader reader, long length) throws SQLException {
        setCharacterStream(parameterIndex, reader, length);
    }

    @Override
    public void addBatch() throws SQLException {
        ensureOpen();
        ensureParametersBound();
        batchParameterValues.add(copyParameterValues(parameterValues));
        batchParametersBound.add(parametersBound.clone());
    }

    @Override
    public void clearBatch() throws SQLException {
        ensureOpen();
        super.clearBatch();
        batchParameterValues.clear();
        batchParametersBound.clear();
    }

    @Override
    public int[] executeBatch() throws SQLException {
        ensureOpen();
        Object[] originalParameterValues = copyParameterValues(parameterValues);
        boolean[] originalParametersBound = parametersBound.clone();
        int[] counts = new int[batchParameterValues.size()];
        int completed = 0;
        SQLException executionFailure = null;
        try {
            for (; completed < batchParameterValues.size(); completed++) {
                applyBindings(batchParameterValues.get(completed), batchParametersBound.get(completed));
                counts[completed] = executeUpdate();
            }
            return counts;
        } catch (SQLException error) {
            BatchUpdateException batchFailure = new BatchUpdateException(
                    error.getMessage(), error.getSQLState(), error.getErrorCode(),
                    Arrays.copyOf(counts, completed), error);
            executionFailure = batchFailure;
            throw batchFailure;
        } finally {
            batchParameterValues.clear();
            batchParametersBound.clear();
            try {
                applyBindings(originalParameterValues, originalParametersBound);
            } catch (SQLException restoreFailure) {
                if (executionFailure == null) throw restoreFailure;
                executionFailure.addSuppressed(restoreFailure);
            }
        }
    }

    @Override
    public long executeLargeUpdate() throws SQLException {
        return executeUpdate();
    }

    @Override
    public ResultSetMetaData getMetaData() throws SQLException {
        ensureOpen();
        if (nativeStatement.columnCount() == 0) return null;
        List<SQLiteResultSetMetaData.Column> columns = new ArrayList<>(nativeStatement.columnCount());
        for (int i = 1; i <= nativeStatement.columnCount(); i++) {
            columns.add(new SQLiteResultSetMetaData.Column(
                    nativeStatement.columnName(i), nativeStatement.declaredType(i)));
        }
        return new SQLiteResultSetMetaData(columns);
    }

    @Override
    public ParameterMetaData getParameterMetaData() throws SQLException {
        ensureOpen();
        return new SQLiteParameterMetaData(parameterValues.length);
    }

    @Override
    public ResultSet executeQuery(String ignoredSql) throws SQLException {
        throw preparedStatementSqlMethod();
    }
    @Override
    public int executeUpdate(String ignoredSql) throws SQLException { throw preparedStatementSqlMethod(); }
    @Override
    public boolean execute(String ignoredSql) throws SQLException { throw preparedStatementSqlMethod(); }
    @Override
    public void addBatch(String ignoredSql) throws SQLException { throw preparedStatementSqlMethod(); }
    @Override
    public int executeUpdate(String ignoredSql, int autoGeneratedKeys) throws SQLException { throw preparedStatementSqlMethod(); }
    @Override
    public int executeUpdate(String ignoredSql, int[] columnIndexes) throws SQLException { throw preparedStatementSqlMethod(); }
    @Override
    public int executeUpdate(String ignoredSql, String[] columnNames) throws SQLException { throw preparedStatementSqlMethod(); }
    @Override
    public boolean execute(String ignoredSql, int autoGeneratedKeys) throws SQLException { throw preparedStatementSqlMethod(); }
    @Override
    public boolean execute(String ignoredSql, int[] columnIndexes) throws SQLException { throw preparedStatementSqlMethod(); }
    @Override
    public boolean execute(String ignoredSql, String[] columnNames) throws SQLException { throw preparedStatementSqlMethod(); }
    @Override
    public long executeLargeUpdate(String ignoredSql) throws SQLException { throw preparedStatementSqlMethod(); }
    @Override
    public long executeLargeUpdate(String ignoredSql, int autoGeneratedKeys) throws SQLException { throw preparedStatementSqlMethod(); }
    @Override
    public long executeLargeUpdate(String ignoredSql, int[] columnIndexes) throws SQLException { throw preparedStatementSqlMethod(); }
    @Override
    public long executeLargeUpdate(String ignoredSql, String[] columnNames) throws SQLException { throw preparedStatementSqlMethod(); }

    private void setValue(int parameterIndex, Object value) throws SQLException {
        ensureOpen();
        validateParameterIndex(parameterIndex);
        Object storedValue = value instanceof byte[] bytes ? bytes.clone() : value;
        try {
            bindValue(parameterIndex, storedValue);
            parameterValues[parameterIndex - 1] = storedValue;
            parametersBound[parameterIndex - 1] = true;
        } catch (NativeException | IndexOutOfBoundsException error) {
            throw parameterException(error);
        }
    }

    private void bindValue(int parameterIndex, Object value) {
        if (value == null) nativeStatement.bindNull(parameterIndex);
        else if (value instanceof Long number) nativeStatement.bindLong(parameterIndex, number);
        else if (value instanceof Double number) nativeStatement.bindDouble(parameterIndex, number);
        else if (value instanceof String text) nativeStatement.bindText(parameterIndex, text);
        else if (value instanceof byte[] bytes) nativeStatement.bindBlob(parameterIndex, bytes);
        else throw new IllegalArgumentException("Unsupported canonical parameter type");
    }

    private void applyBindings(Object[] newParameterValues, boolean[] newParametersBound) throws SQLException {
        try {
            nativeStatement.clearBindings();
            for (int i = 0; i < newParameterValues.length; i++) {
                if (newParametersBound[i]) bindValue(i + 1, newParameterValues[i]);
            }
            for (int i = 0; i < newParameterValues.length; i++) {
                parameterValues[i] = newParameterValues[i] instanceof byte[] bytes ? bytes.clone() : newParameterValues[i];
                parametersBound[i] = newParametersBound[i];
            }
        } catch (NativeException error) {
            throw sqlException(error);
        }
    }

    private void ensureParametersBound() throws SQLException {
        for (int i = 0; i < parametersBound.length; i++) {
            if (!parametersBound[i]) throw new SQLException("Parameter " + (i + 1) + " is not set", "07001");
        }
    }

    private void validateParameterIndex(int parameterIndex) throws SQLException {
        if (parameterIndex < 1 || parameterIndex > parameterValues.length) {
            throw new SQLException("Parameter index out of range: " + parameterIndex, "07009");
        }
    }

    @Override
    final void ensureOpen() throws SQLException {
        if (isClosed()) throw new SQLException("PreparedStatement is closed", "07000");
    }

    private void resetNativeStatement() throws SQLException {
        try {
            if (nativeStatement.isOpen()) nativeStatement.reset();
        } catch (NativeException error) {
            throw sqlException(error);
        }
    }

    private void resetAfterFailure(Throwable failure) {
        try {
            if (nativeStatement.isOpen()) nativeStatement.reset();
        } catch (RuntimeException resetFailure) {
            failure.addSuppressed(resetFailure);
        }
    }

    private static Object[] copyParameterValues(Object[] source) {
        Object[] copy = source.clone();
        for (int i = 0; i < copy.length; i++) {
            if (copy[i] instanceof byte[] bytes) copy[i] = bytes.clone();
        }
        return copy;
    }

    private static byte[] readBytes(InputStream stream, long length) throws SQLException {
        if (stream == null) return null;
        requireLength(length);
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            long remaining = length;
            while (length < 0 || remaining > 0) {
                int requested = length < 0 ? buffer.length : (int) Math.min(buffer.length, remaining);
                int read = stream.read(buffer, 0, requested);
                if (read < 0) break;
                if (read == 0) {
                    int singleByte = stream.read();
                    if (singleByte < 0) break;
                    output.write(singleByte);
                    if (length >= 0) remaining--;
                    continue;
                }
                output.write(buffer, 0, read);
                if (length >= 0) remaining -= read;
            }
            if (length >= 0 && remaining != 0) throw new SQLException("Stream ended before declared length", "22001");
            return output.toByteArray();
        } catch (IOException error) {
            throw new SQLException("Could not read parameter stream", "HY000", error);
        }
    }

    private static String readCharacters(Reader reader, long length) throws SQLException {
        if (reader == null) return null;
        requireLength(length);
        try {
            StringBuilder result = new StringBuilder();
            char[] buffer = new char[4096];
            long remaining = length;
            while (length < 0 || remaining > 0) {
                int requested = length < 0 ? buffer.length : (int) Math.min(buffer.length, remaining);
                int read = reader.read(buffer, 0, requested);
                if (read < 0) break;
                if (read == 0) {
                    int singleCharacter = reader.read();
                    if (singleCharacter < 0) break;
                    result.append((char) singleCharacter);
                    if (length >= 0) remaining--;
                    continue;
                }
                result.append(buffer, 0, read);
                if (length >= 0) remaining -= read;
            }
            if (length >= 0 && remaining != 0) throw new SQLException("Reader ended before declared length", "22001");
            return result.toString();
        } catch (IOException error) {
            throw new SQLException("Could not read parameter reader", "HY000", error);
        }
    }

    private static void requireLength(long length) throws SQLException {
        if (length < -1 || length > Integer.MAX_VALUE) {
            throw new SQLException("Invalid stream length: " + length, "22001");
        }
    }

    private static Calendar calendarOrDefault(Calendar calendar) {
        return calendar == null ? Calendar.getInstance() : (Calendar) calendar.clone();
    }

    private static int sqlTypeNumber(SQLType sqlType) throws SQLException {
        if (sqlType == null || sqlType.getVendorTypeNumber() == null) {
            throw new SQLException("Target SQL type cannot be null", "HY009");
        }
        return sqlType.getVendorTypeNumber();
    }

    private static Number asNumber(Object value) throws SQLException {
        if (value instanceof Number number) return number;
        try { return new BigDecimal(value.toString()); }
        catch (NumberFormatException error) { throw conversionError(value, Types.NUMERIC); }
    }

    private static boolean asBoolean(Object value) throws SQLException {
        if (value instanceof Boolean booleanValue) return booleanValue;
        if (value instanceof Number number) return number.doubleValue() != 0;
        String text = value.toString().trim();
        if (text.equalsIgnoreCase("true") || text.equals("1")) return true;
        if (text.equalsIgnoreCase("false") || text.equals("0")) return false;
        throw conversionError(value, Types.BOOLEAN);
    }

    private static SQLException conversionError(Object value, int type) {
        return new SQLException("Cannot convert " + value.getClass().getName() + " to SQL type " + type, "22018");
    }

    private static SQLException parameterException(Exception error) {
        if (error instanceof NativeException sqlite) return sqlException(sqlite);
        return new SQLException(error.getMessage(), "07009", error);
    }

    private static SQLException sqlException(NativeException error) {
        return SqlExceptionMapper.map(error);
    }

    private static SQLException preparedStatementSqlMethod() {
        return new SQLException("SQL text methods cannot be called on PreparedStatement", "HY000");
    }
}
