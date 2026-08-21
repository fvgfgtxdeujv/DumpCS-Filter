package com.rodroid.il2cppdumper.utils;

/**
 * Il2CppDumper JNI 桥接（Dumper By.wuhai / com.rodroid.il2cppdumper 引擎移植）
 * 类名/方法签名必须与 librodroid_il2cppdumper.so 的 JNI 静态符号完全一致：
 *   Java_com_rodroid_il2cppdumper_utils_DumperBridge_*
 */
public final class DumperBridge {
    public static final DumperBridge INSTANCE = new DumperBridge();

    static {
        System.loadLibrary("rodroid_il2cppdumper");
    }

    private DumperBridge() {
    }

    /**
     * 检测文件格式（"ELF64"/"ELF32"/"Metadata" 等）
     */
    public native String detectFormat(String path);

    /**
     * 执行 Dump。
     * @param binaryPath  libil2cpp.so 路径
     * @param metadataPath global-metadata.dat 路径
     * @param outputDir   输出目录（dump.cs 等输出到这里）
     * @param configJson  DumperConfig JSON
     * @param callback    进度/输入回调
     * @return 输出文件路径或错误信息
     */
    public native String dumpIl2Cpp(String binaryPath, String metadataPath,
                                    String outputDir, String configJson,
                                    DumperCallback callback);

    /**
     * 获取引擎默认配置 JSON
     */
    public native String getDefaultConfig();

    /**
     * 初始化 native crash handler
     */
    public native void initCrashHandler(String logPath);
}