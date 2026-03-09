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
    const val NECROMANCER = "necromancer"

    val ALL = listOf(MINER, FARMER, WARRIOR, FISHER, WOODCUTTER, NECROMANCER)

    fun displayName(job: String?): String = when (job) {
        MINER -> "광부"
        FARMER -> "농부"
        WARRIOR -> "전사"
        FISHER -> "어부"
        WOODCUTTER -> "벌목꾼"
        NECROMANCER -> "네크로맨서"
        else -> "없음"
    }

    fun colorCode(job: String?): String = when (job) {
        MINER -> "§9"
        FARMER -> "§a"
        WARRIOR -> "§c"
        FISHER -> "§3"
        WOODCUTTER -> "§6"
        NECROMANCER -> "§5"
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
        SkillDef("ore_sight", "§b👁 광맥 감지", "miner", 3, listOf(4, 9, 16),
            listOf("§7지하(Y<60)에서 야간 투시를 부여합니다.", "§7Lv1: 야간 투시 I", "§7Lv2: + 채굴 시 2% 추가 냥", "§7Lv3: + 채굴 시 5% 추가 냥"),
            "SPYGLASS"),
        SkillDef("magnet", "§b🧲 자석", "miner", 1, listOf(8),
            listOf("§7채굴한 아이템이 자동으로 인벤토리에 들어옵니다."),
            "IRON_INGOT"),

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
        SkillDef("green_thumb", "§a🌱 녹색 손길", "farmer", 3, listOf(6, 11, 18),
            listOf("§7주변 작물의 성장을 촉진합니다.", "§7Lv1: 10% 확률 / Lv2: 20% / Lv3: 30%", "§7범위: 5블록, 3초마다 체크"),
            "BONE_MEAL"),
        SkillDef("scarecrow", "§a🎃 허수아비", "farmer", 3, listOf(8, 14, 20),
            listOf("§7주변 적대 몹에게 슬로우를 부여합니다.", "§7Lv1: 5블록 / Lv2: 8블록 / Lv3: 12블록", "§7Slowness I, 5초마다 적용"),
            "CARVED_PUMPKIN"),

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
        SkillDef("iron_will", "§c🛡 강철 의지", "warrior", 3, listOf(8, 14, 20),
            listOf("§7치명적 피해 시 HP 1로 생존합니다.", "§7Lv1: 10% / Lv2: 20% / Lv3: 30%", "§7쿨다운: 3분"),
            "TOTEM_OF_UNDYING"),
        SkillDef("lethal_strike", "§c☠ 치명적 일격", "warrior", 3, listOf(6, 11, 18),
            listOf("§7공격 시 적에게 위더 효과를 부여합니다.", "§7Lv1: 10%, 3초 / Lv2: 15%, 5초 / Lv3: 20%, 7초"),
            "WITHER_SKELETON_SKULL"),
        SkillDef("shield_master", "§c🛡 방패 달인", "warrior", 3, listOf(4, 9, 16),
            listOf("§7방패로 막을 때 반사 데미지.", "§7Lv1: 15% / Lv2: 30% / Lv3: 50% 반사"),
            "SHIELD"),
        SkillDef("dash", "§c💨 돌진", "warrior", 3, listOf(5, 10, 18),
            listOf("§7웅크리기 중 맨손 좌클릭으로 전방 돌진.", "§7Lv1: 5블록 / Lv2: 8블록 / Lv3: 12블록", "§7쿨다운: 10초"),
            "FEATHER"),

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
        SkillDef("water_breathing", "§3🫧 수중 호흡", "fisher", 3, listOf(2, 7, 14),
            listOf("§7영구 수중 호흡을 부여합니다.", "§7Lv1: Water Breathing I / Lv2: II / Lv3: III"),
            "TURTLE_HELMET"),
        SkillDef("rain_dancer", "§3🌧 비의 축복", "fisher", 3, listOf(5, 10, 18),
            listOf("§7비가 올 때 능력이 강화됩니다.", "§7Lv1: 속도 I / Lv2: +힘 I / Lv3: +재생 I"),
            "WATER_BUCKET"),
        SkillDef("trident_master", "§3🔱 삼지창 달인", "fisher", 3, listOf(6, 12, 20),
            listOf("§7삼지창 공격 데미지 증가.", "§7Lv1: +15% / Lv2: +30% / Lv3: +50%"),
            "TRIDENT"),
        SkillDef("net_catch", "§3🪤 그물 낚시", "fisher", 3, listOf(4, 9, 16),
            listOf("§7낚시 시 추가 아이템 드롭 확률.", "§7Lv1: 15% / Lv2: 30% / Lv3: 50%", "§7물고기 1마리 추가 드롭"),
            "COBWEB"),

        // === Woodcutter ===
        SkillDef("timber", "§6🪓 벌목", "woodcutter", 1, listOf(5),
            listOf("§7나무를 한번에 베어냅니다.", "§7연결된 원목을 모두 파괴합니다."),
            "DIAMOND_AXE"),
        SkillDef("axe_mastery", "§6⚡ 도끼 달인", "woodcutter", 3, listOf(1, 5, 10),
            listOf("§7도끼 사용 시 채굴 속도 증가.", "§7Lv1: Haste I / Lv2: II / Lv3: III"),
            "IRON_AXE"),
        SkillDef("leaf_blower", "§6🍃 잎 날리기", "woodcutter", 1, listOf(10),
            listOf("§7나무를 벨 때 잎도 함께 파괴됩니다."),
            "OAK_LEAVES"),
        SkillDef("forest_blessing", "§6🌿 숲의 축복", "woodcutter", 3, listOf(3, 8, 15),
            listOf("§7나무 근처에서 재생 효과를 받습니다.", "§7Lv1: Regeneration I / Lv2: II / Lv3: III"),
            "OAK_SAPLING"),
        SkillDef("replanter", "§6🌳 자동 식목", "woodcutter", 1, listOf(7),
            listOf("§7벌목 시 묘목을 자동으로 심습니다."),
            "OAK_SAPLING"),
        SkillDef("bark_armor", "§6🛡 나무 껍질 갑옷", "woodcutter", 3, listOf(5, 10, 18),
            listOf("§7나무 근처에서 방어력이 증가합니다.", "§7Lv1: Resistance I / Lv2: II / Lv3: III", "§7범위: 5블록"),
            "OAK_LOG"),
        SkillDef("lumberjack_fury", "§6🪓 벌목꾼의 분노", "woodcutter", 3, listOf(6, 12, 20),
            listOf("§7도끼로 공격 시 추가 데미지.", "§7Lv1: +15% / Lv2: +30% / Lv3: +50%"),
            "NETHERITE_AXE"),

        // === Necromancer ===
        SkillDef("summon_undead", "§5☠ 언데드 소환", "necromancer", 3, listOf(1, 5, 10),
            listOf("§7좀비 미니언을 소환합니다.", "§7Lv1: 1마리 / Lv2: 2마리 / Lv3: 3마리"),
            "ZOMBIE_HEAD"),
        SkillDef("skeleton_archer", "§5⚔ 추가 소환", "necromancer", 3, listOf(3, 8, 15),
            listOf("§7추가 좀비 미니언을 소환합니다.", "§7Lv1: +1마리 / Lv2: +2마리 / Lv3: +3마리"),
            "ZOMBIE_HEAD"),
        SkillDef("life_siphon", "§5❤ 생명 착취", "necromancer", 3, listOf(5, 10, 18),
            listOf("§7미니언이 공격 시 주인 체력을 회복합니다.", "§7Lv1: 5% / Lv2: 10% / Lv3: 15%"),
            "GHAST_TEAR"),
        SkillDef("dark_aura", "§5🌑 암흑 오라", "necromancer", 3, listOf(7, 12, 20),
            listOf("§7주변 적에게 슬로우+어둠 효과를 줍니다.", "§7Lv1: 3초 / Lv2: 5초 / Lv3: 7초", "§7범위: 8블록, 쿨다운: 15초"),
            "WITHER_ROSE"),
        SkillDef("soul_empower", "§5💀 영혼 강화", "necromancer", 3, listOf(4, 9, 16),
            listOf("§7미니언의 체력, 공격력, 이동속도를 강화합니다.", "§7Lv1: +25%/+10%속도 / Lv2: +50%/+25% / Lv3: +100%/+50%"),
            "SOUL_LANTERN"),
        SkillDef("mind_control", "§5🧠 정신지배", "necromancer", 3, listOf(6, 11, 18),
            listOf("§7몹 처치 시 해당 몹을 미니언으로 부활시킵니다.", "§7Lv1: 30% / Lv2: 50% / Lv3: 100%", "§7부활 미니언: 체력 매우 낮음, 30초 지속"),
            "SCULK_SHRIEKER"),
        SkillDef("soul_shield", "§5🛡 영혼 방패", "necromancer", 3, listOf(8, 14, 20),
            listOf("§7피격 시 미니언이 대신 데미지를 흡수합니다.", "§7Lv1: 20% 확률 / Lv2: 35% / Lv3: 50%"),
            "SHIELD"),
        SkillDef("death_explosion", "§5💥 죽음의 폭발", "necromancer", 3, listOf(7, 13, 19),
            listOf("§7미니언 사망 시 주변에 폭발 데미지.", "§7Lv1: 3데미지/3블록 / Lv2: 5/4 / Lv3: 8/5"),
            "TNT")
    )

    fun forJob(job: String): List<SkillDef> = ALL.filter { it.job == job }
    fun get(key: String): SkillDef? = ALL.find { it.key == key }
}
