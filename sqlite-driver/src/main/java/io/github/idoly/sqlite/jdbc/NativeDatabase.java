package io.github.idoly.sqlite.jdbc;

import io.github.idoly.sqlite.ffm.DatabaseHandle;
import io.github.idoly.sqlite.ffm.OpenResult;
import io.github.idoly.sqlite.ffm.PrepareResult;
import io.github.idoly.sqlite.ffm.SQLiteNative;
import io.github.idoly.sqlite.ffm.SQLiteNativeProvider;
import io.github.idoly.sqlite.ffm.StatementHandle;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;

/**
 * Facade over one sqlite3 handle.
 *
 * <p>It serializes native calls, owns every prepared handle, and closes statements before the
 * database. No FFM type crosses this boundary.
 */
final class NativeDatabase implements AutoCloseable {
    static final int OPEN_READONLY = SQLiteNative.OPEN_READONLY;
    static final int OPEN_READWRITE = SQLiteNative.OPEN_READWRITE;
    static final int OPEN_CREATE = SQLiteNative.OPEN_CREATE;
    static final int OPEN_URI = SQLiteNative.OPEN_URI;

    private static final int SQLITE_OK = 0;
    private static final int SQLITE_ROW = 100;
    private static final int SQLITE_DONE = 101;

    private final SQLiteNative nativeApi;
    private final Set<NativeStatement> openStatements = Collections.newSetFromMap(new IdentityHashMap<>());
    private volatile DatabaseHandle handle;
    private volatile int activeCalls;

    private NativeDatabase(SQLiteNative nativeApi, DatabaseHandle handle) {
        this.nativeApi = nativeApi;
        this.handle = handle;
    }

    static NativeDatabase open(String filename, int openFlags) {
        return open(SQLiteNativeProvider.get(), filename, openFlags);
    }

    static NativeDatabase open(SQLiteNative nativeApi, String filename, int openFlags) {
        Objects.requireNonNull(nativeApi, "nativeApi");
        Objects.requireNonNull(filename, "filename");

        OpenResult result = nativeApi.open(filename, openFlags);
        if (result.resultCode() != SQLITE_OK) {
            String message = result.database().isNull()
                    ? "SQLite could not open the database"
                    : nativeApi.errorMessage(result.database());
            if (!result.database().isNull()) {
                nativeApi.close(result.database());
            }
            throw new NativeException(message, result.resultCode());
        }
        if (result.database().isNull()) {
            throw new NativeException("SQLite returned a null database handle", result.resultCode());
        }
        return new NativeDatabase(nativeApi, result.database());
    }

    synchronized boolean isOpen() {
        return !handle.isNull();
    }

    synchronized void execute(String sql) {
        Objects.requireNonNull(sql, "sql");
        ensureOpen();

        int resultCode;
        activeCalls++;
        try {
            resultCode = nativeApi.execute(handle, sql);
        } finally {
            activeCalls--;
        }
        if (resultCode != SQLITE_OK) {
            throw new NativeException(nativeApi.errorMessage(handle), resultCode);
        }
    }

    synchronized boolean isAutoCommit() {
        ensureOpen();
        return nativeApi.isAutoCommit(handle);
    }

    synchronized void setBusyTimeoutMillis(int timeoutMillis) {
        if (timeoutMillis < 0) throw new IllegalArgumentException("Busy timeout cannot be negative");
        ensureOpen();
        throwOnError(nativeApi.setBusyTimeoutMillis(handle, timeoutMillis));
    }

    void interrupt() {
        DatabaseHandle currentHandle = handle;
        if (activeCalls > 0 && !currentHandle.isNull()) nativeApi.interrupt(currentHandle);
    }

    synchronized NativeStatement prepare(String sql) {
        Objects.requireNonNull(sql, "sql");
        ensureOpen();

        PrepareResult result = nativeApi.prepare(handle, sql);
        if (result.resultCode() != SQLITE_OK) {
            if (!result.statement().isNull()) {
                nativeApi.finalizeStatement(result.statement());
            }
            String message = result.resultCode() == SQLiteNative.DRIVER_MULTIPLE_STATEMENTS
                    ? "Multiple SQL statements are not supported"
                    : nativeApi.errorMessage(handle);
            throw new NativeException(message, result.resultCode());
        }
        if (result.statement().isNull()) {
            throw new NativeException("SQL does not contain a statement", SQLITE_OK);
        }

        NativeStatement statement = new NativeStatement(this, result.statement());
        openStatements.add(statement);
        return statement;
    }

    synchronized int columnCount(StatementHandle statement) {
        ensureOpen(statement);
        return nativeApi.columnCount(statement);
    }

    synchronized StepResult step(StatementHandle statement) {
        ensureOpen(statement);
        int resultCode;
        activeCalls++;
        try {
            resultCode = nativeApi.step(statement);
        } finally {
            activeCalls--;
        }
        return switch (resultCode) {
            case SQLITE_ROW -> StepResult.ROW;
            case SQLITE_DONE -> StepResult.DONE;
            default -> throw new NativeException(nativeApi.errorMessage(handle), resultCode);
        };
    }

    synchronized int totalChanges() {
        ensureOpen();
        return nativeApi.totalChanges(handle);
    }

    synchronized void reset(StatementHandle statement) {
        ensureOpen(statement);
        throwOnError(nativeApi.reset(statement));
    }

    synchronized void clearBindings(StatementHandle statement) {
        ensureOpen(statement);
        throwOnError(nativeApi.clearBindings(statement));
    }

    synchronized int parameterCount(StatementHandle statement) {
        ensureOpen(statement);
        return nativeApi.parameterCount(statement);
    }

    synchronized void bindNull(StatementHandle statement, int parameterIndex) {
        ensureOpen(statement);
        throwOnError(nativeApi.bindNull(statement, parameterIndex));
    }

    synchronized void bindLong(StatementHandle statement, int parameterIndex, long value) {
        ensureOpen(statement);
        throwOnError(nativeApi.bindLong(statement, parameterIndex, value));
    }

    synchronized void bindDouble(StatementHandle statement, int parameterIndex, double value) {
        ensureOpen(statement);
        throwOnError(nativeApi.bindDouble(statement, parameterIndex, value));
    }

    synchronized void bindText(StatementHandle statement, int parameterIndex, String value) {
        ensureOpen(statement);
        throwOnError(nativeApi.bindText(statement, parameterIndex, value));
    }

    synchronized void bindBlob(StatementHandle statement, int parameterIndex, byte[] value) {
        ensureOpen(statement);
        throwOnError(nativeApi.bindBlob(statement, parameterIndex, value));
    }

    synchronized StorageClass storageClass(StatementHandle statement, int columnIndex) {
        ensureOpen(statement);
        return StorageClass.fromCode(nativeApi.storageClass(statement, columnIndex));
    }

    synchronized long columnLong(StatementHandle statement, int columnIndex) {
        ensureOpen(statement);
        return nativeApi.columnLong(statement, columnIndex);
    }

    synchronized double columnDouble(StatementHandle statement, int columnIndex) {
        ensureOpen(statement);
        return nativeApi.columnDouble(statement, columnIndex);
    }

    synchronized String columnText(StatementHandle statement, int columnIndex) {
        ensureOpen(statement);
        return nativeApi.columnText(statement, columnIndex);
    }

    synchronized byte[] columnBlob(StatementHandle statement, int columnIndex) {
        ensureOpen(statement);
        return nativeApi.columnBlob(statement, columnIndex);
    }

    synchronized String columnName(StatementHandle statement, int columnIndex) {
        ensureOpen(statement);
        return nativeApi.columnName(statement, columnIndex);
    }

    synchronized String declaredType(StatementHandle statement, int columnIndex) {
        ensureOpen(statement);
        return nativeApi.declaredType(statement, columnIndex);
    }

    synchronized void finalizeStatement(NativeStatement owner, StatementHandle statement) {
        if (!openStatements.remove(owner)) {
            owner.markClosedByDatabase();
            return;
        }
        int resultCode = nativeApi.finalizeStatement(statement);
        owner.markClosedByDatabase();
        if (resultCode != SQLITE_OK) {
            throw new NativeException(nativeApi.errorMessage(handle), resultCode);
        }
    }

    @Override
    public synchronized void close() {
        if (handle.isNull()) {
            return;
        }
        NativeException failure = null;
        for (NativeStatement statement : Set.copyOf(openStatements)) {
            try {
                statement.close();
            } catch (NativeException error) {
                if (failure == null) {
                    failure = error;
                } else {
                    failure.addSuppressed(error);
                }
            }
        }

        int resultCode = nativeApi.close(handle);
        if (resultCode != SQLITE_OK && failure == null) {
            failure = new NativeException(nativeApi.errorMessage(handle), resultCode);
        }
        handle = DatabaseHandle.NULL;
        if (failure != null) {
            throw failure;
        }
    }

    private void throwOnError(int resultCode) {
        if (resultCode != SQLITE_OK) {
            throw new NativeException(nativeApi.errorMessage(handle), resultCode);
        }
    }

    private void ensureOpen(StatementHandle statement) {
        ensureOpen();
        if (statement.isNull()) {
            throw new IllegalStateException("Statement is closed");
        }
    }

    private void ensureOpen() {
        if (handle.isNull()) {
            throw new IllegalStateException("Database is closed");
        }
    }
}
