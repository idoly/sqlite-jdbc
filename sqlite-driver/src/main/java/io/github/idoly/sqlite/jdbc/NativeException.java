package io.github.idoly.sqlite.jdbc;

/** Failure reported by SQLite before it is mapped to a JDBC exception. */
final class NativeException extends RuntimeException {
    private final int resultCode;

    NativeException(String message, int resultCode) {
        super(message);
        this.resultCode = resultCode;
    }

    int resultCode() {
        return resultCode;
    }
}
