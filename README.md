# SQLite JDBC

[![CI](https://github.com/idoly/sqlite-jdbc/actions/workflows/ci.yml/badge.svg)](https://github.com/idoly/sqlite-jdbc/actions/workflows/ci.yml)
[![Build Native](https://github.com/idoly/sqlite-jdbc/actions/workflows/build-native.yml/badge.svg)](https://github.com/idoly/sqlite-jdbc/actions/workflows/build-native.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.idoly/sqlite-jdbc.svg)](https://central.sonatype.com/artifact/io.github.idoly/sqlite-jdbc)

基于 JDK Foreign Function & Memory API 的 SQLite JDBC 驱动，直接调用 SQLite C ABI，不包含 JNI，运行时无第三方依赖。

## 特性

- 使用 `java.lang.foreign` 调用内置 SQLite 3.53.4。
- 保持标准 `jdbc:sqlite:` URL 和 JDBC `ServiceLoader` 自动注册。
- 只加载 JAR 内当前平台的动态库，不搜索或回退到系统 SQLite。
- 支持事务、savepoint、batch、metadata、BLOB、backup、serialize、回调和用户自定义函数。
- 提供 `DataSource`、`ConnectionPoolDataSource` 和 JPMS 模块。
- 同一 JAR 在 JDK 25、26 上验证。

## 支持范围

| 项目 | 支持范围 |
| --- | --- |
| Java | JDK 25+；CI 验证 JDK 25、26 |
| JDBC | JDK 25：4.3；JDK 26+：4.5 |
| SQLite | 3.53.4 |
| Linux | glibc 2.28+、`x86_64` |
| macOS | 11+、`x86_64`、`aarch64` |
| Windows | `x86_64`、`aarch64` |
| JPMS 模块 | `io.github.idoly.sqlite` |

Linux musl、32 位系统和表格之外的平台不受支持。平台不匹配或内置资源缺失时，连接会明确失败。

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

模块化应用使用 `requires io.github.idoly.sqlite;`。

## 示例

下面的 `Example.java` 创建数据库和表，在事务中批量写入数据，然后查询全部记录：

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
            try (var statement =
                    connection.prepareStatement("insert into item(value) values (?)")) {
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
                    var result =
                            statement.executeQuery("select id, value from item order by id")) {
                while (result.next()) {
                    System.out.printf("%d: %s%n", result.getLong(1), result.getString(2));
                }
            }
        }
    }
}
```

也可以通过标准 JDBC 的 `DriverManager.getConnection("jdbc:sqlite:sample.db")` 创建连接，无需调用 `Class.forName()`。

## URL

| 场景 | URL |
| --- | --- |
| 文件数据库 | `jdbc:sqlite:sample.db` |
| 临时内存数据库 | `jdbc:sqlite::memory:` |
| 共享内存数据库 | `jdbc:sqlite:file:shared?mode=memory&cache=shared` |
| 只读文件 | `jdbc:sqlite:file:sample.db?mode=ro` |

每个 `jdbc:sqlite::memory:` 连接都有独立数据库。共享内存数据库必须使用固定名称和 `cache=shared`，并至少保持一个连接存活。

连接池管理器可通过 `io.github.idoly.sqlite.datasource.SQLiteConnectionPoolDataSource` 创建 `PooledConnection`。业务代码应借用并关闭逻辑 `Connection`，由池管理物理连接。

## 构建

普通 Maven 构建直接使用仓库内已验证的五个平台动态库，不会重建 native 库，也不需要容器或交叉编译工具链：

```shell
mvn spotless:check clean package
```

GraalVM native-image 测试：

```shell
mvn clean -Pnative integration-test
```

使用本机工具链重建当前平台的 SQLite：

```shell
make native
```

全部五个平台的动态库由 [Build Native](https://github.com/idoly/sqlite-jdbc/actions/workflows/build-native.yml) workflow 构建并提交：

- Linux 使用 AlmaLinux 8，保持 glibc 2.28 基线。
- macOS 使用 GitHub macOS runner 的 Apple clang。
- Windows 使用固定版本并校验 SHA-256 的 llvm-mingw。

SQLite 源码版本和 SHA-256 固定在 [`VERSION`](VERSION)，构建参数集中在 [`Makefile`](Makefile)。

## 发布

- [Maven Central](https://central.sonatype.com/artifact/io.github.idoly/sqlite-jdbc)
- [GitHub Releases](https://github.com/idoly/sqlite-jdbc/releases)
