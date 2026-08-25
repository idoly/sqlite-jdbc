# SQLite JDBC FFM

通过 Foreign Function & Memory API 直接调用 SQLite C ABI 的 JDBC 驱动，不包含 JNI，运行时无第三方依赖。

## 支持范围

| 项目 | 支持范围 |
| --- | --- |
| Java | JDK 25+；CI 验证 JDK 25、26 |
| JDBC | JDK 25：4.3；JDK 26+：4.5 |
| SQLite | 3.53.4 |
| Linux | `x86_64` |
| macOS | `x86_64`、`aarch64` |
| Windows | `x86_64`、`aarch64` |
| JPMS 模块 | `io.github.idoly.sqlite` |

JAR 只加载当前平台的内置 SQLite 动态库，不搜索或回退到系统 SQLite。平台不受支持或资源缺失时，连接会明确失败。

## 引入

```xml
<dependency>
    <groupId>io.github.idoly</groupId>
    <artifactId>sqlite-jdbc</artifactId>
    <version>3.53.4.0</version>
</dependency>
```

应用启动时必须允许驱动访问 native API：

```shell
# classpath
java --enable-native-access=ALL-UNNAMED ...

# module path
java --enable-native-access=io.github.idoly.sqlite ...
```

## 使用

驱动使用 `jdbc:sqlite:` URL，并通过 `ServiceLoader` 自动注册，无需调用 `Class.forName()`。

以下 `Example.java` 会创建 `sample.db` 和数据表，在一个事务中批量写入两行数据，然后查询并输出全部记录：

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

也可以通过标准 JDBC 的 `DriverManager.getConnection("jdbc:sqlite:sample.db")` 创建连接。

其他常用 URL：

| 场景 | URL |
| --- | --- |
| 临时内存数据库 | `jdbc:sqlite::memory:` |
| 共享内存数据库 | `jdbc:sqlite:file:shared?mode=memory&cache=shared` |
| 只读文件 | `jdbc:sqlite:file:sample.db?mode=ro` |

每个 `jdbc:sqlite::memory:` 连接都有独立数据库。多个连接共享内存数据库时，应使用带固定名称和 `cache=shared` 的 SQLite URI，并至少保持一个连接存活。

连接池管理器可使用 `io.github.idoly.sqlite.datasource.SQLiteConnectionPoolDataSource` 创建 `PooledConnection`；业务代码应借用并关闭逻辑 `Connection`。

## 构建

native-image 测试需要 GraalVM，重建内置动态库还需要 Docker 或 Podman。

```shell
mvn spotless:check clean package
mvn clean -Pnative integration-test
make native-all
```
