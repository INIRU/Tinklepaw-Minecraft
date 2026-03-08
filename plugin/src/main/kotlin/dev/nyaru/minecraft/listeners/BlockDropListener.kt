package dev.nyaru.minecraft.listeners

import dev.nyaru.minecraft.NyaruPlugin
import dev.nyaru.minecraft.skills.SkillManager
import dev.nyaru.minecraft.util.triggerLevelUp
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.NamespacedKey
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockDropItemEvent
import org.bukkit.persistence.PersistentDataType
import kotlin.math.max

val PURITY_KEY = NamespacedKey("nyaru", "purity")

private val ORE_MATERIALS = setOf(
    Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE,
    Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
    Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE,
    Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE,
    Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
    Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE,
    Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
    Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
    Material.ANCIENT_DEBRIS
)

class BlockDropListener(private val plugin: NyaruPlugin, private val skillManager: SkillManager) : Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockDrop(event: BlockDropItemEvent) {
        val blockType = event.blockState.type
        if (blockType !in ORE_MATERIALS) return

        val y = event.blockState.y
        val baseRandom = 60 + (Math.random() * 40).toInt()
        val yBonus = max(0, (-y / 6))
        val purity = (baseRandom + yBonus).coerceIn(50, 100)

        val purityColor = when {
            purity >= 90 -> "§d"
            purity >= 75 -> "§b"
            purity >= 60 -> "§a"
            else -> "§7"
        }

        for (item in event.items) {
            item.itemStack.editMeta { meta ->
                meta.persistentDataContainer.set(
                    PURITY_KEY,
                    PersistentDataType.INTEGER,
                    purity
                )
                val legacy = LegacyComponentSerializer.legacySection()
                meta.lore(listOf(
                    legacy.deserialize("${purityColor}✦ 순정도: §f${purity}%"),
                    legacy.deserialize("§7상점에서 높은 가격을 받습니다."),
                    legacy.deserialize("§7Y좌표가 낮을수록 순정도가 높아집니다.")
                ))
            }
        }

        val player = event.player
        val uuid = player.uniqueId

        // Lucky Strike: chance to double drops (miner skill)
        val luckyLv = skillManager.getSkills(uuid).getLevel("lucky_strike")
        if (luckyLv > 0) {
            val chance = luckyLv * 0.15
            if (Math.random() < chance) {
                val extraItems = event.items.map { it.itemStack.clone() }
                for (extra in extraItems) {
                    event.block.world.dropItemNaturally(event.block.location, extra)
                }
                player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f)
                player.sendActionBar(
                    LegacyComponentSerializer.legacySection()
                        .deserialize("§6✦ §e행운 채굴! §7아이템이 2배입니다.")
                )
            }
        }

        // Grant XP for mining (miner only) — direct local call, no coroutine
        if (plugin.dataManager.getPlayer(uuid)?.job == "miner") {
            val result = plugin.dataManager.grantXp(uuid, 5)
            if (result != null) {
                plugin.actionBarManager.updateXp(uuid, result.level, result.xp)
                if (result.leveledUp) {
                    triggerLevelUp(plugin, player, result.level, result.newSkillPoints)
                }
            }
        }
    }
}
