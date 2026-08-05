# sqlite-jdbc

A JDK 25+ SQLite JDBC driver built directly on the Foreign Function and Memory API, with reproducible native builds and platform-specific Maven artifacts.

## Requirements

- JDK 25 or later; JDK 17-24 are not supported
- `--enable-native-access=ALL-UNNAMED` when running on the classpath
- `--enable-native-access=io.github.idoly.sqlite.ffm` when running on the module path
- One matching `sqlite-native` platform classifier, or an external library configured with `sqlite.jdbc.library.path`

## Maven

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

Available native classifiers:

```text
linux-x86_64-glibc
linux-aarch64-glibc
linux-x86_64-musl
linux-aarch64-musl
windows-x86_64
macos-x86_64
macos-aarch64
```

Native resources are extracted to a versioned SHA-256 cache below `java.io.tmpdir`. Override this with `sqlite.jdbc.native.dir`. To use a system-provided library instead, set `sqlite.jdbc.library.path` to its absolute path.

For JPMS applications, place `sqlite-driver` and `sqlite-ffm` on the module path and grant native access to `io.github.idoly.sqlite.ffm`. The resource-only native classifier may remain on the classpath. When placing it on the module path too, resolve its automatic module explicitly:

```text
--enable-native-access=io.github.idoly.sqlite.ffm
--add-modules=io.github.idoly.sqlite.nativelib
```

## Quick Start

```java
try (Connection connection = DriverManager.getConnection("jdbc:sqlite:./application.db");
        PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO account(name, balance) VALUES (?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
    statement.setString(1, "primary");
    statement.setBigDecimal(2, new BigDecimal("125.50"));
    statement.executeUpdate();

    try (ResultSet keys = statement.getGeneratedKeys()) {
        if (keys.next()) {
            long id = keys.getLong(1);
        }
    }
}
```

Connection properties can be supplied through `DriverManager`, or configured with the immutable builder:

```java
SQLiteConfig config = SQLiteConfig.builder()
        .busyTimeout(Duration.ofSeconds(5))
        .foreignKeys(true)
        .transactionMode(SQLiteTransactionMode.IMMEDIATE)
        .build();
SQLiteDataSource dataSource = new SQLiteDataSource("jdbc:sqlite:./application.db", config);
```

Equivalent JDBC properties are `busy_timeout`, `foreign_keys`, `read_only`, and `transaction_mode`.

## JDBC Support

Implemented:

- JDBC driver auto-discovery and `SQLiteDataSource`
- `Statement` and reusable `PreparedStatement`
- forward-only, read-only `ResultSet`
- INTEGER, REAL, TEXT, BLOB, NULL, LOB, SQLXML, and Java time conversions
- transactions, savepoints, generated row IDs, and batch updates
- query timeout, cancellation, busy timeout, and SQLite error classification
- result-set, parameter, and database metadata
- connection/statement/result-set close cascading
- read-only connections and foreign-key initialization

Explicitly unsupported because SQLite has no corresponding feature or this driver exposes a narrower contract:

- stored procedures and `CallableStatement`
- SQL `Array`, `Struct`, `Ref`, and user-defined types
- scrollable or updatable result sets
- multiple result sets from one statement
- XA and distributed transactions

## Build

The default Java build requires JDK 25 or later and emits Java 25 bytecode:

```bash
./mvnw --batch-mode --no-transfer-progress verify
```

Build the Linux native library with Podman or Docker:

```bash
podman build -f containers/build-linux-glibc/Containerfile -t sqlite-jdbc-native:glibc .
podman run --rm -v "$PWD/native-output:/output" sqlite-jdbc-native:glibc
```

Package and test the native classifier without an external library path:

```bash
./mvnw clean verify \
  -Dsqlite.native.platform=linux-x86_64-glibc \
  -Dsqlite.native.classifier=linux-x86_64-glibc \
  -Dsqlite.native.inputDirectory="$PWD/native-output/lib" \
  -Dsqlite.native.inputFile=libsqlitejdbc.so
```

Documentation:

- [Usage guide](docs/USAGE.md): setup, URLs, configuration, statements, types, transactions, timeouts, concurrency, native loading, and troubleshooting
- [Architecture](docs/ARCHITECTURE.md): module boundaries, ownership, design patterns, native distribution, and transaction semantics
- [Release guide](docs/RELEASING.md): signed multi-platform publication

## License

Licensed under the [Apache License 2.0](LICENSE).
