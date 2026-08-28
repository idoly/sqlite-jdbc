package io.github.idoly.sqlite.ffm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

@DisabledInNativeImage
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class NativePlatformTest {
    @Test
    void normalizesSupportedArchitectures() {
        assertArchitecture("amd64", "x86_64");
        assertArchitecture("x86_64", "x86_64");
        assertArchitecture("arm64", "aarch64");
        assertArchitecture("aarch64", "aarch64");
    }

    @Test
    void rejectsUnsupportedArchitecture() {
        withSystemProperty(
                "os.arch",
                "riscv64",
                () ->
                        assertThatThrownBy(NativePlatform::getArchName)
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessageContaining("riscv64"));
    }

    @Test
    void rejectsUnsupportedOperatingSystem() {
        withSystemProperty(
                "os.name",
                "AIX",
                () ->
                        assertThatThrownBy(NativePlatform::getOSName)
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessageContaining("AIX"));
    }

    @Test
    void packagedLibraryExistsForCurrentPlatform() {
        String path = NativePlatform.getNativeLibResourcePath();
        String name = NativePlatform.getNativeLibName();
        assertThat(NativePlatform.getNativeLibFolderPathForCurrentOS())
                .isEqualTo(NativePlatform.getOSName() + "/" + NativePlatform.getArchName());
        assertThat(NativePlatform.hasNativeLib(path, name)).isTrue();
    }

    @Test
    void packagedLibrariesMatchOfficial64BitPlatforms() {
        assertThat(hasResource("Linux/x86_64/libsqlite3.so")).isTrue();
        assertThat(hasResource("Linux/aarch64/libsqlite3.so")).isTrue();
        assertThat(hasResource("Mac/x86_64/libsqlite3.dylib")).isTrue();
        assertThat(hasResource("Mac/aarch64/libsqlite3.dylib")).isTrue();
        assertThat(hasResource("Windows/x86_64/sqlite3.dll")).isTrue();
        assertThat(hasResource("Windows/aarch64/sqlite3.dll")).isTrue();

        assertThat(hasResource("Linux-Musl/x86_64/libsqlite3.so")).isFalse();
        assertThat(hasResource("Windows/x86/sqlite3.dll")).isFalse();
    }

    @Test
    void commandLineOutput() {
        assertThat(captureOutput("--os")).isEqualTo(NativePlatform.getOSName());
        assertThat(captureOutput("--arch")).isEqualTo(NativePlatform.getArchName());
        assertThat(captureOutput()).isEqualTo(NativePlatform.getNativeLibFolderPathForCurrentOS());
    }

    private static boolean hasResource(String path) {
        return NativePlatform.class.getResource("/io/github/idoly/sqlite/native/" + path) != null;
    }

    private static void assertArchitecture(String input, String expected) {
        withSystemProperty(
                "os.arch",
                input,
                () -> assertThat(NativePlatform.getArchName()).isEqualTo(expected));
    }

    private static void withSystemProperty(String key, String value, Runnable assertion) {
        String previous = System.setProperty(key, value);
        try {
            assertion.run();
        } finally {
            if (previous == null) System.clearProperty(key);
            else System.setProperty(key, previous);
        }
    }

    private static String captureOutput(String... arguments) {
        PrintStream previous = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PrintStream replacement = new PrintStream(output)) {
            System.setOut(replacement);
            NativePlatform.main(arguments);
        } finally {
            System.setOut(previous);
        }
        return output.toString();
    }
}
