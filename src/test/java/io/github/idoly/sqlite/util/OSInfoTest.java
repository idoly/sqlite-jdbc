package io.github.idoly.sqlite.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.junitpioneer.jupiter.SetSystemProperty;

@DisabledInNativeImage
class OSInfoTest {
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
        assertThatThrownBy(OSInfo::getArchName)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("riscv64");
    }

    @Test
    @SetSystemProperty(key = "os.name", value = "AIX")
    void rejectsUnsupportedOperatingSystem() {
        assertThatThrownBy(OSInfo::getOSName)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AIX");
    }

    @Test
    void packagedLibraryExistsForCurrentPlatform() {
        String path = LibraryLoaderUtil.getNativeLibResourcePath();
        String name = LibraryLoaderUtil.getNativeLibName();
        assertThat(OSInfo.getNativeLibFolderPathForCurrentOS())
                .isEqualTo(OSInfo.getOSName() + "/" + OSInfo.getArchName());
        assertThat(LibraryLoaderUtil.hasNativeLib(path, name)).isTrue();
    }

    @Test
    void commandLineOutput() {
        assertThat(captureOutput("--os")).isEqualTo(OSInfo.getOSName());
        assertThat(captureOutput("--arch")).isEqualTo(OSInfo.getArchName());
        assertThat(captureOutput()).isEqualTo(OSInfo.getNativeLibFolderPathForCurrentOS());
    }

    @Test
    @SetSystemProperty(key = "io.github.idoly.sqlite.osinfo.architecture", value = "custom")
    void architectureOverrideSupportsCrossCompilation() {
        assertThat(OSInfo.getArchName()).isEqualTo("custom");
    }

    private static void assertArchitecture(String input, String expected) {
        String previous = System.setProperty("os.arch", input);
        try {
            assertThat(OSInfo.getArchName()).isEqualTo(expected);
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
            OSInfo.main(arguments);
        } finally {
            System.setOut(previous);
        }
        return output.toString();
    }
}
