# 开发与贡献

## 项目定位

本仓库是基于 `xerial/sqlite-jdbc` JDBC 实现的 JDK 25 FFM 项目，不是 xerial 官方仓库。Java 命名空间已从 `org.sqlite` 迁移到 `io.github.idoly.sqlite`，Maven 坐标为 `io.github.idoly:sqlite-jdbc`；目标是在新命名空间下保留成熟 JDBC 行为，同时以标准 `java.lang.foreign` 替代 JNI。

与上游同步时应保留以下本项目约束：

- 最低和编译基线均为 JDK 25；
- 仅保留 FFM backend；
- 不重新引入 `NativeDB.c`、JNI headers 或双后端兼容层；
- 动态库只包含 SQLite amalgamation 和扩展，并导出 `sqlite3_*`；
- Android 不支持；
- 保持单 Maven 模块；
- 只支持 JDK 25+ 和 JDBC 4.3，不为 JDBC 3 或旧 JDK 增加兼容代码。

## 开发环境

- JDK 25
- Maven 3.9 或更高版本
- GNU Make
- C 编译器
- Perl
- curl
- unzip
- Docker（仅跨平台编译需要）

确认环境：

```shell
java -version
mvn -version
```

## 构建与测试

格式检查：

```shell
mvn --batch-mode --no-transfer-progress spotless:check
```

完整 JVM 测试：

```shell
mvn --batch-mode --no-transfer-progress test
```

使用指定 SQLite 库测试：

```shell
mvn --batch-mode --no-transfer-progress \
  -Dio.github.idoly.sqlite.ffm.lib.path=/absolute/path/to/libsqlite3.so \
  test
```

打包：

```shell
mvn --batch-mode --no-transfer-progress clean package
```

GraalVM native-image：

```shell
mvn --batch-mode --no-transfer-progress -Pnative integration-test
```

提交前至少执行：

```shell
mvn --batch-mode --no-transfer-progress spotless:check clean package
git diff --check
```

## FFM 实现约束

核心代码位于：

- `src/main/java/io/github/idoly/sqlite/core/FfmNative.java`
- `src/main/java/io/github/idoly/sqlite/core/NativeDB.java`

新增 downcall 时：

1. 使用 SQLite 官方 C API 和准确的 `FunctionDescriptor`；
2. 区分指针、`int`、`sqlite3_int64` 和 `double`；
3. 使用结构化 FFM API，不通过字符串拼装调用；
4. 将 SQLite 错误码映射为现有 `SQLiteException` 行为；
5. 添加覆盖成功、NULL、错误和关闭状态的测试；
6. 更新 GraalVM foreign reachability metadata。

新增 upcall 时：

1. callback stub 必须由连接级 shared `Arena` 持有；
2. Java callback 对象必须有强引用；
3. 替换或删除 callback 时先向 SQLite 注销；
4. 关闭连接时按“注销 callback、关闭数据库、关闭 Arena”的顺序处理；
5. Java 异常不能穿过 C ABI 边界，必须转换为 SQLite 错误结果。

## `jdbc3` 遗留层

`io.github.idoly.sqlite.internal` 是从 xerial 继承的历史实现层，不是本项目支持 JDBC 3 的声明。新代码只能面向 JDK 25 `java.sql` / JDBC 4.3，不得新增 JDBC 3 或旧 JDK 兼容分支，也不得让新公开 API 依赖 `jdbc3` 类型。

移除该遗留层时，应将仍被 `jdbc4` 继承的实现迁入内部基类，更新模块导出，并运行完整 JDBC、连接池和公开 API 测试。该重构不能通过简单删除包完成。

## 构建 SQLite 动态库

当前平台：

```shell
make native
```

所有支持平台：

```shell
make native-all
```

构建逻辑会下载 SQLite amalgamation，将 `src/main/ext` 扩展合并后生成动态库。它不编译 Java bridge。

Linux/ELF 验证：

```shell
nm -D target/sqlite-*/libsqlitejdbc.so | grep ' sqlite3_open_v2$'
```

应满足：

- 存在 `sqlite3_open_v2`、`sqlite3_prepare_v2` 等公开符号；
- 不存在 Java JNI 入口；
- 动态库架构与目标 JVM 一致；
- SQLite 编译选项与测试预期一致。

使用外部 amalgamation 或库：

```shell
make native \
  SQLITE_OBJ=/usr/local/lib/libsqlite3.so \
  SQLITE_HEADER=/usr/local/include/sqlite3.h
```

## 平台资源

仓库不接受旧 xerial JNI 二进制。平台资源通过 GitHub Actions 的 `Build Native` workflow 生成。更新资源后必须运行 JVM 和 native-image 测试。

Android target、Android classifier 和 Android cross-build 脚本不得重新加入，除非 Android Runtime 正式提供与本项目兼容的 JDK 25 FFM。

## 文档要求

涉及以下内容时同步更新 `README.adoc` 和 `USAGE.md`：

- JDK 或 JDBC 基线；
- JVM 启动参数；
- 动态库选择规则；
- 支持平台；
- xerial 上游差异；
- 新增或移除的 SQLite API。

面向本仓库用户的文档使用中文，只描述当前受支持实现；上游历史请直接查阅 xerial/sqlite-jdbc。

## 提交规范

使用 Conventional Commits：

```text
feat: 增加新的 SQLite FFM API
fix: 修复 callback 生命周期
build: 更新平台 SQLite 构建
Docs: 更新 FFM 使用说明
```

推荐使用小而完整的提交。不要把平台二进制更新、无关格式化和行为修改混在同一提交中。

## 提交 Pull Request

1. Fork `idoly/sqlite-jdbc`；
2. 基于 `main` 创建分支；
3. 完成实现、测试和中文文档；
4. 推送分支；
5. 向本仓库提交 Pull Request。

若改动源自 xerial 上游，请在 PR 中注明对应上游 commit 或 PR，并说明 FFM 适配内容。

## 发布

发布前必须确认：

- JDK 25 JVM 测试通过；
- GraalVM native-image 测试通过；
- 所有发布平台资源均由当前源码重新生成；
- `nm`/平台等价工具确认导出 SQLite ABI；
- 默认 JAR 不包含旧 JNI binary；
- README 中版本、平台和获取方式准确；
- 发布坐标为 `io.github.idoly:sqlite-jdbc`，不会覆盖或伪装成 xerial 的 `org.xerial:sqlite-jdbc`。
