package io.github.idoly.sqlite.jdbc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class JdbcSupportTest {
    @Test
    void identifiesStatementsThatCanGenerateRowIds() {
        assertTrue(JdbcSupport.mayGenerateKey("INSERT INTO values_table DEFAULT VALUES"));
        assertTrue(JdbcSupport.mayGenerateKey("-- comment\nREPLACE INTO values_table VALUES (1)"));
        assertTrue(JdbcSupport.mayGenerateKey(
                "WITH source(value) AS (SELECT 'INSERT') INSERT INTO values_table SELECT value FROM source"));
        assertFalse(JdbcSupport.mayGenerateKey("UPDATE values_table SET value = 'INSERT'"));
        assertFalse(JdbcSupport.mayGenerateKey("WITH source(value) AS (VALUES (1)) SELECT value FROM source"));
        assertFalse(JdbcSupport.mayGenerateKey("/* INSERT */ DELETE FROM values_table"));
        assertTrue(JdbcSupport.hasUpdateCount("WITH source(value) AS (VALUES (1)) UPDATE values_table SET value = 1"));
        assertFalse(JdbcSupport.hasUpdateCount("CREATE TABLE values_table(value INTEGER)"));
    }
}
