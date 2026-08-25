package io.github.idoly.sqlite.core;

/** This is a helper class for exposing package local functions of FfmDatabase to unit tests */
public class FfmDatabaseTestSupport {
    /**
     * Get the native pointer of the progress handler
     *
     * @param database the native db object
     * @return the pointer of the progress handler
     */
    public static long getProgressHandler(SQLiteDatabase database) {
        return ((FfmDatabase) database).getProgressHandler();
    }

    /**
     * Get the native pointer of the busy handler
     *
     * @param database the native db object
     * @return the pointer of the busy handler
     */
    public static long getBusyHandler(SQLiteDatabase database) {
        return ((FfmDatabase) database).getBusyHandler();
    }

    /**
     * Get the native pointer of the commit listener
     *
     * @param database the native db object
     * @return the pointer of the commit listener
     */
    public static long getCommitListener(SQLiteDatabase database) {
        return ((FfmDatabase) database).getCommitListener();
    }

    /**
     * Get the native pointer of the update listener
     *
     * @param database the native db object
     * @return the pointer of the update listener
     */
    public static long getUpdateListener(SQLiteDatabase database) {
        return ((FfmDatabase) database).getUpdateListener();
    }
}
