/*--------------------------------------------------------------------------
 *  Copyright 2008 Taro L. Saito
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *--------------------------------------------------------------------------*/
// --------------------------------------
// sqlite-jdbc Project
//
// OSInfo.java
// Since: May 20, 2008
//
// $URL$
// $Author$
// --------------------------------------
package io.github.idoly.sqlite.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Provides OS name and architecture name.
 *
 * @author leo
 */
public class OSInfo {
    private static final HashMap<String, String> archMapping = new HashMap<>();

    public static final String X86 = "x86";
    public static final String X86_64 = "x86_64";
    public static final String IA64_32 = "ia64_32";
    public static final String IA64 = "ia64";
    public static final String PPC = "ppc";
    public static final String PPC64 = "ppc64";
    public static final String RISCV64 = "riscv64";

    static {
        // x86 mappings
        archMapping.put(X86, X86);
        archMapping.put("i386", X86);
        archMapping.put("i486", X86);
        archMapping.put("i586", X86);
        archMapping.put("i686", X86);
        archMapping.put("pentium", X86);

        // x86_64 mappings
        archMapping.put(X86_64, X86_64);
        archMapping.put("amd64", X86_64);
        archMapping.put("em64t", X86_64);
        archMapping.put("universal", X86_64); // Needed for openjdk7 in Mac

        // Itanium 64-bit mappings
        archMapping.put(IA64, IA64);
        archMapping.put("ia64w", IA64);

        // Itanium 32-bit mappings, usually an HP-UX construct
        archMapping.put(IA64_32, IA64_32);
        archMapping.put("ia64n", IA64_32);

        // PowerPC mappings
        archMapping.put(PPC, PPC);
        archMapping.put("power", PPC);
        archMapping.put("powerpc", PPC);
        archMapping.put("power_pc", PPC);
        archMapping.put("power_rs", PPC);

        archMapping.put(PPC64, PPC64);
        archMapping.put("power64", PPC64);
        archMapping.put("powerpc64", PPC64);
        archMapping.put("power_pc64", PPC64);
        archMapping.put("power_rs64", PPC64);
        archMapping.put("ppc64el", PPC64);
        archMapping.put("ppc64le", PPC64);

        archMapping.put(RISCV64, RISCV64);
    }

    public static void main(String[] args) {
        if (args.length >= 1) {
            if ("--os".equals(args[0])) {
                System.out.print(getOSName());
                return;
            } else if ("--arch".equals(args[0])) {
                System.out.print(getArchName());
                return;
            }
        }

        System.out.print(getNativeLibFolderPathForCurrentOS());
    }

    public static String getNativeLibFolderPathForCurrentOS() {
        return getOSName() + "/" + getArchName();
    }

    public static String getOSName() {
        return translateOSNameToFolderName(System.getProperty("os.name"));
    }

    public static boolean isMusl() {
        Path mapFilesDir = Paths.get("/proc/self/map_files");
        try (Stream<Path> dirStream = Files.list(mapFilesDir)) {
            boolean found =
                    dirStream
                            .map(OSInfo::toRealPathOrEmpty)
                            .anyMatch(s -> s.toLowerCase().contains("musl"));
            if (found) {
                return true;
            }
        } catch (Exception ignored) {
        }
        // fall back to checking for alpine linux in the event we're using an older kernel which
        // may not fail the above check
        return isAlpineLinux();
    }

    private static String toRealPathOrEmpty(Path path) {
        try {
            return path.toRealPath().toString();
        } catch (IOException e) {
            return "";
        }
    }

    private static boolean isAlpineLinux() {
        try (Stream<String> osLines = Files.lines(Paths.get("/etc/os-release"))) {
            return osLines.anyMatch(l -> l.startsWith("ID") && l.contains("alpine"));
        } catch (Exception ignored2) {
        }
        return false;
    }

    static String getHardwareName() {
        return System.getProperty("os.arch", "unknown");
    }

    static String resolveArmArchType() {
        String architecture = getHardwareName().toLowerCase(Locale.ROOT);
        if (architecture.startsWith("armv6")) return "armv6";
        if (architecture.startsWith("armv7")) return "armv7";
        if (architecture.startsWith("aarch64") || architecture.startsWith("arm64")) {
            return "32".equals(System.getProperty("sun.arch.data.model")) ? "armv7" : "aarch64";
        }

        String abi = System.getProperty("sun.arch.abi", "");
        return abi.startsWith("gnueabihf") ? "armv7" : "arm";
    }

    public static String getArchName() {
        String override = System.getProperty("io.github.idoly.sqlite.osinfo.architecture");
        if (override != null) {
            return override;
        }

        String osArch = System.getProperty("os.arch");

        if (osArch.startsWith("arm")) {
            osArch = resolveArmArchType();
        } else {
            String lc = osArch.toLowerCase(Locale.US);
            if (archMapping.containsKey(lc)) return archMapping.get(lc);
        }
        return translateArchNameToFolderName(osArch);
    }

    static String translateOSNameToFolderName(String osName) {
        if (osName.contains("Windows")) {
            return "Windows";
        } else if (osName.contains("Mac") || osName.contains("Darwin")) {
            return "Mac";
        } else if (osName.contains("AIX")) {
            return "AIX";
        } else if (isMusl()) {
            return "Linux-Musl";
        } else if (osName.contains("Linux")) {
            return "Linux";
        } else {
            return osName.replaceAll("\\W", "");
        }
    }

    static String translateArchNameToFolderName(String archName) {
        return archName.replaceAll("\\W", "");
    }
}
