package io.github.idoly.sqlite.ffm;

import java.util.Objects;

/**
 * Raw result from sqlite3_prepare_v3.
 *
 * @param resultCode SQLite result code
 * @param statement returned statement handle
 */
public record PrepareResult(int resultCode, StatementHandle statement) {
    /** Validates the returned handle. */
    public PrepareResult {
        Objects.requireNonNull(statement, "statement");
    }
}
