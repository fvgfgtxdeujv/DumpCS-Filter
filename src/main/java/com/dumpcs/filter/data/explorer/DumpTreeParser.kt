package com.dumpcs.filter.data.explorer

import java.io.BufferedReader

/**
 * dump.cs 结构化解析器（移植自 DumpCsExplorer-Mobile）
 * 流式逐行解析：Assemblies(Image) → Namespaces → Types → Members
 */
object DumpTreeParser {

    private data class ImageMapEntry(
        val baseTypeDefIndex: Int,
        val assembly: String
    )

    private val rxImage = Regex("""//\s*Image\s+\d+:\s*(.+?)\s*-\s*(\d+)\s*""")
    private val rxNamespace = Regex("""//\s*Namespace:\s*(.*)""")
    private val rxType = Regex("""(class|struct|enum|interface)\s+([^\s:{]+)""")
    private val rxTypeDefIndex = Regex("""TypeDefIndex:\s*(\d+)""")
    private val rxSection = Regex("""//\s*(Methods|Fields|Properties|Events)""")

    private val rxRva = Regex("""RVA:\s*0x([0-9A-Fa-f]+)""")
    private val rxOff = Regex("""Offset:\s*0x([0-9A-Fa-f]+)""")
    private val rxVa = Regex("""VA:\s*0x([0-9A-Fa-f]+)""")

    private val rxInlineHex = Regex("""0x([0-9A-Fa-f]+)""")

    private val rxMethod = Regex(
        """^\s*((?:(?:public|private|protected|internal|static|virtual|override|abstract|sealed|extern)\s+)*)\s*(?:([\w<>\[\],\.]+)\s+)?([\w_]+)\s*\(([^)]*)\)"""
    )

    private val rxEnumValue = Regex(
        """^\s*(?:public|private|protected|internal)?\s*(?:const\s+)?[\w<>\[\],\.]+\s+([\w_]+)\s*=\s*([^;]+)"""
    )

    private fun stripInlineComment(s: String): String {
        val p = s.indexOf("//")
        return if (p == -1) s.trim() else s.substring(0, p).trim()
    }

    private fun parseHex(s: String): ULong = s.toULong(16)

    fun parse(reader: BufferedReader): List<DumpType> {
        val types = ArrayList<DumpType>()
        val membersByType = ArrayList<MutableList<DumpMember>>()

        val images = ArrayList<ImageMapEntry>()

        var currentNs = "-"
        var currentTypeIndex = -1
        var section = ""

        var hasPending = false
        var pendingRva = 0uL
        var pendingOff = 0uL
        var pendingVa = 0uL

        reader.forEachLine { raw ->
            val s = raw.trim()

            rxImage.find(s)?.let {
                val assembly = it.groupValues[1].trim()
                val baseTypeDefIndex = it.groupValues[2].toIntOrNull() ?: -1
                if (assembly.isNotBlank() && baseTypeDefIndex >= 0) {
                    images += ImageMapEntry(baseTypeDefIndex = baseTypeDefIndex, assembly = assembly)
                }
                return@forEachLine
            }

            rxNamespace.find(s)?.let {
                currentNs = it.groupValues[1].ifBlank { "-" }
                return@forEachLine
            }

            rxType.find(s)?.let { mt ->
                val kind = mt.groupValues[1]
                val typeName = mt.groupValues[2]
                val isEnum = kind == "enum"

                val typeDefIndex = rxTypeDefIndex.find(s)?.groupValues?.get(1)?.toIntOrNull() ?: -1
                val assembly = run {
                    var bestBase = -1
                    var bestAsm = ""
                    for (img in images) {
                        if (img.baseTypeDefIndex <= typeDefIndex && img.baseTypeDefIndex >= bestBase) {
                            bestBase = img.baseTypeDefIndex
                            bestAsm = img.assembly
                        }
                    }
                    bestAsm
                }

                types += DumpType(
                    assembly = assembly,
                    name = typeName,
                    nameSpace = currentNs,
                    isEnum = isEnum,
                    typeDefIndex = typeDefIndex
                )

                membersByType.add(mutableListOf())

                currentTypeIndex = types.lastIndex

                section = ""
                hasPending = false
                pendingRva = 0uL; pendingOff = 0uL; pendingVa = 0uL
                return@forEachLine
            }

            if (currentTypeIndex < 0) return@forEachLine

            rxSection.find(s)?.let {
                section = it.groupValues[1]
                return@forEachLine
            }

            if (section.isBlank()) return@forEachLine

            val type = types[currentTypeIndex]
            val members = membersByType[currentTypeIndex]

            if (type.isEnum && section == "Fields") {
                if (s.contains("value__")) {
                    val sig = stripInlineComment(s)
                    val mh = rxInlineHex.find(s)
                    val off = mh?.groupValues?.get(1)?.let(::parseHex) ?: 0uL
                    val details = if (off != 0uL) "$sig\nOffset: 0x${off.toString(16)}" else sig
                    members += DumpMember(kind = MemberKind.Field, signature = sig, details = details, offset = off)
                    return@forEachLine
                }
                rxEnumValue.find(s)?.let { me ->
                    val name = me.groupValues[1]
                    val value = me.groupValues[2].trim()
                    members += DumpMember(
                        kind = MemberKind.EnumValue,
                        name = name,
                        enumValue = value,
                        signature = "$name = $value",
                        details = "$name = $value"
                    )
                }
                return@forEachLine
            }

            if (section == "Methods") {
                if (s.startsWith("//") && (s.contains("RVA:") || s.contains("Offset:") || s.contains("VA:"))) {
                    rxRva.find(s)?.let { pendingRva = parseHex(it.groupValues[1]) }
                    rxOff.find(s)?.let { pendingOff = parseHex(it.groupValues[1]) }
                    rxVa.find(s)?.let { pendingVa = parseHex(it.groupValues[1]) }
                    hasPending = true
                    return@forEachLine
                }

                if (s.isEmpty() || s == "{" || s == "}" || s.startsWith("//")) return@forEachLine
                if (!hasPending) return@forEachLine

                val mm = rxMethod.find(s) ?: return@forEachLine
                val modifiers = mm.groupValues[1].trim()
                val returnType = mm.groupValues[2].trim()
                val name = mm.groupValues[3]
                val params = mm.groupValues[4].trim()

                val kind = when {
                    name == type.name -> MemberKind.Ctor
                    name.startsWith("get_") || name.startsWith("set_") -> MemberKind.Property
                    name.startsWith("add_") || name.startsWith("remove_") -> MemberKind.Event
                    else -> MemberKind.Method
                }

                val fixedName = if (kind == MemberKind.Ctor) ".ctor" else name
                val fixedReturn = if (kind == MemberKind.Ctor) "" else returnType

                val sig = buildString {
                    if (modifiers.isNotBlank()) append(modifiers).append(' ')
                    if (fixedReturn.isNotBlank()) append(fixedReturn).append(' ')
                    append(fixedName).append('(').append(params).append(')')
                }

                val off = if (pendingOff != 0uL) pendingOff else pendingRva
                val details =
                    "$sig\nRVA: 0x${pendingRva.toString(16)}  Offset: 0x${off.toString(16)}  VA: 0x${pendingVa.toString(16)}"

                members += DumpMember(
                    kind = kind,
                    name = fixedName,
                    signature = sig,
                    details = details,
                    rva = pendingRva,
                    offset = off,
                    va = pendingVa
                )

                hasPending = false
                pendingRva = 0uL; pendingOff = 0uL; pendingVa = 0uL
                return@forEachLine
            }

            if (section == "Fields" || section == "Properties" || section == "Events") {
                if (s.isEmpty() || s == "{" || s == "}" || s.startsWith("//")) return@forEachLine

                val kind = when (section) {
                    "Fields" -> MemberKind.Field
                    "Properties" -> MemberKind.Property
                    else -> MemberKind.Event
                }

                val sig = stripInlineComment(s)
                val mh = rxInlineHex.find(s)
                val off = mh?.groupValues?.get(1)?.let(::parseHex) ?: 0uL
                val details = if (off != 0uL) "$sig\nOffset: 0x${off.toString(16)}" else sig

                members += DumpMember(kind = kind, signature = sig, details = details, offset = off)
            }
        }

        return types.mapIndexed { i, t -> t.copy(members = membersByType[i]) }
    }
}
