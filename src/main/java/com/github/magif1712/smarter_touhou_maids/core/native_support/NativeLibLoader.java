package com.github.magif1712.smarter_touhou_maids.core.native_support;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class NativeLibLoader {
    private static volatile boolean loaded;

    private NativeLibLoader() {
    }

    /**
     * 确保原生库已成功加载。
     * <p>
     * 这是加载原生库的主入口。在调用任何声明为 {@code native} 的方法之前，
     * 必须至少调用一次此方法以确保动态链接库（DLL/SO）已导入到 JVM 中。
     * </p>
     * 建议用法：
     * <pre>
     * public class MyNativeLogic {
     *     static {
     *         // 确保 DLL 在此类被使用前已加载，调用该函数会使得如果没加载动态链接库则加载一次动态链接库
     *         NativeLibLoader.ensureLoaded();
     *     }
     *
     *     // 接下来就可以安全地声明 native 函数了
     *     public static native void myGpuFunction();
     * }
     * </pre>
     *
     * @return {@code true} 表示原生库已成功加载
     * @throws IllegalStateException 如果无法找到或加载原生库
     */
    public static boolean ensureLoaded() {
        if (loaded) {
            return true;
        }
        synchronized (NativeLibLoader.class) {
            if (loaded) {
                return true;
            }
            loaded = tryLoad();
            if (!loaded) {
                throw new IllegalStateException("Failed to load stm_ai native library");
            }
            return loaded;
        }
    }

    private static boolean tryLoad() {
        // 原生库加载优先级（从高到低）：
        // 1) -Dstm.ai.native.path=/abs/path/to/stm_ai.dll|so|dylib  指定绝对路径（用于开发调试）
        // 2) 从 mod jar 资源里的 /natives/<os>-<arch>/ 解压到临时目录再 System.load()
        //    这一项要生效，必须在构建时把对应平台产物打进 jar（见 build.gradle 的 processResources）
        // 3) ./natives/<mappedName>  从工作目录的 natives 文件夹加载（用于手动投放）
        // 4) System.loadLibrary("stm_ai")  交给系统搜索路径（一般不建议依赖这个）
        String explicitPath = System.getProperty("stm.ai.native.path");
        if (explicitPath != null && !explicitPath.isBlank()) {
            File f = new File(explicitPath);
            if (f.exists()) {
                try {
                    System.load(f.getAbsolutePath());
                    return true;
                } catch (Throwable ignored) {
                    return false;
                }
            }
        }

        if (tryLoadFromBundledResource()) {
            return true;
        }

        File local = new File("natives", System.mapLibraryName("stm_ai"));
        if (local.exists()) {
            try {
                System.load(local.getAbsolutePath());
                return true;
            } catch (Throwable ignored) {
                return false;
            }
        }

        try {
            System.loadLibrary("stm_ai");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean tryLoadFromBundledResource() {
        String resourcePath = getBundledResourcePath();
        if (resourcePath == null) {
            return false;
        }

        try (InputStream in = NativeLibLoader.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                return false;
            }
            // 注意：System.load() 只能加载“真实文件路径”，不能直接加载 jar 内资源。
            // 所以这里会把 jar 里的库解压到临时目录（JVM 退出时清理），再调用 System.load(解压后的绝对路径)。
            Path dir = Files.createTempDirectory("stm_ai_");
            dir.toFile().deleteOnExit();
            Path outPath = dir.resolve(System.mapLibraryName("stm_ai"));
            try (OutputStream out = Files.newOutputStream(outPath)) {
                in.transferTo(out);
            }
            outPath.toFile().deleteOnExit();
            System.load(outPath.toAbsolutePath().toString());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String getBundledResourcePath() {
        // 这里的目录命名必须与 jar 内资源保持一致：
        //   /natives/windows-x86_64/stm_ai.dll
        //   /natives/linux-x86_64/libstm_ai.so
        //   /natives/macos-aarch64/libstm_ai.dylib
        // 其中文件名由 System.mapLibraryName("stm_ai") 决定，不同平台前后缀不同。
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();

        String osPart;
        if (os.contains("win")) {
            osPart = "windows";
        } else if (os.contains("mac") || os.contains("darwin")) {
            osPart = "macos";
        } else if (os.contains("nux") || os.contains("linux")) {
            osPart = "linux";
        } else {
            return null;
        }

        String archPart;
        if (arch.equals("amd64") || arch.equals("x86_64")) {
            archPart = "x86_64";
        } else if (arch.equals("aarch64") || arch.equals("arm64")) {
            archPart = "aarch64";
        } else {
            return null;
        }

        return "/natives/" + osPart + "-" + archPart + "/" + System.mapLibraryName("stm_ai");
    }
}