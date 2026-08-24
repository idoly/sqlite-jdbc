package io.github.idoly.sqlite.nativeimage;

import io.github.idoly.sqlite.SQLiteJDBCLoader;
import io.github.idoly.sqlite.core.NativeDB;
import io.github.idoly.sqlite.internal.BaseDatabaseMetaData;
import io.github.idoly.sqlite.util.LibraryLoaderUtil;
import io.github.idoly.sqlite.util.OSInfo;
import io.github.idoly.sqlite.util.ProcessRunner;
import java.lang.reflect.Method;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;
import org.graalvm.nativeimage.hosted.RuntimeResourceAccess;

public class SqliteJdbcFeature implements Feature {

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess a) {
        RuntimeClassInitialization.initializeAtBuildTime(SQLiteJDBCLoader.VersionHolder.class);
        RuntimeClassInitialization.initializeAtBuildTime(BaseDatabaseMetaData.class);
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
