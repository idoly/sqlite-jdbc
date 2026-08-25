package io.github.idoly.sqlite;

import java.sql.SQLException;

public class SQLiteException extends SQLException {
    private SQLiteErrorCode resultCode;

    public SQLiteException(String message, SQLiteErrorCode resultCode) {
        super(message, null, resultCode.code() & 0xff);
        this.resultCode = resultCode;
    }

    public SQLiteErrorCode getResultCode() {
        return resultCode;
    }
}
