package com.dumpcs.filter.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.dumpcs.filter.data.DumpMember
import com.dumpcs.filter.data.MemberKind
import com.dumpcs.filter.ui.ExplorerViewModel

enum class SearchScope { All, SelectedAssembly, SelectedNamespace, SelectedType }
enum class SearchKind { Namespaces, Types, Members }
enum class TemplateKind { Method, Field, Property, Event }

/**
 * 浏览页（移植自 DumpCsExplorer-Mobile）：
 * - 打开 dump.cs → Assemblies → Namespaces → Types → Members 树形浏览
 * - 搜索（范围筛选 + 种类筛选 + 命中高亮）
 * - 收藏类型 / 成员（持久化）
 * - Member 详情（RVA/Offset/VA/Signature 复制）+ Copy Hook 模板生成
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorerScreen(vm: ExplorerViewModel) {
    val ui by vm.ui.collectAsState()

    val favoriteTypes by vm.favoriteTypes.collectAsState()
    val favoriteMembers by vm.favoriteMembers.collectAsState()

    val hookTemplateMethod by vm.hookTemplateMethod.collectAsState()
    val hookTemplateField by vm.hookTemplateField.collectAsState()
    val hookTemplateProperty by vm.hookTemplateProperty.collectAsState()
    val hookTemplateEvent by vm.hookTemplateEvent.collectAsState()

    var query by remember { mutableStateOf("") }

    var searchScope by remember { mutableStateOf(SearchScope.All) }
    val kindEnabled = remember { mutableStateMapOf<SearchKind, Boolean>() }
    SearchKind.entries.forEach { k ->
        if (kindEnabled[k] == null) kindEnabled[k] = true
    }

    data class Selection(
        val asm: String = "",
        val ns: String = "",
        val typeName: String = ""
    )

    var selection by remember { mutableStateOf(Selection()) }

    fun matchRange(text: String, q: String): IntRange? {
        val queryTrim = q.trim()
        if (queryTrim.isBlank()) return null
        val idx = text.lowercase().indexOf(queryTrim.lowercase())
        if (idx < 0) return null
        val end = (idx + queryTrim.length).coerceAtMost(text.length)
        return idx until end
    }

    data class MemberDialogState(
        val asm: String,
        val ns: String,
        val typeName: String,
        val member: DumpMember
    )

    var selectedMember by remember { mutableStateOf<MemberDialogState?>(null) }
    var editingTemplates by remember { mutableStateOf(false) }
    var editingTemplateKind by remember { mutableStateOf(TemplateKind.Method) }

    fun paramCount(signature: String): Int {
        val open = signature.indexOf('(')
        val close = signature.lastIndexOf(')')
        if (open == -1 || close == -1 || close <= open) return 0
        val inside = signature.substring(open + 1, close).trim()
        if (inside.isBlank()) return 0
        return inside.split(',').count { it.trim().isNotEmpty() }
    }

    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    fun isExpanded(key: String) = expanded[key] == true
    fun toggle(key: String) {
        expanded[key] = !(expanded[key] == true)
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) vm.loadDump(uri)
    }

    val grouped = remember(ui.types) {
        ui.types
            .groupBy { it.assembly.ifBlank { "(未知)" } }
            .toSortedMap()
            .mapValues { (_, types) ->
                types.groupBy { it.nameSpace }.toSortedMap()
            }
    }

    data class SearchHit(
        val key: String,
        val title: String,
        val titleMatch: IntRange?,
        val subtitle: String,
        val targetRowKey: String,
        val onClick: () -> Unit
    )

    var pendingScrollKey by remember { mutableStateOf<String?>(null) }
    val treeListState = rememberLazyListState()

    val hits = remember(ui.types, query, searchScope, kindEnabled, selection) {
        val q = query.trim()
        if (q.isBlank()) return@remember emptyList<SearchHit>()

        val out = ArrayList<SearchHit>(256)

        fun inScope(asm: String, ns: String, typeName: String): Boolean {
            return when (searchScope) {
                SearchScope.All -> true
                SearchScope.SelectedAssembly -> selection.asm.isNotBlank() && asm == selection.asm
                SearchScope.SelectedNamespace -> selection.asm.isNotBlank() && selection.ns.isNotBlank() && asm == selection.asm && ns == selection.ns
                SearchScope.SelectedType -> selection.asm.isNotBlank() && selection.ns.isNotBlank() && selection.typeName.isNotBlank() &&
                    asm == selection.asm && ns == selection.ns && typeName == selection.typeName
            }
        }

        for (t in ui.types) {
            val asm = t.assembly.ifBlank { "(未知)" }
            val ns = t.nameSpace
            val typeName = t.name
            if (!inScope(asm, ns, typeName)) continue

            val asmKey = "asm:$asm"
            val nsKey = "$asmKey|ns:$ns"
            val typeKey = "$nsKey|type:$typeName"

            if (kindEnabled[SearchKind.Types] == true) {
                val typeHaystack = "$asm $ns $typeName".lowercase()
                if (typeHaystack.contains(q.lowercase())) {
                    out += SearchHit(
                        key = "type|$asm|$ns|$typeName",
                        title = typeName,
                        titleMatch = matchRange(typeName, q),
                        subtitle = "$asm • $ns • ${t.members.size} 个成员",
                        targetRowKey = typeKey,
                        onClick = {
                            selection = Selection(asm = asm, ns = ns, typeName = typeName)
                            pendingScrollKey = typeKey
                            query = ""
                        }
                    )
                }
            }

            if (kindEnabled[SearchKind.Namespaces] == true) {
                val nsHaystack = "$asm $ns".lowercase()
                if (nsHaystack.contains(q.lowercase())) {
                    out += SearchHit(
                        key = "ns|$asm|$ns",
                        title = ns,
                        titleMatch = matchRange(ns, q),
                        subtitle = asm,
                        targetRowKey = nsKey,
                        onClick = {
                            selection = Selection(asm = asm, ns = ns)
                            pendingScrollKey = nsKey
                            query = ""
                        }
                    )
                }
            }

            if (kindEnabled[SearchKind.Members] == true) {
                t.members.forEachIndexed { idx, m ->
                    val sig = m.signature.ifBlank { m.details }
                    val memberHaystack = "$asm $ns $typeName ${m.kind.name} $sig".lowercase()
                    if (!memberHaystack.contains(q.lowercase())) return@forEachIndexed

                    val category = when (m.kind) {
                        MemberKind.Method, MemberKind.Ctor -> "Methods"
                        MemberKind.Field, MemberKind.EnumValue -> "Fields"
                        MemberKind.Property -> "Properties"
                        MemberKind.Event -> "Events"
                    }
                    val catKey = "$typeKey|cat:$category"
                    val memberKey = when (m.kind) {
                        MemberKind.Method, MemberKind.Ctor -> "$typeKey|m:$idx"
                        MemberKind.Field, MemberKind.EnumValue -> "$typeKey|f:$idx"
                        MemberKind.Property -> "$typeKey|p:$idx"
                        MemberKind.Event -> "$typeKey|e:$idx"
                    }

                    out += SearchHit(
                        key = "mem|$asm|$ns|$typeName|$idx",
                        title = sig,
                        titleMatch = matchRange(sig, q),
                        subtitle = "$asm • $ns • $typeName • ${m.kind.name}",
                        targetRowKey = memberKey,
                        onClick = {
                            selection = Selection(asm = asm, ns = ns, typeName = typeName)
                            pendingScrollKey = memberKey
                            expanded[asmKey] = true
                            expanded[nsKey] = true
                            expanded[typeKey] = true
                            expanded[catKey] = true
                            query = ""
                            selectedMember = MemberDialogState(asm = asm, ns = ns, typeName = typeName, member = m)
                        }
                    )
                }
            }
        }

        out
    }

    data class Row(
        val key: String,
        val indent: Int,
        val title: String,
        val subtitle: String? = null,
        val expandable: Boolean = false,
        val expanded: Boolean = false,
        val onClick: (() -> Unit)? = null
    )

    data class MemberRowInfo(
        val key: String,
        val title: String,
        val onClick: () -> Unit
    )

    data class FavoriteMemberTarget(
        val sel: MemberDialogState,
        val memberIndex: Int
    )

    val expandedSnapshot by remember { derivedStateOf { expanded.toMap() } }

    val rows = remember(ui.types, grouped, expandedSnapshot, favoriteTypes, favoriteMembers) {
        val out = ArrayList<Row>(ui.types.size)

        fun memberGroupsLabel(kinds: Set<MemberKind>): String = buildString {
            val parts = ArrayList<String>(4)
            if (kinds.any { it == MemberKind.Method || it == MemberKind.Ctor }) parts += "Methods"
            if (kinds.any { it == MemberKind.Field || it == MemberKind.EnumValue }) parts += "Fields"
            if (kinds.any { it == MemberKind.Property }) parts += "Properties"
            if (kinds.any { it == MemberKind.Event }) parts += "Events"
            append(parts.joinToString(" • "))
        }

        fun categoryEntries(typeKey: String, title: String, indent: Int, members: List<MemberRowInfo>) {
            val catKey = "$typeKey|cat:$title"
            val catExpanded = isExpanded(catKey)
            out += Row(
                key = catKey,
                indent = indent,
                title = "$title (${members.size})",
                expandable = true,
                expanded = catExpanded,
                onClick = { toggle(catKey) }
            )
            if (!catExpanded) return
            for (m in members) {
                out += Row(
                    key = m.key,
                    indent = indent + 1,
                    title = m.title,
                    onClick = m.onClick
                )
            }
        }

        fun typeRowKey(asm: String, ns: String, typeName: String): String {
            val asmKey = "asm:$asm"
            val nsKey = "$asmKey|ns:$ns"
            return "$nsKey|type:$typeName"
        }

        val favRootKey = "fav:root"
        val favTypesKey = "fav:types"
        val favMembersKey = "fav:members"

        val favTypesExpanded = isExpanded(favTypesKey)
        val favMembersExpanded = isExpanded(favMembersKey)

        val favoriteTypeTargets = ui.types
            .mapNotNull { t ->
                val asm = t.assembly.ifBlank { "(未知)" }
                val key = vm.typeFavoriteKey(asm, t.nameSpace, t.name)
                if (!favoriteTypes.contains(key)) return@mapNotNull null
                Triple(asm, t.nameSpace, t.name)
            }
            .distinct()
            .sortedWith(compareBy({ it.first }, { it.second }, { it.third }))

        val favoriteMemberTargets = buildList {
            for (t in ui.types) {
                val asm = t.assembly.ifBlank { "(未知)" }
                val ns = t.nameSpace
                val typeName = t.name
                t.members.withIndex().forEach { (idx, m) ->
                    val key = vm.memberFavoriteKey(asm, ns, typeName, m)
                    if (favoriteMembers.contains(key)) {
                        add(FavoriteMemberTarget(sel = MemberDialogState(asm = asm, ns = ns, typeName = typeName, member = m), memberIndex = idx))
                    }
                }
            }
        }
            .distinctBy { vm.memberFavoriteKey(it.sel.asm, it.sel.ns, it.sel.typeName, it.sel.member) }
            .sortedWith(compareBy({ it.sel.asm }, { it.sel.ns }, { it.sel.typeName }, { it.sel.member.kind.name }, { it.sel.member.name }, { it.sel.member.rva.toString() }, { it.memberIndex }))

        out += Row(
            key = favRootKey,
            indent = 0,
            title = "收藏",
            subtitle = "类型: ${favoriteTypeTargets.size} • 成员: ${favoriteMemberTargets.size}",
            expandable = true,
            expanded = isExpanded(favRootKey),
            onClick = { toggle(favRootKey) }
        )

        if (isExpanded(favRootKey)) {
            out += Row(
                key = favTypesKey,
                indent = 1,
                title = "类型 (${favoriteTypeTargets.size})",
                expandable = true,
                expanded = favTypesExpanded,
                onClick = { toggle(favTypesKey) }
            )
            if (favTypesExpanded) {
                for ((asm, ns, typeName) in favoriteTypeTargets) {
                    val asmKey = "asm:$asm"
                    val nsKey = "$asmKey|ns:$ns"
                    val typeKey = "$nsKey|type:$typeName"
                    out += Row(
                        key = "fav|type|$asm|$ns|$typeName",
                        indent = 2,
                        title = typeName,
                        subtitle = "$asm • $ns",
                        onClick = {
                            selection = Selection(asm = asm, ns = ns, typeName = typeName)
                            expanded[asmKey] = true
                            expanded[nsKey] = true
                            expanded[typeKey] = true
                            pendingScrollKey = typeKey
                            query = ""
                        }
                    )
                }
            }

            out += Row(
                key = favMembersKey,
                indent = 1,
                title = "成员 (${favoriteMemberTargets.size})",
                expandable = true,
                expanded = favMembersExpanded,
                onClick = { toggle(favMembersKey) }
            )
            if (favMembersExpanded) {
                for (fav in favoriteMemberTargets) {
                    val sel = fav.sel
                    val memberIndex = fav.memberIndex
                    val m = sel.member
                    val sig = m.signature.ifBlank { m.details }
                    val asmKey = "asm:${sel.asm}"
                    val nsKey = "$asmKey|ns:${sel.ns}"
                    val typeKey = "$nsKey|type:${sel.typeName}"
                    val category = when (m.kind) {
                        MemberKind.Method, MemberKind.Ctor -> "Methods"
                        MemberKind.Field, MemberKind.EnumValue -> "Fields"
                        MemberKind.Property -> "Properties"
                        MemberKind.Event -> "Events"
                    }
                    val catKey = "$typeKey|cat:$category"
                    val memberKey = when (m.kind) {
                        MemberKind.Method, MemberKind.Ctor -> "$typeKey|m:$memberIndex"
                        MemberKind.Field, MemberKind.EnumValue -> "$typeKey|f:$memberIndex"
                        MemberKind.Property -> "$typeKey|p:$memberIndex"
                        MemberKind.Event -> "$typeKey|e:$memberIndex"
                    }
                    out += Row(
                        key = "fav|mem|${sel.asm}|${sel.ns}|${sel.typeName}|${m.rva}|$memberIndex",
                        indent = 2,
                        title = sig,
                        subtitle = "${sel.asm} • ${sel.ns} • ${sel.typeName} • ${m.kind.name}",
                        onClick = {
                            selection = Selection(asm = sel.asm, ns = sel.ns, typeName = sel.typeName)
                            expanded[asmKey] = true
                            expanded[nsKey] = true
                            expanded[typeKey] = true
                            expanded[catKey] = true
                            pendingScrollKey = memberKey
                            query = ""
                            selectedMember = sel
                        }
                    )
                }
            }
        }

        grouped.forEach { (asm, nsMap) ->
            val asmKey = "asm:$asm"
            val asmExpanded = isExpanded(asmKey)
            out += Row(
                key = asmKey,
                indent = 0,
                title = asm,
                expandable = true,
                expanded = asmExpanded,
                onClick = {
                    selection = Selection(asm = asm)
                    toggle(asmKey)
                }
            )

            if (!asmExpanded) return@forEach

            nsMap.forEach { (ns, types) ->
                val nsKey = "$asmKey|ns:$ns"
                val nsExpanded = isExpanded(nsKey)
                out += Row(
                    key = nsKey,
                    indent = 1,
                    title = ns,
                    expandable = true,
                    expanded = nsExpanded,
                    onClick = {
                        selection = Selection(asm = asm, ns = ns)
                        toggle(nsKey)
                    }
                )

                if (!nsExpanded) return@forEach

                types.sortedBy { it.name }.forEach { t ->
                    val typeKey = "$nsKey|type:${t.name}"
                    val typeExpanded = isExpanded(typeKey)
                    val kinds = t.members.mapTo(HashSet()) { it.kind }

                    val typeIsFav = favoriteTypes.contains(vm.typeFavoriteKey(asm, ns, t.name))
                    out += Row(
                        key = typeKey,
                        indent = 2,
                        title = (if (typeIsFav) "★ " else "") + t.name,
                        subtitle = "${t.members.size} 个成员" + (if (kinds.isNotEmpty()) " • ${memberGroupsLabel(kinds)}" else ""),
                        expandable = true,
                        expanded = typeExpanded,
                        onClick = {
                            selection = Selection(asm = asm, ns = ns, typeName = t.name)
                            toggle(typeKey)
                        }
                    )

                    if (!typeExpanded) return@forEach

                    val asmName = asm
                    val nsName = ns
                    val typeName = t.name

                    val methods = t.members.withIndex()
                        .filter { it.value.kind == MemberKind.Method || it.value.kind == MemberKind.Ctor }
                        .map { (i, m) ->
                            val memberIsFav = favoriteMembers.contains(vm.memberFavoriteKey(asmName, nsName, typeName, m))
                            MemberRowInfo(
                                key = "${typeKey}|m:$i",
                                title = (if (memberIsFav) "★ " else "") + (m.signature.ifBlank { m.details }),
                                onClick = {
                                    selectedMember = MemberDialogState(
                                        asm = asmName,
                                        ns = nsName,
                                        typeName = typeName,
                                        member = m
                                    )
                                }
                            )
                        }
                    val fields = t.members.withIndex()
                        .filter { it.value.kind == MemberKind.Field || it.value.kind == MemberKind.EnumValue }
                        .map { (i, m) ->
                            val memberIsFav = favoriteMembers.contains(vm.memberFavoriteKey(asmName, nsName, typeName, m))
                            MemberRowInfo(
                                key = "${typeKey}|f:$i",
                                title = (if (memberIsFav) "★ " else "") + (m.signature.ifBlank { m.details }),
                                onClick = {
                                    selectedMember = MemberDialogState(
                                        asm = asmName,
                                        ns = nsName,
                                        typeName = typeName,
                                        member = m
                                    )
                                }
                            )
                        }
                    val props = t.members.withIndex()
                        .filter { it.value.kind == MemberKind.Property }
                        .map { (i, m) ->
                            val memberIsFav = favoriteMembers.contains(vm.memberFavoriteKey(asmName, nsName, typeName, m))
                            MemberRowInfo(
                                key = "${typeKey}|p:$i",
                                title = (if (memberIsFav) "★ " else "") + (m.signature.ifBlank { m.details }),
                                onClick = {
                                    selectedMember = MemberDialogState(
                                        asm = asmName,
                                        ns = nsName,
                                        typeName = typeName,
                                        member = m
                                    )
                                }
                            )
                        }
                    val events = t.members.withIndex()
                        .filter { it.value.kind == MemberKind.Event }
                        .map { (i, m) ->
                            val memberIsFav = favoriteMembers.contains(vm.memberFavoriteKey(asmName, nsName, typeName, m))
                            MemberRowInfo(
                                key = "${typeKey}|e:$i",
                                title = (if (memberIsFav) "★ " else "") + (m.signature.ifBlank { m.details }),
                                onClick = {
                                    selectedMember = MemberDialogState(
                                        asm = asmName,
                                        ns = nsName,
                                        typeName = typeName,
                                        member = m
                                    )
                                }
                            )
                        }

                    if (methods.isNotEmpty()) categoryEntries(typeKey, "Methods", 3, methods)
                    if (fields.isNotEmpty()) categoryEntries(typeKey, "Fields", 3, fields)
                    if (props.isNotEmpty()) categoryEntries(typeKey, "Properties", 3, props)
                    if (events.isNotEmpty()) categoryEntries(typeKey, "Events", 3, events)
                }
            }
        }

        out
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Dump 浏览", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Text(
                            "打开 dump.cs 树形浏览 · 搜索 · 收藏 · Hook 模板",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { picker.launch(arrayOf("*/*")) },
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary
            ) {
                Text("打开文件")
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(12.dp)) {

            if (ui.isLoading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
            }
            ui.error?.let {
                Text("错误: $it", color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
            }

            if (!ui.isLoading && ui.error == null && ui.types.isEmpty()) {
                Text(
                    "尚未加载 dump.cs\n点击右下角「打开文件」选择文件",
                    color = MaterialTheme.colorScheme.outline,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                )
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("搜索") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    cursorColor = MaterialTheme.colorScheme.primary
                )
            )
            Spacer(Modifier.height(12.dp))

            if (query.trim().isNotBlank()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = searchScope == SearchScope.All,
                        onClick = { searchScope = SearchScope.All },
                        label = { Text("全部") }
                    )
                    FilterChip(
                        selected = searchScope == SearchScope.SelectedAssembly,
                        onClick = { searchScope = SearchScope.SelectedAssembly },
                        enabled = selection.asm.isNotBlank(),
                        label = { Text("程序集") }
                    )
                    FilterChip(
                        selected = searchScope == SearchScope.SelectedNamespace,
                        onClick = { searchScope = SearchScope.SelectedNamespace },
                        enabled = selection.asm.isNotBlank() && selection.ns.isNotBlank(),
                        label = { Text("命名空间") }
                    )
                    FilterChip(
                        selected = searchScope == SearchScope.SelectedType,
                        onClick = { searchScope = SearchScope.SelectedType },
                        enabled = selection.asm.isNotBlank() && selection.ns.isNotBlank() && selection.typeName.isNotBlank(),
                        label = { Text("类型") }
                    )
                }

                Spacer(Modifier.height(10.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SearchKind.entries.forEach { k ->
                        FilterChip(
                            selected = kindEnabled[k] == true,
                            onClick = { kindEnabled[k] = !(kindEnabled[k] == true) },
                            label = { Text(k.name) }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
            }

            if (query.trim().isNotBlank()) {
                LazyColumn {
                    items(hits, key = { it.key }) { h ->
                        Card(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { h.onClick() },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                if (h.titleMatch == null) {
                                    Text(h.title, style = MaterialTheme.typography.titleSmall)
                                } else {
                                    val start = h.titleMatch.first
                                    val end = h.titleMatch.last + 1
                                    val annotated = buildAnnotatedString {
                                        append(h.title.substring(0, start))
                                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                                            append(h.title.substring(start, end))
                                        }
                                        if (end < h.title.length) append(h.title.substring(end))
                                    }
                                    Text(annotated, style = MaterialTheme.typography.titleSmall)
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(h.subtitle, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(state = treeListState) {
                    items(rows, key = { it.key }) { r ->
                        val padStart = (r.indent * 16).dp
                        Card(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable(enabled = r.onClick != null) { r.onClick?.invoke() },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                                    .padding(start = padStart),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        r.title,
                                        style = when (r.indent) {
                                            0 -> MaterialTheme.typography.titleLarge
                                            1 -> MaterialTheme.typography.titleMedium
                                            else -> MaterialTheme.typography.titleSmall
                                        }
                                    )
                                    r.subtitle?.let { sub ->
                                        Spacer(Modifier.height(2.dp))
                                        Text(sub, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                if (r.expandable) {
                                    Text(
                                        text = if (r.expanded) "-" else "+",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(pendingScrollKey, rows) {
        val target = pendingScrollKey ?: return@LaunchedEffect
        val idx = rows.indexOfFirst { it.key == target }
        if (idx >= 0) {
            treeListState.animateScrollToItem(idx)
        }
        pendingScrollKey = null
    }

    selectedMember?.let { sel ->
        val clipboard = LocalClipboardManager.current
        val m = sel.member
        val sig = m.signature.ifBlank { m.details }
        val pc = paramCount(sig)

        val memberFavKey = vm.memberFavoriteKey(sel.asm, sel.ns, sel.typeName, m)
        val memberIsFav = favoriteMembers.contains(memberFavKey)

        val typeFavKey = vm.typeFavoriteKey(sel.asm, sel.ns, sel.typeName)
        val typeIsFav = favoriteTypes.contains(typeFavKey)

        fun copy(value: String) {
            clipboard.setText(AnnotatedString(value))
        }

        fun memberName(signature: String, fallback: String): String {
            val beforeParen = signature.substringBefore('(').trim()
            val lastToken = beforeParen.substringAfterLast(' ', missingDelimiterValue = beforeParen)
            return lastToken.ifBlank { fallback }
        }

        fun safeIdentifier(value: String): String {
            return value
                .replace(".", "_")
                .replace(Regex("[^A-Za-z0-9_]"), "_")
                .trim('_')
                .ifBlank { "member" }
        }

        fun renderTemplate(template: String, vars: Map<String, String>): String {
            val re = Regex("\\$\\{([A-Za-z0-9_]+)\\}")
            return re.replace(template) { mr ->
                val key = mr.groupValues[1]
                vars[key] ?: mr.value
            }
        }

        fun bnmSnippet(): String {
            val methodName = memberName(sig, m.name)
            val vars = linkedMapOf(
                "assemblyName" to sel.asm,
                "namespace" to sel.ns,
                "className" to sel.typeName,
                "memberName" to m.name,
                "fieldName" to (if (m.kind == MemberKind.Field || m.kind == MemberKind.EnumValue) m.name else ""),
                "methodName" to methodName,
                "parameterCount" to pc.toString(),
                "kind" to m.kind.name,
                "signature" to sig,
                "safeMethodName" to safeIdentifier(methodName)
            )

            val template = when (m.kind) {
                MemberKind.Method, MemberKind.Ctor -> hookTemplateMethod
                MemberKind.Field, MemberKind.EnumValue -> hookTemplateField
                MemberKind.Property -> hookTemplateProperty
                MemberKind.Event -> hookTemplateEvent
            }

            return renderTemplate(template, vars)
        }

        @Composable
        fun FieldRow(label: String, value: String) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(2.dp))
                    Text(value, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.width(12.dp))
                TextButton(onClick = { copy(value) }) { Text("复制") }
            }
        }

        val rvaStr = "0x${m.rva.toString(16)}"
        val offStr = "0x${m.offset.toString(16)}"
        val vaStr = "0x${m.va.toString(16)}"

        AlertDialog(
            onDismissRequest = { selectedMember = null },
            confirmButton = {
                TextButton(onClick = { selectedMember = null }) { Text("关闭") }
            },
            title = { Text("成员详情") },
            text = {
                LazyColumn {
                    item {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    modifier = Modifier.weight(1f),
                                    onClick = { vm.toggleTypeFavorite(sel.asm, sel.ns, sel.typeName) }
                                ) {
                                    Text(if (typeIsFav) "取消收藏类型" else "收藏类型", maxLines = 1)
                                }
                                TextButton(
                                    modifier = Modifier.weight(1f),
                                    onClick = { vm.toggleMemberFavorite(sel.asm, sel.ns, sel.typeName, m) }
                                ) {
                                    Text(if (memberIsFav) "取消收藏" else "收藏", maxLines = 1)
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        editingTemplateKind = when (m.kind) {
                                            MemberKind.Method, MemberKind.Ctor -> TemplateKind.Method
                                            MemberKind.Field, MemberKind.EnumValue -> TemplateKind.Field
                                            MemberKind.Property -> TemplateKind.Property
                                            MemberKind.Event -> TemplateKind.Event
                                        }
                                        editingTemplates = true
                                    }
                                ) {
                                    Text("模板", maxLines = 1)
                                }
                                TextButton(
                                    modifier = Modifier.weight(1f),
                                    onClick = { copy(bnmSnippet()) }
                                ) {
                                    Text("复制 Hook", maxLines = 1)
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                    item { FieldRow("程序集", sel.asm) }
                    item { Spacer(Modifier.height(8.dp)) }
                    item { FieldRow("命名空间", sel.ns) }
                    item { Spacer(Modifier.height(8.dp)) }
                    item { FieldRow("类名", sel.typeName) }
                    item { Spacer(Modifier.height(8.dp)) }
                    item { FieldRow("种类", m.kind.name) }
                    item { Spacer(Modifier.height(8.dp)) }
                    item { FieldRow("参数数量", pc.toString()) }
                    item { Spacer(Modifier.height(8.dp)) }
                    item { FieldRow("偏移", offStr) }
                    item { Spacer(Modifier.height(8.dp)) }
                    item { FieldRow("RVA", rvaStr) }
                    item { Spacer(Modifier.height(8.dp)) }
                    item { FieldRow("VA", vaStr) }
                    item { Spacer(Modifier.height(8.dp)) }
                    item { FieldRow("签名", sig) }
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (editingTemplates) {
        var methodText by remember(hookTemplateMethod) { mutableStateOf(hookTemplateMethod) }
        var fieldText by remember(hookTemplateField) { mutableStateOf(hookTemplateField) }
        var propText by remember(hookTemplateProperty) { mutableStateOf(hookTemplateProperty) }
        var eventText by remember(hookTemplateEvent) { mutableStateOf(hookTemplateEvent) }

        val currentText = when (editingTemplateKind) {
            TemplateKind.Method -> methodText
            TemplateKind.Field -> fieldText
            TemplateKind.Property -> propText
            TemplateKind.Event -> eventText
        }

        AlertDialog(
            onDismissRequest = { editingTemplates = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        when (editingTemplateKind) {
                            TemplateKind.Method -> vm.setHookTemplateMethod(methodText)
                            TemplateKind.Field -> vm.setHookTemplateField(fieldText)
                            TemplateKind.Property -> vm.setHookTemplateProperty(propText)
                            TemplateKind.Event -> vm.setHookTemplateEvent(eventText)
                        }
                        editingTemplates = false
                    }
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { vm.resetHookTemplatesToDefault() }) { Text("重置默认") }
            },
            title = { Text("Hook 模板") },
            text = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TemplateKind.entries.forEach { k ->
                            FilterChip(
                                selected = editingTemplateKind == k,
                                onClick = { editingTemplateKind = k },
                                label = { Text(k.name) }
                            )
                        }
                    }

                    Text(
                        "占位符: ${'$'}{assemblyName} ${'$'}{namespace} ${'$'}{className} ${'$'}{memberName} ${'$'}{fieldName} ${'$'}{methodName} ${'$'}{parameterCount} ${'$'}{safeMethodName}",
                        style = MaterialTheme.typography.bodySmall
                    )

                    OutlinedTextField(
                        value = currentText,
                        onValueChange = { v ->
                            when (editingTemplateKind) {
                                TemplateKind.Method -> methodText = v
                                TemplateKind.Field -> fieldText = v
                                TemplateKind.Property -> propText = v
                                TemplateKind.Event -> eventText = v
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 8
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}
