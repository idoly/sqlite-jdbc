package io.github.idoly.sqlite.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.junitpioneer.jupiter.SetSystemProperty;

@DisabledInNativeImage
class NativePlatformTest {
    @Test
    void normalizesSupportedArchitectures() {
        assertArchitecture("amd64", "x86_64");
        assertArchitecture("x86_64", "x86_64");
        assertArchitecture("arm64", "aarch64");
        assertArchitecture("aarch64", "aarch64");
    }

    @Test
    @SetSystemProperty(key = "os.arch", value = "riscv64")
    void rejectsUnsupportedArchitecture() {
        assertThatThrownBy(NativePlatform::getArchName)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("riscv64");
    }

    @Test
    @SetSystemProperty(key = "os.name", value = "AIX")
    void rejectsUnsupportedOperatingSystem() {
        assertThatThrownBy(NativePlatform::getOSName)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AIX");
    }

    @Test
    void packagedLibraryExistsForCurrentPlatform() {
        String path = NativeLibraryResource.getNativeLibResourcePath();
        String name = NativeLibraryResource.getNativeLibName();
        assertThat(NativePlatform.getNativeLibFolderPathForCurrentOS())
                .isEqualTo(NativePlatform.getOSName() + "/" + NativePlatform.getArchName());
        assertThat(NativeLibraryResource.hasNativeLib(path, name)).isTrue();
    }

    @Test
    void commandLineOutput() {
        assertThat(captureOutput("--os")).isEqualTo(NativePlatform.getOSName());
        assertThat(captureOutput("--arch")).isEqualTo(NativePlatform.getArchName());
        assertThat(captureOutput()).isEqualTo(NativePlatform.getNativeLibFolderPathForCurrentOS());
    }

    @Test
    @SetSystemProperty(key = "io.github.idoly.sqlite.native.architecture", value = "custom")
    void architectureOverrideSupportsCrossCompilation() {
        assertThat(NativePlatform.getArchName()).isEqualTo("custom");
    }

    private static void assertArchitecture(String input, String expected) {
        String previous = System.setProperty("os.arch", input);
        try {
            assertThat(NativePlatform.getArchName()).isEqualTo(expected);
        } finally {
            if (previous == null) System.clearProperty("os.arch");
            else System.setProperty("os.arch", previous);
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
