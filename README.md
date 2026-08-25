# SQLite JDBC FFM

基于 JDK 25 Foreign Function & Memory API 的 SQLite JDBC 4.3 驱动。直接调用 SQLite C ABI，不包含 JNI，运行时无第三方依赖。

## 环境

- JDK 25+
- Linux glibc/musl、macOS 或 Windows
- `x86_64` 或 `aarch64`

JAR 内置 SQLite 3.53.4，只加载与当前平台匹配的内置动态库，不搜索或回退到系统 SQLite。平台不受支持或资源缺失时，连接会明确失败。

## 依赖

```xml
<dependency>
    <groupId>io.github.idoly</groupId>
    <artifactId>sqlite-jdbc</artifactId>
    <version>3.53.4.0-SNAPSHOT</version>
</dependency>
```

允许应用访问 native API：

```shell
# classpath
java --enable-native-access=ALL-UNNAMED ...

# module path，模块名为 io.github.idoly.sqlite
java --enable-native-access=io.github.idoly.sqlite ...
```

## 使用

驱动通过 `ServiceLoader` 自动注册，无需调用 `Class.forName()`。

常用 URL：

| 场景 | URL |
| --- | --- |
| 数据库文件 | `jdbc:sqlite:sample.db` |
| 临时内存数据库 | `jdbc:sqlite::memory:` |
| 共享内存数据库 | `jdbc:sqlite:file:shared?mode=memory&cache=shared` |
| 只读文件 | `jdbc:sqlite:file:sample.db?mode=ro` |

每个 `jdbc:sqlite::memory:` 连接都有独立数据库。需要多个连接共享内存数据库时，应使用带固定名称和 `cache=shared` 的 SQLite URI，并至少保持一个连接存活。

### DriverManager

下面的事务要么完整提交两行数据，要么在失败时回滚：

```java
import java.sql.DriverManager;
import java.sql.SQLException;

try (var connection = DriverManager.getConnection("jdbc:sqlite:sample.db")) {
    try (var statement = connection.createStatement()) {
        statement.executeUpdate(
                "create table if not exists item(id integer primary key, value text)");
    }

    connection.setAutoCommit(false);
    try (var statement = connection.prepareStatement(
            "insert into item(value) values (?)")) {
        statement.setString(1, "first");
        statement.addBatch();
        statement.setString(1, "second");
        statement.addBatch();
        statement.executeBatch();
        connection.commit();
    } catch (SQLException error) {
        connection.rollback();
        throw error;
    }
}
```

### DataSource

使用 `SQLiteConfig` 集中设置连接参数。配置会在创建连接时应用：

```java
import io.github.idoly.sqlite.SQLiteConfig;
import io.github.idoly.sqlite.SQLiteDataSource;

var config = new SQLiteConfig();
config.enforceForeignKeys(true);
config.setJournalMode(SQLiteConfig.JournalMode.WAL);
config.setBusyTimeout(5_000);

var dataSource = new SQLiteDataSource(config);
dataSource.setUrl("jdbc:sqlite:sample.db");

try (var connection = dataSource.getConnection();
     var statement = connection.createStatement();
     var result = statement.executeQuery("select sqlite_version()")) {
    result.next();
    System.out.println(result.getString(1));
}
```

连接池管理器可使用 `io.github.idoly.sqlite.datasource.SQLiteConnectionPoolDataSource`。它实现 JDBC `ConnectionPoolDataSource`，负责创建 `PooledConnection`；业务代码仍应从池管理器借用并关闭逻辑 `Connection`。

## 构建

```shell
# 格式检查、编译和 JVM 测试
mvn spotless:check clean package

# GraalVM native-image 测试
mvn clean -Pnative integration-test

# 重新构建全部 8 个内置 SQLite 动态库，需要容器环境和 JDK 25
make native-all
```
