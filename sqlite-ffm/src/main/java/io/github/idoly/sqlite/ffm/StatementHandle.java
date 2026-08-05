package io.github.idoly.sqlite.ffm;

/**
 * Opaque sqlite3_stmt pointer that does not expose an FFM implementation type.
 *
 * @param address native address, or zero for a null handle
 */
public record StatementHandle(long address) {
    /** Null statement handle. */
    public static final StatementHandle NULL = new StatementHandle(0);

    /** Validates the native address. */
    public StatementHandle {
        if (address < 0) {
            throw new IllegalArgumentException("A native address cannot be negative");
        }
    }

    /** @return whether this handle represents a null pointer */
    public boolean isNull() {
        return address == 0;
    }
}
