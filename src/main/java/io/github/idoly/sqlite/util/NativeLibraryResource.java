package io.github.idoly.sqlite.util;

import io.github.idoly.sqlite.SQLiteJDBCLoader;

public final class NativeLibraryResource {
    public static final String NATIVE_LIB_BASE_NAME = "sqlite3";

    private NativeLibraryResource() {}

    /** Get the resource directory containing the SQLite library for the current platform. */
    public static String getNativeLibResourcePath() {
        String packagePath = SQLiteJDBCLoader.class.getPackage().getName().replace(".", "/");
        return String.format(
                "/%s/native/%s", packagePath, NativePlatform.getNativeLibFolderPathForCurrentOS());
    }

    /** Get the platform-specific SQLite library name. */
    public static String getNativeLibName() {
        return System.mapLibraryName(NATIVE_LIB_BASE_NAME);
    }

    public static boolean hasNativeLib(String path, String libraryName) {
        return SQLiteJDBCLoader.class.getResource(path + "/" + libraryName) != null;
    }
}
