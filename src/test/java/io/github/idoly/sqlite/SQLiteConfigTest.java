package io.github.idoly.sqlite;

import static org.assertj.core.api.Assertions.*;

import io.github.idoly.sqlite.SQLiteConfig.Pragma;
import java.util.Properties;
import org.junit.jupiter.api.Test;

public class SQLiteConfigTest {

    @Test
    public void toProperties() {
        SQLiteConfig config = new SQLiteConfig();

        config.setReadOnly(true);
        config.setDateStringFormat("yyyy/mm/dd");
        config.setDatePrecision("seconds");
        config.setDateClass("real");
        config.setGetGeneratedKeys(false);

        Properties properties = config.toProperties();

        assertThat(properties.getProperty(SQLiteConfig.Pragma.DATE_STRING_FORMAT.pragmaName()))
                .isEqualTo("yyyy/mm/dd");
        assertThat(properties.getProperty(SQLiteConfig.Pragma.DATE_PRECISION.pragmaName()))
                .isEqualTo(SQLiteConfig.DatePrecision.SECONDS.name());
        assertThat(properties.getProperty(SQLiteConfig.Pragma.DATE_CLASS.pragmaName()))
                .isEqualTo(SQLiteConfig.DateClass.REAL.name());
        assertThat(properties.getProperty(Pragma.JDBC_GET_GENERATED_KEYS.pragmaName()))
                .isEqualTo("false");
    }

    @Test
    public void setBusyTimeout() {
        SQLiteConfig config = new SQLiteConfig();

        // verify the default is set in the pragma table and the cached value
        assertThat(config.toProperties().getProperty(SQLiteConfig.Pragma.BUSY_TIMEOUT.pragmaName()))
                .isEqualTo("3000");
        assertThat(config.getBusyTimeout()).isEqualTo(3000);

        // verify that the default is updated in both places
        config.setBusyTimeout(1234);
        assertThat(config.toProperties().getProperty(SQLiteConfig.Pragma.BUSY_TIMEOUT.pragmaName()))
                .isEqualTo("1234");
        assertThat(config.getBusyTimeout()).isEqualTo(1234);

        Properties properties = new Properties();
        properties.setProperty(SQLiteConfig.Pragma.BUSY_TIMEOUT.pragmaName(), "100");
        config = new SQLiteConfig(properties);

        // verify that we can set an initial value other than the default
        assertThat(config.toProperties().getProperty(SQLiteConfig.Pragma.BUSY_TIMEOUT.pragmaName()))
                .isEqualTo("100");
        assertThat(config.getBusyTimeout()).isEqualTo(100);
    }

    @Test
    public void setWalAutocheckpoint() {
        SQLiteConfig config = new SQLiteConfig();
        config.setWalAutocheckpoint(500);
        assertThat(
                        config.toProperties()
                                .getProperty(SQLiteConfig.Pragma.WAL_AUTOCHECKPOINT.pragmaName()))
                .isEqualTo("500");
    }

    @Test
    public void setWalAutocheckpointDisabled() {
        SQLiteConfig config = new SQLiteConfig();
        config.setWalAutocheckpoint(0);
        assertThat(
                        config.toProperties()
                                .getProperty(SQLiteConfig.Pragma.WAL_AUTOCHECKPOINT.pragmaName()))
                .isEqualTo("0");
    }

    @Test
    public void pragmaChoicesAreDefensivelyCopied() {
        String[] choices = Pragma.JOURNAL_MODE.choices();
        String expected = choices[0];
        choices[0] = "modified";

        assertThat(Pragma.JOURNAL_MODE.choices()[0]).isEqualTo(expected);
        assertThat(SQLiteConfig.getDriverPropertyInfo())
                .filteredOn(info -> info.name.equals(Pragma.JOURNAL_MODE.pragmaName()))
                .singleElement()
                .satisfies(info -> assertThat(info.choices[0]).isEqualTo(expected));
    }
}
