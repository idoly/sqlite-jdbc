package io.github.idoly.sqlite.jdbc;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Driver;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

final class SQLiteDriverTest {
    @Test
    void driverIsDiscoverableThroughJdbcServiceProvider() throws Exception {
        Driver driver = ServiceLoader.load(Driver.class).stream()
                .map(ServiceLoader.Provider::get)
                .filter(SQLiteDriver.class::isInstance)
                .findFirst()
                .orElseThrow();

        assertInstanceOf(SQLiteDriver.class, driver);
        assertTrue(driver.acceptsURL("jdbc:sqlite::memory:"));
    }
}
