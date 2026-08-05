package io.github.idoly.sqlite.jdbc;

/** SQLite BEGIN transaction mode used for JDBC manual-commit transactions. */
public enum SQLiteTransactionMode {
    /** Delay both the read and write transaction until required. */
    DEFERRED,
    /** Start a write transaction when the first statement executes. */
    IMMEDIATE,
    /** Acquire an exclusive transaction when the first statement executes. */
    EXCLUSIVE;

    String beginSql() {
        return "BEGIN " + name();
    }
}
