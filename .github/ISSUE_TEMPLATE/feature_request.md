---
name: 功能建议
about: 建议 JDK 25 FFM 或 JDBC 4.3 功能
title: ''
labels: triage
assignees: ''
---

## 使用场景

描述要解决的实际问题，而不仅是建议的 API 名称。

## 期望方案

说明期望的 Java API、JDBC 行为或 SQLite C API。新 API 应使用 `io.github.idoly.sqlite` 命名空间，并只面向 JDK 25+ / JDBC 4.3。

## SQLite 与 FFM 依据

如果涉及 SQLite 能力，请提供对应的 SQLite 官方 C API 或 SQL 文档链接；如果涉及 callback，请说明生命周期、线程和异常处理要求。

## 与 xerial 的关系

说明 xerial/sqlite-jdbc 是否已有类似功能，以及本项目需要进行哪些 FFM 适配。本项目不保持 `org.sqlite` 包兼容。

## 备选方案

列出已考虑的其他实现方式及其取舍。

## 补充信息

提供示例、性能数据或相关链接。
