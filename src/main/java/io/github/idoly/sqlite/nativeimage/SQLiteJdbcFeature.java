package io.github.idoly.sqlite.nativeimage;

import io.github.idoly.sqlite.SQLiteJDBCLoader;
import io.github.idoly.sqlite.core.FfmDatabase;
import io.github.idoly.sqlite.internal.BaseDatabaseMetaData;
import io.github.idoly.sqlite.util.NativeLibraryResource;
import io.github.idoly.sqlite.util.NativePlatform;
import java.lang.reflect.Method;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;
import org.graalvm.nativeimage.hosted.RuntimeResourceAccess;

public class SQLiteJdbcFeature implements Feature {

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess a) {
        RuntimeClassInitialization.initializeAtBuildTime(SQLiteJDBCLoader.VersionHolder.class);
        RuntimeClassInitialization.initializeAtBuildTime(BaseDatabaseMetaData.class);
        RuntimeClassInitialization.initializeAtBuildTime(NativePlatform.class);
        RuntimeClassInitialization.initializeAtBuildTime(NativeLibraryResource.class);
        a.registerReachabilityHandler(this::databaseReachable, method(FfmDatabase.class, "load"));
    }

    private void databaseReachable(DuringAnalysisAccess a) {
        handleLibraryResources();
    }

    private void handleLibraryResources() {
        String libraryPath = NativeLibraryResource.getNativeLibResourcePath();
        String libraryName = NativeLibraryResource.getNativeLibName();
        if (!NativeLibraryResource.hasNativeLib(libraryPath, libraryName)) {
            throw new IllegalStateException(
                    "Missing packaged SQLite library " + libraryPath + "/" + libraryName);
        }
        String libraryResource = libraryPath + "/" + libraryName;
        RuntimeResourceAccess.addResource(
                SQLiteJDBCLoader.class.getModule(), libraryResource.substring(1));
    }

    private Method method(Class<?> clazz, String methodName, Class<?>... args) {
        try {
            return clazz.getDeclaredMethod(methodName, args);
        } catch (NoSuchMethodException e) {
            throw new SQLiteJdbcFeatureException(e);
        }
    }

    private static class SQLiteJdbcFeatureException extends RuntimeException {
        private SQLiteJdbcFeatureException(Throwable cause) {
            super(cause);
        }
    }
}
