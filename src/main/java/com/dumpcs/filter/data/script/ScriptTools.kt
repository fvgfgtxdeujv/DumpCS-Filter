package com.dumpcs.filter.data.script

import java.io.File

/**
 * Lua 工具集 → Kotlin 1:1 移植
 * 原脚本为 GameGuardian(Lua) 工具，这里把每个工具的生成逻辑完整复刻，
 * 输出仍是 GG 可执行的 .lua 脚本（GG 脚本必须为 Lua）。
 *
 * 工具清单：
 *  a) 基址写法 —— 生成 setPtr 指针修改脚本
 *  b) 单机功能生成器 —— 从 dump.cs 提取目标方法 RVA，生成功能修改脚本
 *  c) 菜单生成器 —— 生成单选/多选 GG 菜单脚本
 *  d) 静态基址扫描工具 —— 从地址列表生成静态基址脚本
 *  e) 偏移指针工具 —— 从特征列表生成偏移指针脚本
 *  f) 去除 Lua 语言注释 —— 清理 -- 注释并备份
 */
object ScriptTools {

    /** GG 修改类型映射（与原版一致：Dword=4 Float=16 Qword=32） */
    fun ggTypeName(t: Int): String = when (t) {
        4 -> "Dword"
        16 -> "Float"
        32 -> "Qword"
        5 -> "Double"
        else -> "类型$t"
    }

    // a) 基址写法：生成 setPtr 指针修改脚本
    // 原版：inputs = {模块名, 偏移链, 修改类型, 修改值, 64位?, 保存路径}
    fun genBaseScript(
        module: String,
        chain: String,          // 如 "0x0, 0x10, 0x20"
        type: String,           // "4" / "16" / "32"
        value: String,
        is64: Boolean
    ): String {
        val call = buildString {
            append("setPtr(\"$module, $chain\", $type, $value")
            if (!is64) append(", false")
            append(")")
        }
        return """function setPtr(str, type, val, is64)
is64 = is64 == nil or is64
local toNum, parts, base, addr, ptr, off, res
toNum = function(s)
 sign = 1
if s:sub(1,1) == "-" then sign = -1; s = s:sub(2) end
if s:sub(1,2) == "0x" then return sign * tonumber(s:sub(3), 16) end
return sign * tonumber(s)
end
parts = {}
for w in str:gmatch("[^,]+") do
w = w:gsub("%s", "")
if #w > 0 then parts[#parts+1] = w end
end
if #parts < 2 then return end
base = (gg.getRangesList(parts[1])[1] or {}).start
if not base then return end
addr = base
ptr = is64 and gg.TYPE_QWORD or gg.TYPE_DWORD
for i = 2, #parts - 1 do
off = toNum(parts[i])
if not off then return end
addr = addr + off
res = gg.getValues({{address = addr, flags = ptr}})
if #res == 0 then return end
addr = res[1].value
if not is64 and addr < 0 then addr = addr & 0xFFFFFFFF end
end
off = toNum(parts[#parts])
if not off then return end
addr = addr + off
gg.setValues({{address = addr, flags = type, value = val}})
end

$call
"""
    }

    // b) 单机功能生成器：从 dump.cs 提取目标方法 RVA
    // 目标方法（与原版完全一致）
    val targetMethods = listOf(
        "ShowFreeViewOcclustionEffect",
        "GetAssistAimRotateTime",
        "GetAutoAssistAimRate",
        "virtual.-SingleLineCheckPhysics",
        "override.-SingleLineCheckPhysics",
        "get_MaxJumpHeightScale",
        "get_SlideTackleSpeed",
        "GetScaleRecoil",
        "GetRealSpreadModifier",
        "GetCurrentWeaponKillEffect"
    )

    /** 解析 dump.cs（流式版，大文件不爆内存），返回 方法名->RVA 映射（原版 getCoreName + RVA 匹配逻辑 1:1） */
    fun parseDumpCs(reader: java.io.Reader): Map<String, String> {
        fun getCoreName(method: String): String =
            method.replace(Regex("^[vo][^.]-.-"), "")
        val methodMap = LinkedHashMap<String, String>()
        var lastRVA: String? = null
        val rvaRegex = Regex("^\\s*//\\s*RVA:\\s*(0?x?[0-9a-fA-F]+)")
        reader.forEachLine { line ->
            val rva = rvaRegex.find(line)?.groupValues?.get(1)
            if (rva != null) {
                lastRVA = if (rva.startsWith("0x")) rva else "0x$rva"
            } else if (lastRVA != null) {
                val curRVA: String = lastRVA!!
                for (method in targetMethods) {
                    if (!methodMap.containsKey(method)) {
                        val core = getCoreName(method)
                        if (line.contains(core)) {
                            var needCheckModifier = false
                            var modifier = ""
                            if (method.startsWith("virtual.-")) {
                                needCheckModifier = true
                                modifier = "virtual"
                            } else if (method.startsWith("override.-")) {
                                needCheckModifier = true
                                modifier = "override"
                            }
                            if (!needCheckModifier) {
                                methodMap[method] = curRVA
                            } else {
                                if (line.contains(modifier)) {
                                    methodMap[method] = curRVA
                                }
                            }
                        }
                    }
                }
                lastRVA = null
            }
        }
        return methodMap
    }

    /** 解析 dump.cs（字符串版，内部委托流式实现，兼容旧调用） */
    fun parseDumpCs(content: String): Map<String, String> =
        parseDumpCs(java.io.StringReader(content))

    /** 生成功能脚本（rt热透/zm自瞄/fw范围/gt高跳/hc滑铲/wh无后/jd聚点 + 击杀特效） */
    fun genFeatureScript(offsets: List<String>, killOffset: String?): String {
        // offsets 顺序：1热透 2自瞄A 3自瞄B 4范围A 5范围B 6高跳 7滑铲 8无后（9击杀单独）
        val o = offsets
        val sb = StringBuilder()
        sb.append("""function setvalue(address,flags,value)
local tt={}
tt[1]={}
tt[1].address=address
tt[1].flags=flags
tt[1].value=value
gg.setValues(tt)
end
so=gg.getRangesList('libunity.so')[1].start

local function rt()
CZ=""")
        sb.append(o.getOrElse(0) { "0x0" }).append("\n")
        sb.append("""setvalue(so+CZ,4,"~A8 NOP")
gg.toast("热透开启成功")
end
local function zm()
CZ=""")
        sb.append(o.getOrElse(1) { "0x0" }).append("\n")
        sb.append("""setvalue(so+CZ,4,"~A8 LDR S0, [PC,#0x8]")
CZ=""")
        sb.append(o.getOrElse(1) { "0x0" }).append("""+0x4
setvalue(so+CZ,4,"~A8 RET")
CZ=""")
        sb.append(o.getOrElse(1) { "0x0" }).append("""+0x8
setvalue(so+CZ,16,0.00001)
CZ=""")
        sb.append(o.getOrElse(2) { "0x0" }).append("\n")
        sb.append("""setvalue(so+CZ,4,"~A8 LDR S0, [PC,#0x8]")
CZ=""")
        sb.append(o.getOrElse(2) { "0x0" }).append("""+0x4
setvalue(so+CZ,4,"~A8 RET")
CZ=""")
        sb.append(o.getOrElse(2) { "0x0" }).append("""+0x8
setvalue(so+CZ,16,50)
gg.toast("自瞄开启成功")
end
local function fw()
CZ=""")
        sb.append(o.getOrElse(3) { "0x0" }).append("\n")
        sb.append("""setvalue(so+CZ,4,"~A8 MOV X0, #0x9")
CZ=""")
        sb.append(o.getOrElse(3) { "0x0" }).append("""+0x4
setvalue(so+CZ,4,"~A8 RET")

CZ=""")
        sb.append(o.getOrElse(4) { "0x0" }).append("\n")
        sb.append("""setvalue(so+CZ,4,"~A8 MOV X0, #0x9")
CZ=""")
        sb.append(o.getOrElse(4) { "0x0" }).append("""+0x4
setvalue(so+CZ,4,"~A8 RET")
gg.toast("范围开启成功")
end
function gt()
CZ=""")
        sb.append(o.getOrElse(5) { "0x0" }).append("\n")
        sb.append("""setvalue(so+CZ,5,"~A8 LDR S0, [PC,#0x8]")
CZ=""")
        sb.append(o.getOrElse(5) { "0x0" }).append("""+0x4
setvalue(so+CZ,5,"~A8 RET")
CZ=""")
        sb.append(o.getOrElse(5) { "0x0" }).append("""+0x8
setvalue(so+CZ,16,5)
gg.toast("高跳开启成功")
end
function hc()
CZ=""")
        sb.append(o.getOrElse(6) { "0x0" }).append("\n")
        sb.append("""setvalue(so+CZ,4,"~A8 LDR S0, [PC,#0x8]")
CZ=""")
        sb.append(o.getOrElse(6) { "0x0" }).append("""+0x4
setvalue(so+CZ,4,"~A8 RET")
CZ=""")
        sb.append(o.getOrElse(6) { "0x0" }).append("""+0x8
setvalue(so+CZ,16,15)
gg.toast("滑铲开启成功")
end
function wh()
CZ=""")
        sb.append(o.getOrElse(7) { "0x0" }).append("\n")
        sb.append("""setvalue(so+CZ,4,"~A8 LDR S0, [PC,#0x8]")
CZ=""")
        sb.append(o.getOrElse(7) { "0x0" }).append("""+0x4
setvalue(so+CZ,4,"~A8 RET")
CZ=""")
        sb.append(o.getOrElse(7) { "0x0" }).append("""+0x8
setvalue(so+CZ,16,0.00001)
gg.toast("无后开启成功")
end
function jd()
CZ=""")
        sb.append(o.getOrElse(8) { "0x0" }).append("\n")
        sb.append("""setvalue(so+CZ,32,"h200080D2C0035FD6")
gg.toast("聚点开启成功")
end
local menuList = {
"热透","自瞄","范围","高跳","滑铲","无后","聚点","退出"
}
local funcMap = {rt,zm,fw,gt,hc,wh,jd}
local function showMenu()
local select = gg.multiChoice(menuList, nil, "wuhai基址脚本")
if not select then return end
for i=1,#funcMap do
if select[i] then funcMap[i]() end
end
if select[#menuList] then os.exit() end
end
while true do
if gg.isVisible(true) then
gg.setVisible(false)
gg.clearResults()
showMenu()
end
gg.sleep(100)
end
""")
        return sb.toString()
    }

    /** 生成击杀特效脚本 */
    fun genKillScript(killOffset: String): String = """function setvalue(address,flags,value)
local tt={}
tt[1]={}
tt[1].address=address
tt[1].flags=flags
tt[1].value=value
gg.setValues(tt)
end
so=gg.getRangesList('libunity.so')[1].start
CZ=$killOffset
setvalue(so+CZ,4,"~A8 LDR X0, [PC,#0x8]")
CZ=$killOffset+0x4
setvalue(so+CZ,4,"~A8 RET")
CZ=$killOffset+0x8
setvalue(so+CZ,4,"800040")
"""

    // c) 菜单生成器：generateScript(L, N, mode)
    // mode: 1=单选无圆形 2=单选有圆形 3=多选
    fun genMenuScript(L: Int, N: Int, mode: Int): String {
        val funcDefs = StringBuilder()
        val menuDefs = StringBuilder()
        var funcCounter = 0
        for (i in 1..L) {
            for (j in 1..N) {
                funcCounter++
                funcDefs.append("""
function f$funcCounter()
    gg.toast("子菜单$i - 功能$j 开启成功")
end
""".trimStart())
            }
        }
        for (i in 1..L) {
            val menuName = "SubMenu$i"
            val lines = StringBuilder()
            lines.append("function $menuName()\n")
            val choiceFunc = if (mode == 3) "multiChoice" else "choice"
            val secondParam = if (mode == 2) "2026" else "nil"
            lines.append("    local SN = gg.$choiceFunc({\n")
            for (j in 1..N) {
                lines.append("        \"功能$j\",\n")
            }
            lines.append("        \"返回上一层\"\n")
            lines.append("    }, $secondParam, \"wuhai\")\n")
            lines.append("    if SN == nil then\n")
            lines.append("        XGCK = -1\n")
            lines.append("        return\n")
            lines.append("    end\n")
            for (j in 1..N) {
                val funcIdx = (i - 1) * N + j
                val cond = if (mode == 3) "SN[$j] == true" else "SN == $j"
                lines.append("    if $cond then\n")
                lines.append("        f$funcIdx()\n")
                lines.append("    end\n")
            }
            val condRet = if (mode == 3) "SN[${N + 1}] == true" else "SN == ${N + 1}"
            lines.append("    if $condRet then\n")
            lines.append("        return\n")
            lines.append("    end\n")
            lines.append("    XGCK = -1\n")
            lines.append("end\n")
            menuDefs.append(lines)
        }
        val mainLines = StringBuilder()
        mainLines.append("function Main()\n")
        val choiceFunc2 = if (mode == 3) "multiChoice" else "choice"
        val secondParam2 = if (mode == 2) "2026" else "nil"
        mainLines.append("    local SN = gg.$choiceFunc2({\n")
        for (i in 1..L) {
            mainLines.append("        \"子菜单$i\",\n")
        }
        mainLines.append("        \"退出脚本\"\n")
        mainLines.append("    }, $secondParam2, \"wuhai\")\n")
        mainLines.append("    if SN == nil then\n")
        mainLines.append("        XGCK = -1\n")
        mainLines.append("        return\n")
        mainLines.append("    end\n")
        for (i in 1..L) {
            val cond = if (mode == 3) "SN[$i] == true" else "SN == $i"
            mainLines.append("    if $cond then\n")
            mainLines.append("        SubMenu$i()\n")
            mainLines.append("    end\n")
        }
        val condExit = if (mode == 3) "SN[${L + 1}] == true" else "SN == ${L + 1}"
        mainLines.append("    if $condExit then\n")
        mainLines.append("        Exit()\n")
        mainLines.append("    end\n")
        mainLines.append("    XGCK = -1\n")
        mainLines.append("end\n")
        val exitFunc = """
function Exit()
    print("退出脚本")
    os.exit()
end
"""
        val mainLoop = """
XGCK = -1
while true do
    if gg.isVisible(true) then
        XGCK = 1
        gg.setVisible(false)
    end
    gg.clearResults()
    if XGCK == 1 then
        Main()
    end
end
"""
        return funcDefs.toString() + "\n" + menuDefs.toString() + "\n" +
                mainLines.toString() + "\n" + exitFunc + "\n" + mainLoop
    }

    // d) 静态基址扫描工具
    // 输入：地址项列表(module, offset, flags, value) + 修改值 + 是否冻结 + 输出路径
    // 原版从 GG 选中列表(内存查看器)读取，这里改为手动录入/导入，生成逻辑一致
    data class StaticItem(val module: String, val offset: Long, val flags: Int, val value: String)

    fun genStaticBaseScript(items: List<StaticItem>, newValue: String?, freeze: Boolean): String {
        val sb = StringBuilder()
        sb.append("function setvalue(address,flags,value) local tt={} tt[1]={} tt[1].address=address tt[1].flags=flags tt[1].value=value gg.setValues(tt) end\n\n")
        // 按模块分组
        val byModule = LinkedHashMap<String, MutableList<StaticItem>>()
        for (it in items) byModule.getOrPut(it.module) { mutableListOf() }.add(it)
        for ((module, list) in byModule) {
            sb.append("so=gg.getRangesList('$module')[1].start\n")
            if (freeze) {
                sb.append("local t={}\n")
                list.forEachIndexed { idx, it ->
                    val v = if (!newValue.isNullOrBlank()) newValue else it.value
                    sb.append("t[${idx + 1}]={address=so+0x${it.offset.toString(16).uppercase()},flags=${it.flags},value=$v,freeze=true}\n")
                }
                sb.append("gg.addListItems(t)\n\n")
            } else {
                for (it in list) {
                    val v = if (!newValue.isNullOrBlank()) newValue else it.value
                    sb.append("CZ=0x${it.offset.toString(16).uppercase()}\n")
                    sb.append("setvalue(so+CZ,${it.flags},$v)\n")
                }
                sb.append("\n")
            }
        }
        return sb.toString()
    }

    // e) 偏移指针工具（CZ 引擎生成）
    // 输入：主特征(value, flags, 内存范围) + 副特征列表(value, flags, 相对偏移)
    //       + 修改项列表(新值, 相对偏移, flags, 冻结) + 功能名 + 模式(修改/载入)
    // 内存范围码（原版 15 选 1）：Jh=2 Ch=1 Ca=4 Cd=8 Cb=16 Ps=262144 A=32 J=65536
    //   S=64 As=524288 V=1048576 O=-2080896 B=131072 Xa=16384 Xs=32768
    data class Feature(val value: String, val flags: Int, val relOffset: Long)
    data class ModifyItem(val value: String, val relOffset: Long, val flags: Int, val freeze: Boolean)

    fun genOffsetPtrScript(
        name: String,
        mainValue: String,
        mainFlags: Int,
        mainRange: Int,
        subFeatures: List<Feature>,
        modifyItems: List<ModifyItem>,
        mode: String  // "修改" or "载入"
    ): String {
        val pz = """function CZ(tzb,xgb,gnlx) gg.setRanges(tzb[1][3])
if tzb[1][4]==nil or tzb[1][5]==nil then tzb[1][4]=0tzb[1][5]=-1 end if tzb[1]["name"]==nil then tzb[1]["name"]="" end gg.clearResults() local time = os.clock() gg.searchNumber(tzb[1][1],tzb[1][2], false, gg.SIGN_EQUAL, tzb[1][4], tzb[1][5]) local k=gg.getResults(gg.getResultsCount())
if #k==0 then gg.toast("开启失败") return end gg.clearResults()
local writetable={{},{}} for a=2,#tzb do local b,hook={},{}
for i,v in ip(k) do b[#b+1]={address=v.address+tzb[a][3],flags=tzb[a][2]} end b=gg.getValues(b) for x,y in ip(b) do
if y.value==tzb[a][1] then hook[#hook+1]=k[x] end end k=hook end if #k>0 then for i=1,#k do for v=1,#xgb do if xgb[v][4]==true then writetable[1][#writetable[1]+1]={address=k[i].address+xgb[v][3],flags=xgb[v][2],value=xgb[v][1],freeze=true} else writetable[2][#writetable[2]+1]={address=k[i].address+xgb[v][3],flags=xgb[v][2],value=xgb[v][1]} end end end if gnlx=="修改" then gg.setValues(writetable[2]) gg.addListItems(writetable[1])
gg.toast(tzb[1]["name"].."开启成功\n共修改"..#writetable[2]..",冻结"..#writetable[1].."个值\n耗时:"..os.clock()-time.."秒") elseif gnlx=="载入" then gg.loadResults(writetable[2]) gg.toast(tzb[1]["name"].."解析成功\n共载入"..#writetable[2].."个值\n耗时:"..os.clock()-time.."秒") endelse gg.toast(tzb[1]["name"].."开启失败") end end"""
        val sb = StringBuilder()
        sb.append(pz).append("\n")
        // 主特征
        sb.append("CZ({\n")
        sb.append("{${mainValue},${mainFlags},${mainRange},['name']='$name'},\n")
        for (f in subFeatures) {
            sb.append("{${f.value},${f.flags},${f.relOffset}},\n")
        }
        sb.append("},\n")
        // 修改表
        sb.append("{\n")
        for (m in modifyItems) {
            sb.append("{${m.value},${m.flags},${m.relOffset},${if (m.freeze) "true" else "false"}},\n")
        }
        sb.append("},\n")
        sb.append("\"$mode\")\n")
        return sb.toString()
    }

    // f) 去除 Lua 语言注释（-- 开头到行尾），备份 .h
    fun removeLuaComments(content: String): String =
        content.replace(Regex("--[^\n\r]*"), "")

    fun backupAndClean(filePath: String): Pair<String, String> {
        val f = File(filePath)
        val content = f.readText()
        val backupPath = filePath + ".h"
        File(backupPath).writeText(content)
        val cleaned = removeLuaComments(content)
        f.writeText(cleaned)
        return backupPath to cleaned
    }
}
