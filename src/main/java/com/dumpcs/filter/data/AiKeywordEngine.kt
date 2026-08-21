package com.dumpcs.filter.data

/**
 * AI 辅助关键词引擎：
 * 用户用中文描述意图（如"找攻击和伤害相关的类"），自动扩展成游戏/代码英文关键词。
 * 离线运行、即时响应，支持中文/英文混合输入。
 */
class AiKeywordEngine {

    /** 搜索规格：关键词 + 排除词 + 强关键词（命中强关键词的类优先保留） */
    data class SearchSpec(
        val keywords: List<String>,
        val excludes: List<String>,
        val strongKeywords: List<String>
    )

    // 中文意图/术语 → 英文关键词组（覆盖游戏常见逻辑）
    private val intentMap: Map<String, List<String>> = linkedMapOf(
        "攻击" to listOf("attack", "damage", "hit", "strike", "melee", "combat"),
        "伤害" to listOf("damage", "hurt", "dmg", "injury"),
        "战斗" to listOf("combat", "battle", "fight", "war", "enemy"),
        "武器" to listOf("weapon", "gun", "rifle", "pistol", "sword", "knife", "firearm"),
        "枪" to listOf("gun", "rifle", "pistol", "shoot", "fire", "reload"),
        "子弹" to listOf("bullet", "ammo", "projectile", "pellet", "cartridge"),
        "瞄准" to listOf("aim", "scope", "zoom", "ads", "target"),
        "准星" to listOf("crosshair", "reticle", "aim"),
        "后坐" to listOf("recoil", "kickback", "spread"),
        "玩家" to listOf("player", "character", "pawn", "actor", "human"),
        "敌人" to listOf("enemy", "target", "hostile", "monster", "mob", "npc"),
        "血量" to listOf("health", "hp", "vitality", "life"),
        "护甲" to listOf("armor", "armour", "shield", "defense", "defence"),
        "防御" to listOf("defense", "defend", "block", "parry", "guard"),
        "移动" to listOf("move", "movement", "walk", "run", "locomotion"),
        "跳跃" to listOf("jump", "hop", "leap", "gravity"),
        "滑铲" to listOf("slide", "sliding", "crouch"),
        "飞行" to listOf("fly", "flight", "flying", "gravity"),
        "速度" to listOf("speed", "velocity", "rate", "haste", "boost"),
        "加速" to listOf("speed", "boost", "haste", "accelerate"),
        "透视" to listOf(
            "esp", "wallhack", "visible", "occlusion", "render", "chams", "glow",
            "outline", "radar", "aimbot", "wh", "espdraw", "drawesp",
            "redesp", "thermal", "heat", "hot", "teammateesp", "enemyesp"
        ),
        "热透" to listOf("thermal", "heat", "hot", "redesp", "espdraw", "drawesp", "heatvision", "heatmap"),
        "红透" to listOf("redesp", "espdraw", "drawesp", "esp", "red", "redbox", "redline"),
        "雷达" to listOf("radar", "minimap", "map", "sensor"),
        "地图" to listOf("map", "minimap", "world", "level", "terrain"),
        "物品" to listOf("item", "loot", "pickup", "collectible", "object"),
        "宝箱" to listOf("chest", "box", "crate", "container"),
        "技能" to listOf("skill", "ability", "spell", "power", "talent"),
        "冷却" to listOf("cooldown", "cd", "timer", "recharge"),
        "金币" to listOf("gold", "coin", "money", "currency"),
        "商店" to listOf("shop", "store", "vendor", "merchant", "market"),
        "任务" to listOf("quest", "mission", "task", "objective"),
        "背包" to listOf("inventory", "bag", "backpack", "storage"),
        "队友" to listOf("teammate", "ally", "friend", "party", "squad"),
        "宠物" to listOf("pet", "companion", "summon", "mount"),
        "属性" to listOf("stat", "attribute", "property", "value"),
        "等级" to listOf("level", "rank", "tier", "experience", "xp"),
        "经验" to listOf("experience", "xp", "exp"),
        "范围" to listOf("range", "distance", "radius", "area", "aoe"),
        "碰撞" to listOf("collision", "collider", "hitbox", "physics"),
        "声音" to listOf("sound", "audio", "music", "sfx", "voice"),
        "音乐" to listOf("music", "audio", "bgm"),
        "粒子" to listOf("particle", "effect", "vfx", "fx"),
        "动画" to listOf("animation", "anim", "skeletal", "rig"),
        "网络" to listOf("network", "net", "server", "client", "sync", "socket"),
        "存档" to listOf("save", "checkpoint", "data", "persist"),
        "设置" to listOf("setting", "config", "option", "preference"),
        "界面" to listOf("ui", "hud", "menu", "screen", "panel", "widget"),
        "文字" to listOf("text", "font", "string", "label"),
        "图片" to listOf("texture", "sprite", "image", "icon", "material"),
        "模型" to listOf("model", "mesh", "render", "animation"),
        "管理器" to listOf("manager", "controller", "handler", "system"),
        "数据" to listOf("data", "database", "save", "storage", "json"),
        "暴击" to listOf("crit", "critical", "headshot"),
        "爆头" to listOf("headshot", "crit", "critical"),
        "隐身" to listOf("invisible", "stealth", "cloak", "vanish"),
        "buff" to listOf("buff", "boost", "enhance", "empower"),
        "debuff" to listOf("debuff", "weaken", "slow", "curse"),
        "恢复" to listOf("heal", "regen", "restore", "recovery"),
        "复活" to listOf("revive", "respawn", "rebirth"),
        "陷阱" to listOf("trap", "mine", "hazard"),
        "机关" to listOf("mechanism", "lever", "switch", "door"),
        "传送" to listOf("teleport", "portal", "warp", "transport"),
        "尸体" to listOf("corpse", "body", "death", "ragdoll"),
        "掉落" to listOf("drop", "loot", "reward", "spawn"),
        "生成" to listOf("spawn", "generate", "create", "instantiate"),
        "销毁" to listOf("destroy", "remove", "delete", "dispose"),
        "计时" to listOf("timer", "time", "clock", "duration"),
        "分数" to listOf("score", "point", "rank"),
        "排名" to listOf("rank", "leaderboard", "rating"),
        "天气" to listOf("weather", "rain", "snow", "climate", "sky"),
        "灯光" to listOf("light", "lighting", "lamp", "shader"),
        "相机" to listOf("camera", "view", "fov", "viewport"),
        "视野" to listOf("fov", "view", "camera", "visible"),
        "旋转" to listOf("rotate", "rotation", "spin", "angle"),
        "缩放" to listOf("scale", "zoom", "size"),
        "物理" to listOf("physics", "rigidbody", "force", "gravity"),
        "重力" to listOf("gravity", "force", "fall"),
        "输入" to listOf("input", "keyboard", "mouse", "touch", "controller"),
        "按键" to listOf("key", "button", "input", "keycode"),
        "手势" to listOf("gesture", "touch", "swipe", "tap"),
        "对话" to listOf("dialog", "dialogue", "chat", "message", "talk"),
        "剧情" to listOf("story", "plot", "scene", "chapter", "narrative"),
        "成就" to listOf("achievement", "trophy", "unlock"),
        "公告" to listOf("notice", "announcement", "bulletin"),
        "签到" to listOf("sign", "checkin", "daily", "reward"),
        "抽奖" to listOf("gacha", "lottery", "draw", "random"),
        "登录" to listOf("login", "auth", "account", "session"),
        "注册" to listOf("register", "signup", "account"),
        "支付" to listOf("pay", "payment", "purchase", "iap", "order"),
        "广告" to listOf("ad", "ads", "advertise", "banner"),
        "新手" to listOf("tutorial", "guide", "novice", "onboarding"),
        "帮助" to listOf("help", "assist", "guide", "tutorial"),
        "搜索" to listOf("search", "find", "query", "filter")
    )

/**
     * 中文意图 → 排除词（剔除"连接桥梁/无关管理"类，让结果更精准）。
     * 例：搜"透视"时，命中了 esp/visible 的连接类（ConnectionBridge）会被剔除，
     * 而类名本身带 esp/red/thermal 的（红透/热透）一定保留。
     */
    private val excludeIntentMap: Map<String, List<String>> = linkedMapOf(
        "透视" to listOf(
            "connection", "bridge", "connect", "link", "pipe", "route", "channel",
            "socket", "sync", "network", "listener", "callback", "queue", "stream",
            "transport", "session", "packet", "protocol", "tunnel", "relay", "binding"
        ),
        "雷达" to listOf(
            "connection", "bridge", "connect", "link", "socket", "sync",
            "network", "listener", "callback", "queue", "stream", "protocol"
        ),
        "视野" to listOf(
            "connection", "bridge", "link", "socket", "network", "sync"
        ),
        "瞄准" to listOf(
            "connection", "bridge", "link", "socket", "network", "sync", "protocol"
        )
    )

/**
     * 中文意图 → 强关键词：类名/完整路径命中这些词的，即使含排除词也强制保留（红透/热透这类）
     */
    private val strongIntentMap: Map<String, List<String>> = linkedMapOf(
        "透视" to listOf(
            "esp", "wallhack", "chams", "glow", "visible", "outline", "thermal",
            "heat", "hot", "redesp", "radar", "aimbot", "wh"
        ),
        "雷达" to listOf("radar", "minimap", "sensor", "esp"),
        "视野" to listOf("fov", "camera", "view", "visible")
    )

/**
     * 把用户描述（中文/英文混合）扩展成搜索规格（关键词 + 排除词 + 强关键词）
     */
    fun expandQuerySpec(query: String): SearchSpec {
        val keywords = LinkedHashSet<String>()
        val excludes = LinkedHashSet<String>()
        val strong = LinkedHashSet<String>()
        val q = query.trim()
        if (q.isEmpty()) return SearchSpec(emptyList(), emptyList(), emptyList())

        // 1. 中文意图匹配 → 关键词 + 排除词 + 强关键词
        intentMap.forEach { (key, kw) ->
            if (q.contains(key, ignoreCase = true)) {
                keywords.addAll(kw)
            }
        }
        excludeIntentMap.forEach { (key, ex) ->
            if (q.contains(key, ignoreCase = true)) {
                excludes.addAll(ex)
            }
        }
        strongIntentMap.forEach { (key, st) ->
            if (q.contains(key, ignoreCase = true)) {
                strong.addAll(st)
            }
        }

        // 2. 英文单词直接加入关键词
        q.split(Regex("[,\\s，、]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.matches(Regex("[A-Za-z_][A-Za-z0-9_]*")) }
            .forEach { keywords.add(it.lowercase()) }

        return SearchSpec(keywords.toList(), excludes.toList(), strong.toList())
    }

/**
     * 兼容旧调用：只返回关键词列表
     */
    fun expandQuery(query: String): List<String> = expandQuerySpec(query).keywords

/**
     * 返回匹配到的意图说明（用于展示 AI 识别结果）
     */
    fun describe(query: String): String {
        val matched = intentMap.keys.filter { query.contains(it, ignoreCase = true) }
        val excluded = excludeIntentMap.keys.filter { query.contains(it, ignoreCase = true) }
        return buildString {
            if (matched.isNotEmpty()) append("识别到: ${matched.joinToString("、")}")
            if (excluded.isNotEmpty()) append(" · 自动剔除连接/桥梁类")
        }
    }

/**
     * 对话记忆：解析"只要/只留/就要/光要 X" → 返回意图词 X（如"热透"）。
     * 没有则返回 null（普通搜索）
     */
    fun extractKeepIntent(query: String): String? {
        val m = query.trim()
        for (prefix in listOf("只要", "只留", "就要", "光要", "只想要")) {
            if (m.startsWith(prefix) || m.contains(prefix)) {
                val rest = m.substringAfter(prefix, "").trim()
                return matchIntentIn(rest)
            }
        }
        return null
    }

/**
     * 对话记忆：解析"去掉/不要/排除/剔除/过滤掉 X" → 返回意图词 X（如"设置"）。
     * 没有则返回 null
     */
    fun extractRemoveIntent(query: String): String? {
        val m = query.trim()
        for (prefix in listOf("去掉", "不要", "排除", "剔除", "过滤掉", "删掉")) {
            if (m.contains(prefix)) {
                val rest = m.substringAfter(prefix, "").trim()
                return matchIntentIn(rest)
            }
        }
        return null
    }

    /** 在文本里找最长的已知意图词（如"热透"命中"热透"而非"透"） */
    private fun matchIntentIn(text: String): String? {
        if (text.isEmpty()) return null
        return intentMap.keys
            .filter { text.contains(it) }
            .maxByOrNull { it.length }
    }
}
