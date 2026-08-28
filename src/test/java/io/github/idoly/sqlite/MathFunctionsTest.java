package io.github.idoly.sqlite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import java.sql.DriverManager;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

public class MathFunctionsTest {
    private static final Offset<Double> TOLERANCE = offset(0.000000000001);

    @Test
    public void officialMathFunctions() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                var statement = connection.createStatement();
                var result =
                        statement.executeQuery(
                                "select acos(0.5), atan2(1, 5), ln(2), log2(2),"
                                        + " mod(11, 3.5), pi(), pow(10, 2), radians(45),"
                                        + " trunc(-1.5)")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getDouble(1)).isCloseTo(1.0471975511966, TOLERANCE);
            assertThat(result.getDouble(2)).isCloseTo(0.197395559849881, TOLERANCE);
            assertThat(result.getDouble(3)).isCloseTo(0.693147180559945, TOLERANCE);
            assertThat(result.getDouble(4)).isEqualTo(1.0);
            assertThat(result.getDouble(5)).isEqualTo(0.5);
            assertThat(result.getDouble(6)).isCloseTo(3.141592653589793, TOLERANCE);
            assertThat(result.getDouble(7)).isEqualTo(100.0);
            assertThat(result.getDouble(8)).isCloseTo(0.785398163397448, TOLERANCE);
            assertThat(result.getDouble(9)).isEqualTo(-1.0);
        }
    }

    @Test
    public void officialPercentileFunctions() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                var statement = connection.createStatement();
                var result =
                        statement.executeQuery(
                                "with value(x) as (values (1), (2), (3), (4))"
                                        + " select median(x), percentile(x, 25),"
                                        + " percentile_cont(x, 0.75), percentile_disc(x, 0.75)"
                                        + " from value")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getDouble(1)).isEqualTo(2.5);
            assertThat(result.getDouble(2)).isEqualTo(1.75);
            assertThat(result.getDouble(3)).isEqualTo(3.25);
            assertThat(result.getDouble(4)).isEqualTo(3.0);
        }
    }

    @Test
    public void officialFunctionsAreCompileOptions() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            assertThat(TestSupport.getCompileOptions(connection))
                    .contains("ENABLE_MATH_FUNCTIONS", "ENABLE_PERCENTILE")
                    .doesNotContain("JDBC_EXTENSIONS");
        }
    }
}
