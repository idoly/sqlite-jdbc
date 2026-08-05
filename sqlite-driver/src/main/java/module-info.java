/** JDBC API implementation backed by the sqlite-ffm module. */
module io.github.idoly.sqlite.jdbc {
    requires io.github.idoly.sqlite.ffm;
    requires transitive java.logging;
    requires transitive java.sql;
    requires java.sql.rowset;
    requires java.xml;

    exports io.github.idoly.sqlite.jdbc;

    provides java.sql.Driver with io.github.idoly.sqlite.jdbc.SQLiteDriver;
}
