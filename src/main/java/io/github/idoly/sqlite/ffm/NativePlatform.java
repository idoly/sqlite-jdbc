package io.github.idoly.sqlite.ffm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Stream;

/** Resolves the supported native-library platform from JDK system properties. */
public final class NativePlatform {
    public static final String X86_64 = "x86_64";
    public static final String AARCH64 = "aarch64";
    private static final String SQLITE_LIBRARY_BASE_NAME = "sqlite3";

    private NativePlatform() {}

    public static void main(String[] args) {
        if (args.length > 0 && "--os".equals(args[0])) {
            System.out.print(getOSName());
        } else if (args.length > 0 && "--arch".equals(args[0])) {
            System.out.print(getArchName());
        } else {
            System.out.print(getNativeLibFolderPathForCurrentOS());
        }
    }

    public static String getNativeLibFolderPathForCurrentOS() {
        return getOSName() + "/" + getArchName();
    }

    public static String getNativeLibResourcePath() {
        return "/io/github/idoly/sqlite/native/" + getNativeLibFolderPathForCurrentOS();
    }

    public static String getNativeLibName() {
        return System.mapLibraryName(SQLITE_LIBRARY_BASE_NAME);
    }

    public static boolean hasNativeLib(String path, String libraryName) {
        return NativePlatform.class.getResource(path + "/" + libraryName) != null;
    }

    public static String getOSName() {
        String osName = System.getProperty("os.name", "");
        if (osName.contains("Windows")) return "Windows";
        if (osName.contains("Mac") || osName.contains("Darwin")) return "Mac";
        if (osName.contains("Linux")) return isMusl() ? "Linux-Musl" : "Linux";
        throw new IllegalStateException("Unsupported operating system: " + osName);
    }

    public static String getArchName() {
        String override = System.getProperty("io.github.idoly.sqlite.native.architecture");
        if (override != null && !override.isBlank()) return override;

        String architecture = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        return switch (architecture) {
            case "amd64", "x86_64" -> X86_64;
            case "aarch64", "arm64" -> AARCH64;
            default -> throw new IllegalStateException("Unsupported architecture: " + architecture);
        };
    }

    public static boolean isMusl() {
        Path mapFilesDirectory = Path.of("/proc/self/map_files");
        try (Stream<Path> paths = Files.list(mapFilesDirectory)) {
            if (paths.map(NativePlatform::realPath).anyMatch(path -> path.contains("musl")))
                return true;
        } catch (IOException | SecurityException ignored) {
            // /proc may be unavailable in containers or restricted environments.
        }

        try (Stream<String> lines = Files.lines(Path.of("/etc/os-release"))) {
            return lines.anyMatch(line -> line.equals("ID=alpine") || line.equals("ID=\"alpine\""));
        } catch (IOException | SecurityException ignored) {
            return false;
        }
    }

    private static String realPath(Path path) {
        try {
            return path.toRealPath().toString().toLowerCase(Locale.ROOT);
        } catch (IOException | SecurityException ignored) {
            return "";
        }
    }
}
