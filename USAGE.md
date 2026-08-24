# 使用说明

本文档描述 `idoly/sqlite-jdbc` 的 JDK 25 FFM 实现。xerial 官方 Maven Central 版本仍是独立项目，不应将两者的本地库或运行参数混用。

## 环境要求

- JDK 25 或更高版本
- JDK 25 `java.sql` 提供的最新 JDBC 标准：JDBC 4.3
- Maven 3.9 或更高版本（仅构建时需要）
- SQLite 动态库
- JVM native access 权限

本项目不支持 JDBC 3。源码中的 `io.github.idoly.sqlite.internal` 只是从 xerial 继承的内部历史分层，不能据此推断兼容旧 JDBC。应用只应使用标准 `java.sql` / `javax.sql`、`io.github.idoly.sqlite` 和 `io.github.idoly.sqlite.jdbc4`。JDBC 4.3 是当前 JDBC 规范版本；SQLite SQL 语法则由加载的 SQLite 版本决定。

## 获取驱动

当前 FFM 版本从本仓库构建：

```shell
git clone https://github.com/idoly/sqlite-jdbc.git
cd sqlite-jdbc
mvn clean package
```

生成的主 JAR 位于：

```text
target/sqlite-jdbc-3.53.2.2-SNAPSHOT.jar
```

不要用 xerial 官方的 `org.xerial:sqlite-jdbc` JAR 替代该文件。它不包含本仓库的 FFM 实现，Java 包名也是 `org.sqlite`；本项目使用 `io.github.idoly:sqlite-jdbc` 和 `io.github.idoly.sqlite`。

## 启动参数

类路径：

```shell
java --enable-native-access=ALL-UNNAMED \
  -cp "target/sqlite-jdbc-3.53.2.2-SNAPSHOT.jar:slf4j-api.jar:." \
  Sample
```

模块路径：

```shell
java --enable-native-access=io.github.idoly.sqlitejdbc \
  --module-path target/sqlite-jdbc-3.53.2.2-SNAPSHOT.jar:slf4j-api.jar \
  --module your.module/your.Main
```

Windows 类路径分隔符使用 `;`。

## 建立连接

内存数据库：

```java
try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
    // 使用 connection
}
```

文件数据库：

```java
try (Connection connection = DriverManager.getConnection("jdbc:sqlite:data/app.db")) {
    // 使用 connection
}
```

只读连接：

```java
SQLiteConfig config = new SQLiteConfig();
config.setReadOnly(true);
try (Connection connection = DriverManager.getConnection(
        "jdbc:sqlite:data/app.db", config.toProperties())) {
    // 只读操作
}
```

URI 参数：

```java
String url = "jdbc:sqlite:data/app.db?foreign_keys=on&busy_timeout=5000";
try (Connection connection = DriverManager.getConnection(url)) {
    // 使用 connection
}
```

## SQLite 动态库

驱动按以下顺序查找动态库：

1. `io.github.idoly.sqlite.ffm.lib.path` 指定的完整路径。
2. JAR 中当前操作系统和架构对应的资源。
3. 系统 SQLite。

指定完整路径：

```shell
java --enable-native-access=ALL-UNNAMED \
  -Dio.github.idoly.sqlite.ffm.lib.path=/opt/sqlite/lib/libsqlite3.so \
  -cp "sqlite-jdbc.jar:slf4j-api.jar:." \
  Sample
```

Windows 示例：

```text
-Dio.github.idoly.sqlite.ffm.lib.path=C:\sqlite\sqlite3.dll
```

本项目不支持以下旧参数：

```text
org.sqlite.lib.path
org.sqlite.lib.name
org.sqlite.tmpdir
```

也不支持 xerial 旧 JNI `libsqlitejdbc` 二进制。自定义库必须导出标准 `sqlite3_*` 符号，并与当前 JVM 的操作系统和 CPU 架构一致。

## Statement 和 PreparedStatement

```java
try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        Statement statement = connection.createStatement()) {
    statement.executeUpdate("create table person(id integer primary key, name text)");
}
```

```java
try (PreparedStatement statement = connection.prepareStatement(
        "insert into person(id, name) values(?, ?)")) {
    statement.setLong(1, 1L);
    statement.setString(2, "Alice");
    statement.executeUpdate();
}
```

查询：

```java
try (PreparedStatement statement = connection.prepareStatement(
        "select id, name from person where id >= ?")) {
    statement.setLong(1, 1L);
    try (ResultSet result = statement.executeQuery()) {
        while (result.next()) {
            long id = result.getLong("id");
            String name = result.getString("name");
        }
    }
}
```

## 事务

```java
connection.setAutoCommit(false);
try {
    // 执行多条 SQL
    connection.commit();
} catch (SQLException error) {
    connection.rollback();
    throw error;
}
```

可通过 `SQLiteConfig#setTransactionMode` 设置事务模式。

## SQLiteConfig

```java
SQLiteConfig config = new SQLiteConfig();
config.enforceForeignKeys(true);
config.setBusyTimeout(5_000);
config.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);
config.setJournalMode(SQLiteConfig.JournalMode.WAL);

try (Connection connection = DriverManager.getConnection(
        "jdbc:sqlite:data/app.db", config.toProperties())) {
    // 使用连接
}
```

`SQLiteConfig`、`SQLiteDataSource` 和连接池行为继承自 xerial，但类型已迁移到 `io.github.idoly.sqlite` 命名空间。

## 自定义函数

标量函数：

```java
Function.create(connection, "add_values", new Function() {
    @Override
    protected void xFunc() throws SQLException {
        result(value_int(0) + value_int(1));
    }
});
```

Aggregate、Window Function、Collation、BusyHandler 和 ProgressHandler 通过迁移到 `io.github.idoly.sqlite` 的 API 注册。底层实现使用 FFM upcall；连接关闭时自动注销 callback 并释放 shared Arena。

## Backup 和 Restore

驱动继承 xerial 的 backup/restore 行为，Java API 已迁移到 `io.github.idoly.sqlite`。Backup 支持内存数据库与文件数据库之间复制，并使用 `sqlite3_backup_*` 实现。

```java
try (Statement statement = connection.createStatement()) {
    statement.executeUpdate("backup to backup.db");
}
```

恢复：

```java
try (Statement statement = connection.createStatement()) {
    statement.executeUpdate("restore from backup.db");
}
```

## Serialize 和 Deserialize

支持 SQLite `sqlite3_serialize` / `sqlite3_deserialize`。该能力要求动态库提供对应符号。

## 加载 SQLite 扩展

```java
SQLiteConfig config = new SQLiteConfig();
config.enableLoadExtension(true);
```

之后可执行：

```sql
select load_extension('/absolute/path/to/extension');
```

只加载可信扩展。扩展必须与当前 SQLite ABI、操作系统和架构匹配。

## 加密数据库

本项目不内置 SQLCipher 或其他加密实现。需要使用兼容 SQLite 公开 ABI 的自定义动态库：

```shell
-Dio.github.idoly.sqlite.ffm.lib.path=/absolute/path/to/libsqlcipher.so
```

是否支持 `PRAGMA key`、`PRAGMA hexkey` 等语法由所选动态库决定。

## 多 classloader

FFM 不受 JNI “同一动态库不能被多个 classloader 重复加载”的限制。驱动测试覆盖多个隔离 classloader；每个 classloader 维护自己的 Java symbol table，底层 SQLite 动态库由系统加载器管理。

## GraalVM native-image

项目提供 FFM reachability metadata，并启用 shared Arena：

```shell
mvn -Pnative integration-test
```

使用外部 SQLite 时，在运行 native image 时设置：

```text
-Dio.github.idoly.sqlite.ffm.lib.path=/absolute/path/to/libsqlite3.so
```

## Android

不支持 Android。Android Runtime 不提供 JDK 25 `java.lang.foreign`，因此本项目不生成 Android artifact。

## 常见错误

### `IllegalCallerException` 或 native access 警告

缺少：

```text
--enable-native-access=ALL-UNNAMED
```

模块路径使用：

```text
--enable-native-access=io.github.idoly.sqlitejdbc
```

### `UnsatisfiedLinkError` 或找不到 `sqlite3_*`

检查：

- 动态库路径是否为完整路径；
- 库架构是否与 JVM 一致；
- 库是否导出标准 SQLite API；
- 是否错误使用了 xerial 的旧 JNI `libsqlitejdbc`。

Linux 可检查：

```shell
nm -D /path/to/libsqlite3.so | grep ' sqlite3_open_v2$'
```

### 找不到系统 SQLite

显式设置：

```text
-Dio.github.idoly.sqlite.ffm.lib.path=/absolute/path/to/library
```
