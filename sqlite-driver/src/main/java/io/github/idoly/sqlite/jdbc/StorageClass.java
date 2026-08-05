package io.github.idoly.sqlite.jdbc;

/** Runtime storage class reported by sqlite3_column_type. */
enum StorageClass {
    INTEGER(1),
    REAL(2),
    TEXT(3),
    BLOB(4),
    NULL(5);

    private final int code;

    StorageClass(int code) {
        this.code = code;
    }

    static StorageClass fromCode(int code) {
        for (StorageClass value : values()) {
            if (value.code == code) return value;
        }
        throw new IllegalArgumentException("Unknown SQLite storage class: " + code);
    }
}
