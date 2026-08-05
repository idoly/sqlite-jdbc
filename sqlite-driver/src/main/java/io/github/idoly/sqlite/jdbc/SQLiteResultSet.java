package io.github.idoly.sqlite.jdbc;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.NClob;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.sql.rowset.serial.SerialBlob;
import javax.sql.rowset.serial.SerialClob;

/**
 * Forward-only, read-only cursor with an explicit position state machine.
 *
 * <p>The first native row is prefetched so {@code execute()} can report result availability without
 * advancing the JDBC cursor. Closing either resets a reusable prepared handle or finalizes an
 * ordinary statement handle.
 */
@SuppressWarnings("deprecation")
final class SQLiteResultSet extends ResultSetAdapter {
    private enum Position { BEFORE, ROW, AFTER, CLOSED }

    private final SQLiteStatement owner;
    private final NativeStatement statement;
    private final SQLiteResultSetMetaData metadata;
    private final boolean reusable;
    private Position position;
    private boolean statementReleased;
    private boolean prefetched;
    private boolean hadRows;
    private boolean wasNull;
    private int row;

    SQLiteResultSet(SQLiteStatement owner, NativeStatement statement) throws SQLException {
        this(owner, statement, false);
    }

    SQLiteResultSet(SQLiteStatement owner, NativeStatement statement, boolean reusable) throws SQLException {
        this.owner = owner;
        this.statement = statement;
        this.metadata = readMetadata(statement);
        this.reusable = reusable;
        try {
            prefetched = owner.step(statement) == StepResult.ROW;
            hadRows = prefetched;
            position = prefetched ? Position.BEFORE : Position.AFTER;
            if (!prefetched) releaseStatement();
        } catch (NativeException error) {
            statement.close();
            throw sqlException(error);
        }
    }

    @Override
    public boolean next() throws SQLException {
        ensureOpen();
        if (position == Position.AFTER) return false;
        if (position == Position.BEFORE && prefetched) {
            position = Position.ROW;
            row = 1;
            return true;
        }
        if (owner.getMaxRows() > 0 && row >= owner.getMaxRows()) {
            finish();
            return false;
        }
        try {
            if (owner.step(statement) == StepResult.ROW) {
                position = Position.ROW;
                row++;
                hadRows = true;
                return true;
            }
            finish();
            return false;
        } catch (NativeException error) {
            throw sqlException(error);
        }
    }

    @Override
    public void close() throws SQLException {
        if (position == Position.CLOSED) return;
        SQLException failure = null;
        try {
            releaseStatement();
        } catch (NativeException error) {
            failure = sqlException(error);
        }
        position = Position.CLOSED;
        try {
            owner.onResultSetClosed(this);
        } catch (SQLException ownerFailure) {
            if (failure == null) failure = ownerFailure;
            else failure.addSuppressed(ownerFailure);
        }
        if (failure != null) throw failure;
    }

    @Override
    public boolean wasNull() throws SQLException {
        ensureOpen();
        return wasNull;
    }

    @Override
    public String getString(int columnIndex) throws SQLException {
        if (isNull(columnIndex)) return null;
        return statement.columnText(columnIndex);
    }

    @Override
    public boolean getBoolean(int columnIndex) throws SQLException {
        StorageClass type = storageClass(columnIndex);
        return switch (type) {
            case NULL -> false;
            case INTEGER -> statement.columnLong(columnIndex) != 0;
            case REAL -> statement.columnDouble(columnIndex) != 0;
            case TEXT -> parseBoolean(statement.columnText(columnIndex));
            case BLOB -> throw new SQLException("BLOB column cannot be converted to boolean", "22018");
        };
    }

    @Override
    public byte getByte(int columnIndex) throws SQLException {
        long value = getLong(columnIndex);
        if (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE) throw numericOverflow(value, "byte");
        return (byte) value;
    }

    @Override
    public short getShort(int columnIndex) throws SQLException {
        long value = getLong(columnIndex);
        if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) throw numericOverflow(value, "short");
        return (short) value;
    }

    @Override
    public int getInt(int columnIndex) throws SQLException {
        long value = getLong(columnIndex);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) throw numericOverflow(value, "int");
        return (int) value;
    }

    @Override
    public long getLong(int columnIndex) throws SQLException {
        if (isNull(columnIndex)) return 0;
        return statement.columnLong(columnIndex);
    }

    @Override
    public float getFloat(int columnIndex) throws SQLException {
        return (float) getDouble(columnIndex);
    }

    @Override
    public double getDouble(int columnIndex) throws SQLException {
        if (isNull(columnIndex)) return 0;
        return statement.columnDouble(columnIndex);
    }

    @Override
    public BigDecimal getBigDecimal(int columnIndex) throws SQLException {
        String value = getString(columnIndex);
        if (value == null) return null;
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException error) {
            throw new SQLException("Column cannot be converted to BigDecimal: " + value, "22018", error);
        }
    }

    @Override
    @Deprecated
    public BigDecimal getBigDecimal(int columnIndex, int scale) throws SQLException {
        BigDecimal value = getBigDecimal(columnIndex);
        return value == null ? null : value.setScale(scale, RoundingMode.HALF_UP);
    }

    @Override
    public byte[] getBytes(int columnIndex) throws SQLException {
        if (isNull(columnIndex)) return null;
        return statement.columnBlob(columnIndex);
    }

    @Override
    public Date getDate(int columnIndex) throws SQLException {
        String value = getString(columnIndex);
        if (value == null) return null;
        try {
            return parseDate(value);
        } catch (IllegalArgumentException error) {
            throw temporalConversion("Date", value, error);
        }
    }

    @Override
    public Time getTime(int columnIndex) throws SQLException {
        String value = getString(columnIndex);
        if (value == null) return null;
        try {
            return parseTime(value);
        } catch (IllegalArgumentException error) {
            throw temporalConversion("Time", value, error);
        }
    }

    @Override
    public Timestamp getTimestamp(int columnIndex) throws SQLException {
        String value = getString(columnIndex);
        if (value == null) return null;
        try {
            return parseTimestamp(value);
        } catch (IllegalArgumentException error) {
            throw temporalConversion("Timestamp", value, error);
        }
    }

    @Override
    public InputStream getAsciiStream(int columnIndex) throws SQLException {
        String value = getString(columnIndex);
        return value == null ? null : new ByteArrayInputStream(value.getBytes(StandardCharsets.US_ASCII));
    }

    @Override
    public InputStream getBinaryStream(int columnIndex) throws SQLException {
        byte[] value = getBytes(columnIndex);
        return value == null ? null : new ByteArrayInputStream(value);
    }

    @Override
    public Reader getCharacterStream(int columnIndex) throws SQLException {
        String value = getString(columnIndex);
        return value == null ? null : new StringReader(value);
    }

    @Override
    public Blob getBlob(int columnIndex) throws SQLException {
        byte[] value = getBytes(columnIndex);
        return value == null ? null : new SerialBlob(value);
    }

    @Override
    public Blob getBlob(String columnLabel) throws SQLException { return getBlob(findColumn(columnLabel)); }

    @Override
    public Clob getClob(int columnIndex) throws SQLException {
        String value = getString(columnIndex);
        return value == null ? null : new SerialClob(value.toCharArray());
    }

    @Override
    public Clob getClob(String columnLabel) throws SQLException { return getClob(findColumn(columnLabel)); }

    @Override
    public NClob getNClob(int columnIndex) throws SQLException {
        String value = getString(columnIndex);
        return value == null ? null : new SQLiteNClob(value);
    }

    @Override
    public NClob getNClob(String columnLabel) throws SQLException { return getNClob(findColumn(columnLabel)); }

    @Override
    public SQLXML getSQLXML(int columnIndex) throws SQLException {
        String value = getString(columnIndex);
        if (value == null) return null;
        SQLiteSQLXML xml = new SQLiteSQLXML();
        xml.setString(value);
        return xml;
    }

    @Override
    public SQLXML getSQLXML(String columnLabel) throws SQLException { return getSQLXML(findColumn(columnLabel)); }

    @Override
    public Object getObject(int columnIndex) throws SQLException {
        StorageClass type = storageClass(columnIndex);
        return switch (type) {
            case INTEGER -> statement.columnLong(columnIndex);
            case REAL -> statement.columnDouble(columnIndex);
            case TEXT -> statement.columnText(columnIndex);
            case BLOB -> statement.columnBlob(columnIndex);
            case NULL -> null;
        };
    }

    @Override
    public <T> T getObject(int columnIndex, Class<T> type) throws SQLException {
        if (type == null) throw new SQLException("Target type cannot be null", "HY009");
        if (isNull(columnIndex)) return null;
        Object value;
        if (type == String.class) value = getString(columnIndex);
        else if (type == Boolean.class) value = getBoolean(columnIndex);
        else if (type == Byte.class) value = getByte(columnIndex);
        else if (type == Short.class) value = getShort(columnIndex);
        else if (type == Integer.class) value = getInt(columnIndex);
        else if (type == Long.class) value = getLong(columnIndex);
        else if (type == Float.class) value = getFloat(columnIndex);
        else if (type == Double.class) value = getDouble(columnIndex);
        else if (type == BigDecimal.class) value = getBigDecimal(columnIndex);
        else if (type == byte[].class) value = getBytes(columnIndex);
        else if (type == Date.class) value = getDate(columnIndex);
        else if (type == Time.class) value = getTime(columnIndex);
        else if (type == Timestamp.class) value = getTimestamp(columnIndex);
        else if (type == java.time.LocalDate.class) {
            Date date = getDate(columnIndex);
            value = date == null ? null : date.toLocalDate();
        } else if (type == java.time.LocalTime.class) {
            Time time = getTime(columnIndex);
            value = time == null ? null : time.toLocalTime();
        } else if (type == java.time.LocalDateTime.class) {
            Timestamp timestamp = getTimestamp(columnIndex);
            value = timestamp == null ? null : timestamp.toLocalDateTime();
        } else if (type == Blob.class) value = getBlob(columnIndex);
        else if (type == Clob.class) value = getClob(columnIndex);
        else if (type == NClob.class) value = getNClob(columnIndex);
        else if (type == SQLXML.class) value = getSQLXML(columnIndex);
        else if (type == Object.class) value = getObject(columnIndex);
        else throw new SQLException("Unsupported target type: " + type.getName(), "22005");
        return value == null ? null : type.cast(value);
    }

    @Override
    public String getString(String columnLabel) throws SQLException { return getString(findColumn(columnLabel)); }
    @Override
    public boolean getBoolean(String columnLabel) throws SQLException { return getBoolean(findColumn(columnLabel)); }
    @Override
    public byte getByte(String columnLabel) throws SQLException { return getByte(findColumn(columnLabel)); }
    @Override
    public short getShort(String columnLabel) throws SQLException { return getShort(findColumn(columnLabel)); }
    @Override
    public int getInt(String columnLabel) throws SQLException { return getInt(findColumn(columnLabel)); }
    @Override
    public long getLong(String columnLabel) throws SQLException { return getLong(findColumn(columnLabel)); }
    @Override
    public float getFloat(String columnLabel) throws SQLException { return getFloat(findColumn(columnLabel)); }
    @Override
    public double getDouble(String columnLabel) throws SQLException { return getDouble(findColumn(columnLabel)); }
    @Override
    public BigDecimal getBigDecimal(String columnLabel) throws SQLException { return getBigDecimal(findColumn(columnLabel)); }
    @Override
    @Deprecated
    public BigDecimal getBigDecimal(String columnLabel, int scale) throws SQLException {
        return getBigDecimal(findColumn(columnLabel), scale);
    }
    @Override
    public byte[] getBytes(String columnLabel) throws SQLException { return getBytes(findColumn(columnLabel)); }
    @Override
    public Date getDate(String columnLabel) throws SQLException { return getDate(findColumn(columnLabel)); }
    @Override
    public Time getTime(String columnLabel) throws SQLException { return getTime(findColumn(columnLabel)); }
    @Override
    public Timestamp getTimestamp(String columnLabel) throws SQLException { return getTimestamp(findColumn(columnLabel)); }
    @Override
    public InputStream getAsciiStream(String columnLabel) throws SQLException {
        return getAsciiStream(findColumn(columnLabel));
    }
    @Override
    public InputStream getBinaryStream(String columnLabel) throws SQLException {
        return getBinaryStream(findColumn(columnLabel));
    }
    @Override
    public Reader getCharacterStream(String columnLabel) throws SQLException {
        return getCharacterStream(findColumn(columnLabel));
    }
    @Override
    public Object getObject(String columnLabel) throws SQLException { return getObject(findColumn(columnLabel)); }
    @Override
    public <T> T getObject(String columnLabel, Class<T> type) throws SQLException {
        return getObject(findColumn(columnLabel), type);
    }

    @Override
    public int findColumn(String columnLabel) throws SQLException {
        ensureOpen();
        if (columnLabel == null) throw new SQLException("Column label cannot be null", "HY009");
        for (int i = 1; i <= metadata.getColumnCount(); i++) {
            if (columnLabel.equals(metadata.getColumnLabel(i))) return i;
        }
        for (int i = 1; i <= metadata.getColumnCount(); i++) {
            if (columnLabel.equalsIgnoreCase(metadata.getColumnLabel(i))) return i;
        }
        throw new SQLException("Unknown column: " + columnLabel, "S0022");
    }

    @Override
    public ResultSetMetaData getMetaData() throws SQLException {
        ensureOpen();
        return metadata;
    }

    @Override
    public SQLWarning getWarnings() throws SQLException { ensureOpen(); return null; }
    @Override
    public void clearWarnings() throws SQLException { ensureOpen(); }
    @Override
    public String getCursorName() throws SQLException { ensureOpen(); return null; }
    @Override
    public java.sql.Statement getStatement() throws SQLException { ensureOpen(); return owner; }
    @Override
    public int getType() throws SQLException { ensureOpen(); return TYPE_FORWARD_ONLY; }
    @Override
    public int getConcurrency() throws SQLException { ensureOpen(); return CONCUR_READ_ONLY; }
    @Override
    public int getHoldability() throws SQLException { ensureOpen(); return CLOSE_CURSORS_AT_COMMIT; }
    @Override
    public int getFetchDirection() throws SQLException { ensureOpen(); return FETCH_FORWARD; }
    @Override
    public void setFetchDirection(int direction) throws SQLException {
        ensureOpen();
        if (direction != FETCH_FORWARD) throw unsupported();
    }
    @Override
    public int getFetchSize() throws SQLException { ensureOpen(); return owner.getFetchSize(); }
    @Override
    public void setFetchSize(int rows) throws SQLException { owner.setFetchSize(rows); }
    @Override
    public int getRow() throws SQLException { ensureOpen(); return position == Position.ROW ? row : 0; }
    @Override
    public boolean isBeforeFirst() throws SQLException { ensureOpen(); return position == Position.BEFORE; }
    @Override
    public boolean isAfterLast() throws SQLException { ensureOpen(); return position == Position.AFTER && hadRows; }
    @Override
    public boolean isFirst() throws SQLException { ensureOpen(); return position == Position.ROW && row == 1; }
    @Override
    public boolean rowUpdated() throws SQLException { ensureOpen(); return false; }
    @Override
    public boolean rowInserted() throws SQLException { ensureOpen(); return false; }
    @Override
    public boolean rowDeleted() throws SQLException { ensureOpen(); return false; }
    @Override
    public boolean isClosed() { return position == Position.CLOSED; }

    @Override
    public Date getDate(int columnIndex, Calendar calendar) throws SQLException {
        String value = getString(columnIndex);
        if (value == null) return null;
        try {
            java.time.LocalDate localDate = parseDate(value).toLocalDate();
            Calendar effectiveCalendar = calendarOrDefault(calendar);
            effectiveCalendar.clear();
            effectiveCalendar.set(localDate.getYear(), localDate.getMonthValue() - 1, localDate.getDayOfMonth());
            return new Date(effectiveCalendar.getTimeInMillis());
        } catch (IllegalArgumentException error) {
            throw temporalConversion("Date", value, error);
        }
    }
    @Override
    public Date getDate(String columnLabel, Calendar calendar) throws SQLException {
        return getDate(findColumn(columnLabel), calendar);
    }
    @Override
    public Time getTime(int columnIndex, Calendar calendar) throws SQLException {
        String value = getString(columnIndex);
        if (value == null) return null;
        try {
            java.time.LocalTime localTime = parseTime(value).toLocalTime();
            Calendar effectiveCalendar = calendarOrDefault(calendar);
            effectiveCalendar.clear();
            effectiveCalendar.set(1970, Calendar.JANUARY, 1,
                    localTime.getHour(), localTime.getMinute(), localTime.getSecond());
            return new Time(effectiveCalendar.getTimeInMillis());
        } catch (IllegalArgumentException error) {
            throw temporalConversion("Time", value, error);
        }
    }
    @Override
    public Time getTime(String columnLabel, Calendar calendar) throws SQLException {
        return getTime(findColumn(columnLabel), calendar);
    }
    @Override
    public Timestamp getTimestamp(int columnIndex, Calendar calendar) throws SQLException {
        String value = getString(columnIndex);
        if (value == null) return null;
        try {
            java.time.LocalDateTime localDateTime = parseTimestamp(value).toLocalDateTime();
            Calendar effectiveCalendar = calendarOrDefault(calendar);
            effectiveCalendar.clear();
            effectiveCalendar.set(localDateTime.getYear(), localDateTime.getMonthValue() - 1,
                    localDateTime.getDayOfMonth(), localDateTime.getHour(), localDateTime.getMinute(),
                    localDateTime.getSecond());
            Timestamp result = new Timestamp(effectiveCalendar.getTimeInMillis());
            result.setNanos(localDateTime.getNano());
            return result;
        } catch (IllegalArgumentException error) {
            throw temporalConversion("Timestamp", value, error);
        }
    }
    @Override
    public Timestamp getTimestamp(String columnLabel, Calendar calendar) throws SQLException {
        return getTimestamp(findColumn(columnLabel), calendar);
    }
    @Override
    public URL getURL(int columnIndex) throws SQLException {
        String value = getString(columnIndex);
        if (value == null) return null;
        try { return new URL(value); }
        catch (MalformedURLException error) { throw new SQLException("Invalid URL: " + value, "22000", error); }
    }
    @Override
    public URL getURL(String columnLabel) throws SQLException { return getURL(findColumn(columnLabel)); }
    @Override
    public String getNString(int columnIndex) throws SQLException { return getString(columnIndex); }
    @Override
    public String getNString(String columnLabel) throws SQLException { return getString(columnLabel); }
    @Override
    public Reader getNCharacterStream(int columnIndex) throws SQLException { return getCharacterStream(columnIndex); }
    @Override
    public Reader getNCharacterStream(String columnLabel) throws SQLException { return getCharacterStream(columnLabel); }
    @Override
    public Object getObject(int columnIndex, Map<String, Class<?>> map) throws SQLException {
        if (map != null && !map.isEmpty()) throw unsupported();
        return getObject(columnIndex);
    }
    @Override
    public Object getObject(String columnLabel, Map<String, Class<?>> map) throws SQLException {
        return getObject(findColumn(columnLabel), map);
    }
    @Override
    public <T> T unwrap(Class<T> type) throws SQLException {
        if (type != null && type.isInstance(this)) return type.cast(this);
        throw new SQLException("ResultSet does not wrap " + type, "HY000");
    }
    @Override
    public boolean isWrapperFor(Class<?> type) { return type != null && type.isInstance(this); }

    private StorageClass storageClass(int columnIndex) throws SQLException {
        checkRow();
        try {
            StorageClass type = statement.storageClass(columnIndex);
            wasNull = type == StorageClass.NULL;
            return type;
        } catch (IndexOutOfBoundsException error) {
            throw new SQLException(error.getMessage(), "07009", error);
        }
    }

    private boolean isNull(int columnIndex) throws SQLException {
        return storageClass(columnIndex) == StorageClass.NULL;
    }

    private void ensureOpen() throws SQLException {
        if (position == Position.CLOSED) throw new SQLException("ResultSet is closed", "24000");
    }

    private void checkRow() throws SQLException {
        ensureOpen();
        if (position != Position.ROW) throw new SQLException("ResultSet is not positioned on a row", "24000");
    }

    private void finish() {
        releaseStatement();
        position = Position.AFTER;
    }

    private void releaseStatement() {
        if (statementReleased) return;
        if (reusable) statement.reset();
        else statement.close();
        statementReleased = true;
    }

    private static SQLiteResultSetMetaData readMetadata(NativeStatement statement) {
        List<SQLiteResultSetMetaData.Column> columns = new ArrayList<>(statement.columnCount());
        for (int i = 1; i <= statement.columnCount(); i++) {
            columns.add(new SQLiteResultSetMetaData.Column(statement.columnName(i), statement.declaredType(i)));
        }
        return new SQLiteResultSetMetaData(columns);
    }

    private static Date parseDate(String value) {
        return Date.valueOf(value.length() > 10 ? value.substring(0, 10) : value);
    }

    private static Time parseTime(String value) {
        int separator = value.indexOf(' ');
        String time = separator >= 0 ? value.substring(separator + 1) : value;
        return Time.valueOf(time.length() > 8 ? time.substring(0, 8) : time);
    }

    private static Timestamp parseTimestamp(String value) {
        return Timestamp.valueOf(value.length() == 10 ? value + " 00:00:00" : value);
    }

    private static Calendar calendarOrDefault(Calendar calendar) {
        return calendar == null ? Calendar.getInstance() : (Calendar) calendar.clone();
    }

    private static SQLException sqlException(NativeException error) {
        return SqlExceptionMapper.map(error);
    }

    private static boolean parseBoolean(String value) throws SQLException {
        String text = value.trim();
        if (text.equalsIgnoreCase("true")) return true;
        if (text.equalsIgnoreCase("false")) return false;
        try {
            return new BigDecimal(text).compareTo(BigDecimal.ZERO) != 0;
        } catch (NumberFormatException error) {
            throw new SQLException("Column cannot be converted to boolean: " + value, "22018", error);
        }
    }

    private static SQLException numericOverflow(long value, String targetType) {
        return new SQLException("Column value " + value + " is outside the " + targetType + " range", "22003");
    }

    private static SQLException temporalConversion(String type, String value, Exception cause) {
        return new SQLException("Column cannot be converted to " + type + ": " + value, "22007", cause);
    }
}
