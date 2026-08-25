package io.github.idoly.sqlite;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests User Defined Collations. */
public class SQLiteCollationTest {
    private Connection conn;
    private Statement stat;

    private static String valStr1 = "";
    private static String valStr2 = "";

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
    public void reverseCollation() throws SQLException {
        ArrayList<String> received = new ArrayList<>();
        SQLiteCollation.create(
                conn,
                "REVERSE",
                new SQLiteCollation() {
                    @Override
                    protected int xCompare(String str1, String str2) {
                        received.add(str1);
                        received.add(str2);
                        return str1.compareTo(str2) * -1;
                    }
                });
        stat.executeUpdate("create table t (c1);");
        stat.executeUpdate("insert into t values ('aaa');");
        stat.executeUpdate("insert into t values ('aba');");
        stat.executeUpdate("insert into t values ('aca');");
        ResultSet rs = stat.executeQuery("select c1 from t order by c1 collate REVERSE;");
        assertThat(rs.next()).isTrue();
        assertThat(rs.getString(1)).isEqualTo("aca");
        assertThat(rs.next()).isTrue();
        assertThat(rs.getString(1)).isEqualTo("aba");
        assertThat(rs.next()).isTrue();
        assertThat(rs.getString(1)).isEqualTo("aaa");

        String[] expected = {"aba", "aca", "aaa"};
        assertThat(received.stream().distinct().sorted().toArray())
                .isEqualTo(Arrays.stream(expected).distinct().sorted().toArray());
    }

    @Test
    public void unicodeCollation() throws SQLException {
        ArrayList<String> received = new ArrayList<>();
        SQLiteCollation.create(
                conn,
                "UNICODE",
                new SQLiteCollation() {
                    @Override
                    protected int xCompare(String str1, String str2) {
                        received.add(str1);
                        received.add(str2);

                        Collator collator = Collator.getInstance();
                        collator.setDecomposition(Collator.TERTIARY);
                        collator.setStrength(Collator.CANONICAL_DECOMPOSITION);

                        return collator.compare(str1, str2);
                    }
                });
        stat.executeUpdate("create table t (c1);");
        stat.executeUpdate("insert into t values ('aec');");
        stat.executeUpdate("insert into t values ('aea');");
        stat.executeUpdate("insert into t values ('aéb');");
        ResultSet rs = stat.executeQuery("select c1 from t order by c1 collate UNICODE;");
        assertThat(rs.next()).isTrue();
        assertThat(rs.getString(1)).isEqualTo("aea");
        assertThat(rs.next()).isTrue();
        assertThat(rs.getString(1)).isEqualTo("aéb");
        assertThat(rs.next()).isTrue();
        assertThat(rs.getString(1)).isEqualTo("aec");

        String[] expected = {"aea", "aéb", "aec"};
        assertThat(received.stream().distinct().sorted().toArray())
                .isEqualTo(Arrays.stream(expected).distinct().sorted().toArray());
    }

    @Test
    public void twoCollationsNoConflict() throws SQLException {
        SQLiteCollation.create(
                conn,
                "REVERSE",
                new SQLiteCollation() {
                    @Override
                    protected int xCompare(String str1, String str2) {
                        return str1.compareTo(str2) * -1;
                    }
                });
        SQLiteCollation.create(
                conn,
                "NORMAL",
                new SQLiteCollation() {
                    @Override
                    protected int xCompare(String str1, String str2) {
                        return str1.compareTo(str2);
                    }
                });

        stat.executeUpdate("create table t (c1);");
        stat.executeUpdate("insert into t values ('a');");
        stat.executeUpdate("insert into t values ('b');");
        stat.executeUpdate("insert into t values ('c');");

        ResultSet rs = stat.executeQuery("select c1 from t order by c1 collate REVERSE;");
        assertThat(rs.next()).isTrue();
        assertThat(rs.getString(1)).isEqualTo("c");
        assertThat(rs.next()).isTrue();
        assertThat(rs.getString(1)).isEqualTo("b");
        assertThat(rs.next()).isTrue();
        assertThat(rs.getString(1)).isEqualTo("a");

        ResultSet rs2 = stat.executeQuery("select c1 from t order by c1 collate NORMAL;");
        assertThat(rs2.next()).isTrue();
        assertThat(rs2.getString(1)).isEqualTo("a");
        assertThat(rs2.next()).isTrue();
        assertThat(rs2.getString(1)).isEqualTo("b");
        assertThat(rs2.next()).isTrue();
        assertThat(rs2.getString(1)).isEqualTo("c");

        ResultSet rs3 = stat.executeQuery("select c1 from t order by c1 collate REVERSE;");
        assertThat(rs3.next()).isTrue();
        assertThat(rs3.getString(1)).isEqualTo("c");
        assertThat(rs3.next()).isTrue();
        assertThat(rs3.getString(1)).isEqualTo("b");
        assertThat(rs3.next()).isTrue();
        assertThat(rs3.getString(1)).isEqualTo("a");
    }

    @Test
    public void validateSpecialCharactersAreCorrectlyPassedToJava() throws SQLException {
        ArrayList<String> received = new ArrayList<>();
        SQLiteCollation.create(
                conn,
                "UNICODE",
                new SQLiteCollation() {
                    @Override
                    protected int xCompare(String str1, String str2) {
                        received.add(str1);
                        received.add(str2);

                        Collator collator = Collator.getInstance();
                        collator.setDecomposition(Collator.TERTIARY);
                        collator.setStrength(Collator.CANONICAL_DECOMPOSITION);

                        return collator.compare(str1, str2);
                    }
                });
        stat.executeUpdate("create table t (c1);");
        stat.executeUpdate("insert into t values ('😀');");
        stat.executeUpdate("insert into t values ('おはよう');");
        stat.executeUpdate("insert into t values ('你好');");
        stat.executeUpdate("insert into t values ('안녕하세요');");
        stat.executeQuery("select c1 from t order by c1 collate UNICODE;");

        String[] expected = {"😀", "おはよう", "你好", "안녕하세요"};

        assertThat(received.stream().distinct().sorted().toArray())
                .isEqualTo(Arrays.stream(expected).distinct().sorted().toArray());
    }

    @Test
    public void destroy() throws SQLException {
        SQLiteCollation.create(
                conn,
                "c1",
                new SQLiteCollation() {
                    @Override
                    protected int xCompare(String str1, String str2) {
                        valStr1 = str1;
                        valStr2 = str2;
                        return str1.compareTo(str2) * -1;
                    }
                });
        stat.executeUpdate("create table t (c1);");
        stat.executeUpdate("insert into t values ('a');");
        stat.executeUpdate("insert into t values ('b');");
        stat.executeQuery("select c1 from t order by c1 collate c1;");

        assertThat(valStr1).isEqualTo("a");
        assertThat(valStr2).isEqualTo("b");

        SQLiteCollation.destroy(conn, "c1");
        SQLiteCollation.destroy(conn, "c1");
    }
}
