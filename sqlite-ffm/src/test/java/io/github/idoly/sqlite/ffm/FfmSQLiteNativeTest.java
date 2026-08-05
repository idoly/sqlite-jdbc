package io.github.idoly.sqlite.ffm;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class FfmSQLiteNativeTest {
    @Test
    void opensAndClosesInMemoryDatabaseWhenNativeLibraryIsProvided() {
        String libraryPath = System.getProperty("sqlite.jdbc.library.path");
        assumeTrue(libraryPath != null && Files.isRegularFile(Path.of(libraryPath)));

        SQLiteNative nativeApi = new FfmSQLiteNative();
        assertFalse(nativeApi.libraryVersion().isBlank());

        OpenResult result = nativeApi.open(
                ":memory:", SQLiteNative.OPEN_READWRITE | SQLiteNative.OPEN_CREATE | SQLiteNative.OPEN_URI);
        assertEquals(0, result.resultCode(), () -> nativeApi.errorMessage(result.database()));
        assertTrue(nativeApi.isAutoCommit(result.database()));
        assertEquals(0, nativeApi.execute(result.database(), "BEGIN DEFERRED"));
        assertFalse(nativeApi.isAutoCommit(result.database()));
        assertEquals(0, nativeApi.execute(result.database(), "ROLLBACK"));
        assertTrue(nativeApi.isAutoCommit(result.database()));

        assertEquals(0, nativeApi.execute(result.database(),
                "CREATE TABLE values_test (i INTEGER, r REAL, t TEXT, b BLOB, n TEXT)"));
        PrepareResult insert = nativeApi.prepare(result.database(), "INSERT INTO values_test VALUES (?, ?, ?, ?, ?)");
        assertEquals(0, insert.resultCode());
        assertEquals(5, nativeApi.parameterCount(insert.statement()));
        assertEquals(0, nativeApi.bindLong(insert.statement(), 1, 42));
        assertEquals(0, nativeApi.bindDouble(insert.statement(), 2, 3.5));
        assertEquals(0, nativeApi.bindText(insert.statement(), 3, "hello\0world"));
        assertEquals(0, nativeApi.bindBlob(insert.statement(), 4, new byte[] {0, 1, -1}));
        assertEquals(0, nativeApi.bindNull(insert.statement(), 5));
        assertEquals(101, nativeApi.step(insert.statement()));
        assertEquals(1, nativeApi.changes(result.database()));
        assertEquals(1, nativeApi.lastInsertRowId(result.database()));
        assertEquals(0, nativeApi.finalizeStatement(insert.statement()));

        PrepareResult commentOnlyTail = nativeApi.prepare(result.database(), "SELECT 1; -- allowed tail");
        assertEquals(0, commentOnlyTail.resultCode());
        assertEquals(0, nativeApi.finalizeStatement(commentOnlyTail.statement()));
        PrepareResult multiple = nativeApi.prepare(result.database(), "SELECT 1; SELECT 2");
        assertEquals(SQLiteNative.DRIVER_MULTIPLE_STATEMENTS, multiple.resultCode());
        assertTrue(multiple.statement().isNull());

        PrepareResult select = nativeApi.prepare(result.database(), "SELECT i, r, t, b, n FROM values_test");
        assertEquals(0, select.resultCode());
        assertEquals(5, nativeApi.columnCount(select.statement()));
        assertEquals(100, nativeApi.step(select.statement()));
        assertEquals(1, nativeApi.storageClass(select.statement(), 0));
        assertEquals(42, nativeApi.columnLong(select.statement(), 0));
        assertEquals(3.5, nativeApi.columnDouble(select.statement(), 1));
        assertEquals("hello\0world", nativeApi.columnText(select.statement(), 2));
        assertArrayEquals(new byte[] {0, 1, -1}, nativeApi.columnBlob(select.statement(), 3));
        assertEquals(5, nativeApi.storageClass(select.statement(), 4));
        assertNull(nativeApi.columnText(select.statement(), 4));
        assertEquals("i", nativeApi.columnName(select.statement(), 0));
        assertEquals("INTEGER", nativeApi.declaredType(select.statement(), 0));
        assertEquals(101, nativeApi.step(select.statement()));
        assertEquals(0, nativeApi.finalizeStatement(select.statement()));

        PrepareResult binaryText = nativeApi.prepare(result.database(), "SELECT CAST(x'610062' AS TEXT), x''");
        assertEquals(0, binaryText.resultCode());
        assertEquals(100, nativeApi.step(binaryText.statement()));
        assertEquals("a\0b", nativeApi.columnText(binaryText.statement(), 0));
        assertArrayEquals(new byte[0], nativeApi.columnBlob(binaryText.statement(), 1));
        assertEquals(0, nativeApi.finalizeStatement(binaryText.statement()));

        assertEquals(0, nativeApi.close(result.database()));
    }
}
