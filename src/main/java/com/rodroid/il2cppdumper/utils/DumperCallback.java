package com.rodroid.il2cppdumper.utils;

/**
 * Il2Cpp Dumper native 回调接口（与 librodroid_il2cppdumper.so 的 JNI 绑定匹配）
 */
public interface DumperCallback {
    /** 引擎实时日志 */
    void onLog(String log);

    /**
     * 引擎请求输入（如手动地址等）。
     * @return 用户输入；返回 null/空 表示跳过
     */
    String onRequestInput(String request);
}