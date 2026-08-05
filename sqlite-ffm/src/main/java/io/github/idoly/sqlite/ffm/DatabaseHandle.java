package io.github.idoly.sqlite.ffm;

/**
 * Opaque sqlite3 pointer that does not expose an FFM implementation type.
 *
 * @param address native address, or zero for a null handle
 */
public record DatabaseHandle(long address) {
    /** Null database handle. */
    public static final DatabaseHandle NULL = new DatabaseHandle(0);

    /** Validates the native address. */
    public DatabaseHandle {
        if (address < 0) {
            throw new IllegalArgumentException("A native address cannot be negative");
        }
    }

    /** @return whether this handle represents a null pointer */
    public boolean isNull() {
        return address == 0;
    }
}
