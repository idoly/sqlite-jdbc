package io.github.idoly.sqlite;

import static org.assertj.core.api.Assertions.*;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class SQLiteDriverTest {
    @Test
    public void versions() throws Exception {
        Driver driver = DriverManager.getDriver("jdbc:sqlite:");
        assertThat(driver.getMajorVersion()).isEqualTo(3);
        assertThat(driver.getMinorVersion()).isEqualTo(53);
        assertThat(SQLiteDriver.getVersion()).isEqualTo("3.53.4.0");

        try (SQLiteConnection connection =
                SQLiteDriver.createConnection("jdbc:sqlite:", new Properties())) {
            assertThat(connection.libversion()).isEqualTo("3.53.4");
        }
    }

    @Test
    public void parentLoggerIsNotSupported() {
        assertThatThrownBy(() -> new SQLiteDriver().getParentLogger())
                .isInstanceOf(SQLFeatureNotSupportedException.class);
    }

    @Test
    public void createConnectionThrowsIfProtocolUnhandled() {
        assertThatExceptionOfType(SQLException.class)
                .isThrownBy(
                        () ->
                                SQLiteDriver.createConnection(
                                        "jdbc:anotherpopulardatabaseprotocol:", null))
                .withMessageContaining("invalid database address");
    }

    @Test
    public void driverConnectReturnsNullIfProtocolUnhandled() throws Exception {
        assertThat(new SQLiteDriver().connect("jdbc:anotherpopulardatabaseprotocol:", null))
                .isNull();
        assertThat(new SQLiteDriver().connect("jdbc:wrongprotocol:test.db", new Properties()))
                .isNull();
    }

    @Test
    public void createConnectionThrowsOnNullUrl() {
        assertThatExceptionOfType(SQLException.class)
                .isThrownBy(() -> SQLiteDriver.createConnection(null, null))
                .withMessageContaining("invalid database address");
    }

    @Test
    public void createConnectionAcceptsValidSqliteUrl() throws Exception {
        try (Connection conn = SQLiteDriver.createConnection("jdbc:sqlite:", null)) {
            assertThat(conn).isNotNull();
            assertThat(conn.isClosed()).isFalse();
        }
    }

    @Test
    public void allDriverPropertyInfoShouldHaveADescription() throws Exception {
        Driver driver = DriverManager.getDriver("jdbc:sqlite:");
        assertThat(driver.getPropertyInfo(null, null))
                .allSatisfy((info) -> assertThat(info.description).isNotNull());
    }

    @Test
    public void pragmaReadOnly() throws SQLException {
        SQLiteConnection connection =
                (SQLiteConnection)
                        DriverManager.getConnection(
                                "jdbc:sqlite::memory:?jdbc.explicit_readonly=true");
        assertThat(connection.database().getConfig().isExplicitReadOnly()).isTrue();
    }

    @Test
    public void canSetJdbcConnectionToReadOnly() throws Exception {
        SQLiteDataSource dataSource = createDatasourceWithExplicitReadonly();
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            assertThat(connection.isReadOnly()).isFalse();
            connection.setReadOnly(true);
            assertThat(connection.isReadOnly()).isTrue();
            connection.setReadOnly(false);
            assertThat(connection.isReadOnly()).isFalse();
            connection.setReadOnly(true);
            assertThat(connection.isReadOnly()).isTrue();
        }
    }

    @Test
    public void cannotSetJdbcConnectionToReadOnlyAfterFirstStatement() throws Exception {
        SQLiteDataSource dataSource = createDatasourceWithExplicitReadonly();

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            // execute a statement
            try (Statement statement = connection.createStatement()) {
                boolean success = statement.execute("SELECT * FROM sqlite_schema");
                assertThat(success).isTrue();
            }
            // try to assign read-only
            assertThatExceptionOfType(SQLException.class)
                    .as("Managed to set readOnly = true on a dirty connection!")
                    .isThrownBy(() -> connection.setReadOnly(true));
        }
    }

    @Test
    public void canSetJdbcConnectionToReadOnlyAfterCommit() throws Exception {
        SQLiteDataSource dataSource = createDatasourceWithExplicitReadonly();
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            connection.setReadOnly(true);
            // execute a statement
            try (Statement statement = connection.createStatement()) {
                boolean success = statement.execute("SELECT * FROM sqlite_schema");
                assertThat(success).isTrue();
            }
            connection.commit();

            // try to assign a new read-only value
            connection.setReadOnly(false);
        }
    }

    @Test
    public void canSetJdbcConnectionToReadOnlyAfterRollback() throws Exception {
        SQLiteDataSource dataSource = createDatasourceWithExplicitReadonly();
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                assertThat(statement.execute("SELECT * FROM sqlite_schema")).isTrue();
            }
            connection.rollback();

            connection.setReadOnly(true);
            try (Statement statement = connection.createStatement()) {
                assertThat(statement.execute("SELECT * FROM sqlite_schema")).isTrue();
            }
            connection.rollback();
        }
    }

    @Test
    public void cannotExecuteUpdatesWhenConnectionIsSetToReadOnly() throws Exception {
        SQLiteDataSource dataSource = createDatasourceWithExplicitReadonly();
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            connection.setReadOnly(true);

            // execute a statement
            try (Statement statement = connection.createStatement()) {
                assertThatExceptionOfType(SQLException.class)
                        .as("Managed to modify database contents on a read-only connection!")
                        .isThrownBy(
                                () ->
                                        statement.execute(
                                                "CREATE TABLE TestTable(ID VARCHAR(255), PRIMARY KEY(ID))"));
            }
            connection.rollback();

            // try to assign read-only
            connection.setReadOnly(true);
        }
    }

    @Test
    public void jdbcHammer(@TempDir File tempDir) throws Exception {
        final SQLiteDataSource dataSource = createDatasourceWithExplicitReadonly();
        File tempFile = File.createTempFile("myTestDB", ".db", tempDir);
        dataSource.setUrl("jdbc:sqlite:" + tempFile.getAbsolutePath());
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate("CREATE TABLE TestTable(ID INT, testval INT, PRIMARY KEY(ID));");
                stmt.executeUpdate("INSERT INTO TestTable (ID, testval) VALUES(1, 0);");
            }
            connection.commit();
        }

        final AtomicInteger count = new AtomicInteger();
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            int worker = i;
            Thread thread =
                    new Thread(
                            () -> {
                                for (int iteration = 0; iteration < 100; iteration++) {
                                    boolean read = ((worker + iteration) & 1) == 0;
                                    for (int attempt = 0; ; attempt++) {
                                        try {
                                            runHammerOperation(dataSource, read, count);
                                            break;
                                        } catch (SQLException error) {
                                            if (!isTransientLock(error) || attempt == 4) {
                                                throw new RuntimeException(
                                                        "Worker failed: " + error.getMessage(),
                                                        error);
                                            }
                                            try {
                                                Thread.sleep(10L << attempt);
                                            } catch (InterruptedException interrupted) {
                                                Thread.currentThread().interrupt();
                                                throw new RuntimeException(
                                                        "Worker interrupted", interrupted);
                                            }
                                        }
                                    }
                                }
                            });
            thread.setName("Worker #" + (i + 1));
            threads.add(thread);
        }
        for (Thread thread : threads) {
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
        try (Connection connection2 = dataSource.getConnection()) {
            connection2.setAutoCommit(false);
            connection2.setReadOnly(true);
            try (Statement stmt = connection2.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT * FROM TestTable")) {
                    assertThat(rs.next()).isTrue();
                    int id = rs.getInt("ID");
                    int val = rs.getInt("testval");
                    assertThat(id).isEqualTo(1);
                    assertThat(val).isEqualTo(count.get());
                    assertThat(rs.next()).isFalse();
                }
            }
            connection2.commit();
        }
    }

    private static void runHammerOperation(
            SQLiteDataSource dataSource, boolean read, AtomicInteger count) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            if (read) {
                connection.setReadOnly(true);
                try (Statement statement = connection.createStatement();
                        ResultSet resultSet = statement.executeQuery("SELECT * FROM TestTable")) {
                    while (resultSet.next()) {
                        resultSet.getInt("testval");
                    }
                }
                return;
            }

            try (Statement statement = connection.createStatement();
                    ResultSet resultSet = statement.executeQuery("SELECT * FROM TestTable")) {
                while (resultSet.next()) {
                    int id = resultSet.getInt("ID");
                    int value = resultSet.getInt("testval");
                    statement.executeUpdate(
                            "UPDATE TestTable SET testval = " + (value + 1) + " WHERE ID = " + id);
                }
            }
            connection.commit();
            count.incrementAndGet();
        }
    }

    private static boolean isTransientLock(SQLException error) {
        int primaryCode = error.getErrorCode() & 0xFF;
        return primaryCode == 5 || primaryCode == 6;
    }

    // helper methods -----------------------------------------------------------------

    private SQLiteDataSource createDatasourceWithExplicitReadonly() {
        SQLiteConfig config = new SQLiteConfig();
        config.setExplicitReadOnly(true);
        config.setBusyTimeout(10000);

        return new SQLiteDataSource(config);
    }
}
