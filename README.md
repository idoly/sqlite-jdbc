# SQLite JDBC

[![CI](https://github.com/idoly/sqlite-jdbc/actions/workflows/ci.yml/badge.svg)](https://github.com/idoly/sqlite-jdbc/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.idoly/sqlite-jdbc.svg)](https://central.sonatype.com/artifact/io.github.idoly/sqlite-jdbc)

使用 JDK Foreign Function & Memory API 直接调用 SQLite C ABI 的 JDBC 驱动，不包含 JNI，运行时无第三方依赖。

## 支持范围

| 项目 | 支持范围 |
| --- | --- |
| Java | JDK 25+ |
| JDBC | JDK 25：4.3；JDK 26+：4.5 |
| SQLite | 3.53.4 |
| Linux | glibc 2.28+、`x86_64` |
| macOS | 11+、`x86_64`、`aarch64` |
| Windows | `x86_64`、`aarch64` |
| JPMS | `io.github.idoly.sqlite` |

JAR 只加载当前平台的内置 SQLite，不回退到系统 SQLite。Linux musl 和 32 位系统不受支持。

## 引入

```xml
<dependency>
    <groupId>io.github.idoly</groupId>
    <artifactId>sqlite-jdbc</artifactId>
    <version>3.53.4.0</version>
</dependency>
```

运行时允许 native access：

```shell
# classpath
java --enable-native-access=ALL-UNNAMED ...

# module path
java --enable-native-access=io.github.idoly.sqlite ...
```

## 使用

`SQLiteDemo.java`：

```java
import java.sql.DriverManager;

public final class SQLiteDemo {
    private SQLiteDemo() {}

    public static void main(String[] args) throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            try (var statement = connection.createStatement()) {
                statement.executeUpdate(
                        "create table item(id integer primary key, value text not null)");
            }

            try (var statement =
                    connection.prepareStatement("insert into item(value) values (?)")) {
                statement.setString(1, "first");
                statement.addBatch();
                statement.setString(1, "second");
                statement.addBatch();
                statement.executeBatch();
            }

            try (var statement = connection.createStatement();
                    var result = statement.executeQuery("select id, value from item order by id")) {
                while (result.next()) {
                    System.out.printf("%d: %s%n", result.getLong(1), result.getString(2));
                }
            }
        }
    }
}
```

驱动通过 `ServiceLoader` 自动注册，无需调用 `Class.forName()`。

常用 URL：

| 场景 | URL |
| --- | --- |
| 文件数据库 | `jdbc:sqlite:sample.db` |
| 内存数据库 | `jdbc:sqlite::memory:` |
| 共享内存数据库 | `jdbc:sqlite:file:shared?mode=memory&cache=shared` |
| 只读文件 | `jdbc:sqlite:file:sample.db?mode=ro` |

## 构建

```shell
mvn spotless:check clean package
```

仓库内的五个平台动态库由 [Build Native](https://github.com/idoly/sqlite-jdbc/actions/workflows/build-native.yml) workflow 构建。使用本机工具链重建当前平台时执行：

```shell
make native
```
