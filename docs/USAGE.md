# Usage Guide

## Runtime setup

The driver requires JDK 25 or later. It consists of the Java driver and one native classifier matching the runtime platform.

```xml
<dependency>
    <groupId>io.github.idoly</groupId>
    <artifactId>sqlite-driver</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
<dependency>
    <groupId>io.github.idoly</groupId>
    <artifactId>sqlite-native</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <classifier>linux-x86_64-glibc</classifier>
    <scope>runtime</scope>
</dependency>
```

Replace the classifier with one of:

```text
linux-x86_64-glibc   linux-aarch64-glibc
linux-x86_64-musl    linux-aarch64-musl
windows-x86_64
macos-x86_64         macos-aarch64
```

Enable native access when starting the application.

Classpath:

```bash
java --enable-native-access=ALL-UNNAMED -cp "app.jar:lib/*" com.example.Main
```

Module path:

```bash
java \
  --enable-native-access=io.github.idoly.sqlite.ffm \
  --add-modules=io.github.idoly.sqlite.nativelib \
  --module-path "app.jar:lib/*" \
  -m com.example.app/com.example.Main
```

The resource-only native classifier may instead remain on the classpath while `sqlite-driver` and `sqlite-ffm` are on the module path.

## JDBC URLs

The driver accepts URLs beginning with `jdbc:sqlite:`.

```text
jdbc:sqlite::memory:                         private in-memory database
jdbc:sqlite:./data/application.db            relative file
jdbc:sqlite:/var/lib/example/application.db  absolute file
jdbc:sqlite:file:shared?mode=memory&cache=shared  SQLite URI
```

SQLite URI handling is enabled for every connection. URI options are interpreted by SQLite, not by the JDBC property parser.

## DriverManager

The driver is discovered through the JDBC service-provider mechanism; `Class.forName` is not required.

```java
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;

Properties properties = new Properties();
properties.setProperty("busy_timeout", "5000");
properties.setProperty("foreign_keys", "true");
properties.setProperty("read_only", "false");
properties.setProperty("transaction_mode", "IMMEDIATE");

try (Connection connection = DriverManager.getConnection(
        "jdbc:sqlite:./data/application.db", properties)) {
    // Use the standard JDBC API.
}
```

Connection properties:

| Property | Default | Values | Meaning |
| --- | ---: | --- | --- |
| `busy_timeout` | `5000` | non-negative milliseconds | Wait for a locked database before returning `SQLITE_BUSY` |
| `foreign_keys` | `true` | `true`, `false` | Set `PRAGMA foreign_keys` on writable connections |
| `read_only` | `false` | `true`, `false` | Open with `SQLITE_OPEN_READONLY` |
| `transaction_mode` | `DEFERRED` | `DEFERRED`, `IMMEDIATE`, `EXCLUSIVE` | Mode used by lazy manual transactions |

Invalid values fail connection creation with SQLState `08001`.

## Configuration builder and DataSource

`SQLiteConfig` is immutable. Its builder centralizes validation and can be shared safely as a configuration snapshot.

```java
import io.github.idoly.sqlite.jdbc.SQLiteConfig;
import io.github.idoly.sqlite.jdbc.SQLiteDataSource;
import io.github.idoly.sqlite.jdbc.SQLiteTransactionMode;
import java.time.Duration;

SQLiteConfig config = SQLiteConfig.builder()
        .busyTimeout(Duration.ofSeconds(10))
        .foreignKeys(true)
        .readOnly(false)
        .transactionMode(SQLiteTransactionMode.IMMEDIATE)
        .build();

SQLiteDataSource dataSource = new SQLiteDataSource(
        "jdbc:sqlite:./data/application.db", config);
```

The conventional bean setters remain available for dependency-injection frameworks:

```java
dataSource.setBusyTimeoutMillis(10_000);
dataSource.setForeignKeys(true);
dataSource.setTransactionMode(SQLiteTransactionMode.IMMEDIATE);
```

Each setter replaces the current immutable configuration snapshot. `getConfig()` returns that snapshot.

## Statements and queries

Statements are forward-only and read-only.

```java
try (var connection = dataSource.getConnection();
        var statement = connection.createStatement()) {
    statement.executeUpdate("CREATE TABLE IF NOT EXISTS account ("
            + "id INTEGER PRIMARY KEY, name TEXT NOT NULL, balance NUMERIC NOT NULL)");

    try (var result = statement.executeQuery(
            "SELECT id, name, balance FROM account ORDER BY id")) {
        while (result.next()) {
            long id = result.getLong("id");
            String name = result.getString("name");
            var balance = result.getBigDecimal("balance");
        }
    }
}
```

`Statement.execute` returns `true` for a row-producing statement and `false` for an update. Multiple SQL statements in one string and SQL containing NUL characters are rejected; trailing whitespace and comments are accepted. JDBC large-update methods delegate to the same execution path and return `long` counts.

## Prepared statements

A `PreparedStatement` owns one native prepared handle and reuses it across executions. Every positional parameter must be set before execution.

```java
try (var connection = dataSource.getConnection();
        var insert = connection.prepareStatement(
                "INSERT INTO account(name, balance) VALUES (?, ?)",
                java.sql.Statement.RETURN_GENERATED_KEYS)) {
    insert.setString(1, "primary");
    insert.setBigDecimal(2, new java.math.BigDecimal("125.50"));
    insert.executeUpdate();

    try (var keys = insert.getGeneratedKeys()) {
        if (keys.next()) {
            long id = keys.getLong(1);
        }
    }
}
```

Generated keys are snapshots of `last_insert_rowid()` taken after a successful top-level `INSERT` or `REPLACE`, including those preceded by a `WITH` clause. Updates, failed executions, and executions that did not request keys return an empty generated-keys result.

Canonical SQLite mappings:

| Java/JDBC value | SQLite storage class |
| --- | --- |
| `null` | `NULL` |
| boolean and integral numbers | `INTEGER` |
| floating-point numbers | `REAL` |
| strings, decimal values, dates and times | `TEXT` |
| `byte[]`, binary streams and `Blob` | `BLOB` |
| character streams, `Clob`, `NClob`, `SQLXML` | `TEXT` |

`LocalDate`, `LocalTime`, and `LocalDateTime` are accepted by `setObject`. Date and time values use JDBC string representations; the database schema remains responsible for storage conventions. Calendar overloads interpret those fields in the supplied time zone. TEXT values preserve embedded NUL characters because binding uses explicit UTF-8 byte lengths.

## Transactions

Disabling auto-commit changes JDBC state immediately but starts SQLite's transaction lazily before the first statement.

```java
try (var connection = dataSource.getConnection()) {
    connection.setAutoCommit(false);
    try (var update = connection.prepareStatement(
            "UPDATE account SET balance = balance + ? WHERE id = ?")) {
        update.setBigDecimal(1, new java.math.BigDecimal("10.00"));
        update.setLong(2, 1);
        update.executeUpdate();
        connection.commit();
    } catch (java.sql.SQLException failure) {
        connection.rollback();
        throw failure;
    }
}
```

After `commit()` or `rollback()`, manual-commit mode remains enabled. The next statement starts a new transaction lazily. Switching back to auto-commit commits an active transaction. Closing a connection rolls back any active transaction.

Transaction modes:

- `DEFERRED`: acquire locks only when reads or writes require them.
- `IMMEDIATE`: acquire the write reservation when the transaction starts.
- `EXCLUSIVE`: request an exclusive transaction when it starts.

## Savepoints

```java
connection.setAutoCommit(false);
var savepoint = connection.setSavepoint("before_optional_work");
try {
    // Execute optional work.
    connection.releaseSavepoint(savepoint);
    connection.commit();
} catch (java.sql.SQLException failure) {
    connection.rollback(savepoint);
    connection.commit();
}
```

Driver-generated SQL identifiers are used internally; application savepoint names are never interpolated into SQL.

## Batch updates

```java
try (var insert = connection.prepareStatement(
        "INSERT INTO account(name, balance) VALUES (?, ?)")) {
    for (int index = 0; index < 100; index++) {
        insert.setString(1, "account-" + index);
        insert.setBigDecimal(2, java.math.BigDecimal.ZERO);
        insert.addBatch();
    }
    int[] counts = insert.executeBatch();
}
```

Use an explicit transaction when a batch must be atomic. A `BatchUpdateException` contains counts for operations completed before the failure.

## Timeouts, cancellation, and locking

`busy_timeout` controls how long SQLite waits to acquire a database lock. `Statement.setQueryTimeout` controls execution time in whole seconds and interrupts the active connection operation when the deadline expires.

```java
statement.setQueryTimeout(2);
try (var result = statement.executeQuery("""
        WITH RECURSIVE counter(value) AS (
            VALUES(0) UNION ALL
            SELECT value + 1 FROM counter WHERE value < 100000000
        )
        SELECT sum(value) FROM counter
        """)) {
    result.next();
}
```

`Statement.cancel()` also calls SQLite interrupt. Cancellation is connection-scoped at the native level, which is another reason not to run concurrent statements on one connection.

Relevant SQLStates:

| Condition | Exception category | SQLState |
| --- | --- | --- |
| busy or locked | `SQLTransientException` | `40001` |
| read-only write | `SQLNonTransientException` | `25006` |
| interrupted | `SQLNonTransientException` | `57014` |
| cannot open database | `SQLNonTransientConnectionException` | `08001` |
| constraint violation | `SQLIntegrityConstraintViolationException` | `23000` |
| query timeout | `SQLTimeoutException` | `HYT00` |

## Concurrency

The native library is compiled with `SQLITE_THREADSAFE=1`. The driver serializes access to each native database handle, but an application should still use one connection per concurrent unit of work. SQLite supports concurrent readers and one writer at a time.

A connection owns its statements, result sets, transaction state, and savepoints. Closing a connection closes all statements and result sets before closing the native database handle.

## Native library loading

The default classifier resource is extracted under:

```text
${java.io.tmpdir}/sqlite-jdbc/<driver-version>/<platform>/<sha256>/
```

Extraction uses a file lock and atomic replacement. Existing files are accepted only when size and SHA-256 match.

System properties:

| Property | Purpose |
| --- | --- |
| `sqlite.jdbc.library.path` | Absolute path to an externally supplied `sqlitejdbc` library |
| `sqlite.jdbc.native.dir` | Root directory for the extraction cache |
| `sqlite.jdbc.native.platform` | Override platform detection, primarily for controlled testing |

When no matching classifier or external library can be found, connection creation fails with SQLState `08001` and preserves the loader error as its cause.

## Unsupported JDBC features

The driver throws `SQLFeatureNotSupportedException` with SQLState `0A000` for features without a sound SQLite implementation:

- stored procedures and `CallableStatement`
- SQL `Array`, `Struct`, `Ref`, and user-defined types
- scrollable or updatable result sets
- multiple simultaneous results from one statement
- JDBC schemas, catalog switching, and metadata for attached databases beyond `main`
- XA and distributed transactions

## Troubleshooting

`IllegalCallerException: Illegal native access`:

Add the appropriate `--enable-native-access` option shown in Runtime setup.

`Could not initialize the SQLite native backend`:

Verify that the classifier matches the operating system, CPU architecture, and Linux libc. Check the nested cause for the attempted platform and library path.

`SQLTransientException` with SQLState `40001`:

Another connection holds a conflicting lock. Keep write transactions short, configure `busy_timeout`, and use `IMMEDIATE` when acquiring the write reservation early is preferable.

Changes are missing after process restart:

Confirm that the URL names a file rather than `:memory:` or an empty temporary database, and ensure manual transactions are committed.
