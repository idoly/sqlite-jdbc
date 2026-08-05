package io.github.idoly.sqlite.jdbc;

import io.github.idoly.sqlite.ffm.StatementHandle;

/** Owns one sqlite3_stmt handle and delegates all serialized access to its database facade. */
final class NativeStatement implements AutoCloseable {
    private final NativeDatabase database;
    private final boolean hasUpdateCount;
    private volatile StatementHandle handle;

    NativeStatement(NativeDatabase database, StatementHandle handle, String sql) {
        this.database = database;
        this.handle = handle;
        this.hasUpdateCount = JdbcSupport.hasUpdateCount(sql);
    }

    boolean isOpen() {
        return !handle.isNull();
    }

    int columnCount() {
        ensureOpen();
        return database.columnCount(handle);
    }

    int parameterCount() {
        ensureOpen();
        return database.parameterCount(handle);
    }

    void bindNull(int parameterIndex) {
        validateParameterIndex(parameterIndex);
        database.bindNull(handle, parameterIndex);
    }

    void bindLong(int parameterIndex, long value) {
        validateParameterIndex(parameterIndex);
        database.bindLong(handle, parameterIndex, value);
    }

    void bindDouble(int parameterIndex, double value) {
        validateParameterIndex(parameterIndex);
        database.bindDouble(handle, parameterIndex, value);
    }

    void bindText(int parameterIndex, String value) {
        validateParameterIndex(parameterIndex);
        if (value == null) {
            bindNull(parameterIndex);
        } else {
            database.bindText(handle, parameterIndex, value);
        }
    }

    void bindBlob(int parameterIndex, byte[] value) {
        validateParameterIndex(parameterIndex);
        if (value == null) {
            bindNull(parameterIndex);
        } else {
            database.bindBlob(handle, parameterIndex, value);
        }
    }

    void reset() {
        ensureOpen();
        database.reset(handle);
    }

    void clearBindings() {
        ensureOpen();
        database.clearBindings(handle);
    }

    StorageClass storageClass(int columnIndex) {
        validateColumnIndex(columnIndex);
        return database.storageClass(handle, columnIndex - 1);
    }

    long columnLong(int columnIndex) {
        validateColumnIndex(columnIndex);
        return database.columnLong(handle, columnIndex - 1);
    }

    double columnDouble(int columnIndex) {
        validateColumnIndex(columnIndex);
        return database.columnDouble(handle, columnIndex - 1);
    }

    String columnText(int columnIndex) {
        validateColumnIndex(columnIndex);
        return database.columnText(handle, columnIndex - 1);
    }

    byte[] columnBlob(int columnIndex) {
        validateColumnIndex(columnIndex);
        return database.columnBlob(handle, columnIndex - 1);
    }

    String columnName(int columnIndex) {
        validateColumnIndex(columnIndex);
        return database.columnName(handle, columnIndex - 1);
    }

    String declaredType(int columnIndex) {
        validateColumnIndex(columnIndex);
        return database.declaredType(handle, columnIndex - 1);
    }

    StepResult step() {
        ensureOpen();
        return database.step(handle);
    }

    int executeUpdate() {
        ensureOpen();
        if (columnCount() != 0) {
            throw new IllegalStateException("Statement produces a result set");
        }

        StepResult result = step();
        if (result != StepResult.DONE) {
            throw new IllegalStateException("Update statement unexpectedly produced a row");
        }
        return hasUpdateCount ? database.changes() : 0;
    }

    @Override
    public void close() {
        if (handle.isNull()) {
            return;
        }
        database.finalizeStatement(this, handle);
        handle = StatementHandle.NULL;
    }

    void markClosedByDatabase() {
        handle = StatementHandle.NULL;
    }

    private void validateParameterIndex(int parameterIndex) {
        ensureOpen();
        int count = parameterCount();
        if (parameterIndex < 1 || parameterIndex > count) {
            throw new IndexOutOfBoundsException("Parameter index out of range: " + parameterIndex);
        }
    }

    private void validateColumnIndex(int columnIndex) {
        ensureOpen();
        int count = columnCount();
        if (columnIndex < 1 || columnIndex > count) {
            throw new IndexOutOfBoundsException("Column index out of range: " + columnIndex);
        }
    }

    private void ensureOpen() {
        if (handle.isNull()) {
            throw new IllegalStateException("Statement is closed");
        }
    }
}
