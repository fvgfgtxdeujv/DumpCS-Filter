package com.dumpcs.filter.data

data class ClassInfo(
    val namespace: String,
    val className: String,
    val parentClass: String = "",
    val methods: List<MethodInfo>,
    val fields: List<FieldInfo>
) {
    /** 完整类名（命名空间.类名），用于列表展示 */
    val fullName: String
        get() = if (namespace.isNotBlank()) "$namespace.$className" else className

    /** 匹配范围：命名空间 + 完整类名 + 父类 + 方法名 + 字段名（全部转小写） */
    fun containsKeyword(keywords: List<String>): Boolean {
        val allText = buildString {
            append(namespace.lowercase())
            append(" ")
            append(className.lowercase())
            append(" ")
            if (parentClass.isNotBlank()) append(parentClass.lowercase()).append(" ")
            methods.forEach { append(it.name.lowercase()).append(" ") }
            fields.forEach { append(it.name.lowercase()).append(" ") }
        }
        return keywords.any { kw -> allText.contains(kw) }
    }

    /** 是否命中排除词（连接/桥梁等无关类） */
    fun containsExclude(excludes: List<String>): Boolean {
        val allText = buildString {
            append(namespace.lowercase())
            append(" ")
            append(className.lowercase())
            append(" ")
            if (parentClass.isNotBlank()) append(parentClass.lowercase()).append(" ")
            methods.forEach { append(it.name.lowercase()).append(" ") }
            fields.forEach { append(it.name.lowercase()).append(" ") }
        }
        return excludes.any { ex -> allText.contains(ex) }
    }
}
