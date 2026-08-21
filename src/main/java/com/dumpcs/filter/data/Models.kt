package com.dumpcs.filter.data

/**
 * dump.cs 结构化浏览模型（移植自 DumpCsExplorer-Mobile）
 */
enum class MemberKind { Method, Ctor, Field, Property, Event, EnumValue }

data class DumpMember(
    val kind: MemberKind,
    val name: String = "",
    val signature: String = "",
    val details: String = "",
    val rva: ULong = 0u,
    val offset: ULong = 0u,
    val va: ULong = 0u,
    val enumValue: String = ""
)

data class DumpType(
    val assembly: String = "",
    val name: String,
    val nameSpace: String,
    val isEnum: Boolean = false,
    val typeDefIndex: Int = -1,
    val members: List<DumpMember> = emptyList()
)
