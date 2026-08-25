package io.github.idoly.sqlite;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests User Defined Functions. */
public class UserDefinedFunctionErrorTest {
    private Connection conn;
    private Statement stat;

    @BeforeEach
    public void connect() throws Exception {
        conn = DriverManager.getConnection("jdbc:sqlite:");
        stat = conn.createStatement();
    }

    @AfterEach
    public void close() throws SQLException {
        stat.close();
        conn.close();
    }

    @Test
    public void customErr() throws SQLException {
        SQLiteFunction.create(
                conn,
                "f9",
                new SQLiteFunction() {
                    @Override
                    public void xFunc() throws SQLException {
                        throw new SQLException("myErr");
                    }
                });
        assertThatExceptionOfType(SQLException.class)
                .isThrownBy(() -> stat.executeQuery("select f9();"));
    }
}
