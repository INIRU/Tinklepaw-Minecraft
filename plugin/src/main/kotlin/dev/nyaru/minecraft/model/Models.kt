package dev.nyaru.minecraft.model

data class JobSlot(
    val job: String,
    val level: Int = 1,
    val xp: Int = 0
)

data class PlayerInfo(
    val linked: Boolean,
    val discordUserId: String? = null,
    val minecraftName: String? = null,
    val balance: Int = 0,
    val jobSlots: List<JobSlot> = emptyList(),
    val activeSlot: Int = 0,
    val maxSlots: Int = 1,
    val lastJobSwitch: String? = null,
    val title: String? = null,
    val titleColor: String? = null,
    val titleIconUrl: String? = null
) {
    val job: String? get() = jobSlots.getOrNull(activeSlot)?.job
    val level: Int get() = jobSlots.getOrNull(activeSlot)?.level ?: 1
    val xp: Int get() = jobSlots.getOrNull(activeSlot)?.xp ?: 0
}

data class SkillData(
    val skillPoints: Int = 0,
    val levels: Map<String, Int> = emptyMap()
) {
    fun getLevel(key: String): Int = levels[key] ?: 0

    fun withUpgrade(key: String): SkillData {
        val newLevels = levels.toMutableMap()
        newLevels[key] = (newLevels[key] ?: 0) + 1
        return copy(skillPoints = skillPoints - 1, levels = newLevels)
    }
}

data class XpResult(
    val level: Int,
    val xp: Int,
    val leveledUp: Boolean,
    val xpToNextLevel: Int,
    val newSkillPoints: Int? = null
)

data class LinkRequestResult(
    val otp: String,
    val expiresAt: String
)

data class HomeLocation(
    val world: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val pitch: Float
)

data class ShopItem(
    val id: String,
    val material: String,
    val displayName: String,
    val buyPrice: Int,
    val sellPrice: Int,
    val category: String
)

object LevelFormula {
    fun xpRequired(level: Int): Int = Math.floor(100.0 * Math.pow(level.toDouble(), 1.6)).toInt()
}

object Jobs {
    const val MINER = "miner"
    const val FARMER = "farmer"
    const val WARRIOR = "warrior"
    const val FISHER = "fisher"
    const val WOODCUTTER = "woodcutter"

    val ALL = listOf(MINER, FARMER, WARRIOR, FISHER, WOODCUTTER)

    fun displayName(job: String?): String = when (job) {
        MINER -> "광부"
        FARMER -> "농부"
        WARRIOR -> "전사"
        FISHER -> "어부"
        WOODCUTTER -> "벌목꾼"
        else -> "없음"
    }

    fun colorCode(job: String?): String = when (job) {
        MINER -> "§9"
        FARMER -> "§a"
        WARRIOR -> "§c"
        FISHER -> "§3"
        WOODCUTTER -> "§6"
        else -> "§7"
    }
}

data class SkillDef(
    val key: String,
    val displayName: String,
    val job: String,
    val maxLevel: Int,
    val levelReqs: List<Int>,
    val description: List<String>,
    val materialName: String
)

object SkillRegistry {
    val ALL: List<SkillDef> = listOf(
        // === Miner ===
        SkillDef("mining_speed", "§b⛏ 채굴 속도", "miner", 3, listOf(1, 5, 10),
            listOf("§7채굴 속도(Haste)를 부여합니다.", "§7Lv1: Haste I / Lv2: Haste II / Lv3: Haste III"),
            "DIAMOND_PICKAXE"),
        SkillDef("lucky_strike", "§b✨ 행운 채굴", "miner", 3, listOf(3, 8, 15),
            listOf("§7광물 채굴 시 드롭이 2배가 될 수 있습니다.", "§7Lv1: 15% / Lv2: 30% / Lv3: 45%"),
            "NETHER_STAR"),
        SkillDef("stone_skin", "§b🛡 철갑 피부", "miner", 3, listOf(7, 12, 20),
            listOf("§7영구 저항(Resistance)을 부여합니다.", "§7Lv1: Resistance I / Lv2: II / Lv3: III"),
            "IRON_CHESTPLATE"),
        SkillDef("mace_master", "§b🔨 철퇴 달인", "miner", 3, listOf(5, 10, 18),
            listOf("§7철퇴 사용 시 특수 능력을 부여합니다.", "§7Lv1: 내구도 소모 X / Lv2: +도약 / Lv3: +낙뎀 X"),
            "MACE"),

        // === Farmer ===
        SkillDef("wide_harvest", "§a🌾 넓은 수확", "farmer", 1, listOf(3),
            listOf("§7작물 수확 시 3x3 범위로 확장됩니다."),
            "GOLDEN_HOE"),
        SkillDef("wide_plant", "§a🌱 넓은 파종", "farmer", 1, listOf(5),
            listOf("§7씨앗 심기 시 3x3 범위로 확장됩니다."),
            "WHEAT_SEEDS"),
        SkillDef("freshness", "§a⏱ 신선도 마스터", "farmer", 3, listOf(1, 7, 15),
            listOf("§7작물 신선도 감소를 느리게 합니다.", "§7Lv1: -15% / Lv2: -30% / Lv3: -45%"),
            "CLOCK"),
        SkillDef("harvest_fortune", "§a🍀 풍작", "farmer", 3, listOf(5, 10, 20),
            listOf("§7작물 수확 시 추가 드롭.", "§7Lv1: +1 / Lv2: +2 / Lv3: +3"),
            "WHEAT"),
        SkillDef("life_drain", "§a❤ 생명 흡수", "farmer", 3, listOf(4, 9, 16),
            listOf("§7괭이로 공격 시 체력을 흡수합니다.", "§7Lv1: 10% / Lv2: 20% / Lv3: 30% 피흡"),
            "GOLDEN_APPLE"),

        // === Warrior ===
        SkillDef("sword_mastery", "§c⚔ 검술 달인", "warrior", 3, listOf(1, 5, 10),
            listOf("§7검으로 공격 시 추가 데미지.", "§7Lv1: +10% / Lv2: +20% / Lv3: +30%"),
            "DIAMOND_SWORD"),
        SkillDef("berserker", "§c🔥 광전사", "warrior", 3, listOf(3, 8, 15),
            listOf("§7체력이 낮을수록 공격력 증가.", "§7Lv1: HP<50%→+15% / Lv2: +25% / Lv3: +35%"),
            "BLAZE_POWDER"),
        SkillDef("critical_strike", "§c💥 치명타", "warrior", 3, listOf(5, 10, 18),
            listOf("§7공격 시 치명타 확률.", "§7Lv1: 10% / Lv2: 20% / Lv3: 30%"),
            "FLINT"),
        SkillDef("war_cry", "§c📯 전투의 함성", "warrior", 3, listOf(7, 12, 20),
            listOf("§7웅크리기+공격 시 주변 적에게 약화.", "§7Lv1: 3초 / Lv2: 5초 / Lv3: 7초"),
            "GOAT_HORN"),

        // === Fisher ===
        SkillDef("lucky_catch", "§3🎣 행운의 낚시", "fisher", 3, listOf(1, 5, 10),
            listOf("§7낚시 시 보물 확률 증가.", "§7Lv1: Luck I / Lv2: II / Lv3: III"),
            "FISHING_ROD"),
        SkillDef("sea_blessing", "§3🌊 바다의 축복", "fisher", 3, listOf(3, 8, 15),
            listOf("§7물속에서 빠르게 이동합니다.", "§7Lv1: Dolphin's Grace I / Lv2: II / Lv3: III"),
            "HEART_OF_THE_SEA"),
        SkillDef("treasure_hunter", "§3💎 보물 사냥꾼", "fisher", 3, listOf(7, 12, 20),
            listOf("§7낚시로 희귀 아이템 확률.", "§7Lv1: 5% / Lv2: 10% / Lv3: 15%"),
            "NAUTILUS_SHELL"),

        // === Woodcutter ===
        SkillDef("timber", "§6🪓 벌목", "woodcutter", 1, listOf(5),
            listOf("§7나무를 한번에 베어냅니다.", "§7연결된 원목을 모두 파괴합니다."),
            "DIAMOND_AXE"),
        SkillDef("axe_mastery", "§6⚡ 도끼 달인", "woodcutter", 3, listOf(1, 5, 10),
            listOf("§7도끼 사용 시 채굴 속도 증가.", "§7Lv1: Haste I / Lv2: II / Lv3: III"),
            "IRON_AXE"),
        SkillDef("leaf_blower", "§6🍃 잎 날리기", "woodcutter", 1, listOf(10),
            listOf("§7나무를 벨 때 잎도 함께 파괴됩니다."),
            "OAK_LEAVES")
    )

    fun forJob(job: String): List<SkillDef> = ALL.filter { it.job == job }
    fun get(key: String): SkillDef? = ALL.find { it.key == key }
}
