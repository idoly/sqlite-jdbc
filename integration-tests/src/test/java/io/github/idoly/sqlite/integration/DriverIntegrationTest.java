package io.github.idoly.sqlite.integration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import io.github.idoly.sqlite.jdbc.SQLiteConfig;
import io.github.idoly.sqlite.jdbc.SQLiteDataSource;
import io.github.idoly.sqlite.jdbc.SQLiteDriver;
import java.nio.file.Path;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Savepoint;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.SQLNonTransientException;
import java.sql.SQLTimeoutException;
import java.sql.SQLTransientException;
import java.sql.SQLXML;
import java.sql.Statement;
import java.util.Properties;
import org.junit.jupiter.api.Test;

final class DriverIntegrationTest {
    @Test
    void opensAndClosesConnectionThroughDriverManager() throws Exception {
        assumeNativeLibrary();

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            assertFalse(connection.isClosed());
            assertTrue(connection.getAutoCommit());
            assertTrue(connection.isValid(0));
        }
    }

    @Test
    void executesUpdatesAndBatchesThroughJdbcStatement() throws Exception {
        assumeNativeLibrary();

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                Statement statement = connection.createStatement()) {
            assertEquals(0, statement.executeUpdate(
                    "CREATE TABLE items (id INTEGER PRIMARY KEY, name TEXT NOT NULL)"));
            assertEquals(1, statement.executeUpdate("INSERT INTO items(name) VALUES ('first')"));
            SQLException multipleStatements = assertThrows(SQLException.class,
                    () -> statement.executeUpdate("INSERT INTO items(name) VALUES ('ignored'); DROP TABLE items"));
            assertTrue(multipleStatements.getMessage().contains("Multiple SQL statements"));

            statement.addBatch("INSERT INTO items(name) VALUES ('second')");
            statement.addBatch("UPDATE items SET name = 'updated' WHERE id = 1");
            assertArrayEquals(new int[] {1, 1}, statement.executeBatch());

            connection.setAutoCommit(false);
            assertEquals(1, statement.executeUpdate("INSERT INTO items(name) VALUES ('rolled back')"));
            connection.rollback();
            assertFalse(connection.getAutoCommit());

            try (ResultSet resultSet = statement.executeQuery(
                    "SELECT id, name, NULL AS missing, X'0001FF' AS payload FROM items ORDER BY id")) {
                assertTrue(resultSet.isBeforeFirst());
                assertTrue(resultSet.next());
                assertEquals(1L, resultSet.getLong("id"));
                assertEquals("updated", resultSet.getString(2));
                assertEquals(null, resultSet.getObject("missing"));
                assertTrue(resultSet.wasNull());
                assertArrayEquals(new byte[] {0, 1, -1}, resultSet.getBytes("payload"));

                ResultSetMetaData metadata = resultSet.getMetaData();
                assertEquals(4, metadata.getColumnCount());
                assertEquals("name", metadata.getColumnLabel(2));

                assertTrue(resultSet.next());
                assertEquals("second", resultSet.getString("name"));
                assertFalse(resultSet.next());
                assertTrue(resultSet.isAfterLast());
            }

            assertTrue(statement.execute("SELECT count(*) AS item_count FROM items"));
            try (ResultSet resultSet = statement.getResultSet()) {
                assertTrue(resultSet.next());
                assertEquals(2, resultSet.getInt("item_count"));
            }

            SQLException queryError = assertThrows(
                    SQLException.class, () -> statement.executeUpdate("SELECT * FROM items"));
            assertEquals("07000", queryError.getSQLState());
        }
    }

    @Test
    void bindsAndReusesPreparedStatements() throws Exception {
        assumeNativeLibrary();

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                Statement schema = connection.createStatement()) {
            schema.executeUpdate("CREATE TABLE typed_values (id INTEGER, amount REAL, name TEXT, payload BLOB, note TEXT)");

            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO typed_values VALUES (?, ?, ?, ?, ?)")) {
                assertEquals(5, insert.getParameterMetaData().getParameterCount());
                insert.setLong(1, 1);
                insert.setDouble(2, 2.5);
                insert.setString(3, "first");
                insert.setBytes(4, new byte[] {1, 2, 3});
                insert.setNull(5, java.sql.Types.VARCHAR);
                assertEquals(1, insert.executeUpdate());

                insert.setLong(1, 2);
                insert.setDouble(2, 7.25);
                insert.setString(3, "second");
                insert.setBytes(4, new byte[] {4, 5});
                insert.setString(5, "note");
                insert.addBatch();

                insert.setLong(1, 3);
                insert.setString(3, "third");
                insert.addBatch();
                assertArrayEquals(new int[] {1, 1}, insert.executeBatch());

                insert.clearParameters();
                SQLException missing = assertThrows(SQLException.class, insert::executeUpdate);
                assertEquals("07001", missing.getSQLState());
            }

            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT id, amount, name, payload, note FROM typed_values WHERE id >= ? ORDER BY id")) {
                assertEquals(5, query.getMetaData().getColumnCount());
                query.setInt(1, 2);
                try (ResultSet rows = query.executeQuery()) {
                    assertTrue(rows.next());
                    assertEquals(2, rows.getInt(1));
                    assertEquals(7.25, rows.getDouble("amount"));
                    assertArrayEquals(new byte[] {4, 5}, rows.getBytes("payload"));
                    assertEquals("note", rows.getString("note"));
                    assertTrue(rows.next());
                    assertEquals("third", rows.getString("name"));
                    assertFalse(rows.next());
                }

                query.setInt(1, 3);
                try (ResultSet rows = query.executeQuery()) {
                    assertTrue(rows.next());
                    assertEquals(3, rows.getInt("id"));
                    assertFalse(rows.next());
                }
            }
        }
    }

    @Test
    void returnsGeneratedRowIdsWhenRequested() throws Exception {
        assumeNativeLibrary();

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE generated_values (id INTEGER PRIMARY KEY, value TEXT)");
            statement.executeUpdate(
                    "INSERT INTO generated_values(value) VALUES ('statement')", Statement.RETURN_GENERATED_KEYS);
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertTrue(keys.next());
                assertEquals(1, keys.getLong("GENERATED_KEY"));
                assertFalse(keys.next());
            }

            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO generated_values(value) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {
                insert.setString(1, "prepared");
                assertEquals(1, insert.executeUpdate());
                try (ResultSet keys = insert.getGeneratedKeys()) {
                    assertTrue(keys.next());
                    assertEquals(2, keys.getLong(1));
                }
            }

            statement.executeUpdate("INSERT INTO generated_values(value) VALUES ('not requested')");
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertFalse(keys.next());
            }
        }
    }

    @Test
    void supportsJdbcSavepoints() throws Exception {
        assumeNativeLibrary();

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE savepoint_values (value TEXT)");
            connection.setAutoCommit(false);
            statement.executeUpdate("INSERT INTO savepoint_values VALUES ('kept')");
            Savepoint savepoint = connection.setSavepoint("before_optional_value");
            assertEquals("before_optional_value", savepoint.getSavepointName());
            statement.executeUpdate("INSERT INTO savepoint_values VALUES ('discarded')");
            connection.rollback(savepoint);
            connection.releaseSavepoint(savepoint);
            connection.commit();

            try (ResultSet rows = statement.executeQuery("SELECT count(*) FROM savepoint_values")) {
                assertTrue(rows.next());
                assertEquals(1, rows.getInt(1));
            }
            SQLException released = assertThrows(SQLException.class, savepoint::getSavepointName);
            assertEquals("3B001", released.getSQLState());
        }
    }

    @Test
    void exposesDatabaseMetadata() throws Exception {
        assumeNativeLibrary();

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE metadata_items (tenant_id INTEGER, item_id INTEGER, name TEXT NOT NULL, PRIMARY KEY (tenant_id, item_id))");
            statement.executeUpdate("CREATE VIEW metadata_view AS SELECT name FROM metadata_items");
            statement.executeUpdate("CREATE TABLE metadata_children (tenant_id INTEGER, item_id INTEGER, "
                    + "FOREIGN KEY (tenant_id, item_id) REFERENCES metadata_items(tenant_id, item_id) ON DELETE CASCADE)");
            statement.executeUpdate("CREATE INDEX metadata_children_item_idx ON metadata_children(item_id)");

            DatabaseMetaData metadata = connection.getMetaData();
            assertEquals("SQLite", metadata.getDatabaseProductName());
            assertTrue(metadata.getDatabaseProductVersion().startsWith("3."));
            assertTrue(metadata.supportsTransactions());
            assertTrue(metadata.supportsSavepoints());
            assertTrue(metadata.supportsGetGeneratedKeys());

            try (ResultSet tables = metadata.getTables(null, null, "metadata_%", new String[] {"TABLE", "VIEW"})) {
                assertTrue(tables.next());
                assertEquals("metadata_children", tables.getString("TABLE_NAME"));
                assertEquals("TABLE", tables.getString("TABLE_TYPE"));
                assertTrue(tables.next());
                assertEquals("metadata_items", tables.getString("TABLE_NAME"));
                assertEquals("TABLE", tables.getString("TABLE_TYPE"));
                assertTrue(tables.next());
                assertEquals("metadata_view", tables.getString("TABLE_NAME"));
                assertEquals("VIEW", tables.getString("TABLE_TYPE"));
                assertFalse(tables.next());
            }

            try (ResultSet columns = metadata.getColumns(null, null, "metadata_items", "%")) {
                assertTrue(columns.next());
                assertEquals("tenant_id", columns.getString("COLUMN_NAME"));
                assertEquals(java.sql.Types.BIGINT, columns.getInt("DATA_TYPE"));
                assertTrue(columns.next());
                assertTrue(columns.next());
                assertEquals("name", columns.getString("COLUMN_NAME"));
                assertEquals("NO", columns.getString("IS_NULLABLE"));
                assertFalse(columns.next());
            }

            try (ResultSet keys = metadata.getPrimaryKeys(null, null, "metadata_items")) {
                assertTrue(keys.next());
                assertEquals("tenant_id", keys.getString("COLUMN_NAME"));
                assertEquals(1, keys.getInt("KEY_SEQ"));
                assertTrue(keys.next());
                assertEquals("item_id", keys.getString("COLUMN_NAME"));
                assertEquals(2, keys.getInt("KEY_SEQ"));
                assertFalse(keys.next());
            }

            try (ResultSet imported = metadata.getImportedKeys(null, null, "metadata_children")) {
                assertTrue(imported.next());
                assertEquals("metadata_items", imported.getString("PKTABLE_NAME"));
                assertEquals("tenant_id", imported.getString("FKCOLUMN_NAME"));
                assertEquals(DatabaseMetaData.importedKeyCascade, imported.getInt("DELETE_RULE"));
                assertTrue(imported.next());
                assertEquals("item_id", imported.getString("FKCOLUMN_NAME"));
                assertFalse(imported.next());
            }

            try (ResultSet indexes = metadata.getIndexInfo(null, null, "metadata_children", false, false)) {
                boolean found = false;
                while (indexes.next()) {
                    if ("metadata_children_item_idx".equals(indexes.getString("INDEX_NAME"))) {
                        found = true;
                        assertEquals("item_id", indexes.getString("COLUMN_NAME"));
                    }
                }
                assertTrue(found);
            }

            try (ResultSet catalogs = metadata.getCatalogs()) {
                assertTrue(catalogs.next());
                assertEquals("main", catalogs.getString("TABLE_CAT"));
            }
            try (ResultSet types = metadata.getTypeInfo()) {
                assertTrue(types.next());
                assertEquals("INTEGER", types.getString("TYPE_NAME"));
            }
        }
    }

    @Test
    void enforcesTimeoutsAndMapsSqliteErrors() throws Exception {
        assumeNativeLibrary();

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE unique_values (value TEXT UNIQUE)");
            statement.executeUpdate("INSERT INTO unique_values VALUES ('duplicate')");
            assertThrows(SQLIntegrityConstraintViolationException.class,
                    () -> statement.executeUpdate("INSERT INTO unique_values VALUES ('duplicate')"));

            statement.setQueryTimeout(1);
            assertThrows(SQLTimeoutException.class, () -> statement.executeQuery(
                    "WITH RECURSIVE counter(value) AS (VALUES(1) UNION ALL SELECT value + 1 FROM counter WHERE value < 1000000000) SELECT sum(value) FROM counter"));
            statement.setQueryTimeout(0);
            try (ResultSet result = statement.executeQuery("SELECT 1")) {
                assertTrue(result.next());
                assertEquals(1, result.getInt(1));
            }
        }

        Path databaseFile = Files.createTempFile("sqlite-jdbc-lock-", ".db");
        Properties shortWait = new Properties();
        shortWait.setProperty(SQLiteDriver.BUSY_TIMEOUT_PROPERTY, "50");
        String url = "jdbc:sqlite:" + databaseFile;
        try (Connection writer = DriverManager.getConnection(url, shortWait);
                Connection contender = DriverManager.getConnection(url, shortWait);
                Statement writerStatement = writer.createStatement();
                Statement contenderStatement = contender.createStatement()) {
            writerStatement.executeUpdate("CREATE TABLE lock_values (value TEXT)");
            writer.setAutoCommit(false);
            writerStatement.executeUpdate("INSERT INTO lock_values VALUES ('writer')");
            assertThrows(SQLTransientException.class,
                    () -> contenderStatement.executeUpdate("INSERT INTO lock_values VALUES ('contender')"));
            writer.rollback();
        } finally {
            Files.deleteIfExists(databaseFile);
        }
    }

    @Test
    void supportsFilePathsConnectionPropertiesAndCloseCascades() throws Exception {
        assumeNativeLibrary();

        Path directory = Files.createTempDirectory("sqlite-jdbc-path-");
        Path databaseFile = directory.resolve("database-\u6570\u636e\u5e93 with spaces.db");
        SQLiteConfig config = SQLiteConfig.builder()
                .busyTimeoutMillis(2_000)
                .foreignKeys(true)
                .build();
        SQLiteDataSource dataSource = new SQLiteDataSource("jdbc:sqlite:" + databaseFile, config);
        try {
            try (Connection connection = dataSource.getConnection();
                    Statement setup = connection.createStatement()) {
                setup.executeUpdate("CREATE TABLE parents (id INTEGER PRIMARY KEY)");
                setup.executeUpdate("CREATE TABLE children (parent_id INTEGER REFERENCES parents(id))");
                assertThrows(SQLIntegrityConstraintViolationException.class,
                        () -> setup.executeUpdate("INSERT INTO children VALUES (999)"));
            }

            dataSource.setReadOnly(true);
            try (Connection connection = dataSource.getConnection()) {
                assertTrue(connection.isReadOnly());
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT count(*) FROM parents");
                assertTrue(resultSet.next());
                assertEquals(0, resultSet.getInt(1));
                connection.close();
                assertTrue(statement.isClosed());
                assertTrue(resultSet.isClosed());
            }

            try (Connection connection = dataSource.getConnection();
                    Statement statement = connection.createStatement()) {
                SQLNonTransientException readOnly = assertThrows(
                        SQLNonTransientException.class,
                        () -> statement.executeUpdate("INSERT INTO parents VALUES (1)"));
                assertEquals("25006", readOnly.getSQLState());
            }
        } finally {
            Files.deleteIfExists(databaseFile);
            Files.deleteIfExists(directory);
        }
    }

    @Test
    void supportsLobXmlAndJavaTimeConversions() throws Exception {
        assumeNativeLibrary();

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE rich_values (payload BLOB, content TEXT, national TEXT, xml TEXT, event_date TEXT)");

            Blob blob = connection.createBlob();
            blob.setBytes(1, new byte[] {9, 8, 7});
            Clob clob = connection.createClob();
            clob.setString(1, "clob-value");
            NClob nclob = connection.createNClob();
            nclob.setString(1, "nclob-value");
            SQLXML xml = connection.createSQLXML();
            xml.setString("<value>xml</value>");

            try (PreparedStatement insert = connection.prepareStatement("INSERT INTO rich_values VALUES (?, ?, ?, ?, ?)")) {
                insert.setBlob(1, blob);
                insert.setClob(2, clob);
                insert.setNClob(3, nclob);
                insert.setSQLXML(4, xml);
                insert.setObject(5, java.time.LocalDate.of(2026, 8, 5));
                assertEquals(1, insert.executeUpdate());
            }

            try (ResultSet result = statement.executeQuery("SELECT * FROM rich_values")) {
                assertTrue(result.next());
                assertArrayEquals(new byte[] {9, 8, 7}, result.getBlob(1).getBytes(1, 3));
                assertEquals("clob-value", result.getClob(2).getSubString(1, 10));
                assertEquals("nclob-value", result.getNClob(3).getSubString(1, 11));
                assertEquals("<value>xml</value>", result.getSQLXML(4).getString());
                assertEquals(java.time.LocalDate.of(2026, 8, 5), result.getObject(5, java.time.LocalDate.class));
            }
        }
    }

    private static void assumeNativeLibrary() {
        String libraryPath = System.getProperty("sqlite.jdbc.library.path");
        boolean externalLibrary = libraryPath != null && Files.isRegularFile(Path.of(libraryPath));
        ClassLoader loader = DriverIntegrationTest.class.getClassLoader();
        boolean packagedLibrary = loader.getResource(
                        "META-INF/native/linux-x86_64-glibc/libsqlitejdbc.so") != null
                || loader.getResource("META-INF/native/linux-x86_64-musl/libsqlitejdbc.so") != null
                || loader.getResource("META-INF/native/macos-x86_64/libsqlitejdbc.dylib") != null
                || loader.getResource("META-INF/native/macos-aarch64/libsqlitejdbc.dylib") != null
                || loader.getResource("META-INF/native/windows-x86_64/sqlitejdbc.dll") != null;
        assumeTrue(externalLibrary || packagedLibrary);
    }
}
