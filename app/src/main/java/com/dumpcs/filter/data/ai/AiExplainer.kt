package com.dumpcs.filter.data.ai

import com.dumpcs.filter.data.model.ClassInfo

/**
 * 猫娘 AI 解释引擎（离线规则，稳定不依赖网络）：
 * 分析类的命名空间/类名/父类/方法/字段，推断出"是干什么的、有什么用"，
 * 再用猫娘语气（喵~、的说、哦~）讲给用户听，并支持继续追问。
 */
class AiExplainer {

    data class Category(
        val label: String,
        val purpose: String,
        val use: String,
        val keywords: List<String>
    )

    private val categories = listOf(
        Category(
            "玩家角色", "负责玩家角色的数据和行为管理",
            "控制玩家的移动、血量、技能、装备等一切表现，是整个游戏的主角核心，改它就能改角色强度",
            listOf("player", "character", "actor", "hero", "protagonist", "avatar", "role", "roleinfo", "playerdata")
        ),
        Category(
            "武器系统", "管理武器的属性、开火、换弹、弹道",
            "决定武器伤害、射速、后坐力、弹夹容量，想调武器性能就找它",
            listOf("weapon", "gun", "rifle", "pistol", "sword", "bow", "firearm", "bullet", "ammo", "reload", "muzzle", "barrel")
        ),
        Category(
            "战斗伤害", "负责战斗数值计算，如攻击、伤害、暴击",
            "控制每次攻击造成多少伤害、命中判定、暴击倍率，战斗平衡全靠它",
            listOf("damage", "attack", "combat", "battle", "fight", "hit", "hurt", "crit", "dmg", "strike", "wound")
        ),
        Category(
            "血量护甲", "管理角色的生命值、护甲、护盾与恢复",
            "决定角色能抗多少伤害、回血机制、死亡判定，调生存难度就找它",
            listOf("health", "hp", "armor", "shield", "life", "heal", "revive", "regen", "defense", "defence", "durability")
        ),
        Category(
            "移动操作", "处理角色的移动、跳跃、飞行、传送等操作",
            "控制走路速度、跳跃高度、冲刺、传送点，手感好不好全看它",
            listOf("move", "movement", "jump", "run", "walk", "fly", "teleport", "dash", "slide", "speed", "navmesh", "locomotion", "gravity")
        ),
        Category(
            "技能系统", "管理技能、魔法、增益减益效果",
            "控制技能冷却、Buff/Debuff 生效逻辑、被动天赋，技能强不强它说了算",
            listOf("skill", "ability", "spell", "magic", "buff", "debuff", "cooldown", "talent", "passive", "aura", "cast")
        ),
        Category(
            "敌人怪物", "负责敌人、怪物、Boss、NPC 的 AI 与行为",
            "决定敌人攻击逻辑、巡逻路线、掉落物品，想改怪就找它",
            listOf("enemy", "monster", "npc", "boss", "zombie", "creep", "mob", "villain", "foe", "guard")
        ),
        Category(
            "背包物品", "管理背包、物品、掉落、宝箱、装备",
            "控制道具数量、拾取逻辑、背包容量、装备栏位，仓库玩法全靠它",
            listOf("inventory", "bag", "item", "loot", "chest", "pickup", "equipment", "prop", "storage", "backpack", "goods")
        ),
        Category(
            "地图关卡", "处理地图、关卡、场景、区域的加载与切换",
            "决定地图结构、出生点、传送门、区域解锁，关卡设计都在这",
            listOf("map", "level", "zone", "world", "scene", "stage", "dungeon", "terrain", "region", "area", "field")
        ),
        Category(
            "网络同步", "负责客户端与服务器的通信、数据同步",
            "控制联网对战、消息收发、状态同步，多人玩法全靠它",
            listOf("network", "client", "server", "socket", "http", "sync", "rpc", "connection", "protocol", "packet", "net")
        ),
        Category(
            "数据配置", "管理游戏数据、配置表、存档、数据库",
            "控制读表、存读档、数值配置，所有策划数值都经过它",
            listOf("data", "config", "setting", "info", "database", "table", "json", "save", "archive", "record", "profile")
        ),
        Category(
            "管理器系统", "游戏里的调度中枢，负责各种系统之间的协调",
            "把零零散散的模块串起来统一管理，是游戏运行的骨架",
            listOf("manager", "controller", "system", "service", "handler", "provider", "factory", "center", "core", "module", "dispatcher")
        ),
        Category(
            "界面 UI", "负责游戏界面的显示与交互",
            "控制血条、背包、菜单、弹窗、HUD 的展示，界面长啥样它说了算",
            listOf("ui", "panel", "widget", "screen", "menu", "hud", "dialog", "window", "layout", "view", "button", "textureui")
        ),
        Category(
            "渲染相机", "处理 3D 渲染、相机、特效、动画",
            "控制画面表现、镜头视角、粒子特效、动画播放，画质与视觉全靠它",
            listOf("camera", "render", "graphic", "sprite", "shader", "animation", "particle", "effect", "visual", "mesh", "material", "light")
        ),
        Category(
            "音频音效", "管理声音、音乐、音效的播放",
            "控制 BGM、打击音效、语音，游戏好不好听就靠它",
            listOf("audio", "sound", "music", "voice", "sfx", "bgm", "vocal")
        )
    )

/**
     * 生成猫娘语气的完整解释
     */
    fun explain(cls: ClassInfo): String {
        val cat = matchCategory(cls)
        val methodSamples = cls.methods.take(4).map { it.name }.joinToString(", ")
        val fieldSamples = cls.fields.take(4).map { it.name }.joinToString(", ")
        val parent = if (cls.parentClass.isNotBlank()) " 它继承自 ${cls.parentClass} 的说。" else ""

        return buildString {
            append("喵~这个是【${cat.label}】相关的类哦！🎀\n")
            append("📌 它是干什么的：${cat.purpose}。\n")
            append("💡 有什么用：${cat.use}。\n")
            if (parent.isNotBlank()) append("🧬 继承关系：$parent\n")
            if (methodSamples.isNotBlank()) {
                append("🔧 方法线索：$methodSamples\n")
            }
            if (fieldSamples.isNotBlank()) {
                append("📦 字段线索：$fieldSamples\n")
            }
            append("（完整类名：${cls.fullName}）\n")
            append("还有什么想知道的尽管问喵~✨")
        }
    }

/**
     * 用户继续追问时，换个角度回答（保持猫娘语气）
     */
    fun reply(cls: ClassInfo, userMsg: String): String {
        val cat = matchCategory(cls)
        val msg = userMsg.lowercase()

        return when {
            msg.contains("继承") || msg.contains("父类") || msg.contains("基类") -> {
                if (cls.parentClass.isNotBlank()) {
                    "喵~${cls.className} 继承自 ${cls.parentClass} 的说！\n" +
                        "这意味着它能直接使用父类的属性和方法，方便复用公共逻辑，改父类会影响所有子类哦~"
                } else {
                    "喵~${cls.className} 没有继承任何类呢，是个独立类哦！它自己管自己，最省心的说~"
                }
            }
            msg.contains("方法") || msg.contains("函数") || msg.contains("接口") -> {
                val methods = cls.methods.take(6).map { it.name }.joinToString(", ")
                if (cls.methods.isNotEmpty()) {
                    "喵~它一共有 ${cls.methods.size} 个方法哦，比如：$methods\n" +
                        "这些方法就是它的行为动作，调用它们就能触发对应的功能，RVA 地址还能直接去内存里定位到它呢！"
                } else {
                    "喵~这个类没有解析到方法呢，可能是纯数据类，只负责存东西的说~"
                }
            }
            msg.contains("字段") || msg.contains("属性") || msg.contains("变量") -> {
                val fields = cls.fields.take(6).map { it.name }.joinToString(", ")
                if (cls.fields.isNotEmpty()) {
                    "喵~它有 ${cls.fields.size} 个字段/属性哦，比如：$fields\n" +
                        "字段就是它的状态数据，想改数值（比如伤害、血量）直接找这些字段就对了！"
                } else {
                    "喵~这个类没有字段呢，是个纯行为类，只负责做事不存数据哦~"
                }
            }
            msg.contains("伤害") || msg.contains("强") || msg.contains("厉害") || msg.contains("修改") || msg.contains("改") -> {
                "喵~【${cat.label}】相关的核心类就是它了！\n" +
                    "想改${cat.label}相关的数值，直接搜这个类里的字段和方法就行。\n" +
                    "💡 小技巧：改字段数值 → 属性强度；改方法逻辑 → 行为规则。改完记得备份原文件哦~"
            }
            else -> {
                "喵~再给你讲讲 ${cls.className} 哦！\n" +
                    "它属于【${cat.label}】系统，${cat.use}。\n" +
                    "🔍 看它的名字 ${cls.className} 就知道它管哪块了，配合方法 ${cls.methods.take(3).joinToString(", ").ifBlank { "（暂无）" }} 基本能推断全貌。\n" +
                    "想深入看哪个部分，问我就好喵~✨"
            }
        }
    }

/**
     * 匹配最相关的分类（按关键词命中数排序）
     */
    private fun matchCategory(cls: ClassInfo): Category {
        val allText = buildString {
            append(cls.namespace.lowercase()).append(" ")
            append(cls.className.lowercase()).append(" ")
            append(cls.parentClass.lowercase()).append(" ")
            cls.methods.forEach { append(it.name.lowercase()).append(" ") }
            cls.fields.forEach { append(it.name.lowercase()).append(" ") }
        }
        return categories
            .map { cat -> cat to cat.keywords.count { allText.contains(it) } }
            .maxByOrNull { it.second }
            ?.takeIf { it.second > 0 }
            ?.first
            ?: Category(
                label = "通用/工具",
                purpose = "基础的工具或辅助逻辑",
                use = "被其他类调用，提供通用能力，是整个游戏的地基",
                keywords = emptyList()
            )
    }
}