package com.dumpcs.filter.data.parser

import com.dumpcs.filter.data.model.ClassInfo
import com.dumpcs.filter.data.model.FieldInfo
import com.dumpcs.filter.data.model.MethodInfo
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

class DumpCSParser {

    fun parse(inputStream: InputStream, onProgress: (Float) -> Unit): List<ClassInfo> {
        val reader = BufferedReader(InputStreamReader(inputStream))
        val classes = mutableListOf<ClassInfo>()
        // 流式逐行读取，避免大文件整体载入内存（O(N) → O(1)）
        val total = -1
        var lineCount = 0L

        var currentNamespace = ""
        var currentClassName = ""
        var currentParent = ""
        val currentMethods = mutableListOf<MethodInfo>()
        val currentFields = mutableListOf<FieldInfo>()
        var inClass = false

        fun flushClass() {
            if (inClass && currentClassName.isNotEmpty()) {
                classes.add(
                    ClassInfo(
                        namespace = currentNamespace,
                        className = currentClassName,
                        parentClass = currentParent,
                        methods = currentMethods.toList(),
                        fields = currentFields.toList()
                    )
                )
                currentMethods.clear()
                currentFields.clear()
                currentClassName = ""
                currentParent = ""
                inClass = false
            }
        }

        reader.forEachLine { rawLine ->
            lineCount++
            val line = rawLine.trim()

            when {
                line.startsWith("// Namespace:") -> {
                    currentNamespace = line.removePrefix("// Namespace:").trim()
                }
                line.startsWith("public class ") || line.startsWith("private class ") ||
                line.startsWith("internal class ") || line.startsWith("class ") ||
                line.startsWith("public abstract class ") || line.startsWith("public sealed class ") -> {
                    flushClass()
                    currentClassName = extractClassName(line)
                    currentParent = extractParentClass(line)
                    inClass = true
                }
                line.contains("// RVA:") && inClass -> {
                    val method = parseMethod(line)
                    if (method != null) currentMethods.add(method)
                }
                line.contains("private ") || line.contains("public ") || line.contains("protected ") -> {
                    if (inClass && !line.contains("(") && !line.contains("// RVA:")) {
                        val field = parseField(line)
                        if (field != null) currentFields.add(field)
                    }
                }
                line == "}" && inClass -> {
                    flushClass()
                }
            }

            if (lineCount % 2000 == 0L) {
                onProgress(0.5f + 0.5f * (lineCount % 1000000) / 1000000f)
            }
        }

        // 处理文件末尾未闭合的类
        flushClass()

        onProgress(1f)
        return classes
    }

    private fun extractClassName(line: String): String {
        val regex = Regex("class\\s+(\\w+)")
        return regex.find(line)?.groupValues?.get(1) ?: "Unknown"
    }

/**
     * 提取父类/接口（类名扩展）："public class Foo : Bar, IBaz // TypeDefIndex:1" → "Bar, IBaz"
     */
    private fun extractParentClass(line: String): String {
        val colonIdx = line.indexOf(" : ")
        if (colonIdx < 0) return ""
        var tail = line.substring(colonIdx + 3).trim()
        tail = tail.substringBefore(" //").trim()
        return tail
    }

    private fun parseMethod(line: String): MethodInfo? {
        val rvaRegex = Regex("//\\s*RVA:\\s*(0x[0-9A-Fa-f]+)")
        val rva = rvaRegex.find(line)?.groupValues?.get(1) ?: return null
        val offset = Regex("Offset:\\s*(0x[0-9A-Fa-f]+)").find(line)?.groupValues?.get(1) ?: ""
        val va = Regex("VA:\\s*(0x[0-9A-Fa-f]+)").find(line)?.groupValues?.get(1) ?: ""
        val name = line.substringBefore("(").trim().split(" ").lastOrNull() ?: "Unknown"
        return MethodInfo(name = name, rva = rva, rawLine = line, offset = offset, va = va)
    }

    private fun parseField(line: String): FieldInfo? {
        // 支持带注释格式："public int hp; //0x10" → 声明部分在 // 前
        val decl = line.substringBefore("//").trim()
        val name = decl.split(" ").lastOrNull()?.removeSuffix(";") ?: return null
        val offset = Regex("//\\s*(0x[0-9A-Fa-f]+)").find(line)?.groupValues?.get(1) ?: ""
        return FieldInfo(name = name, rawLine = line, offset = offset)
    }
}
