package io.github.idoly.sqlite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests User Defined Functions. */
public class UserDefinedFunctionTest {
    private static int val = 0;
    private static final byte[] b1 = new byte[] {2, 5, -4, 8, -1, 3, -5};
    private static int gotTrigger = 0;

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
    public void calling() throws SQLException {
        SQLiteFunction.create(
                conn,
                "f1",
                new SQLiteFunction() {
                    @Override
                    public void xFunc() {
                        val = 4;
                    }
                });
        stat.executeQuery("select f1();").close();
        assertThat(val).isEqualTo(4);
    }

    @Test
    public void returning() throws SQLException {
        SQLiteFunction.create(
                conn,
                "f2",
                new SQLiteFunction() {
                    @Override
                    public void xFunc() throws SQLException {
                        result(4);
                    }
                });
        ResultSet rs = stat.executeQuery("select f2();");
        assertThat(rs.next()).isTrue();
        assertThat(rs.getInt(1)).isEqualTo(4);
        rs.close();

        for (int i = 0; i < 20; i++) {
            rs = stat.executeQuery("select (f2() + " + i + ");");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(4 + i);
            rs.close();
        }
    }

    @Test
    public void accessArgs() throws SQLException {
        SQLiteFunction.create(
                conn,
                "f3",
                new SQLiteFunction() {
                    @Override
                    public void xFunc() throws SQLException {
                        result(value_int(0));
                    }
                });
        for (int i = 0; i < 15; i++) {
            ResultSet rs = stat.executeQuery("select f3(" + i + ");");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(i);
            rs.close();
        }
    }

    @Test
    public void multipleArgs() throws SQLException {
        SQLiteFunction.create(
                conn,
                "f4",
                new SQLiteFunction() {
                    @Override
                    public void xFunc() throws SQLException {
                        int ret = 0;
                        for (int i = 0; i < args(); i++) {
                            ret += value_int(i);
                        }
                        result(ret);
                    }
                });
        ResultSet rs = stat.executeQuery("select f4(2, 3, 9, -5);");
        assertThat(rs.next()).isTrue();
        assertThat(rs.getInt(1)).isEqualTo(9);
        rs.close();
        rs = stat.executeQuery("select f4(2);");
        assertThat(rs.next()).isTrue();
        assertThat(rs.getInt(1)).isEqualTo(2);
        rs.close();
        rs = stat.executeQuery("select f4(-3, -4, -5);");
        assertThat(rs.next()).isTrue();
        assertThat(rs.getInt(1)).isEqualTo(-12);
    }

    @Test
    public void returnTypes() throws SQLException {
        SQLiteFunction.create(
                conn,
                "f5",
                new SQLiteFunction() {
                    @Override
                    public void xFunc() throws SQLException {
                        result("Hello World");
                    }
                });
        ResultSet rs = stat.executeQuery("select f5();");
        assertThat(rs.next()).isTrue();
        assertThat(rs.getString(1)).isEqualTo("Hello World");

        SQLiteFunction.create(
                conn,
                "f6",
                new SQLiteFunction() {
                    @Override
                    public void xFunc() throws SQLException {
                        result(Long.MAX_VALUE);
                    }
                });
        rs.close();
        rs = stat.executeQuery("select f6();");
        assertThat(rs.next()).isTrue();
        assertThat(rs.getLong(1)).isEqualTo(Long.MAX_VALUE);

        SQLiteFunction.create(
                conn,
                "f7",
                new SQLiteFunction() {
                    @Override
                    public void xFunc() throws SQLException {
                        result(Double.MAX_VALUE);
                    }
                });
        rs.close();
        rs = stat.executeQuery("select f7();");
        assertThat(rs.next()).isTrue();
        assertThat(Double.MAX_VALUE).isCloseTo(rs.getDouble(1), offset(0.0001));

        SQLiteFunction.create(
                conn,
                "f8",
                new SQLiteFunction() {
                    @Override
                    public void xFunc() throws SQLException {
                        result(b1);
                    }
                });
        rs.close();
        rs = stat.executeQuery("select f8();");
        assertThat(rs.next()).isTrue();
        assertThat(rs.getBytes(1)).containsExactly(b1);
    }

    @Test
    public void returnArgInt() throws SQLException {
        SQLiteFunction.create(
                conn,
                "farg_int",
                new SQLiteFunction() {
                    @Override
                    public void xFunc() throws SQLException {
                        result(value_int(0));
                    }
                });
        PreparedStatement prep = conn.prepareStatement("select farg_int(?);");
        prep.setInt(1, Integer.MAX_VALUE);
        ResultSet rs = prep.executeQuery();
        assertThat(rs.next()).isTrue();
        assertThat(rs.getInt(1)).isEqualTo(Integer.MAX_VALUE);
        prep.close();
    }

    @Test
    public void returnArgLong() throws SQLException {
        SQLiteFunction.create(
                conn,
                "farg_long",
                new SQLiteFunction() {
                    @Override
                    public void xFunc() throws SQLException {
                        result(value_long(0));
                    }
                });
        PreparedStatement prep = conn.prepareStatement("select farg_long(?);");
        prep.setLong(1, Long.MAX_VALUE);
        ResultSet rs = prep.executeQuery();
        assertThat(rs.next()).isTrue();
        assertThat(rs.getLong(1)).isEqualTo(Long.MAX_VALUE);
        prep.close();
    }

    @Test
    public void returnArgDouble() throws SQLException {
        SQLiteFunction.create(
                conn,
                "farg_doub",
                new SQLiteFunction() {
                    @Override
                    public void xFunc() throws SQLException {
                        result(value_double(0));
                    }
                });
        PreparedStatement prep = conn.prepareStatement("select farg_doub(?);");
        prep.setDouble(1, Double.MAX_VALUE);
        ResultSet rs = prep.executeQuery();
        assertThat(rs.next()).isTrue();
        assertThat(Double.MAX_VALUE).isCloseTo(rs.getDouble(1), offset(0.0001));
        prep.close();
    }

    @Test
    public void returnArgBlob() throws SQLException {
        SQLiteFunction.create(
                conn,
                "farg_blob",
                new SQLiteFunction() {
                    @Override
                    public void xFunc() throws SQLException {
                        result(value_blob(0));
                    }
                });
        PreparedStatement prep = conn.prepareStatement("select farg_blob(?);");
        prep.setBytes(1, b1);
        ResultSet rs = prep.executeQuery();
        assertThat(rs.next()).isTrue();
        assertThat(rs.getBytes(1)).containsExactly(b1);
        prep.close();
    }

    @Test
    public void returnArgString() throws SQLException {
        SQLiteFunction.create(
                conn,
                "farg_str",
                new SQLiteFunction() {
                    @Override
                    public void xFunc() throws SQLException {
                        result(value_text(0));
                    }
                });
        PreparedStatement prep = conn.prepareStatement("select farg_str(?);");
        prep.setString(1, "Hello");
        ResultSet rs = prep.executeQuery();
        assertThat(rs.next()).isTrue();
        assertThat(rs.getString(1)).isEqualTo("Hello");
        prep.close();
    }

    @Test
    public void trigger() throws SQLException {
        SQLiteFunction.create(
                conn,
                "inform",
                new SQLiteFunction() {
                    @Override
                    protected void xFunc() throws SQLException {
                        gotTrigger = value_int(0);
                    }
                });
        stat.executeUpdate("create table trigtest (c1);");
        stat.executeUpdate(
                "create trigger trigt after insert on trigtest"
                        + " begin select inform(new.c1); end;");
        stat.executeUpdate("insert into trigtest values (5);");
        assertThat(gotTrigger).isEqualTo(5);
    }

    @Test
    public void aggregate() throws SQLException {
        SQLiteFunction.create(
                conn,
                "mySum",
                new SQLiteFunction.Aggregate() {
                    private int val = 0;

                    @Override
                    protected void xStep() throws SQLException {
                        for (int i = 0; i < args(); i++) {
                            val += value_int(i);
                        }
                    }

                    @Override
                    protected void xFinal() throws SQLException {
                        result(val);
                    }
                });
        stat.executeUpdate("create table t (c1);");
        stat.executeUpdate("insert into t values (5);");
        stat.executeUpdate("insert into t values (3);");
        stat.executeUpdate("insert into t values (8);");
        stat.executeUpdate("insert into t values (2);");
        stat.executeUpdate("insert into t values (7);");
        ResultSet rs = stat.executeQuery("select mySum(c1), sum(c1) from t;");
        assertThat(rs.next()).isTrue();
        assertThat(rs.getInt(1)).isEqualTo(rs.getInt(2));
    }

    @Test
    public void window() throws SQLException {
        SQLiteFunction.create(
                conn,
                "mySum",
                new SQLiteFunction.Window() {
                    private int val = 0;

                    @Override
                    protected void xStep() throws SQLException {
                        for (int i = 0; i < args(); i++) {
                            val += value_int(i);
                        }
                    }

                    @Override
                    protected void xInverse() throws SQLException {
                        for (int i = 0; i < args(); i++) {
                            val -= value_int(i);
                        }
                    }

                    @Override
                    protected void xValue() throws SQLException {
                        result(val);
                    }

                    @Override
                    protected void xFinal() throws SQLException {
                        result(val);
                    }
                });

        stat.executeUpdate("create table t (x);");
        stat.executeUpdate("insert into t values(1);");
        stat.executeUpdate("insert into t values(2);");
        stat.executeUpdate("insert into t values(3);");
        stat.executeUpdate("insert into t values(4);");
        stat.executeUpdate("insert into t values(5);");

        ResultSet rs =
                stat.executeQuery(
                        "select mySum(x) over (order by x rows between 1 preceding and 1 following) from t order by x;");
        assertThat(rs.next()).isTrue();
        assertThat(rs.getInt(1)).isEqualTo(3);
        assertThat(rs.next()).isTrue();
        assertThat(rs.getInt(1)).isEqualTo(6);
        assertThat(rs.next()).isTrue();
        assertThat(rs.getInt(1)).isEqualTo(9);
        assertThat(rs.next()).isTrue();
        assertThat(rs.getInt(1)).isEqualTo(12);
        assertThat(rs.next()).isTrue();
        assertThat(rs.getInt(1)).isEqualTo(9);
    }

    @Test
    public void destroy() throws SQLException {
        SQLiteFunction.create(
                conn,
                "f1",
                new SQLiteFunction() {
                    @Override
                    public void xFunc() {
                        val = 9;
                    }
                });
        stat.executeQuery("select f1();").close();
        assertThat(val).isEqualTo(9);

        SQLiteFunction.destroy(conn, "f1");
        SQLiteFunction.destroy(conn, "f1");
    }

    @Test
    public void functionsCanBeOverloadedByArgumentCount() throws SQLException {
        SQLiteFunction.create(
                conn,
                "overloaded",
                new SQLiteFunction() {
                    @Override
                    protected void xFunc() throws SQLException {
                        result(0);
                    }
                },
                0,
                0);
        SQLiteFunction.create(
                conn,
                "OVERLOADED",
                new SQLiteFunction() {
                    @Override
                    protected void xFunc() throws SQLException {
                        result(value_int(0));
                    }
                },
                1,
                0);

        try (ResultSet result = stat.executeQuery("select overloaded(), overloaded(7)")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isZero();
            assertThat(result.getInt(2)).isEqualTo(7);
        }

        SQLiteFunction.destroy(conn, "Overloaded", 1);
        assertThatThrownBy(() -> stat.executeQuery("select overloaded(7)"))
                .isInstanceOf(SQLException.class);
        try (ResultSet result = stat.executeQuery("select overloaded()")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isZero();
        }
    }

    @Test
    public void manyfunctions() throws SQLException {
        SQLiteFunction.create(
                conn,
                "f1",
                new SQLiteFunction() {
                    @Override
                    public void xFunc() throws SQLException {
                        result(1);
                    }
                });
        SQLiteFunction.create(
                conn,
                "f2",
                new SQLiteFunction() {
                    @Override
                    public void xFunc() throws SQLException {
                        result(2);
                    }
                });
        SQLiteFunction.create(
                conn,
                "f3",
                new SQLiteFunction() {
                    @Override
                    public void xFunc() throws SQLException {
                        result(3);
                    }
                });
        SQLiteFunction.create(
                conn,
                "f4",
                new SQLiteFunction() {
                    @Override
                    public void xFunc() throws SQLException {
                        result(4);
                    }
                });
        SQLiteFunction.create(
                conn,
                "f5",
                new SQLiteFunction() {
                    @Override
                    public void xFunc() throws SQLException {
                        result(5);
                    }
                });
        SQLiteFunction.create(
                conn,
                "f6",
                new SQLiteFunction() {
                    @Override
                    public void xFunc() throws SQLException {
                        result(6);
                    }
                });
        SQLiteFunction.create(
                conn,
                "f7",
                new SQLiteFunction() {
                    @Override
                    public void xFunc() throws SQLException {
                        result(7);
                    }
                });
        SQLiteFunction.create(
                conn,
                "f8",
                new SQLiteFunction() {
                    @Override
                    public void xFunc() throws SQLException {
                        result(8);
                    }
                });
        SQLiteFunction.create(
                conn,
                "f9",
                new SQLiteFunction() {
                    @Override
                    public void xFunc() throws SQLException {
                        result(9);
                    }
                });
        SQLiteFunction.create(
                conn,
                "f10",
                new SQLiteFunction() {
                    @Override
                    public void xFunc() throws SQLException {
                        result(10);
                    }
                });
        SQLiteFunction.create(
                conn,
                "f11",
                new SQLiteFunction() {
                    @Override
                    public void xFunc() throws SQLException {
                        result(11);
                    }
                });

        ResultSet rs =
                stat.executeQuery(
                        "select f1() + f2() + f3() + f4() + f5() + f6()"
                                + " + f7() + f8() + f9() + f10() + f11();");
        assertThat(rs.next()).isTrue();
        assertThat(rs.getInt(1)).isEqualTo(1 + 2 + 3 + 4 + 5 + 6 + 7 + 8 + 9 + 10 + 11);
        rs.close();
    }

    @Test
    public void multipleThreads() throws Exception {
        SQLiteFunction func =
                new SQLiteFunction() {
                    int sum = 0;

                    @Override
                    protected void xFunc() {
                        try {
                            sum += value_int(1);
                        } catch (SQLException e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public String toString() {
                        return String.valueOf(sum);
                    }
                };
        SQLiteFunction.create(conn, "func", func);
        stat.executeUpdate("create table foo (col integer);");
        stat.executeUpdate(
                "create trigger foo_trigger after insert on foo begin"
                        + " select func(new.rowid, new.col); end;");
        int times = 1000;
        List<Thread> threads = new LinkedList<>();
        for (int tn = 0; tn < times; tn++) {
            threads.add(
                    new Thread("func thread " + tn) {
                        @Override
                        public void run() {
                            try {
                                Statement s = conn.createStatement();
                                s.executeUpdate("insert into foo values (1);");
                                s.close();
                            } catch (SQLException e) {
                                e.printStackTrace();
                            }
                        }
                    });
        }
        for (Thread thread : threads) {
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }

        // check that all of the threads successfully executed
        ResultSet rs = stat.executeQuery("select sum(col) from foo;");
        assertThat(rs.next()).isTrue();
        assertThat(rs.getInt(1)).isEqualTo(times);
        rs.close();

        // check that custom function was executed each time
        assertThat(Integer.parseInt(func.toString())).isEqualTo(times);
    }
}
