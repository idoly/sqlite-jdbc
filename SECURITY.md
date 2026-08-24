# 安全策略

## 支持范围

| 版本 | 安全更新 |
|---|---|
| `main` 和最新发布版本 | 支持 |
| 旧版本 | 不支持 |
| xerial 官方版本 | 请向 xerial/sqlite-jdbc 报告 |

本项目仅支持 JDK 25+、JDBC 4.3 和 `io.github.idoly.sqlite` 命名空间。`org.xerial:sqlite-jdbc` / `org.sqlite` 属于 xerial 官方项目，不在本仓库的安全维护范围内。

## 报告漏洞

请通过 GitHub 的私有漏洞报告功能联系维护者，不要先创建公开 Issue：

https://github.com/idoly/sqlite-jdbc/security/advisories/new

报告应包括：

- 受影响的 commit 或版本；
- JDK 25 具体版本、操作系统和 CPU 架构；
- 使用的 SQLite 动态库版本及来源；
- 是否设置 `io.github.idoly.sqlite.ffm.lib.path`；
- 最小复现代码；
- 影响分析和可能的修复建议。

## FFM 与动态库安全

`--enable-native-access` 允许驱动调用本地代码。只加载可信 SQLite 动态库，并使用绝对路径配置 `io.github.idoly.sqlite.ffm.lib.path`。本项目不会验证外部库的签名或来源。

SQLite extension 与驱动具有相同的进程权限。除非确有需要，不要启用 extension loading；不要加载不可信 `.so`、`.dylib` 或 `.dll`。

## 与 xerial 的安全边界

本项目继承了 xerial 的大量 JDBC 代码，但使用不同的 Java 包名、Maven 坐标和 FFM backend。漏洞可能只影响其中一个项目。报告前请确认实际依赖：

```text
本项目：io.github.idoly:sqlite-jdbc / io.github.idoly.sqlite
xerial：org.xerial:sqlite-jdbc / org.sqlite
```
