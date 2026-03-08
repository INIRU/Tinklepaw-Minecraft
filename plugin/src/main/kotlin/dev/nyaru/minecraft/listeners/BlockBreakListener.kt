package dev.nyaru.minecraft.listeners

import dev.nyaru.minecraft.NyaruPlugin
import dev.nyaru.minecraft.skills.SkillManager
import dev.nyaru.minecraft.util.triggerLevelUp
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.NamespacedKey
import org.bukkit.block.data.Ageable
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.util.LinkedList
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

val HARVEST_TIME_KEY = NamespacedKey("nyaru", "harvest_time")

private val CROP_MATERIALS = setOf(
    Material.WHEAT, Material.POTATOES, Material.CARROTS,
    Material.SUGAR_CANE, Material.PUMPKIN, Material.MELON,
    Material.BEETROOTS, Material.COCOA
)

private val AGEABLE_CROPS = setOf(
    Material.WHEAT, Material.POTATOES, Material.CARROTS, Material.BEETROOTS, Material.COCOA
)

private val CROP_DROP_ITEM = mapOf(
    Material.WHEAT to Material.WHEAT,
    Material.POTATOES to Material.POTATO,
    Material.CARROTS to Material.CARROT,
    Material.SUGAR_CANE to Material.SUGAR_CANE,
    Material.PUMPKIN to Material.PUMPKIN,
    Material.MELON to Material.MELON_SLICE,
    Material.BEETROOTS to Material.BEETROOT,
    Material.COCOA to Material.COCOA_BEANS
)

private val SEED_MATERIALS = setOf(
    Material.WHEAT_SEEDS, Material.BEETROOT_SEEDS,
    Material.MELON_SEEDS, Material.PUMPKIN_SEEDS,
    Material.TORCHFLOWER_SEEDS, Material.PITCHER_POD
)

val LOG_MATERIALS = setOf(
    Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG, Material.JUNGLE_LOG,
    Material.ACACIA_LOG, Material.DARK_OAK_LOG, Material.MANGROVE_LOG, Material.CHERRY_LOG,
    Material.BAMBOO_BLOCK, Material.CRIMSON_STEM, Material.WARPED_STEM,
    Material.OAK_WOOD, Material.SPRUCE_WOOD, Material.BIRCH_WOOD, Material.JUNGLE_WOOD,
    Material.ACACIA_WOOD, Material.DARK_OAK_WOOD, Material.MANGROVE_WOOD, Material.CHERRY_WOOD,
    Material.CRIMSON_HYPHAE, Material.WARPED_HYPHAE,
    Material.STRIPPED_OAK_LOG, Material.STRIPPED_SPRUCE_LOG, Material.STRIPPED_BIRCH_LOG,
    Material.STRIPPED_JUNGLE_LOG, Material.STRIPPED_ACACIA_LOG, Material.STRIPPED_DARK_OAK_LOG,
    Material.STRIPPED_MANGROVE_LOG, Material.STRIPPED_CHERRY_LOG,
    Material.STRIPPED_CRIMSON_STEM, Material.STRIPPED_WARPED_STEM,
    Material.STRIPPED_OAK_WOOD, Material.STRIPPED_SPRUCE_WOOD, Material.STRIPPED_BIRCH_WOOD,
    Material.STRIPPED_JUNGLE_WOOD, Material.STRIPPED_ACACIA_WOOD, Material.STRIPPED_DARK_OAK_WOOD,
    Material.STRIPPED_MANGROVE_WOOD, Material.STRIPPED_CHERRY_WOOD,
    Material.STRIPPED_CRIMSON_HYPHAE, Material.STRIPPED_WARPED_HYPHAE
)

val LEAF_MATERIALS = setOf(
    Material.OAK_LEAVES, Material.SPRUCE_LEAVES, Material.BIRCH_LEAVES, Material.JUNGLE_LEAVES,
    Material.ACACIA_LEAVES, Material.DARK_OAK_LEAVES, Material.MANGROVE_LEAVES,
    Material.CHERRY_LEAVES, Material.AZALEA_LEAVES, Material.FLOWERING_AZALEA_LEAVES
)

class BlockBreakListener(private val plugin: NyaruPlugin, private val skillManager: SkillManager) : Listener {

    // Prevent recursive wide harvest / timber
    private val wideHarvestActive = ConcurrentHashMap.newKeySet<UUID>()
    private val timberActive = ConcurrentHashMap.newKeySet<UUID>()

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        val player = event.player
        val uuid = player.uniqueId
        val blockType = event.block.type

        // ── Farmer crop logic ──────────────────────────────────────────────
        if (blockType in CROP_MATERIALS) {
            // Tag dropped items with harvest time; remove seeds
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                val items = event.block.location.world?.getNearbyEntities(
                    event.block.location.add(0.5, 0.5, 0.5), 2.0, 2.0, 2.0
                )?.filterIsInstance<org.bukkit.entity.Item>() ?: return@Runnable

                for (item in items) {
                    if (item.itemStack.type in SEED_MATERIALS) {
                        item.remove()
                        continue
                    }
                    item.itemStack.editMeta { meta ->
                        val windowMs = 5 * 60 * 1000L
                        val roundedTime = (System.currentTimeMillis() / windowMs) * windowMs
                        meta.persistentDataContainer.set(
                            HARVEST_TIME_KEY,
                            PersistentDataType.LONG,
                            roundedTime
                        )
                        val legacy = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                        meta.lore(listOf(
                            legacy.deserialize("§b✦ 신선한 농작물"),
                            legacy.deserialize("§7상점에서 높은 가격을 받습니다."),
                            legacy.deserialize("§7시간이 지날수록 신선도가 감소합니다.")
                        ))
                    }
                }
            }, 1L)

            val skills = skillManager.getSkills(uuid)

            // Wide Harvest: 3x3 crop break
            if (skills.getLevel("wide_harvest") >= 1 && blockType in AGEABLE_CROPS && !wideHarvestActive.contains(uuid)) {
                wideHarvestActive.add(uuid)
                val center = event.block.location
                val world = center.world ?: run { wideHarvestActive.remove(uuid); return }
                val tool = player.inventory.itemInMainHand
                player.playSound(player.location, Sound.ITEM_CROP_PLANT, 0.7f, 1.2f)
                plugin.server.scheduler.runTask(plugin, Runnable {
                    try {
                        for (dx in -1..1) {
                            for (dz in -1..1) {
                                if (dx == 0 && dz == 0) continue
                                val neighbor = world.getBlockAt(
                                    center.blockX + dx, center.blockY, center.blockZ + dz
                                )
                                if (neighbor.type in AGEABLE_CROPS) {
                                    val data = neighbor.blockData
                                    if (data is Ageable && data.age >= data.maximumAge) {
                                        val neighborLoc = neighbor.location.clone()
                                        neighbor.breakNaturally(tool)
                                        plugin.server.scheduler.runTaskLater(plugin, Runnable {
                                            neighborLoc.world?.getNearbyEntities(
                                                neighborLoc.add(0.5, 0.5, 0.5), 1.5, 1.5, 1.5
                                            )?.filterIsInstance<org.bukkit.entity.Item>()
                                                ?.filter { it.itemStack.type in SEED_MATERIALS }
                                                ?.forEach { it.remove() }
                                        }, 2L)
                                    }
                                }
                            }
                        }
                    } finally {
                        wideHarvestActive.remove(uuid)
                    }
                })
            }

            // Harvest Fortune: extra drops
            val harvestFortune = skills.getLevel("harvest_fortune")
            if (harvestFortune > 0 && blockType in CROP_MATERIALS) {
                val dropMaterial = CROP_DROP_ITEM[blockType]
                if (dropMaterial != null) {
                    player.playSound(player.location, Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.4f)
                    plugin.server.scheduler.runTaskLater(plugin, Runnable {
                        event.block.location.world?.dropItemNaturally(
                            event.block.location.add(0.5, 0.5, 0.5),
                            ItemStack(dropMaterial, harvestFortune)
                        )
                    }, 2L)
                }
            }

            // Grant XP for crop harvest (farmer only) — direct local call, no coroutine
            if (plugin.dataManager.getPlayer(uuid)?.job == "farmer") {
                val result = plugin.dataManager.grantXp(uuid, 2)
                if (result != null) {
                    plugin.actionBarManager.updateXp(uuid, result.level, result.xp)
                    if (result.leveledUp) {
                        triggerLevelUp(plugin, player, result.level, result.newSkillPoints)
                    }
                }
            }

            return
        }

        // ── Woodcutter log logic ───────────────────────────────────────────
        if (blockType in LOG_MATERIALS && !timberActive.contains(uuid)) {
            if (plugin.dataManager.getPlayer(uuid)?.job != "woodcutter") return

            // Grant 3 XP for log break
            val result = plugin.dataManager.grantXp(uuid, 3)
            if (result != null) {
                plugin.actionBarManager.updateXp(uuid, result.level, result.xp)
                if (result.leveledUp) {
                    triggerLevelUp(plugin, player, result.level, result.newSkillPoints)
                }
            }

            val skills = skillManager.getSkills(uuid)
            val timberLevel = skills.getLevel("timber")
            val leafBlowerLevel = skills.getLevel("leaf_blower")

            if (timberLevel >= 1) {
                timberActive.add(uuid)
                val tool = player.inventory.itemInMainHand
                plugin.server.scheduler.runTask(plugin, Runnable {
                    try {
                        breakConnectedLogs(event.block, blockType, leafBlowerLevel >= 1, tool)
                    } finally {
                        timberActive.remove(uuid)
                    }
                })
            }
        }
    }

    private fun breakConnectedLogs(
        origin: org.bukkit.block.Block,
        logType: Material,
        breakLeaves: Boolean,
        tool: ItemStack
    ) {
        val visited = mutableSetOf<org.bukkit.block.Block>()
        val queue = LinkedList<org.bukkit.block.Block>()
        queue.add(origin)
        visited.add(origin)

        val offsets = buildList {
            for (dx in -1..1) for (dy in -1..1) for (dz in -1..1) {
                if (dx != 0 || dy != 0 || dz != 0) add(Triple(dx, dy, dz))
            }
        }

        while (queue.isNotEmpty() && visited.size <= 64) {
            val current = queue.poll()
            for ((dx, dy, dz) in offsets) {
                val neighbor = current.getRelative(dx, dy, dz)
                if (neighbor in visited) continue
                if (neighbor.type == logType) {
                    visited.add(neighbor)
                    queue.add(neighbor)
                    neighbor.breakNaturally(tool)
                } else if (breakLeaves && neighbor.type in LEAF_MATERIALS) {
                    visited.add(neighbor)
                    neighbor.breakNaturally(tool)
                }
            }
        }
    }
}
