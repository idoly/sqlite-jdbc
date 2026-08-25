package io.github.idoly.sqlite.internal;

import io.github.idoly.sqlite.SQLiteErrorCode;
import io.github.idoly.sqlite.core.SQLiteDatabase;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * parsing SQLite specific extension of SQL command
 *
 * @author leo
 */
final class BackupRestoreCommand {
    private BackupRestoreCommand() {}

    public interface Command {
        void execute(SQLiteDatabase database) throws SQLException;
    }

    /**
     * Parses extended commands of "backup" or "restore" for SQLite database.
     *
     * @param sql One of the extended commands:<br>
     *     backup sourceDatabaseName to destinationFileName OR restore targetDatabaseName from
     *     sourceFileName
     * @return BackupCommand object if the argument is a backup command; RestoreCommand object if
     *     the argument is a restore command;
     */
    public static Command parse(String sql) throws SQLException {
        if (sql == null) return null;
        if (sql.regionMatches(true, 0, "backup", 0, 6)) return BackupCommand.parse(sql);
        if (sql.regionMatches(true, 0, "restore", 0, 7)) return RestoreCommand.parse(sql);

        return null;
    }

    /**
     * Remove the quotation mark from string.
     *
     * @param s String with quotation mark.
     * @return String with quotation mark removed.
     */
    public static String removeQuotation(String s) {
        if (s == null) return s;

        if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'")))
            return (s.length() >= 2) ? s.substring(1, s.length() - 1) : s;
        else return s;
    }

    public static final class BackupCommand implements Command {
        public final String srcDB;
        public final String destFile;

        /**
         * Constructs a BackupCommand instance that backup the database to a target file.
         *
         * @param srcDB Source database name.
         * @param destFile Target file name.
         */
        public BackupCommand(String srcDB, String destFile) {
            this.srcDB = srcDB;
            this.destFile = destFile;
        }

        private static final Pattern BACKUP_COMMAND =
                Pattern.compile(
                        "backup(\\s+(\"[^\"]*\"|'[^\']*\'|\\S+))?\\s+to\\s+(\"[^\"]*\"|'[^\']*\'|\\S+)",
                        Pattern.CASE_INSENSITIVE);

        /**
         * Parses SQLite database backup command and creates a BackupCommand object.
         *
         * @param sql SQLite database backup command.
         * @return BackupCommand object.
         */
        public static BackupCommand parse(String sql) throws SQLException {
            if (sql != null) {
                Matcher matcher = BACKUP_COMMAND.matcher(sql);
                if (matcher.matches()) {
                    String dbName = removeQuotation(matcher.group(2));
                    String dest = removeQuotation(matcher.group(3));
                    if (dbName == null || dbName.isEmpty()) dbName = "main";

                    return new BackupCommand(dbName, dest);
                }
            }
            throw new SQLException("syntax error: " + sql);
        }

        public void execute(SQLiteDatabase db) throws SQLException {
            int rc = db.backup(srcDB, destFile, null);

            if (rc != SQLiteErrorCode.SQLITE_OK.code) {
                throw SQLiteDatabase.newSQLException(rc, "Backup failed");
            }
        }
    }

    public static final class RestoreCommand implements Command {
        public final String targetDB;
        public final String srcFile;
        private static final Pattern RESTORE_COMMAND =
                Pattern.compile(
                        "restore(\\s+(\"[^\"]*\"|'[^\']*\'|\\S+))?\\s+from\\s+(\"[^\"]*\"|'[^\']*\'|\\S+)",
                        Pattern.CASE_INSENSITIVE);

        /**
         * Constructs a RestoreCommand instance that restores the database from a given source file.
         *
         * @param targetDB Target database name
         * @param srcFile Source file name
         */
        public RestoreCommand(String targetDB, String srcFile) {
            this.targetDB = targetDB;
            this.srcFile = srcFile;
        }

        /**
         * Parses SQLite database restore command and creates a RestoreCommand object.
         *
         * @param sql SQLite restore backup command
         * @return RestoreCommand object.
         */
        public static RestoreCommand parse(String sql) throws SQLException {
            if (sql != null) {
                Matcher matcher = RESTORE_COMMAND.matcher(sql);
                if (matcher.matches()) {
                    String dbName = removeQuotation(matcher.group(2));
                    String dest = removeQuotation(matcher.group(3));
                    if (dbName == null || dbName.isEmpty()) dbName = "main";
                    return new RestoreCommand(dbName, dest);
                }
            }
            throw new SQLException("syntax error: " + sql);
        }

        /**
         * @see
         *     io.github.idoly.sqlite.internal.BackupRestoreCommand.Command#execute(io.github.idoly.sqlite.core.SQLiteDatabase)
         */
        public void execute(SQLiteDatabase db) throws SQLException {
            int rc = db.restore(targetDB, srcFile, null);

            if (rc != SQLiteErrorCode.SQLITE_OK.code) {
                throw SQLiteDatabase.newSQLException(rc, "Restore failed");
            }
        }
    }
}
