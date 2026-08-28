# SQLite JDBC

[![CI](https://github.com/idoly/sqlite-jdbc/actions/workflows/ci.yml/badge.svg)](https://github.com/idoly/sqlite-jdbc/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.idoly/sqlite-jdbc.svg)](https://central.sonatype.com/artifact/io.github.idoly/sqlite-jdbc)

基于 JDK Foreign Function & Memory API 的 SQLite JDBC 驱动。

## 使用

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
java --enable-native-access=ALL-UNNAMED SQLiteDemo

# module path
java --enable-native-access=io.github.idoly.sqlite ...
```

`SQLiteDemo.java`：

```java
import java.sql.DriverManager;

public final class SQLiteDemo {
    public static void main(String[] args) throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                var statement = connection.createStatement()) {
            statement.executeUpdate("create table item(id integer primary key, value text)");
            statement.executeUpdate("insert into item(value) values ('first'), ('second')");

            try (var result = statement.executeQuery("select id, value from item order by id")) {
                while (result.next()) {
                    System.out.printf("%d: %s%n", result.getLong(1), result.getString(2));
                }
            }
        }
    }
}
```

驱动通过 `ServiceLoader` 自动注册，无需调用 `Class.forName()`。

## URL

| 场景 | URL |
| --- | --- |
| 文件数据库 | `jdbc:sqlite:sample.db` |
| 内存数据库 | `jdbc:sqlite::memory:` |
| 共享内存数据库 | `jdbc:sqlite:file:shared?mode=memory&cache=shared` |
| 只读文件 | `jdbc:sqlite:file:sample.db?mode=ro` |

JAR 只加载内置 SQLite，不回退到系统库。内置库启用 SQLite 官方 math 和 percentile 函数，不包含历史非标准 SQL 扩展；自定义函数使用 `SQLiteFunction`。

## 构建

```shell
mvn spotless:check clean package
```

六个平台动态库由 [Build Native](https://github.com/idoly/sqlite-jdbc/actions/workflows/build-native.yml) 构建。FFM 绑定由 jextract 25 生成并提交源码；更新 SQLite C ABI 绑定时执行：

```shell
make generate-bindings JEXTRACT=/path/to/jextract
```
