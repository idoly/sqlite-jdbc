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
import java.sql.JDBCType;
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
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Calendar;
import java.util.Properties;
import java.util.TimeZone;
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

            assertEquals(0, statement.executeUpdate("CREATE TABLE item_audit(item_id INTEGER)"));
            assertEquals(0, statement.executeUpdate("CREATE TRIGGER items_audit AFTER INSERT ON items "
                    + "BEGIN INSERT INTO item_audit VALUES (new.id); END"));
            assertEquals(1, statement.executeUpdate("INSERT INTO items(name) VALUES ('triggered')"));
            assertEquals(1L, statement.executeLargeUpdate("INSERT INTO items(name) VALUES ('large')"));
            assertEquals(1L, statement.getLargeUpdateCount());
            statement.addBatch("INSERT INTO items(name) VALUES ('large-batch')");
            assertArrayEquals(new long[] {1L}, statement.executeLargeBatch());
            statement.setLargeMaxRows(25);
            assertEquals(25L, statement.getLargeMaxRows());
            statement.setLargeMaxRows(0);

            ResultSet batchPredecessor = statement.executeQuery("SELECT 1");
            statement.addBatch("UPDATE items SET name = name WHERE 0");
            assertArrayEquals(new int[] {0}, statement.executeBatch());
            assertTrue(batchPredecessor.isClosed());

            connection.setAutoCommit(false);
            assertEquals(1, statement.executeUpdate("INSERT INTO items(name) VALUES ('rolled back')"));
            connection.rollback();
            assertFalse(connection.getAutoCommit());

            try (ResultSet resultSet = statement.executeQuery(
                    "SELECT id, name, NULL AS missing, X'0001FF' AS payload FROM items WHERE id <= 2 ORDER BY id")) {
                assertTrue(resultSet.isBeforeFirst());
                assertTrue(resultSet.next());
                assertEquals(1L, resultSet.getLong("id"));
                assertEquals("updated", resultSet.getString(2));
                assertEquals(null, resultSet.getObject("missing"));
                assertEquals(null, resultSet.getObject("missing", Long.class));
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
                assertFalse(statement.getMoreResults(Statement.KEEP_CURRENT_RESULT));
                assertFalse(resultSet.isClosed());
                assertTrue(resultSet.next());
                assertEquals(5, resultSet.getInt("item_count"));
            }

            SQLException queryError = assertThrows(
                    SQLException.class, () -> statement.executeUpdate("SELECT * FROM items"));
            assertEquals("07000", queryError.getSQLState());
            SQLException nulSql = assertThrows(SQLException.class, () -> statement.execute("SELECT 1\0; SELECT 2"));
            assertEquals("42000", nulSql.getSQLState());
            try (ResultSet overflow = statement.executeQuery("SELECT 128, 0.5, 'true', 'not-boolean'")) {
                assertTrue(overflow.next());
                SQLException rangeError = assertThrows(SQLException.class, () -> overflow.getByte(1));
                assertEquals("22003", rangeError.getSQLState());
                assertTrue(overflow.getBoolean(2));
                assertTrue(overflow.getBoolean(3));
                SQLException booleanError = assertThrows(SQLException.class, () -> overflow.getBoolean(4));
                assertEquals("22018", booleanError.getSQLState());
            }
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
                insert.setString(5, "embedded\0nul");
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
                    assertEquals("embedded\0nul", rows.getString("note"));
                    assertEquals("656D626564646564006E756C", scalarString(connection,
                            "SELECT hex(note) FROM typed_values WHERE id = 3"));
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

            statement.executeUpdate("UPDATE generated_values SET value = 'updated'", Statement.RETURN_GENERATED_KEYS);
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertFalse(keys.next());
            }

            statement.executeUpdate("/* generated key */ WITH next(value) AS (VALUES('cte')) "
                    + "INSERT INTO generated_values(value) SELECT value FROM next", Statement.RETURN_GENERATED_KEYS);
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertTrue(keys.next());
                assertEquals(4, keys.getLong(1));
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
            statement.executeUpdate("CREATE TABLE metadata_identity (id INTEGER PRIMARY KEY, base TEXT, "
                    + "generated_value TEXT GENERATED ALWAYS AS (upper(base)))");
            statement.executeUpdate("CREATE TABLE metadata_implicit (parent_id REFERENCES metadata_identity)");
            statement.executeUpdate("CREATE TABLE edge_desc_pk (id INTEGER PRIMARY KEY DESC)");

            DatabaseMetaData metadata = connection.getMetaData();
            assertEquals("SQLite", metadata.getDatabaseProductName());
            assertTrue(metadata.getDatabaseProductVersion().startsWith("3."));
            assertTrue(metadata.supportsTransactions());
            assertTrue(metadata.supportsSavepoints());
            assertTrue(metadata.supportsGetGeneratedKeys());
            assertFalse(metadata.supportsNamedParameters());
            assertFalse(metadata.supportsConvert());
            assertEquals(2000, metadata.getMaxColumnsInIndex());

            try (ResultSet tables = metadata.getTables(null, null, "metadata_%", new String[] {"TABLE", "VIEW"})) {
                assertTrue(tables.next());
                assertEquals("metadata_children", tables.getString("TABLE_NAME"));
                assertEquals("main", tables.getString("TABLE_CAT"));
                assertEquals("TABLE", tables.getString("TABLE_TYPE"));
                assertTrue(tables.next());
                assertEquals("metadata_identity", tables.getString("TABLE_NAME"));
                assertEquals("TABLE", tables.getString("TABLE_TYPE"));
                assertTrue(tables.next());
                assertEquals("metadata_implicit", tables.getString("TABLE_NAME"));
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
                assertEquals("main", columns.getString("TABLE_CAT"));
                assertEquals(java.sql.Types.BIGINT, columns.getInt("DATA_TYPE"));
                assertEquals("YES", columns.getString("IS_NULLABLE"));
                assertTrue(columns.next());
                assertEquals("item_id", columns.getString("COLUMN_NAME"));
                assertEquals("YES", columns.getString("IS_NULLABLE"));
                assertTrue(columns.next());
                assertEquals("name", columns.getString("COLUMN_NAME"));
                assertEquals("NO", columns.getString("IS_NULLABLE"));
                assertFalse(columns.next());
            }

            try (ResultSet columns = metadata.getColumns(null, null, "metadata_identity", "%")) {
                assertTrue(columns.next());
                assertEquals("id", columns.getString("COLUMN_NAME"));
                assertEquals(DatabaseMetaData.columnNoNulls, columns.getInt("NULLABLE"));
                assertEquals("NO", columns.getString("IS_NULLABLE"));
                assertEquals("YES", columns.getString("IS_AUTOINCREMENT"));
                assertTrue(columns.next());
                assertTrue(columns.next());
                assertEquals("generated_value", columns.getString("COLUMN_NAME"));
                assertEquals("YES", columns.getString("IS_GENERATEDCOLUMN"));
                assertFalse(columns.next());
            }

            try (ResultSet columns = metadata.getColumns(null, null, "edge_desc_pk", "id")) {
                assertTrue(columns.next());
                assertEquals("YES", columns.getString("IS_NULLABLE"));
                assertEquals("NO", columns.getString("IS_AUTOINCREMENT"));
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
                assertEquals("main", imported.getString("PKTABLE_CAT"));
                assertEquals("main", imported.getString("FKTABLE_CAT"));
                assertEquals("tenant_id", imported.getString("FKCOLUMN_NAME"));
                assertEquals(DatabaseMetaData.importedKeyCascade, imported.getInt("DELETE_RULE"));
                assertTrue(imported.next());
                assertEquals("item_id", imported.getString("FKCOLUMN_NAME"));
                assertFalse(imported.next());
            }

            try (ResultSet imported = metadata.getImportedKeys(null, null, "metadata_implicit")) {
                assertTrue(imported.next());
                assertEquals("id", imported.getString("PKCOLUMN_NAME"));
                assertEquals("parent_id", imported.getString("FKCOLUMN_NAME"));
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
            try (ResultSet procedures = metadata.getProcedures(null, null, "%")) {
                assertEquals(9, procedures.getMetaData().getColumnCount());
                assertFalse(procedures.next());
            }
            try (ResultSet procedureColumns = metadata.getProcedureColumns(null, null, "%", "%")) {
                assertEquals(20, procedureColumns.getMetaData().getColumnCount());
                assertFalse(procedureColumns.next());
            }
            try (ResultSet bestRow = metadata.getBestRowIdentifier(
                    null, null, "metadata_items", DatabaseMetaData.bestRowSession, true)) {
                assertEquals(8, bestRow.getMetaData().getColumnCount());
                assertFalse(bestRow.next());
            }
            try (ResultSet clientInfo = metadata.getClientInfoProperties()) {
                assertEquals(4, clientInfo.getMetaData().getColumnCount());
                assertFalse(clientInfo.next());
            }
            assertFalse(metadata.supportsStatementPooling());
            assertFalse(metadata.generatedKeyAlwaysReturned());
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
            try (PreparedStatement insert = connection.prepareStatement(
                    "WITH RECURSIVE counter(value) AS (VALUES(0) UNION ALL "
                            + "SELECT value + 1 FROM counter WHERE value < 100000000) "
                            + "INSERT INTO unique_values SELECT value FROM counter")) {
                insert.setQueryTimeout(1);
                SQLTimeoutException timeout = assertThrows(SQLTimeoutException.class, insert::executeUpdate);
                assertEquals("HYT00", timeout.getSQLState());
            }
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
                assertEquals("1", scalarString(connection, "PRAGMA foreign_keys"));
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
                insert.setObject(5, java.time.LocalDate.of(2026, 8, 5), JDBCType.DATE);
                assertEquals(1L, insert.executeLargeUpdate());
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

    @Test
    void appliesCalendarToTimestampConversions() throws Exception {
        assumeNativeLibrary();

        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        Calendar plusEight = Calendar.getInstance(TimeZone.getTimeZone("GMT+08:00"));
        Timestamp instant = Timestamp.from(Instant.parse("2026-08-05T12:34:56.123456789Z"));
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                PreparedStatement insert = connection.prepareStatement("SELECT ?")) {
            insert.setTimestamp(1, instant, plusEight);
            try (ResultSet result = insert.executeQuery()) {
                assertTrue(result.next());
                assertEquals("2026-08-05 20:34:56.123456789", result.getString(1));
                assertEquals("2026-08-05", result.getDate(1).toString());
                assertEquals("20:34:56", result.getTime(1).toString());
                assertEquals(Instant.parse("2026-08-05T20:34:56.123456789Z"),
                        result.getTimestamp(1, utc).toInstant());
                assertEquals(instant.toInstant(), result.getTimestamp(1, plusEight).toInstant());
            }
            SQLException conversion = assertThrows(
                    SQLException.class, () -> insert.setObject(1, "not-a-date", JDBCType.DATE));
            assertEquals("22018", conversion.getSQLState());
        }
    }

    private static String scalarString(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            if (!result.next()) throw new SQLException("Scalar query returned no rows");
            return result.getString(1);
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
