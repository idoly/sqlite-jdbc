package io.github.idoly.sqlite.ffm;

/** Provides the built-in JDK FFM SQLite binding. */
public final class SQLiteNativeProvider {
    private SQLiteNativeProvider() {}

    /** @return the process-wide JDK FFM binding */
    public static SQLiteNative get() {
        return Holder.INSTANCE;
    }

    private static final class Holder {
        private static final SQLiteNative INSTANCE = new FfmSQLiteNative();
    }
}
