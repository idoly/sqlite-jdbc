package io.github.idoly.sqlite.jdbc;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Locale;

final class SQLiteResultSetMetaData implements ResultSetMetaData {
    record Column(String name, String declaredType) {}

    private final List<Column> columns;

    SQLiteResultSetMetaData(List<Column> columns) {
        this.columns = List.copyOf(columns);
    }

    @Override
    public int getColumnCount() {
        return columns.size();
    }

    @Override
    public boolean isAutoIncrement(int columnIndex) throws SQLException {
        validateColumnIndex(columnIndex);
        return false;
    }

    @Override
    public boolean isCaseSensitive(int columnIndex) throws SQLException {
        int type = getColumnType(columnIndex);
        return type == Types.VARCHAR || type == Types.CHAR || type == Types.LONGVARCHAR;
    }

    @Override
    public boolean isSearchable(int columnIndex) throws SQLException {
        validateColumnIndex(columnIndex);
        return true;
    }

    @Override
    public boolean isCurrency(int columnIndex) throws SQLException {
        validateColumnIndex(columnIndex);
        return false;
    }

    @Override
    public int isNullable(int columnIndex) throws SQLException {
        validateColumnIndex(columnIndex);
        return columnNullableUnknown;
    }

    @Override
    public boolean isSigned(int columnIndex) throws SQLException {
        return switch (getColumnType(columnIndex)) {
            case Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT,
                    Types.FLOAT, Types.REAL, Types.DOUBLE, Types.NUMERIC, Types.DECIMAL -> true;
            default -> false;
        };
    }

    @Override
    public int getColumnDisplaySize(int columnIndex) throws SQLException {
        validateColumnIndex(columnIndex);
        return Integer.MAX_VALUE;
    }

    @Override
    public String getColumnLabel(int columnIndex) throws SQLException {
        return getColumnName(columnIndex);
    }

    @Override
    public String getColumnName(int columnIndex) throws SQLException {
        return validateColumnIndex(columnIndex).name();
    }

    @Override
    public String getSchemaName(int columnIndex) throws SQLException {
        validateColumnIndex(columnIndex);
        return "";
    }

    @Override
    public int getPrecision(int columnIndex) throws SQLException {
        validateColumnIndex(columnIndex);
        return 0;
    }

    @Override
    public int getScale(int columnIndex) throws SQLException {
        validateColumnIndex(columnIndex);
        return 0;
    }

    @Override
    public String getTableName(int columnIndex) throws SQLException {
        validateColumnIndex(columnIndex);
        return "";
    }

    @Override
    public String getCatalogName(int columnIndex) throws SQLException {
        validateColumnIndex(columnIndex);
        return "";
    }

    @Override
    public int getColumnType(int columnIndex) throws SQLException {
        String declaredType = validateColumnIndex(columnIndex).declaredType();
        if (declaredType == null || declaredType.isBlank()) {
            return Types.JAVA_OBJECT;
        }
        String type = declaredType.toUpperCase(Locale.ROOT);
        if (type.contains("INT")) return Types.BIGINT;
        if (type.contains("CHAR") || type.contains("CLOB") || type.contains("TEXT")) return Types.VARCHAR;
        if (type.contains("BLOB")) return Types.BLOB;
        if (type.contains("REAL") || type.contains("FLOA") || type.contains("DOUB")) return Types.DOUBLE;
        if (type.contains("BOOL")) return Types.BOOLEAN;
        if (type.contains("TIMESTAMP") || type.contains("DATETIME")) return Types.TIMESTAMP;
        if (type.equals("DATE")) return Types.DATE;
        if (type.equals("TIME")) return Types.TIME;
        return Types.NUMERIC;
    }

    @Override
    public String getColumnTypeName(int columnIndex) throws SQLException {
        String type = validateColumnIndex(columnIndex).declaredType();
        return type == null ? "" : type;
    }

    @Override
    public boolean isReadOnly(int columnIndex) throws SQLException {
        validateColumnIndex(columnIndex);
        return true;
    }

    @Override
    public boolean isWritable(int columnIndex) throws SQLException {
        validateColumnIndex(columnIndex);
        return false;
    }

    @Override
    public boolean isDefinitelyWritable(int columnIndex) throws SQLException {
        validateColumnIndex(columnIndex);
        return false;
    }

    @Override
    public String getColumnClassName(int columnIndex) throws SQLException {
        return switch (getColumnType(columnIndex)) {
            case Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT -> Long.class.getName();
            case Types.FLOAT, Types.REAL, Types.DOUBLE -> Double.class.getName();
            case Types.BLOB, Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY -> byte[].class.getName();
            case Types.BOOLEAN, Types.BIT -> Boolean.class.getName();
            case Types.DATE -> java.sql.Date.class.getName();
            case Types.TIME -> java.sql.Time.class.getName();
            case Types.TIMESTAMP -> java.sql.Timestamp.class.getName();
            default -> String.class.getName();
        };
    }

    @Override
    public <T> T unwrap(Class<T> type) throws SQLException {
        if (type != null && type.isInstance(this)) return type.cast(this);
        throw new SQLException("ResultSetMetaData does not wrap " + type, "HY000");
    }

    @Override
    public boolean isWrapperFor(Class<?> type) {
        return type != null && type.isInstance(this);
    }

    private Column validateColumnIndex(int columnIndex) throws SQLException {
        if (columnIndex < 1 || columnIndex > columns.size()) {
            throw new SQLException("Column index out of range: " + columnIndex, "07009");
        }
        return columns.get(columnIndex - 1);
    }
}
