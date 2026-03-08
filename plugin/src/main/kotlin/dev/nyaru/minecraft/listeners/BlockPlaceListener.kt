package dev.nyaru.minecraft.listeners

import dev.nyaru.minecraft.NyaruPlugin
import dev.nyaru.minecraft.skills.SkillManager
import dev.nyaru.minecraft.util.triggerLevelUp
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.persistence.PersistentDataType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private val SEED_TO_CROP = mapOf(
    Material.WHEAT_SEEDS to Material.WHEAT,
    Material.POTATO to Material.POTATOES,
    Material.CARROT to Material.CARROTS,
    Material.BEETROOT_SEEDS to Material.BEETROOTS,
    Material.MELON_SEEDS to Material.MELON_STEM,
    Material.PUMPKIN_SEEDS to Material.PUMPKIN_STEM
)

class BlockPlaceListener(private val plugin: NyaruPlugin, private val skillManager: SkillManager) : Listener {

    private val widePlantActive = ConcurrentHashMap.newKeySet<UUID>()

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onSellItemBlock(event: BlockPlaceEvent) {
        val meta = event.itemInHand.itemMeta ?: return
        if (meta.persistentDataContainer.get(CROP_USAGE_KEY, PersistentDataType.STRING) == "sell") {
            event.isCancelled = true
            event.player.sendActionBar(
                net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                    .deserialize("§c이 아이템은 판매 전용입니다.")
            )
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        val placedType = event.blockPlaced.type
        // Check if the placed block is a crop (from seed)
        if (placedType !in SEED_TO_CROP.values) return

        val player = event.player
        val uuid = player.uniqueId

        // Grant 1 XP for planting crops (farmer only)
        if (plugin.dataManager.getPlayer(uuid)?.job == "farmer") {
            val result = plugin.dataManager.grantXp(uuid, 1)
            if (result != null) {
                plugin.actionBarManager.updateXp(uuid, result.level, result.xp)
                if (result.leveledUp) {
                    triggerLevelUp(plugin, player, result.level, result.newSkillPoints)
                }
            }
        }

        val skills = skillManager.getSkills(uuid)
        if (skills.getLevel("wide_plant") < 1) return
        if (widePlantActive.contains(uuid)) return

        // Find which seed item corresponds to the placed crop
        val seedMaterial = SEED_TO_CROP.entries.find { it.value == placedType }?.key ?: return

        widePlantActive.add(uuid)
        val center = event.blockPlaced.location
        val world = center.world ?: run { widePlantActive.remove(uuid); return }

        plugin.server.scheduler.runTask(plugin, Runnable {
            try {
                for (dx in -1..1) {
                    for (dz in -1..1) {
                        if (dx == 0 && dz == 0) continue
                        val targetBlock = world.getBlockAt(
                            center.blockX + dx, center.blockY, center.blockZ + dz
                        )
                        val belowBlock = world.getBlockAt(
                            center.blockX + dx, center.blockY - 1, center.blockZ + dz
                        )

                        // Must be air on farmland
                        if (targetBlock.type != Material.AIR) continue
                        if (belowBlock.type != Material.FARMLAND) continue

                        // Check if player has plant-tagged seeds in inventory
                        val inv = player.inventory
                        val seedSlot = inv.contents.indexOfFirst { stack ->
                            stack != null && stack.type == seedMaterial &&
                            stack.itemMeta?.persistentDataContainer?.get(CROP_USAGE_KEY, PersistentDataType.STRING) == "plant"
                        }
                        if (seedSlot == -1) break // No more seeds

                        // Consume one seed
                        val seedStack = inv.getItem(seedSlot) ?: continue
                        if (seedStack.amount > 1) {
                            seedStack.amount = seedStack.amount - 1
                        } else {
                            inv.setItem(seedSlot, null)
                        }

                        // Place the crop
                        targetBlock.type = placedType
                    }
                }
            } finally {
                widePlantActive.remove(uuid)
            }
        })
    }
}
