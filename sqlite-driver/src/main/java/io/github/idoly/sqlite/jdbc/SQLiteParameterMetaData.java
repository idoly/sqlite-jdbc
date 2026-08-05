package io.github.idoly.sqlite.jdbc;

import java.sql.ParameterMetaData;
import java.sql.SQLException;
import java.sql.Types;

final class SQLiteParameterMetaData implements ParameterMetaData {
    private final int parameterCount;

    SQLiteParameterMetaData(int parameterCount) {
        this.parameterCount = parameterCount;
    }

    @Override
    public int getParameterCount() { return parameterCount; }

    @Override
    public int isNullable(int parameterIndex) throws SQLException {
        validateParameterIndex(parameterIndex);
        return parameterNullableUnknown;
    }

    @Override
    public boolean isSigned(int parameterIndex) throws SQLException {
        validateParameterIndex(parameterIndex);
        return true;
    }

    @Override
    public int getPrecision(int parameterIndex) throws SQLException { validateParameterIndex(parameterIndex); return 0; }
    @Override
    public int getScale(int parameterIndex) throws SQLException { validateParameterIndex(parameterIndex); return 0; }
    @Override
    public int getParameterType(int parameterIndex) throws SQLException { validateParameterIndex(parameterIndex); return Types.JAVA_OBJECT; }
    @Override
    public String getParameterTypeName(int parameterIndex) throws SQLException { validateParameterIndex(parameterIndex); return ""; }
    @Override
    public String getParameterClassName(int parameterIndex) throws SQLException {
        validateParameterIndex(parameterIndex);
        return Object.class.getName();
    }
    @Override
    public int getParameterMode(int parameterIndex) throws SQLException { validateParameterIndex(parameterIndex); return parameterModeIn; }

    @Override
    public <T> T unwrap(Class<T> type) throws SQLException {
        if (type != null && type.isInstance(this)) return type.cast(this);
        throw new SQLException("ParameterMetaData does not wrap " + type, "HY000");
    }

    @Override
    public boolean isWrapperFor(Class<?> type) { return type != null && type.isInstance(this); }

    private void validateParameterIndex(int parameterIndex) throws SQLException {
        if (parameterIndex < 1 || parameterIndex > parameterCount) {
            throw new SQLException("Parameter index out of range: " + parameterIndex, "07009");
        }
    }
}
