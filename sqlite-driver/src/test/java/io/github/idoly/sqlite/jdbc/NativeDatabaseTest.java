package io.github.idoly.sqlite.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.idoly.sqlite.ffm.DatabaseHandle;
import io.github.idoly.sqlite.ffm.OpenResult;
import io.github.idoly.sqlite.ffm.PrepareResult;
import io.github.idoly.sqlite.ffm.SQLiteNative;
import io.github.idoly.sqlite.ffm.StatementHandle;
import org.junit.jupiter.api.Test;

final class NativeDatabaseTest {
    @Test
    void closesNativeHandleOnlyOnce() {
        FakeSQLiteNative nativeApi = new FakeSQLiteNative(0);
        NativeDatabase database = NativeDatabase.open(nativeApi, ":memory:", SQLiteNative.OPEN_READWRITE);

        database.close();
        database.close();

        assertFalse(database.isOpen());
        assertTrue(nativeApi.closed);
    }

    @Test
    void closesHandleReturnedByFailedOpen() {
        FakeSQLiteNative nativeApi = new FakeSQLiteNative(14);

        assertThrows(NativeException.class,
                () -> NativeDatabase.open(nativeApi, "missing/parent/database.db", SQLiteNative.OPEN_READWRITE));
        assertTrue(nativeApi.closed);
    }

    @Test
    void reportsNativeExecutionErrors() {
        FakeSQLiteNative nativeApi = new FakeSQLiteNative(0);
        nativeApi.executeResult = 5;
        NativeDatabase database = NativeDatabase.open(nativeApi, ":memory:", SQLiteNative.OPEN_READWRITE);

        NativeException error = assertThrows(NativeException.class, () -> database.execute("BEGIN"));

        assertEquals(5, error.resultCode());
        assertEquals("BEGIN", nativeApi.executedSql);
    }

    private static final class FakeSQLiteNative implements SQLiteNative {
        private final int openResult;
        private boolean closed;
        private int executeResult;
        private String executedSql;

        private FakeSQLiteNative(int openResult) {
            this.openResult = openResult;
        }

        @Override
        public String libraryVersion() {
            return "test";
        }

        @Override
        public OpenResult open(String filename, int openFlags) {
            return new OpenResult(openResult, new DatabaseHandle(1));
        }

        @Override
        public int close(DatabaseHandle database) {
            closed = true;
            return 0;
        }

        @Override
        public int execute(DatabaseHandle database, String sql) {
            executedSql = sql;
            return executeResult;
        }

        @Override
        public boolean isAutoCommit(DatabaseHandle database) {
            return true;
        }

        @Override
        public int setBusyTimeoutMillis(DatabaseHandle database, int timeoutMillis) { return 0; }

        @Override
        public void interrupt(DatabaseHandle database) {}

        @Override
        public int changes(DatabaseHandle database) {
            return 0;
        }

        @Override
        public long lastInsertRowId(DatabaseHandle database) {
            return 0;
        }

        @Override
        public PrepareResult prepare(DatabaseHandle database, String sql) {
            return new PrepareResult(0, new StatementHandle(2));
        }

        @Override
        public int step(StatementHandle statement) {
            return 101;
        }

        @Override
        public int reset(StatementHandle statement) { return 0; }

        @Override
        public int clearBindings(StatementHandle statement) { return 0; }

        @Override
        public int parameterCount(StatementHandle statement) { return 0; }

        @Override
        public int bindNull(StatementHandle statement, int parameterIndex) { return 0; }

        @Override
        public int bindLong(StatementHandle statement, int parameterIndex, long value) { return 0; }

        @Override
        public int bindDouble(StatementHandle statement, int parameterIndex, double value) { return 0; }

        @Override
        public int bindText(StatementHandle statement, int parameterIndex, String value) { return 0; }

        @Override
        public int bindBlob(StatementHandle statement, int parameterIndex, byte[] value) { return 0; }

        @Override
        public int columnCount(StatementHandle statement) { return 0; }

        @Override
        public int storageClass(StatementHandle statement, int columnIndex) { return 5; }

        @Override
        public long columnLong(StatementHandle statement, int columnIndex) { return 0; }

        @Override
        public double columnDouble(StatementHandle statement, int columnIndex) { return 0; }

        @Override
        public String columnText(StatementHandle statement, int columnIndex) { return null; }

        @Override
        public byte[] columnBlob(StatementHandle statement, int columnIndex) { return new byte[0]; }

        @Override
        public String columnName(StatementHandle statement, int columnIndex) { return "column"; }

        @Override
        public String declaredType(StatementHandle statement, int columnIndex) { return null; }

        @Override
        public int finalizeStatement(StatementHandle statement) {
            return 0;
        }

        @Override
        public String errorMessage(DatabaseHandle database) {
            return "native operation failed";
        }
    }
}
