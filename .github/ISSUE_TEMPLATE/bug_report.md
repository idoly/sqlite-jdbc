---
name: Bug 报告
about: 报告可复现的驱动问题
title: ''
labels: triage
assignees: ''
---

## 问题描述

清晰描述实际行为以及问题造成的影响。

## 最小复现

提供可以直接运行的最小 Java 代码、SQL 和数据库初始化步骤。请确认使用的是本项目 `io.github.idoly.sqlite`，而不是 xerial 的 `org.sqlite`。

## 预期行为

描述期望的 JDBC 或 SQLite 行为。

## 环境

- 本项目版本或 commit：
- JDK 版本（仅支持 25+）：
- 操作系统：
- CPU 架构：
- SQLite 版本：
- SQLite 库来源：系统 / 本项目构建 / `io.github.idoly.sqlite.ffm.lib.path`
- 运行方式：class path / module path / GraalVM native-image
- JVM 参数：

## 日志与异常

提供完整异常堆栈和相关日志。删除密码、数据库内容和本地敏感路径。

## 补充信息

说明该问题是否也能在 xerial 官方 `org.xerial:sqlite-jdbc` 中复现。两者 backend 和包名不同，请不要混用 JAR 或本地库。
