package com.dumpcs.filter.data.model

data class FilterResult(
    val fileName: String,
    val keywords: List<String>,
    val classes: List<ClassInfo>,
    val timestamp: Long = System.currentTimeMillis(),
    val excludedCount: Int = 0,
    val excludedHint: String = ""
)
