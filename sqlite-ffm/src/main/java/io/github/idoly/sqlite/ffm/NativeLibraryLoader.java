package io.github.idoly.sqlite.ffm;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

/**
 * Loads an external native library path or extracts a packaged classifier resource.
 *
 * <p>Extraction uses a content-addressed cache and an inter-process file lock, so concurrent
 * class loaders converge on the same immutable file.
 */
final class NativeLibraryLoader {
    private static final String LIBRARY_PATH_PROPERTY = "sqlite.jdbc.library.path";
    private static final String NATIVE_DIRECTORY_PROPERTY = "sqlite.jdbc.native.dir";
    private static final String PLATFORM_PROPERTY = "sqlite.jdbc.native.platform";
    private static final String DRIVER_VERSION = Optional.ofNullable(
                    NativeLibraryLoader.class.getPackage().getImplementationVersion())
            .orElse("development");

    private NativeLibraryLoader() {}

    static Optional<Path> load() {
        String configuredPath = System.getProperty(LIBRARY_PATH_PROPERTY);
        if (configuredPath != null && !configuredPath.isBlank()) {
            Path path = Path.of(configuredPath).toAbsolutePath().normalize();
            if (!Files.isRegularFile(path)) {
                throw new IllegalStateException("Configured SQLite native library does not exist: " + path);
            }
            return Optional.of(path);
        }

        String platform = System.getProperty(PLATFORM_PROPERTY);
        if (platform == null || platform.isBlank()) platform = detectPlatform();
        String libraryName = libraryName(platform);
        String resourceName = "META-INF/native/" + platform + "/" + libraryName;
        try (InputStream input = NativeLibraryLoader.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (input == null) return Optional.empty();
            byte[] libraryBytes = input.readAllBytes();
            return Optional.of(extract(platform, libraryName, libraryBytes));
        } catch (IOException error) {
            throw new IllegalStateException("Could not extract SQLite native library " + resourceName, error);
        }
    }

    static String detectPlatform() {
        String os = normalizeOs(System.getProperty("os.name", ""));
        String arch = normalizeArchitecture(System.getProperty("os.arch", ""));
        return os.equals("linux") ? os + "-" + arch + "-" + detectLibc() : os + "-" + arch;
    }

    private static String normalizeOs(String value) {
        String os = value.toLowerCase(Locale.ROOT);
        if (os.contains("linux")) return "linux";
        if (os.contains("mac") || os.contains("darwin")) return "macos";
        if (os.contains("win")) return "windows";
        throw new IllegalStateException("Unsupported operating system: " + value);
    }

    private static String normalizeArchitecture(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "x86_64", "amd64" -> "x86_64";
            case "aarch64", "arm64" -> "aarch64";
            default -> throw new IllegalStateException("Unsupported architecture: " + value);
        };
    }

    private static String detectLibc() {
        if (Files.exists(Path.of("/etc/alpine-release"))) return "musl";
        Path lib = Path.of("/lib");
        if (Files.isDirectory(lib)) {
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(lib, "ld-musl-*.so.1")) {
                if (entries.iterator().hasNext()) return "musl";
            } catch (IOException ignored) {
                // Fall through to the glibc default when the runtime does not expose /lib.
            }
        }
        return "glibc";
    }

    private static String libraryName(String platform) {
        if (platform.startsWith("windows-")) return "sqlitejdbc.dll";
        if (platform.startsWith("macos-")) return "libsqlitejdbc.dylib";
        return "libsqlitejdbc.so";
    }

    private static Path extract(String platform, String libraryName, byte[] libraryBytes) throws IOException {
        String contentDigest = sha256(libraryBytes);
        String configuredDirectory = System.getProperty(NATIVE_DIRECTORY_PROPERTY);
        Path cacheRoot = configuredDirectory == null || configuredDirectory.isBlank()
                ? Path.of(System.getProperty("java.io.tmpdir"))
                : Path.of(configuredDirectory);
        Path cacheDirectory = cacheRoot.toAbsolutePath().normalize()
                .resolve("sqlite-jdbc")
                .resolve(DRIVER_VERSION)
                .resolve(platform)
                .resolve(contentDigest);
        Files.createDirectories(cacheDirectory);
        Path libraryPath = cacheDirectory.resolve(libraryName);
        Path lockPath = cacheDirectory.resolve(".extract.lock");

        try (FileChannel channel = FileChannel.open(
                        lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                FileLock ignored = channel.lock()) {
            if (!matches(libraryPath, libraryBytes.length, contentDigest)) {
                Path temporaryPath = Files.createTempFile(cacheDirectory, libraryName, ".tmp");
                try {
                    Files.write(temporaryPath, libraryBytes);
                    moveAtomically(temporaryPath, libraryPath);
                } finally {
                    Files.deleteIfExists(temporaryPath);
                }
            }
        }
        return libraryPath;
    }

    private static boolean matches(Path path, int expectedSize, String expectedDigest) throws IOException {
        return Files.isRegularFile(path)
                && Files.size(path) == expectedSize
                && sha256(Files.readAllBytes(path)).equals(expectedDigest);
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException error) {
            throw new AssertionError("SHA-256 is required by the Java platform", error);
        }
    }
}
