package org.sqlite.nativeimage;

import java.lang.reflect.Method;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;
import org.graalvm.nativeimage.hosted.RuntimeResourceAccess;
import org.sqlite.SQLiteJDBCLoader;
import org.sqlite.core.NativeDB;
import org.sqlite.jdbc3.JDBC3DatabaseMetaData;
import org.sqlite.util.LibraryLoaderUtil;
import org.sqlite.util.OSInfo;
import org.sqlite.util.ProcessRunner;

public class SqliteJdbcFeature implements Feature {

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess a) {
        RuntimeClassInitialization.initializeAtBuildTime(SQLiteJDBCLoader.VersionHolder.class);
        RuntimeClassInitialization.initializeAtBuildTime(JDBC3DatabaseMetaData.class);
        RuntimeClassInitialization.initializeAtBuildTime(OSInfo.class);
        RuntimeClassInitialization.initializeAtBuildTime(ProcessRunner.class);
        RuntimeClassInitialization.initializeAtBuildTime(LibraryLoaderUtil.class);
        a.registerReachabilityHandler(this::nativeDbReachable, method(NativeDB.class, "load"));
    }

    private void nativeDbReachable(DuringAnalysisAccess a) {
        handleLibraryResources();
    }

    private void handleLibraryResources() {
        String libraryPath = LibraryLoaderUtil.getNativeLibResourcePath();
        String libraryName = LibraryLoaderUtil.getNativeLibName();
        if (LibraryLoaderUtil.hasNativeLib(libraryPath, libraryName)) {
            String libraryResource = libraryPath + "/" + libraryName;
            RuntimeResourceAccess.addResource(
                    SQLiteJDBCLoader.class.getModule(), libraryResource.substring(1));
        }
    }

    private Method method(Class<?> clazz, String methodName, Class<?>... args) {
        try {
            return clazz.getDeclaredMethod(methodName, args);
        } catch (NoSuchMethodException e) {
            throw new SqliteJdbcFeatureException(e);
        }
    }

    private static class SqliteJdbcFeatureException extends RuntimeException {
        private SqliteJdbcFeatureException(Throwable cause) {
            super(cause);
        }
    }
}
