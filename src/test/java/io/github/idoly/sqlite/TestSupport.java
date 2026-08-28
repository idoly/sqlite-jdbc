package io.github.idoly.sqlite;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class TestSupport {
    public static List<String> getCompileOptions(Connection connection) throws SQLException {
        List<String> compileOptions = new ArrayList<>();
        try (var statement = connection.createStatement();
                var result = statement.executeQuery("pragma compile_options")) {
            while (result.next()) {
                compileOptions.add(result.getString(1));
            }
        }
        return compileOptions;
    }
}
