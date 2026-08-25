package io.github.idoly.sqlite.internal;

import io.github.idoly.sqlite.SQLiteConnectionConfig;
import io.github.idoly.sqlite.core.SQLiteDatabase;
import io.github.idoly.sqlite.core.SQLiteResultCodes;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.NClob;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ResultSetImpl implements ResultSet, ResultSetMetaData, SQLiteResultCodes {
    protected final StatementImpl stmt;

    /** If the result set does not have any rows. */
    public boolean emptyResultSet = false;

    /** If the result set is open. Doesn't mean it has results. */
    public boolean open = false;

    /** Maximum number of rows as set by a Statement */
    public long maxRows;

    /** if null, the RS is closed() */
    public String[] cols = null;

    /** same as cols, but used by Meta interface */
    public String[] colsMeta = null;

    protected boolean[][] meta = null;

    /** 0 means no limit, must check against maxRows */
    protected int limitRows;

    /** number of current row, starts at 1 (0 is for before loading data) */
    protected int row = 0;

    protected boolean pastLastRow = false;

    /** last column accessed, for wasNull(). -1 if none */
    protected int lastCol;

    public boolean closeStmt;
    protected Map<String, Integer> columnNameToIndex = null;

    /**
     * Default constructor for a given statement.
     *
     * @param stmt The statement.
     */
    public ResultSetImpl(StatementImpl stmt) {
        this.stmt = stmt;
    }

    // INTERNAL FUNCTIONS ///////////////////////////////////////////

    protected SQLiteDatabase getDatabase() {
        return stmt.getDatabase();
    }

    protected SQLiteConnectionConfig getConnectionConfig() {
        return stmt.getConnectionConfig();
    }

    /**
     * Checks the status of the result set.
     *
     * @return True if has results and can iterate them; false otherwise.
     */
    public boolean isOpen() {
        return open;
    }

    /**
     * @throws SQLException if ResultSet is not open.
     */
    protected void checkOpen() throws SQLException {
        if (!open || stmt.conn.isClosed()) {
            throw new SQLException("ResultSet closed");
        }
    }

    /**
     * Takes col in [1,x] form, returns in [0,x-1] form
     *
     * @return
     */
    public int checkCol(int col) throws SQLException {
        if (colsMeta == null) {
            throw new SQLException("SQLite JDBC: inconsistent internal state");
        }
        if (col < 1 || col > colsMeta.length) {
            throw new SQLException("column " + col + " out of bounds [1," + colsMeta.length + "]");
        }
        return --col;
    }

    /**
     * Takes col in [1,x] form, marks it as last accessed and returns [0,x-1]
     *
     * @return
     */
    protected int markCol(int col) throws SQLException {
        checkCol(col);
        lastCol = col;
        return --col;
    }

    public void checkMeta() throws SQLException {
        checkCol(1);
        if (meta == null) {
            meta = stmt.pointer.safeRun(SQLiteDatabase::column_metadata);
        }
    }

    private void closeInternal() throws SQLException {
        cols = null;
        colsMeta = null;
        meta = null;
        limitRows = 0;
        row = 0;
        pastLastRow = false;
        lastCol = -1;
        columnNameToIndex = null;
        emptyResultSet = false;

        if (stmt.pointer.isClosed() || (!open && !closeStmt)) {
            return;
        }

        SQLiteDatabase db = stmt.getDatabase();
        synchronized (db) {
            if (!stmt.pointer.isClosed()) {
                stmt.pointer.safeRunInt(SQLiteDatabase::reset);

                if (closeStmt) {
                    closeStmt = false; // break recursive call
                    ((Statement) stmt).close();
                }
            }
        }

        open = false;
    }

    protected Integer findColumnIndexInCache(String col) {
        if (columnNameToIndex == null) {
            return null;
        }
        return columnNameToIndex.get(col);
    }

    protected int addColumnIndexInCache(String col, int index) {
        if (columnNameToIndex == null) {
            columnNameToIndex = new HashMap<String, Integer>(cols.length);
        }
        columnNameToIndex.put(col, index);
        return index;
    }

    // ResultSet Functions //////////////////////////////////////////

    /**
     * returns col in [1,x] form
     *
     * @see java.sql.ResultSet#findColumn(java.lang.String)
     */
    public int findColumn(String col) throws SQLException {
        checkOpen();
        Integer index = findColumnIndexInCache(col);
        if (index != null) {
            return index;
        }
        for (int i = 0; i < cols.length; i++) {
            if (col.equalsIgnoreCase(cols[i])) {
                return addColumnIndexInCache(col, i + 1);
            }
        }
        throw new SQLException("no such column: '" + col + "'");
    }

    public boolean next() throws SQLException {
        if (stmt.conn.isClosed()) throw new SQLException("ResultSet closed");
        if (!open || emptyResultSet || pastLastRow) {
            return false; // finished ResultSet
        }
        lastCol = -1;

        // first row is loaded by execute(), so do not step() again
        if (row == 0) {
            row++;
            return true;
        }

        // check if we are row limited by the statement or the ResultSet
        if (maxRows != 0 && row == maxRows) {
            return false;
        }

        // do the real work
        int statusCode = stmt.pointer.safeRunInt(SQLiteDatabase::step);
        switch (statusCode) {
            case SQLITE_DONE:
                pastLastRow = true;
                return false;
            case SQLITE_ROW:
                row++;
                return true;
            case SQLITE_BUSY:
            default:
                getDatabase().throwex(statusCode);
                return false;
        }
    }

    public int getType() {
        return ResultSet.TYPE_FORWARD_ONLY;
    }

    public int getFetchSize() {
        return limitRows;
    }

    public void setFetchSize(int rows) throws SQLException {
        if (0 > rows || (maxRows != 0 && rows > maxRows)) {
            throw new SQLException("fetch size " + rows + " out of bounds " + maxRows);
        }
        limitRows = rows;
    }

    public int getFetchDirection() throws SQLException {
        checkOpen();
        return ResultSet.FETCH_FORWARD;
    }

    public void setFetchDirection(int d) throws SQLException {
        checkOpen();
        // Only FORWARD_ONLY ResultSets exist in SQLite, so only FETCH_FORWARD is permitted
        if (
        /*getType() == ResultSet.TYPE_FORWARD_ONLY &&*/
        d != ResultSet.FETCH_FORWARD) {
            throw new SQLException("only FETCH_FORWARD direction supported");
        }
    }

    public boolean isAfterLast() {
        return pastLastRow && !emptyResultSet;
    }

    public boolean isBeforeFirst() {
        return !emptyResultSet && open && row == 0;
    }

    public boolean isFirst() {
        return row == 1;
    }

    public boolean isLast() throws SQLException {
        throw new SQLFeatureNotSupportedException("not supported by sqlite");
    }

    public int getRow() {
        return row;
    }

    public boolean wasNull() throws SQLException {
        return safeGetColumnType(markCol(lastCol)) == SQLITE_NULL;
    }

    // DATA ACCESS FUNCTIONS ////////////////////////////////////////

    public BigDecimal getBigDecimal(int col) throws SQLException {
        switch (safeGetColumnType(checkCol(col))) {
            case SQLITE_NULL:
                return null;
            case SQLITE_INTEGER:
                return BigDecimal.valueOf(safeGetLongCol(col));
            case SQLITE_FLOAT:
            // avoid double precision
            default:
                final String stringValue = safeGetColumnText(col);
                try {
                    return new BigDecimal(stringValue);
                } catch (NumberFormatException e) {
                    throw new SQLException("Bad value for type BigDecimal : " + stringValue);
                }
        }
    }

    public BigDecimal getBigDecimal(String col) throws SQLException {
        return getBigDecimal(findColumn(col));
    }

    public boolean getBoolean(int col) throws SQLException {
        return getInt(col) != 0;
    }

    public boolean getBoolean(String col) throws SQLException {
        return getBoolean(findColumn(col));
    }

    public InputStream getBinaryStream(int col) throws SQLException {
        byte[] bytes = getBytes(col);
        if (bytes != null) {
            return new ByteArrayInputStream(bytes);
        } else {
            return null;
        }
    }

    public InputStream getBinaryStream(String col) throws SQLException {
        return getBinaryStream(findColumn(col));
    }

    public byte getByte(int col) throws SQLException {
        return (byte) getInt(col);
    }

    public byte getByte(String col) throws SQLException {
        return getByte(findColumn(col));
    }

    public byte[] getBytes(int col) throws SQLException {
        return stmt.pointer.safeRun((db, ptr) -> db.column_blob(ptr, markCol(col)));
    }

    public byte[] getBytes(String col) throws SQLException {
        return getBytes(findColumn(col));
    }

    public Reader getCharacterStream(int col) throws SQLException {
        String string = getString(col);
        return string == null ? null : new StringReader(string);
    }

    public Reader getCharacterStream(String col) throws SQLException {
        return getCharacterStream(findColumn(col));
    }

    public Date getDate(int col) throws SQLException {
        switch (safeGetColumnType(markCol(col))) {
            case SQLITE_NULL:
                return null;

            case SQLITE_TEXT:
                String dateText = safeGetColumnText(col);
                if ("".equals(dateText)) {
                    return null;
                }
                try {
                    return new Date(getConnectionConfig().parseDate(dateText).getTime());
                } catch (Exception e) {
                    throw new SQLException("Error parsing date", e);
                }

            case SQLITE_FLOAT:
                return new Date(julianDateToCalendar(safeGetDoubleCol(col)).getTimeInMillis());

            default: // SQLITE_INTEGER:
                return new Date(safeGetLongCol(col) * getConnectionConfig().getDateMultiplier());
        }
    }

    public Date getDate(int col, Calendar cal) throws SQLException {
        requireCalendarNotNull(cal);
        switch (safeGetColumnType(markCol(col))) {
            case SQLITE_NULL:
                return null;

            case SQLITE_TEXT:
                String dateText = safeGetColumnText(col);
                if ("".equals(dateText)) {
                    return null;
                }
                try {
                    return new java.sql.Date(
                            getConnectionConfig().parseDate(dateText, cal.getTimeZone()).getTime());
                } catch (Exception e) {
                    throw new SQLException("Error parsing time stamp", e);
                }

            case SQLITE_FLOAT:
                return new Date(julianDateToCalendar(safeGetDoubleCol(col), cal).getTimeInMillis());

            default: // SQLITE_INTEGER:
                cal.setTimeInMillis(
                        safeGetLongCol(col) * getConnectionConfig().getDateMultiplier());
                return new Date(cal.getTime().getTime());
        }
    }

    public Date getDate(String col) throws SQLException {
        return getDate(findColumn(col), Calendar.getInstance());
    }

    public Date getDate(String col, Calendar cal) throws SQLException {
        return getDate(findColumn(col), cal);
    }

    public double getDouble(int col) throws SQLException {
        if (safeGetColumnType(markCol(col)) == SQLITE_NULL) {
            return 0;
        }
        return safeGetDoubleCol(col);
    }

    public double getDouble(String col) throws SQLException {
        return getDouble(findColumn(col));
    }

    public float getFloat(int col) throws SQLException {
        if (safeGetColumnType(markCol(col)) == SQLITE_NULL) {
            return 0;
        }
        return (float) safeGetDoubleCol(col);
    }

    public float getFloat(String col) throws SQLException {
        return getFloat(findColumn(col));
    }

    public int getInt(int col) throws SQLException {
        return stmt.pointer.safeRunInt((db, ptr) -> db.column_int(ptr, markCol(col)));
    }

    public int getInt(String col) throws SQLException {
        return getInt(findColumn(col));
    }

    public long getLong(int col) throws SQLException {
        return safeGetLongCol(col);
    }

    public long getLong(String col) throws SQLException {
        return getLong(findColumn(col));
    }

    public short getShort(int col) throws SQLException {
        return (short) getInt(col);
    }

    public short getShort(String col) throws SQLException {
        return getShort(findColumn(col));
    }

    public String getString(int col) throws SQLException {
        return safeGetColumnText(col);
    }

    public String getString(String col) throws SQLException {
        return getString(findColumn(col));
    }

    public Time getTime(int col) throws SQLException {
        switch (safeGetColumnType(markCol(col))) {
            case SQLITE_NULL:
                return null;

            case SQLITE_TEXT:
                String dateText = safeGetColumnText(col);
                if ("".equals(dateText)) {
                    return null;
                }
                try {
                    return new Time(getConnectionConfig().parseDate(dateText).getTime());
                } catch (Exception e) {
                    throw new SQLException("Error parsing time", e);
                }

            case SQLITE_FLOAT:
                return new Time(julianDateToCalendar(safeGetDoubleCol(col)).getTimeInMillis());

            default: // SQLITE_INTEGER
                return new Time(safeGetLongCol(col) * getConnectionConfig().getDateMultiplier());
        }
    }

    public Time getTime(int col, Calendar cal) throws SQLException {
        requireCalendarNotNull(cal);
        switch (safeGetColumnType(markCol(col))) {
            case SQLITE_NULL:
                return null;

            case SQLITE_TEXT:
                String dateText = safeGetColumnText(col);
                if ("".equals(dateText)) {
                    return null;
                }
                try {
                    return new Time(
                            getConnectionConfig().parseDate(dateText, cal.getTimeZone()).getTime());
                } catch (Exception e) {
                    throw new SQLException("Error parsing time", e);
                }

            case SQLITE_FLOAT:
                return new Time(julianDateToCalendar(safeGetDoubleCol(col), cal).getTimeInMillis());

            default: // SQLITE_INTEGER
                cal.setTimeInMillis(
                        safeGetLongCol(col) * getConnectionConfig().getDateMultiplier());
                return new Time(cal.getTime().getTime());
        }
    }

    public Time getTime(String col) throws SQLException {
        return getTime(findColumn(col));
    }

    public Time getTime(String col, Calendar cal) throws SQLException {
        return getTime(findColumn(col), cal);
    }

    public Timestamp getTimestamp(int col) throws SQLException {
        switch (safeGetColumnType(markCol(col))) {
            case SQLITE_NULL:
                return null;

            case SQLITE_TEXT:
                String dateText = safeGetColumnText(col);
                if ("".equals(dateText)) {
                    return null;
                }
                try {
                    return new Timestamp(getConnectionConfig().parseDate(dateText).getTime());
                } catch (Exception e) {
                    throw new SQLException("Error parsing time stamp", e);
                }

            case SQLITE_FLOAT:
                return new Timestamp(julianDateToCalendar(safeGetDoubleCol(col)).getTimeInMillis());

            default: // SQLITE_INTEGER:
                return new Timestamp(
                        safeGetLongCol(col) * getConnectionConfig().getDateMultiplier());
        }
    }

    public Timestamp getTimestamp(int col, Calendar cal) throws SQLException {
        requireCalendarNotNull(cal);
        switch (safeGetColumnType(markCol(col))) {
            case SQLITE_NULL:
                return null;

            case SQLITE_TEXT:
                String dateText = safeGetColumnText(col);
                if ("".equals(dateText)) {
                    return null;
                }
                try {
                    return new Timestamp(
                            getConnectionConfig().parseDate(dateText, cal.getTimeZone()).getTime());
                } catch (Exception e) {
                    throw new SQLException("Error parsing time stamp", e);
                }

            case SQLITE_FLOAT:
                return new Timestamp(julianDateToCalendar(safeGetDoubleCol(col)).getTimeInMillis());

            default: // SQLITE_INTEGER
                cal.setTimeInMillis(
                        safeGetLongCol(col) * getConnectionConfig().getDateMultiplier());

                return new Timestamp(cal.getTime().getTime());
        }
    }

    public Timestamp getTimestamp(String col) throws SQLException {
        return getTimestamp(findColumn(col));
    }

    public Timestamp getTimestamp(String c, Calendar ca) throws SQLException {
        return getTimestamp(findColumn(c), ca);
    }

    public Object getObject(int col) throws SQLException {
        switch (safeGetColumnType(markCol(col))) {
            case SQLITE_INTEGER:
                long val = getLong(col);
                if (val > Integer.MAX_VALUE || val < Integer.MIN_VALUE) {
                    return new Long(val);
                } else {
                    return new Integer((int) val);
                }
            case SQLITE_FLOAT:
                return new Double(getDouble(col));
            case SQLITE_BLOB:
                return getBytes(col);
            case SQLITE_NULL:
                return null;
            case SQLITE_TEXT:
            default:
                return getString(col);
        }
    }

    public Object getObject(String col) throws SQLException {
        return getObject(findColumn(col));
    }

    public Statement getStatement() {
        return (Statement) stmt;
    }

    public String getCursorName() {
        return null;
    }

    public SQLWarning getWarnings() {
        return null;
    }

    public void clearWarnings() {}

    // ResultSetMetaData Functions //////////////////////////////////

    /** Pattern used to extract the column type name from table column definition. */
    protected static final Pattern COLUMN_TYPENAME = Pattern.compile("([^\\(]*)");

    /** Pattern used to extract the column type name from a cast(col as type) */
    protected static final Pattern COLUMN_TYPECAST =
            Pattern.compile("cast\\(.*?\\s+as\\s+(.*?)\\s*\\)");

    /**
     * Pattern used to extract the precision and scale from column meta returned by the JDBC driver.
     */
    protected static final Pattern COLUMN_PRECISION = Pattern.compile(".*?\\((.*?)\\)");

    // we do not need to check the RS is open, only that colsMeta
    // is not null, done with checkCol(int).

    public ResultSetMetaData getMetaData() {
        return (ResultSetMetaData) this;
    }

    public String getCatalogName(int col) throws SQLException {
        return "";
    }

    public String getColumnClassName(int col) throws SQLException {
        switch (safeGetColumnType(markCol(col))) {
            case SQLITE_INTEGER:
                long val = getLong(col);
                if (val > Integer.MAX_VALUE || val < Integer.MIN_VALUE) {
                    return "java.lang.Long";
                } else {
                    return "java.lang.Integer";
                }
            case SQLITE_FLOAT:
                return "java.lang.Double";
            case SQLITE_BLOB:
            case SQLITE_NULL:
                return "java.lang.Object";
            case SQLITE_TEXT:
            default:
                return "java.lang.String";
        }
    }

    public int getColumnCount() throws SQLException {
        checkCol(1);
        return colsMeta.length;
    }

    public int getColumnDisplaySize(int col) {
        return Integer.MAX_VALUE;
    }

    public String getColumnLabel(int col) throws SQLException {
        return getColumnName(col);
    }

    public String getColumnName(int col) throws SQLException {
        return safeGetColumnName(col);
    }

    public int getColumnType(int col) throws SQLException {
        String typeName = getColumnTypeName(col);
        int valueType = safeGetColumnType(checkCol(col));

        if (valueType == SQLITE_INTEGER || valueType == SQLITE_NULL) {
            if ("BOOLEAN".equals(typeName)) {
                return Types.BOOLEAN;
            }

            if ("TINYINT".equals(typeName)) {
                return Types.TINYINT;
            }

            if ("SMALLINT".equals(typeName) || "INT2".equals(typeName)) {
                return Types.SMALLINT;
            }

            if ("BIGINT".equals(typeName)
                    || "INT8".equals(typeName)
                    || "UNSIGNED BIG INT".equals(typeName)) {
                return Types.BIGINT;
            }

            if ("DATE".equals(typeName)) {
                return Types.DATE;
            }

            if ("DATETIME".equals(typeName) || "TIMESTAMP".equals(typeName)) {
                return Types.TIMESTAMP;
            }

            if (valueType == SQLITE_INTEGER
                    || "INT".equals(typeName)
                    || "INTEGER".equals(typeName)
                    || "MEDIUMINT".equals(typeName)) {
                long val = getLong(col);
                if (val > Integer.MAX_VALUE || val < Integer.MIN_VALUE) {
                    return Types.BIGINT;
                } else {
                    return Types.INTEGER;
                }
            }
        }

        if (valueType == SQLITE_FLOAT || valueType == SQLITE_NULL) {
            if ("DECIMAL".equals(typeName)) {
                return Types.DECIMAL;
            }

            if ("DOUBLE".equals(typeName) || "DOUBLE PRECISION".equals(typeName)) {
                return Types.DOUBLE;
            }

            if ("NUMERIC".equals(typeName)) {
                return Types.NUMERIC;
            }

            if ("REAL".equals(typeName)) {
                return Types.REAL;
            }

            if (valueType == SQLITE_FLOAT || "FLOAT".equals(typeName)) {
                return Types.FLOAT;
            }
        }

        if (valueType == SQLITE_TEXT || valueType == SQLITE_NULL) {
            if ("CHARACTER".equals(typeName)
                    || "NCHAR".equals(typeName)
                    || "NATIVE CHARACTER".equals(typeName)
                    || "CHAR".equals(typeName)) {
                return Types.CHAR;
            }

            if ("CLOB".equals(typeName)) {
                return Types.CLOB;
            }

            if ("DATE".equals(typeName)) {
                return Types.DATE;
            }

            if ("DATETIME".equals(typeName) || "TIMESTAMP".equals(typeName)) {
                return Types.TIMESTAMP;
            }

            if (valueType == SQLITE_TEXT
                    || "VARCHAR".equals(typeName)
                    || "VARYING CHARACTER".equals(typeName)
                    || "NVARCHAR".equals(typeName)
                    || "TEXT".equals(typeName)) {
                return Types.VARCHAR;
            }
        }

        if (valueType == SQLITE_BLOB || valueType == SQLITE_NULL) {
            if ("BINARY".equals(typeName)) {
                return Types.BINARY;
            }

            if (valueType == SQLITE_BLOB || "BLOB".equals(typeName)) {
                return Types.BLOB;
            }
        }

        return Types.NUMERIC;
    }

    /**
     * @return The data type from either the 'create table' statement, or CAST(expr AS TYPE)
     *     otherwise sqlite3_value_type.
     * @see java.sql.ResultSetMetaData#getColumnTypeName(int)
     */
    public String getColumnTypeName(int col) throws SQLException {
        String declType = getColumnDeclType(col);

        if (declType != null) {
            Matcher matcher = COLUMN_TYPENAME.matcher(declType);

            matcher.find();
            return matcher.group(1).toUpperCase(Locale.ENGLISH);
        }

        switch (safeGetColumnType(checkCol(col))) {
            case SQLITE_INTEGER:
                return "INTEGER";
            case SQLITE_FLOAT:
                return "FLOAT";
            case SQLITE_BLOB:
                return "BLOB";
            case SQLITE_TEXT:
                return "TEXT";
            case SQLITE_NULL:
            default:
                return "NUMERIC";
        }
    }

    public int getPrecision(int col) throws SQLException {
        String declType = getColumnDeclType(col);

        if (declType != null) {
            Matcher matcher = COLUMN_PRECISION.matcher(declType);

            return matcher.find() ? Integer.parseInt(matcher.group(1).split(",")[0].trim()) : 0;
        }

        return 0;
    }

    private String getColumnDeclType(int col) throws SQLException {
        String declType = stmt.pointer.safeRun((db, ptr) -> db.column_decltype(ptr, checkCol(col)));

        if (declType == null) {
            Matcher matcher = COLUMN_TYPECAST.matcher(safeGetColumnName(col));
            declType = matcher.find() ? matcher.group(1) : null;
        }

        return declType;
    }

    public int getScale(int col) throws SQLException {
        String declType = getColumnDeclType(col);

        if (declType != null) {
            Matcher matcher = COLUMN_PRECISION.matcher(declType);

            if (matcher.find()) {
                String[] array = matcher.group(1).split(",");

                if (array.length == 2) {
                    return Integer.parseInt(array[1].trim());
                }
            }
        }

        return 0;
    }

    public String getSchemaName(int col) {
        return "";
    }

    public String getTableName(int col) throws SQLException {
        final String tableName = safeGetColumnTableName(col);
        if (tableName == null) {
            // JDBC specifies an empty string instead of null
            return "";
        }
        return tableName;
    }

    public int isNullable(int col) throws SQLException {
        checkMeta();
        return meta[checkCol(col)][0]
                ? ResultSetMetaData.columnNoNulls
                : ResultSetMetaData.columnNullable;
    }

    public boolean isAutoIncrement(int col) throws SQLException {
        checkMeta();
        return meta[checkCol(col)][2];
    }

    public boolean isCaseSensitive(int col) {
        return true;
    }

    public boolean isCurrency(int col) {
        return false;
    }

    public boolean isDefinitelyWritable(int col) {
        return false;
    }

    public boolean isReadOnly(int col) {
        return false;
    }

    public boolean isSearchable(int col) {
        return true;
    }

    public boolean isSigned(int col) throws SQLException {
        String typeName = getColumnTypeName(col);

        return "NUMERIC".equals(typeName) || "INTEGER".equals(typeName) || "REAL".equals(typeName);
    }

    public boolean isWritable(int col) {
        return true;
    }

    public int getConcurrency() {
        return ResultSet.CONCUR_READ_ONLY;
    }

    public boolean rowDeleted() {
        return false;
    }

    public boolean rowInserted() {
        return false;
    }

    public boolean rowUpdated() {
        return false;
    }

    /** Transforms a Julian Date to java.util.Calendar object. */
    private Calendar julianDateToCalendar(Double jd) {
        return julianDateToCalendar(jd, Calendar.getInstance());
    }

    /**
     * Transforms a Julian Date to java.util.Calendar object. Based on Guine Christian's function
     * found here:
     * http://java.ittoolbox.com/groups/technical-functional/java-l/java-function-to-convert-julian-date-to-calendar-date-1947446
     */
    private Calendar julianDateToCalendar(Double jd, Calendar cal) {
        if (jd == null) {
            return null;
        }

        int yyyy, dd, mm, hh, mn, ss, ms, A;

        double w = jd + 0.5;
        int Z = (int) w;
        double F = w - Z;

        if (Z < 2299161) {
            A = Z;
        } else {
            int alpha = (int) ((Z - 1867216.25) / 36524.25);
            A = Z + 1 + alpha - (int) (alpha / 4.0);
        }

        int B = A + 1524;
        int C = (int) ((B - 122.1) / 365.25);
        int D = (int) (365.25 * C);
        int E = (int) ((B - D) / 30.6001);

        //  month
        mm = E - ((E < 13.5) ? 1 : 13);

        // year
        yyyy = C - ((mm > 2.5) ? 4716 : 4715);

        // Day
        double jjd = B - D - (int) (30.6001 * E) + F;
        dd = (int) jjd;

        // Hour
        double hhd = jjd - dd;
        hh = (int) (24 * hhd);

        // Minutes
        double mnd = (24 * hhd) - hh;
        mn = (int) (60 * mnd);

        // Seconds
        double ssd = (60 * mnd) - mn;
        ss = (int) (60 * ssd);

        // Milliseconds
        double msd = (60 * ssd) - ss;
        ms = (int) (1000 * msd);

        cal.set(yyyy, mm - 1, dd, hh, mn, ss);
        cal.set(Calendar.MILLISECOND, ms);

        if (yyyy < 1) {
            cal.set(Calendar.ERA, GregorianCalendar.BC);
            cal.set(Calendar.YEAR, -(yyyy - 1));
        }

        return cal;
    }

    private void requireCalendarNotNull(Calendar cal) throws SQLException {
        if (cal == null) {
            throw new SQLException("Expected a calendar instance.", new IllegalArgumentException());
        }
    }

    protected int safeGetColumnType(int col) throws SQLException {
        return stmt.pointer.safeRunInt((db, ptr) -> db.column_type(ptr, col));
    }

    private long safeGetLongCol(int col) throws SQLException {
        return stmt.pointer.safeRunLong((db, ptr) -> db.column_long(ptr, markCol(col)));
    }

    private double safeGetDoubleCol(int col) throws SQLException {
        return stmt.pointer.safeRunDouble((db, ptr) -> db.column_double(ptr, markCol(col)));
    }

    private String safeGetColumnText(int col) throws SQLException {
        return stmt.pointer.safeRun((db, ptr) -> db.column_text(ptr, markCol(col)));
    }

    private String safeGetColumnTableName(int col) throws SQLException {
        return stmt.pointer.safeRun((db, ptr) -> db.column_table_name(ptr, checkCol(col)));
    }

    private String safeGetColumnName(int col) throws SQLException {
        return stmt.pointer.safeRun((db, ptr) -> db.column_name(ptr, checkCol(col)));
    }

    @Override
    public void close() throws SQLException {
        final boolean wasOpen = isOpen(); // prevent close() recursion
        closeInternal();
        // close-on-completion regardless of closeStmt
        if (wasOpen && stmt instanceof StatementImpl) {
            StatementImpl stat = (StatementImpl) stmt;
            // check if its not closed already in which case no-op
            if (stat.closeOnCompletion && !stat.isClosed()) {
                stat.close();
            }
        }
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

    public RowId getRowId(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    public RowId getRowId(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    public void updateRowId(int columnIndex, RowId x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    public void updateRowId(String columnLabel, RowId x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    public int getHoldability() throws SQLException {
        return ResultSet.CLOSE_CURSORS_AT_COMMIT;
    }

    public boolean isClosed() throws SQLException {
        return !isOpen() || stmt.conn.isClosed();
    }

    public void updateNString(int columnIndex, String nString) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    public void updateNString(String columnLabel, String nString) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    public void updateNClob(int columnIndex, NClob nClob) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    public void updateNClob(String columnLabel, NClob nClob) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    public NClob getNClob(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    public NClob getNClob(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    public SQLXML getSQLXML(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    public SQLXML getSQLXML(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    public void updateSQLXML(int columnIndex, SQLXML xmlObject) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    public void updateSQLXML(String columnLabel, SQLXML xmlObject) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    public String getNString(int columnIndex) throws SQLException {
        return getString(columnIndex);
    }

    public String getNString(String columnLabel) throws SQLException {
        return getString(columnLabel);
    }

    public Reader getNCharacterStream(int col) throws SQLException {
        String data = getString(col);
        return getNCharacterStreamInternal(data);
    }

    private Reader getNCharacterStreamInternal(String data) {
        if (data == null) {
            return null;
        }
        Reader reader = new StringReader(data);
        return reader;
    }

    public Reader getNCharacterStream(String col) throws SQLException {
        String data = getString(col);
        return getNCharacterStreamInternal(data);
    }

    public void updateNCharacterStream(int columnIndex, Reader x, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    public void updateNCharacterStream(String columnLabel, Reader reader, long length)
            throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    public void updateAsciiStream(int columnIndex, InputStream x, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    public void updateBinaryStream(int columnIndex, InputStream x, long length)
            throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    public void updateCharacterStream(int columnIndex, Reader x, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    public void updateAsciiStream(String columnLabel, InputStream x, long length)
            throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    public void updateBinaryStream(String columnLabel, InputStream x, long length)
            throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    public void updateCharacterStream(String columnLabel, Reader reader, long length)
            throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    public void updateBlob(int columnIndex, InputStream inputStream, long length)
            throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    public void updateBlob(String columnLabel, InputStream inputStream, long length)
            throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    public void updateClob(int columnIndex, Reader reader, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    public void updateClob(String columnLabel, Reader reader, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    public void updateNClob(int columnIndex, Reader reader, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    public void updateNClob(String columnLabel, Reader reader, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    public void updateNCharacterStream(int columnIndex, Reader x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    public void updateNCharacterStream(String columnLabel, Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    public void updateAsciiStream(int columnIndex, InputStream x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    public void updateBinaryStream(int columnIndex, InputStream x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    public void updateCharacterStream(int columnIndex, Reader x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    public void updateAsciiStream(String columnLabel, InputStream x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    public void updateBinaryStream(String columnLabel, InputStream x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    public void updateCharacterStream(String columnLabel, Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    public void updateBlob(int columnIndex, InputStream inputStream) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    public void updateBlob(String columnLabel, InputStream inputStream) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    public void updateClob(int columnIndex, Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    public void updateClob(String columnLabel, Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    public void updateNClob(int columnIndex, Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    public void updateNClob(String columnLabel, Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    public <T> T getObject(int columnIndex, Class<T> type) throws SQLException {
        if (type == null) throw new SQLException("requested type cannot be null");
        if (type == String.class) return type.cast(getString(columnIndex));
        if (type == Boolean.class) return type.cast(getBoolean(columnIndex));
        if (type == BigDecimal.class) return type.cast(getBigDecimal(columnIndex));
        if (type == byte[].class) return type.cast(getBytes(columnIndex));
        if (type == Date.class) return type.cast(getDate(columnIndex));
        if (type == Time.class) return type.cast(getTime(columnIndex));
        if (type == Timestamp.class) return type.cast(getTimestamp(columnIndex));
        if (type == LocalDate.class) {
            try {
                Date date = getDate(columnIndex);
                return date == null ? null : type.cast(date.toLocalDate());
            } catch (SQLException sqlException) {
                // Accept an ISO date without a time component.
                return type.cast(LocalDate.parse(getString(columnIndex)));
            }
        }
        if (type == LocalTime.class) {
            try {
                Time time = getTime(columnIndex);
                return time == null ? null : type.cast(time.toLocalTime());
            } catch (SQLException sqlException) {
                // Accept an ISO time without a date component.
                return type.cast(LocalTime.parse(getString(columnIndex)));
            }
        }
        if (type == LocalDateTime.class) {
            try {
                Timestamp timestamp = getTimestamp(columnIndex);
                return timestamp == null ? null : type.cast(timestamp.toLocalDateTime());
            } catch (SQLException e) {
                // Accept an ISO local date-time.
                return type.cast(LocalDateTime.parse(getString(columnIndex)));
            }
        }

        int columnType = safeGetColumnType(markCol(columnIndex));
        if (type == Double.class) {
            if (columnType == SQLITE_INTEGER || columnType == SQLITE_FLOAT)
                return type.cast(getDouble(columnIndex));
            throw new SQLException("Bad value for type Double");
        }
        if (type == Long.class) {
            if (columnType == SQLITE_INTEGER || columnType == SQLITE_FLOAT)
                return type.cast(getLong(columnIndex));
            throw new SQLException("Bad value for type Long");
        }
        if (type == Float.class) {
            if (columnType == SQLITE_INTEGER || columnType == SQLITE_FLOAT)
                return type.cast(getFloat(columnIndex));
            throw new SQLException("Bad value for type Float");
        }
        if (type == Integer.class) {
            if (columnType == SQLITE_INTEGER || columnType == SQLITE_FLOAT)
                return type.cast(getInt(columnIndex));
            throw new SQLException("Bad value for type Integer");
        }

        throw unsupported();
    }

    public <T> T getObject(String columnLabel, Class<T> type) throws SQLException {
        return getObject(findColumn(columnLabel), type);
    }

    protected SQLException unsupported() {
        return new SQLFeatureNotSupportedException("not implemented by SQLite JDBC driver");
    }

    // ResultSet ////////////////////////////////////////////////////

    public Array getArray(int i) throws SQLException {
        throw unsupported();
    }

    public Array getArray(String col) throws SQLException {
        throw unsupported();
    }

    public InputStream getAsciiStream(int col) throws SQLException {
        String data = getString(col);
        return getAsciiStreamInternal(data);
    }

    public InputStream getAsciiStream(String col) throws SQLException {
        String data = getString(col);
        return getAsciiStreamInternal(data);
    }

    private InputStream getAsciiStreamInternal(String data) {
        if (data == null) {
            return null;
        }
        InputStream inputStream;
        try {
            inputStream = new ByteArrayInputStream(data.getBytes("ASCII"));
        } catch (UnsupportedEncodingException e) {
            return null;
        }
        return inputStream;
    }

    @Deprecated
    public BigDecimal getBigDecimal(int col, int s) throws SQLException {
        throw unsupported();
    }

    @Deprecated
    public BigDecimal getBigDecimal(String col, int s) throws SQLException {
        throw unsupported();
    }

    public Blob getBlob(int col) throws SQLException {
        throw unsupported();
    }

    public Blob getBlob(String col) throws SQLException {
        throw unsupported();
    }

    public Clob getClob(int col) throws SQLException {
        String clob = getString(col);
        return clob == null ? null : new SqliteClob(clob);
    }

    public Clob getClob(String col) throws SQLException {
        String clob = getString(col);
        return clob == null ? null : new SqliteClob(clob);
    }

    @SuppressWarnings("rawtypes")
    public Object getObject(int col, Map map) throws SQLException {
        throw unsupported();
    }

    @SuppressWarnings("rawtypes")
    public Object getObject(String col, Map map) throws SQLException {
        throw unsupported();
    }

    public Ref getRef(int i) throws SQLException {
        throw unsupported();
    }

    public Ref getRef(String col) throws SQLException {
        throw unsupported();
    }

    public InputStream getUnicodeStream(int col) throws SQLException {
        return getAsciiStream(col);
    }

    public InputStream getUnicodeStream(String col) throws SQLException {
        return getAsciiStream(col);
    }

    public URL getURL(int col) throws SQLException {
        throw unsupported();
    }

    public URL getURL(String col) throws SQLException {
        throw unsupported();
    }

    public void insertRow() throws SQLException {
        throw new SQLException("ResultSet is TYPE_FORWARD_ONLY");
    }

    public void moveToCurrentRow() throws SQLException {
        throw new SQLException("ResultSet is TYPE_FORWARD_ONLY");
    }

    public void moveToInsertRow() throws SQLException {
        throw new SQLException("ResultSet is TYPE_FORWARD_ONLY");
    }

    public boolean last() throws SQLException {
        throw new SQLException("ResultSet is TYPE_FORWARD_ONLY");
    }

    public boolean previous() throws SQLException {
        throw new SQLException("ResultSet is TYPE_FORWARD_ONLY");
    }

    public boolean relative(int rows) throws SQLException {
        throw new SQLException("ResultSet is TYPE_FORWARD_ONLY");
    }

    public boolean absolute(int row) throws SQLException {
        throw new SQLException("ResultSet is TYPE_FORWARD_ONLY");
    }

    public void afterLast() throws SQLException {
        throw new SQLException("ResultSet is TYPE_FORWARD_ONLY");
    }

    public void beforeFirst() throws SQLException {
        throw new SQLException("ResultSet is TYPE_FORWARD_ONLY");
    }

    public boolean first() throws SQLException {
        throw new SQLException("ResultSet is TYPE_FORWARD_ONLY");
    }

    public void cancelRowUpdates() throws SQLException {
        throw unsupported();
    }

    public void deleteRow() throws SQLException {
        throw unsupported();
    }

    public void updateArray(int col, Array x) throws SQLException {
        throw unsupported();
    }

    public void updateArray(String col, Array x) throws SQLException {
        throw unsupported();
    }

    public void updateAsciiStream(int col, InputStream x, int l) throws SQLException {
        throw unsupported();
    }

    public void updateAsciiStream(String col, InputStream x, int l) throws SQLException {
        throw unsupported();
    }

    public void updateBigDecimal(int col, BigDecimal x) throws SQLException {
        throw unsupported();
    }

    public void updateBigDecimal(String col, BigDecimal x) throws SQLException {
        throw unsupported();
    }

    public void updateBinaryStream(int c, InputStream x, int l) throws SQLException {
        throw unsupported();
    }

    public void updateBinaryStream(String c, InputStream x, int l) throws SQLException {
        throw unsupported();
    }

    public void updateBlob(int col, Blob x) throws SQLException {
        throw unsupported();
    }

    public void updateBlob(String col, Blob x) throws SQLException {
        throw unsupported();
    }

    public void updateBoolean(int col, boolean x) throws SQLException {
        throw unsupported();
    }

    public void updateBoolean(String col, boolean x) throws SQLException {
        throw unsupported();
    }

    public void updateByte(int col, byte x) throws SQLException {
        throw unsupported();
    }

    public void updateByte(String col, byte x) throws SQLException {
        throw unsupported();
    }

    public void updateBytes(int col, byte[] x) throws SQLException {
        throw unsupported();
    }

    public void updateBytes(String col, byte[] x) throws SQLException {
        throw unsupported();
    }

    public void updateCharacterStream(int c, Reader x, int l) throws SQLException {
        throw unsupported();
    }

    public void updateCharacterStream(String c, Reader r, int l) throws SQLException {
        throw unsupported();
    }

    public void updateClob(int col, Clob x) throws SQLException {
        throw unsupported();
    }

    public void updateClob(String col, Clob x) throws SQLException {
        throw unsupported();
    }

    public void updateDate(int col, Date x) throws SQLException {
        throw unsupported();
    }

    public void updateDate(String col, Date x) throws SQLException {
        throw unsupported();
    }

    public void updateDouble(int col, double x) throws SQLException {
        throw unsupported();
    }

    public void updateDouble(String col, double x) throws SQLException {
        throw unsupported();
    }

    public void updateFloat(int col, float x) throws SQLException {
        throw unsupported();
    }

    public void updateFloat(String col, float x) throws SQLException {
        throw unsupported();
    }

    public void updateInt(int col, int x) throws SQLException {
        throw unsupported();
    }

    public void updateInt(String col, int x) throws SQLException {
        throw unsupported();
    }

    public void updateLong(int col, long x) throws SQLException {
        throw unsupported();
    }

    public void updateLong(String col, long x) throws SQLException {
        throw unsupported();
    }

    public void updateNull(int col) throws SQLException {
        throw unsupported();
    }

    public void updateNull(String col) throws SQLException {
        throw unsupported();
    }

    public void updateObject(int c, Object x) throws SQLException {
        throw unsupported();
    }

    public void updateObject(int c, Object x, int s) throws SQLException {
        throw unsupported();
    }

    public void updateObject(String col, Object x) throws SQLException {
        throw unsupported();
    }

    public void updateObject(String c, Object x, int s) throws SQLException {
        throw unsupported();
    }

    public void updateRef(int col, Ref x) throws SQLException {
        throw unsupported();
    }

    public void updateRef(String c, Ref x) throws SQLException {
        throw unsupported();
    }

    public void updateRow() throws SQLException {
        throw unsupported();
    }

    public void updateShort(int c, short x) throws SQLException {
        throw unsupported();
    }

    public void updateShort(String c, short x) throws SQLException {
        throw unsupported();
    }

    public void updateString(int c, String x) throws SQLException {
        throw unsupported();
    }

    public void updateString(String c, String x) throws SQLException {
        throw unsupported();
    }

    public void updateTime(int c, Time x) throws SQLException {
        throw unsupported();
    }

    public void updateTime(String c, Time x) throws SQLException {
        throw unsupported();
    }

    public void updateTimestamp(int c, Timestamp x) throws SQLException {
        throw unsupported();
    }

    public void updateTimestamp(String c, Timestamp x) throws SQLException {
        throw unsupported();
    }

    public void refreshRow() throws SQLException {
        throw unsupported();
    }

    class SqliteClob implements NClob {

        private String data;

        protected SqliteClob(String data) {
            this.data = data;
        }

        public void free() throws SQLException {
            data = null;
        }

        public InputStream getAsciiStream() throws SQLException {
            return getAsciiStreamInternal(data);
        }

        public Reader getCharacterStream() throws SQLException {
            return getNCharacterStreamInternal(data);
        }

        public Reader getCharacterStream(long arg0, long arg1) throws SQLException {
            return getNCharacterStreamInternal(data);
        }

        public String getSubString(long position, int length) throws SQLException {
            if (data == null) {
                throw new SQLException("no data");
            }
            if (position < 1) {
                throw new SQLException("Position must be greater than or equal to 1");
            }
            if (length < 0) {
                throw new SQLException("Length must be greater than or equal to 0");
            }
            int start = (int) position - 1;
            return data.substring(start, Math.min(start + length, data.length()));
        }

        public long length() throws SQLException {
            if (data == null) {
                throw new SQLException("no data");
            }
            return data.length();
        }

        public long position(String arg0, long arg1) throws SQLException {
            unsupported();
            return -1;
        }

        public long position(Clob arg0, long arg1) throws SQLException {
            unsupported();
            return -1;
        }

        public OutputStream setAsciiStream(long arg0) throws SQLException {
            unsupported();
            return null;
        }

        public Writer setCharacterStream(long arg0) throws SQLException {
            unsupported();
            return null;
        }

        public int setString(long arg0, String arg1) throws SQLException {
            unsupported();
            return -1;
        }

        public int setString(long arg0, String arg1, int arg2, int arg3) throws SQLException {
            unsupported();
            return -1;
        }

        public void truncate(long arg0) throws SQLException {
            unsupported();
        }
    }
}
