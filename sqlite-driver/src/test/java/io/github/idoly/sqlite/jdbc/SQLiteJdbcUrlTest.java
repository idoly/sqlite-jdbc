package io.github.idoly.sqlite.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;

final class SQLiteJdbcUrlTest {
    @Test
    void parsesMemoryAndFileUrlsWithoutRewritingThem() throws SQLException {
        assertEquals(":memory:", SQLiteJdbcUrl.parse("jdbc:sqlite::memory:").filename());
        assertEquals("./data/app.db", SQLiteJdbcUrl.parse("jdbc:sqlite:./data/app.db").filename());
        assertEquals("file:app.db?mode=ro", SQLiteJdbcUrl.parse("jdbc:sqlite:file:app.db?mode=ro").filename());
    }

    @Test
    void recognizesOnlySQLiteUrls() {
        assertTrue(SQLiteJdbcUrl.accepts("jdbc:sqlite:"));
        assertFalse(SQLiteJdbcUrl.accepts("jdbc:postgresql:test"));
        assertFalse(SQLiteJdbcUrl.accepts(null));
    }

    @Test
    void rejectsForeignUrlsAndEmbeddedNulCharactersDuringExplicitParsing() {
        assertThrows(SQLException.class, () -> SQLiteJdbcUrl.parse("jdbc:other:test"));
        SQLException nulUrl = assertThrows(
                SQLException.class, () -> SQLiteJdbcUrl.parse("jdbc:sqlite:actual.db\0ignored.db"));
        assertEquals("08001", nulUrl.getSQLState());
    }
}
