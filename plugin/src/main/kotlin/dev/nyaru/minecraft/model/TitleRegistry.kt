package dev.nyaru.minecraft.model

import org.bukkit.Material

data class TitleDef(
    val id: String,
    val displayName: String,
    val description: String,
    val icon: Material,
    val condition: TitleCondition
)

sealed class TitleCondition {
    data object FirstJoin : TitleCondition()
    data class JobLevel(val job: String, val level: Int) : TitleCondition()
    data class Balance(val amount: Int) : TitleCondition()
    data class TotalSkillPoints(val job: String, val points: Int) : TitleCondition()
}

object TitleRegistry {

    val ALL: List<TitleDef> = listOf(
        // ── General ──
        TitleDef("first_step", "§7첫 발걸음", "§7서버에 처음 접속하면 획득", Material.LEATHER_BOOTS, TitleCondition.FirstJoin),
        TitleDef("rich", "§e부자", "§7잔고 100,000냥 달성", Material.GOLD_INGOT, TitleCondition.Balance(100_000)),
        TitleDef("tycoon", "§6§l대부호", "§7잔고 1,000,000냥 달성", Material.GOLD_BLOCK, TitleCondition.Balance(1_000_000)),

        // ── Miner ──
        TitleDef("miner_5", "§9초보 광부", "§7광부 Lv.5 달성", Material.STONE_PICKAXE, TitleCondition.JobLevel(Jobs.MINER, 5)),
        TitleDef("miner_15", "§9§l숙련된 광부", "§7광부 Lv.15 달성", Material.IRON_PICKAXE, TitleCondition.JobLevel(Jobs.MINER, 15)),
        TitleDef("miner_30", "§9§l\u2726 전설의 광부", "§7광부 Lv.30 달성", Material.DIAMOND_PICKAXE, TitleCondition.JobLevel(Jobs.MINER, 30)),

        // ── Farmer ──
        TitleDef("farmer_5", "§a새싹 농부", "§7농부 Lv.5 달성", Material.WHEAT_SEEDS, TitleCondition.JobLevel(Jobs.FARMER, 5)),
        TitleDef("farmer_15", "§a§l풍요의 농부", "§7농부 Lv.15 달성", Material.GOLDEN_HOE, TitleCondition.JobLevel(Jobs.FARMER, 15)),
        TitleDef("farmer_30", "§a§l\u2726 대지의 수호자", "§7농부 Lv.30 달성", Material.DIAMOND_HOE, TitleCondition.JobLevel(Jobs.FARMER, 30)),

        // ── Warrior ──
        TitleDef("warrior_5", "§c전사 수련생", "§7전사 Lv.5 달성", Material.WOODEN_SWORD, TitleCondition.JobLevel(Jobs.WARRIOR, 5)),
        TitleDef("warrior_15", "§c§l전장의 영웅", "§7전사 Lv.15 달성", Material.IRON_SWORD, TitleCondition.JobLevel(Jobs.WARRIOR, 15)),
        TitleDef("warrior_30", "§c§l\u2726 불멸의 전사", "§7전사 Lv.30 달성", Material.NETHERITE_SWORD, TitleCondition.JobLevel(Jobs.WARRIOR, 30)),

        // ── Fisher ──
        TitleDef("fisher_5", "§3초보 어부", "§7어부 Lv.5 달성", Material.FISHING_ROD, TitleCondition.JobLevel(Jobs.FISHER, 5)),
        TitleDef("fisher_15", "§3§l바다의 사냥꾼", "§7어부 Lv.15 달성", Material.COD, TitleCondition.JobLevel(Jobs.FISHER, 15)),
        TitleDef("fisher_30", "§3§l\u2726 심해의 지배자", "§7어부 Lv.30 달성", Material.TRIDENT, TitleCondition.JobLevel(Jobs.FISHER, 30)),

        // ── Woodcutter ──
        TitleDef("woodcutter_5", "§6견습 벌목꾼", "§7벌목꾼 Lv.5 달성", Material.WOODEN_AXE, TitleCondition.JobLevel(Jobs.WOODCUTTER, 5)),
        TitleDef("woodcutter_15", "§6§l숲의 파괴자", "§7벌목꾼 Lv.15 달성", Material.IRON_AXE, TitleCondition.JobLevel(Jobs.WOODCUTTER, 15)),
        TitleDef("woodcutter_30", "§6§l\u2726 세계수의 벌목꾼", "§7벌목꾼 Lv.30 달성", Material.DIAMOND_AXE, TitleCondition.JobLevel(Jobs.WOODCUTTER, 30)),

        // ── Necromancer ──
        TitleDef("necromancer_5", "§5사령술 입문자", "§7네크로맨서 Lv.5 달성", Material.BONE, TitleCondition.JobLevel(Jobs.NECROMANCER, 5)),
        TitleDef("necromancer_15", "§5§l죽음의 인도자", "§7네크로맨서 Lv.15 달성", Material.WITHER_SKELETON_SKULL, TitleCondition.JobLevel(Jobs.NECROMANCER, 15)),
        TitleDef("necromancer_30", "§5§l\u2726 영혼의 군주", "§7네크로맨서 Lv.30 달성", Material.NETHER_STAR, TitleCondition.JobLevel(Jobs.NECROMANCER, 30))
    )

    fun get(id: String): TitleDef? = ALL.find { it.id == id }
}
