# SQLite JDBC FFM

面向 JDK 25+ 的 SQLite JDBC 4.3 驱动。驱动通过 Foreign Function & Memory API 直接调用 SQLite C ABI，不包含 JNI，运行时无第三方依赖。

## 支持范围

| 项目 | 支持范围 |
| --- | --- |
| Java | JDK 25+ |
| JDBC | JDBC 4.3 |
| SQLite | 3.53.4 |
| Linux | glibc、musl；`x86_64`、`aarch64` |
| macOS | `x86_64`、`aarch64` |
| Windows | `x86_64`、`aarch64` |
| JPMS 模块 | `io.github.idoly.sqlite` |

JAR 只加载与当前平台匹配的内置 SQLite 动态库，不搜索或回退到系统 SQLite。平台不受支持或资源缺失时，连接会明确失败。

## 引入

Maven 坐标：

```xml
<dependency>
    <groupId>io.github.idoly</groupId>
    <artifactId>sqlite-jdbc</artifactId>
    <version>3.53.4.0-SNAPSHOT</version>
</dependency>
```

当前版本是 SNAPSHOT。从源码使用时，先执行 `mvn clean install` 将其安装到本地 Maven 仓库。

应用必须允许驱动访问 native API：

```shell
# classpath
java --enable-native-access=ALL-UNNAMED ...

# module path
java --enable-native-access=io.github.idoly.sqlite ...
```

## 完整示例

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

## 连接

驱动保留 `jdbc:sqlite:` URL，并通过 `ServiceLoader` 自动注册，无需调用 `Class.forName()`。

| 场景 | URL |
| --- | --- |
| 数据库文件 | `jdbc:sqlite:sample.db` |
| 临时内存数据库 | `jdbc:sqlite::memory:` |
| 共享内存数据库 | `jdbc:sqlite:file:shared?mode=memory&cache=shared` |
| 只读文件 | `jdbc:sqlite:file:sample.db?mode=ro` |

每个 `jdbc:sqlite::memory:` 连接都有独立数据库。多个连接共享内存数据库时，应使用带固定名称和 `cache=shared` 的 SQLite URI，并至少保持一个连接存活。

可用的连接入口：

- 标准 JDBC：`DriverManager.getConnection("jdbc:sqlite:sample.db")`
- 可配置数据源：`io.github.idoly.sqlite.SQLiteDataSource`
- 连接池数据源：`io.github.idoly.sqlite.datasource.SQLiteConnectionPoolDataSource`

`SQLiteConnectionPoolDataSource` 实现 JDBC `ConnectionPoolDataSource`，供连接池管理器创建 `PooledConnection`。业务代码应从池管理器借用并关闭逻辑 `Connection`。

## 构建

普通构建需要 JDK 25：

```shell
mvn spotless:check clean package
```

GraalVM native-image 测试需要 GraalVM for JDK 25：

```shell
mvn clean -Pnative integration-test
```

重新构建全部 8 个内置 SQLite 动态库需要 JDK 25，以及 Docker 或 Podman：

```shell
make native-all
```
