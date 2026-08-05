package io.github.idoly.sqlite.jpms;

import java.sql.DriverManager;

public final class Main {
    private Main() {}

    public static void main(String[] args) throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT 42")) {
            if (!result.next() || result.getInt(1) != 42 || result.next()) {
                throw new AssertionError("Unexpected JPMS query result");
            }
        }
    }
}
