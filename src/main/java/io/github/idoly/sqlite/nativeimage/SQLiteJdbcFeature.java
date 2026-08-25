package io.github.idoly.sqlite.nativeimage;

import io.github.idoly.sqlite.SQLiteDriver;
import io.github.idoly.sqlite.ffm.FfmDatabase;
import io.github.idoly.sqlite.ffm.NativePlatform;
import io.github.idoly.sqlite.internal.DatabaseMetaDataImpl;
import java.lang.reflect.Method;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;
import org.graalvm.nativeimage.hosted.RuntimeResourceAccess;

public final class SQLiteJdbcFeature implements Feature {

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess a) {
        RuntimeClassInitialization.initializeAtBuildTime(SQLiteDriver.VersionHolder.class);
        RuntimeClassInitialization.initializeAtBuildTime(DatabaseMetaDataImpl.class);
        RuntimeClassInitialization.initializeAtBuildTime(NativePlatform.class);
        a.registerReachabilityHandler(this::databaseReachable, method(FfmDatabase.class, "load"));
    }

    private void databaseReachable(DuringAnalysisAccess a) {
        handleLibraryResources();
    }

    private void handleLibraryResources() {
        String libraryPath = NativePlatform.getNativeLibResourcePath();
        String libraryName = NativePlatform.getNativeLibName();
        if (!NativePlatform.hasNativeLib(libraryPath, libraryName)) {
            throw new IllegalStateException(
                    "Missing packaged SQLite library " + libraryPath + "/" + libraryName);
        }
        String libraryResource = libraryPath + "/" + libraryName;
        RuntimeResourceAccess.addResource(
                SQLiteDriver.class.getModule(), libraryResource.substring(1));
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
