package io.github.idoly.sqlite.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.time.Duration;
import java.util.Properties;
import org.junit.jupiter.api.Test;

final class SQLiteConfigTest {
    @Test
    void exposesStableDefaults() {
        SQLiteConfig config = SQLiteConfig.defaults();

        assertEquals(5_000, config.busyTimeoutMillis());
        assertTrue(config.foreignKeys());
        assertFalse(config.readOnly());
        assertEquals(SQLiteTransactionMode.DEFERRED, config.transactionMode());
    }

    @Test
    void builderCopiesWithoutMutatingSource() {
        SQLiteConfig source = SQLiteConfig.builder()
                .busyTimeout(Duration.ofSeconds(2))
                .foreignKeys(false)
                .transactionMode(SQLiteTransactionMode.IMMEDIATE)
                .build();

        SQLiteConfig copy = source.toBuilder().readOnly(true).build();

        assertFalse(source.readOnly());
        assertTrue(copy.readOnly());
        assertEquals(2_000, copy.busyTimeoutMillis());
        assertFalse(copy.foreignKeys());
        assertEquals(SQLiteTransactionMode.IMMEDIATE, copy.transactionMode());
    }

    @Test
    void parsesJdbcProperties() throws SQLException {
        Properties properties = new Properties();
        properties.setProperty(SQLiteDriver.BUSY_TIMEOUT_PROPERTY, "250");
        properties.setProperty(SQLiteDriver.FOREIGN_KEYS_PROPERTY, "false");
        properties.setProperty(SQLiteDriver.READ_ONLY_PROPERTY, "true");
        properties.setProperty(SQLiteDriver.TRANSACTION_MODE_PROPERTY, "exclusive");

        SQLiteConfig config = SQLiteConfig.fromProperties(properties);

        assertEquals(250, config.busyTimeoutMillis());
        assertFalse(config.foreignKeys());
        assertTrue(config.readOnly());
        assertEquals(SQLiteTransactionMode.EXCLUSIVE, config.transactionMode());
    }

    @Test
    void rejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class,
                () -> SQLiteConfig.builder().busyTimeout(Duration.ofMillis(-1)));

        Properties properties = new Properties();
        properties.setProperty(SQLiteDriver.FOREIGN_KEYS_PROPERTY, "yes");
        assertThrows(SQLException.class, () -> SQLiteConfig.fromProperties(properties));
    }

    @Test
    void dataSourceUsesImmutableConfigSnapshots() {
        SQLiteConfig initial = SQLiteConfig.builder().busyTimeoutMillis(100).build();
        SQLiteDataSource dataSource = new SQLiteDataSource("jdbc:sqlite::memory:", initial);

        dataSource.setReadOnly(true);

        assertFalse(initial.readOnly());
        assertTrue(dataSource.getConfig().readOnly());
        assertEquals(100, dataSource.getBusyTimeoutMillis());
    }
}
