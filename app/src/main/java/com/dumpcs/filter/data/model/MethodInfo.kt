package com.dumpcs.filter.data.model

data class MethodInfo(
    val name: String,
    val rva: String,
    val rawLine: String,
    val offset: String = "",
    val va: String = ""
)
