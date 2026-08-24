# SQLite JDBC FFM

JDK 25+ 的 SQLite JDBC 4.3 驱动。

## 依赖

```xml
<dependency>
    <groupId>io.github.idoly</groupId>
    <artifactId>sqlite-jdbc</artifactId>
    <version>3.53.2.2-SNAPSHOT</version>
</dependency>
```

## 构建

```shell
mvn clean package
```

## 使用

```java
import java.sql.DriverManager;

try (var connection = DriverManager.getConnection("jdbc:sqlite:sample.db");
     var statement = connection.createStatement()) {
    statement.executeUpdate("create table item(id integer primary key, value text)");
}
```

驱动只加载 JAR 内置的 SQLite 3.53.2 动态库，不使用系统 SQLite。支持 Linux glibc/musl、macOS 和 Windows 的 `x86_64` 与 `aarch64`。

classpath：

```shell
--enable-native-access=ALL-UNNAMED
```

module path：

```shell
--enable-native-access=io.github.idoly.sqlitejdbc
```

## 许可

Java 代码使用 Apache License 2.0，SQLite 为 public domain。第三方归属和许可已合并到 [LICENSE](LICENSE)。
