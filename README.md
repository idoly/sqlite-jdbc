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

### 完整示例

下面是可直接运行的 `Example.java`。程序创建数据库和表，在一个事务中批量写入两行数据，然后查询并输出全部记录：

```java
import io.github.idoly.sqlite.SQLiteConfig;
import io.github.idoly.sqlite.SQLiteDataSource;
import java.sql.SQLException;

public final class Example {
    private Example() {}

    public static void main(String[] args) throws SQLException {
        var config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        config.setBusyTimeout(5_000);

        var dataSource = new SQLiteDataSource(config);
        dataSource.setUrl("jdbc:sqlite:sample.db");

        try (var connection = dataSource.getConnection()) {
            try (var statement = connection.createStatement()) {
                statement.executeUpdate(
                        "create table if not exists item("
                                + "id integer primary key, value text not null)");
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
                try {
                    connection.rollback();
                } catch (SQLException rollbackError) {
                    error.addSuppressed(rollbackError);
                }
                throw error;
            }
            connection.setAutoCommit(true);

            try (var statement = connection.createStatement();
                    var result = statement.executeQuery(
                            "select id, value from item order by id")) {
                while (result.next()) {
                    System.out.printf("%d: %s%n", result.getLong(1), result.getString(2));
                }
            }
        }
    }
}
```

也可以使用标准 `DriverManager.getConnection("jdbc:sqlite:sample.db")` 创建连接。连接池管理器可使用 `io.github.idoly.sqlite.datasource.SQLiteConnectionPoolDataSource`；它实现 JDBC `ConnectionPoolDataSource`，业务代码应从池管理器借用并关闭逻辑 `Connection`。

## 构建

```shell
# 格式检查、编译和 JVM 测试
mvn spotless:check clean package

# GraalVM native-image 测试
mvn clean -Pnative integration-test

# 重新构建全部 8 个内置 SQLite 动态库，需要容器环境和 JDK 25
make native-all
```
