package dev.nyaru.minecraft.listeners

import dev.nyaru.minecraft.NyaruPlugin
import dev.nyaru.minecraft.skills.SkillManager
import dev.nyaru.minecraft.util.triggerLevelUp
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.NamespacedKey
import org.bukkit.block.data.Ageable
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockDropItemEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.util.LinkedList
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

val HARVEST_TIME_KEY = NamespacedKey("nyaru", "harvest_time")
val CROP_USAGE_KEY = NamespacedKey("nyaru", "crop_usage")

private val CROP_MATERIALS = setOf(
    Material.WHEAT, Material.POTATOES, Material.CARROTS,
    Material.SUGAR_CANE, Material.PUMPKIN, Material.MELON,
    Material.BEETROOTS, Material.COCOA
)

private val AGEABLE_CROPS = setOf(
    Material.WHEAT, Material.POTATOES, Material.CARROTS, Material.BEETROOTS, Material.COCOA
)

private val CROP_XP = mapOf(
    Material.WHEAT to 3,
    Material.POTATOES to 3,
    Material.CARROTS to 3,
    Material.BEETROOTS to 4,
    Material.SUGAR_CANE to 2,
    Material.PUMPKIN to 5,
    Material.MELON to 5,
    Material.COCOA to 4
)

private data class CropDropInfo(
    val sellMaterial: Material,
    val sellName: String,
    val plantMaterial: Material,
    val plantName: String,
    val sellRange: IntRange = 1..2,
    val plantCount: Int = 1
)

private val CROP_DROPS = mapOf(
    Material.WHEAT to CropDropInfo(Material.WHEAT, "밀", Material.WHEAT_SEEDS, "밀 씨앗"),
    Material.POTATOES to CropDropInfo(Material.POTATO, "감자", Material.POTATO, "감자", 1..3),
    Material.CARROTS to CropDropInfo(Material.CARROT, "당근", Material.CARROT, "당근", 1..3),
    Material.BEETROOTS to CropDropInfo(Material.BEETROOT, "비트", Material.BEETROOT_SEEDS, "비트 씨앗"),
    Material.COCOA to CropDropInfo(Material.COCOA_BEANS, "코코아", Material.COCOA_BEANS, "코코아", 1..2),
    Material.SUGAR_CANE to CropDropInfo(Material.SUGAR_CANE, "사탕수수", Material.SUGAR_CANE, "사탕수수"),
    Material.PUMPKIN to CropDropInfo(Material.PUMPKIN, "호박", Material.PUMPKIN_SEEDS, "호박 씨앗"),
    Material.MELON to CropDropInfo(Material.MELON_SLICE, "수박", Material.MELON_SEEDS, "수박 씨앗", 3..7)
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
    private val legacy = LegacyComponentSerializer.legacySection()

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onCropDrop(event: BlockDropItemEvent) {
        val blockType = event.blockState.type
        if (blockType !in CROP_MATERIALS) return

        // For ageable crops, only apply custom drops when fully grown
        if (blockType in AGEABLE_CROPS) {
            val data = event.blockState.blockData
            if (data is Ageable && data.age < data.maximumAge) {
                // Not fully grown — replace all drops with 1 plant item
                event.items.forEach { it.remove() }
                val dropInfo = CROP_DROPS[blockType] ?: return
                event.block.world.dropItemNaturally(
                    event.block.location.add(0.5, 0.5, 0.5),
                    createPlantItem(dropInfo)
                )
                return
            }
        }

        val dropInfo = CROP_DROPS[blockType] ?: return

        // Remove all vanilla drops
        event.items.forEach { it.remove() }

        // Calculate harvest fortune bonus (farmer only)
        val uuid = event.player.uniqueId
        val harvestFortune = if (plugin.dataManager.getPlayer(uuid)?.job == "farmer") {
            skillManager.getSkills(uuid).getLevel("harvest_fortune")
        } else 0

        dropCustomCropItems(event.block.location, blockType, harvestFortune)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        val player = event.player
        val uuid = player.uniqueId
        val blockType = event.block.type

        // ── Farmer crop logic ──────────────────────────────────────────────
        if (blockType in CROP_MATERIALS) {
            val skills = skillManager.getSkills(uuid)

            // Wide Harvest: 3x3 crop break
            if (skills.getLevel("wide_harvest") >= 1 && blockType in AGEABLE_CROPS && !wideHarvestActive.contains(uuid)) {
                wideHarvestActive.add(uuid)
                val center = event.block.location
                val world = center.world ?: run { wideHarvestActive.remove(uuid); return }
                val pm = plugin.protectionManager
                val playerUuidStr = uuid.toString()
                player.playSound(player.location, Sound.ITEM_CROP_PLANT, 0.7f, 1.2f)
                player.world.spawnParticle(
                    Particle.HAPPY_VILLAGER,
                    player.location.add(0.0, 0.5, 0.0),
                    12, 1.5, 0.3, 1.5, 0.0
                )
                val harvestFortune = skills.getLevel("harvest_fortune")
                plugin.server.scheduler.runTask(plugin, Runnable {
                    try {
                        for (dx in -1..1) {
                            for (dz in -1..1) {
                                if (dx == 0 && dz == 0) continue
                                val neighbor = world.getBlockAt(
                                    center.blockX + dx, center.blockY, center.blockZ + dz
                                )
                                // Skip protected blocks
                                if (pm.isProtected(neighbor.location) && !pm.canAccess(neighbor.location, playerUuidStr)) continue
                                if (neighbor.type in AGEABLE_CROPS) {
                                    val data = neighbor.blockData
                                    if (data is Ageable && data.age >= data.maximumAge) {
                                        val neighborType = neighbor.type
                                        neighbor.type = Material.AIR
                                        dropCustomCropItems(neighbor.location, neighborType, harvestFortune)
                                    }
                                }
                            }
                        }
                    } finally {
                        wideHarvestActive.remove(uuid)
                    }
                })
            }

            // Grant XP for crop harvest (farmer only) — crop-specific XP
            if (plugin.dataManager.getPlayer(uuid)?.job == "farmer") {
                val xpAmount = CROP_XP[blockType] ?: 2
                val result = plugin.dataManager.grantXp(uuid, xpAmount)
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

            // Don't activate timber if the broken block is protected
            if (timberLevel >= 1 && !plugin.protectionManager.isProtected(event.block.location)) {
                timberActive.add(uuid)
                val tool = player.inventory.itemInMainHand
                val brokenBlock = event.block
                player.playSound(player.location, Sound.BLOCK_WOOD_BREAK, 1.0f, 0.6f)
                player.world.spawnParticle(
                    Particle.SWEEP_ATTACK,
                    brokenBlock.location.add(0.5, 0.5, 0.5),
                    3, 0.5, 0.5, 0.5, 0.0
                )
                plugin.server.scheduler.runTask(plugin, Runnable {
                    try {
                        breakConnectedLogs(brokenBlock, blockType, leafBlowerLevel >= 1, tool)
                    } finally {
                        timberActive.remove(uuid)
                    }
                })

                // Replanter: auto-plant sapling
                if (skills.getLevel("replanter") >= 1) {
                    val sapling = logToSapling(blockType)
                    if (sapling != null) {
                        plugin.server.scheduler.runTaskLater(plugin, Runnable {
                            if (brokenBlock.type == Material.AIR) {
                                brokenBlock.type = sapling
                                val sapLoc = brokenBlock.location.add(0.5, 0.5, 0.5)
                                player.world.spawnParticle(Particle.HAPPY_VILLAGER, sapLoc, 8, 0.3, 0.3, 0.3, 0.0)
                                player.playSound(sapLoc, Sound.BLOCK_GRASS_PLACE, 0.8f, 1.3f)
                            }
                        }, 5L)
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onMinerItemDrop(event: BlockDropItemEvent) {
        val blockType = event.blockState.type
        // Skip crops — handled by onCropDrop
        if (blockType in CROP_MATERIALS) return

        val player = event.player
        val uuid = player.uniqueId
        val job = plugin.dataManager.getPlayer(uuid)?.job
        if (job != "miner") return
        val skills = skillManager.getSkills(uuid)
        if (skills.getLevel("magnet") < 1) return

        for (item in event.items.toList()) {
            val stack = item.itemStack
            val remaining = player.inventory.addItem(stack)
            if (remaining.isEmpty()) {
                item.remove()
            } else {
                item.itemStack = remaining.values.first()
            }
        }
        player.playSound(player.location, Sound.ENTITY_ITEM_PICKUP, 0.3f, 1.5f)
        player.world.spawnParticle(
            Particle.ELECTRIC_SPARK,
            player.location.add(0.0, 1.0, 0.0),
            5, 0.3, 0.3, 0.3, 0.05
        )
    }

    private fun logToSapling(logType: Material): Material? = when (logType) {
        Material.OAK_LOG, Material.OAK_WOOD, Material.STRIPPED_OAK_LOG, Material.STRIPPED_OAK_WOOD -> Material.OAK_SAPLING
        Material.BIRCH_LOG, Material.BIRCH_WOOD, Material.STRIPPED_BIRCH_LOG, Material.STRIPPED_BIRCH_WOOD -> Material.BIRCH_SAPLING
        Material.SPRUCE_LOG, Material.SPRUCE_WOOD, Material.STRIPPED_SPRUCE_LOG, Material.STRIPPED_SPRUCE_WOOD -> Material.SPRUCE_SAPLING
        Material.JUNGLE_LOG, Material.JUNGLE_WOOD, Material.STRIPPED_JUNGLE_LOG, Material.STRIPPED_JUNGLE_WOOD -> Material.JUNGLE_SAPLING
        Material.ACACIA_LOG, Material.ACACIA_WOOD, Material.STRIPPED_ACACIA_LOG, Material.STRIPPED_ACACIA_WOOD -> Material.ACACIA_SAPLING
        Material.DARK_OAK_LOG, Material.DARK_OAK_WOOD, Material.STRIPPED_DARK_OAK_LOG, Material.STRIPPED_DARK_OAK_WOOD -> Material.DARK_OAK_SAPLING
        Material.CHERRY_LOG, Material.CHERRY_WOOD, Material.STRIPPED_CHERRY_LOG, Material.STRIPPED_CHERRY_WOOD -> Material.CHERRY_SAPLING
        Material.MANGROVE_LOG, Material.MANGROVE_WOOD, Material.STRIPPED_MANGROVE_LOG, Material.STRIPPED_MANGROVE_WOOD -> Material.MANGROVE_PROPAGULE
        else -> null
    }

    private fun dropCustomCropItems(loc: org.bukkit.Location, cropType: Material, harvestFortune: Int = 0) {
        val dropInfo = CROP_DROPS[cropType] ?: return
        val world = loc.world ?: return
        val dropLoc = loc.clone().add(0.5, 0.5, 0.5)

        val sellCount = dropInfo.sellRange.random() + harvestFortune
        for (i in 0 until sellCount) {
            world.dropItemNaturally(dropLoc, createSellItem(dropInfo))
        }
        for (i in 0 until dropInfo.plantCount) {
            world.dropItemNaturally(dropLoc, createPlantItem(dropInfo))
        }
    }

    private fun createSellItem(info: CropDropInfo): ItemStack {
        val item = ItemStack(info.sellMaterial)
        item.editMeta { meta ->
            meta.persistentDataContainer.set(CROP_USAGE_KEY, PersistentDataType.STRING, "sell")
            val windowMs = 5 * 60 * 1000L
            val roundedTime = (System.currentTimeMillis() / windowMs) * windowMs
            meta.persistentDataContainer.set(HARVEST_TIME_KEY, PersistentDataType.LONG, roundedTime)
            meta.displayName(legacy.deserialize("§e${info.sellName} §7(판매용)"))
            meta.lore(listOf(
                legacy.deserialize("§7상점에서 판매할 수 있습니다."),
                legacy.deserialize("§b✦ 신선한 농작물"),
                legacy.deserialize("§7시간이 지날수록 신선도가 감소합니다.")
            ))
        }
        return item
    }

    private fun createPlantItem(info: CropDropInfo): ItemStack {
        val item = ItemStack(info.plantMaterial)
        item.editMeta { meta ->
            meta.persistentDataContainer.set(CROP_USAGE_KEY, PersistentDataType.STRING, "plant")
            meta.displayName(legacy.deserialize("§a${info.plantName} §7(심는용)"))
            meta.lore(listOf(
                legacy.deserialize("§7밭에 심을 수 있습니다.")
            ))
        }
        return item
    }

    private fun breakConnectedLogs(
        origin: org.bukkit.block.Block,
        logType: Material,
        breakLeaves: Boolean,
        tool: ItemStack
    ) {
        val pm = plugin.protectionManager
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
                val isLog = neighbor.type == logType
                val isLeaf = breakLeaves && neighbor.type in LEAF_MATERIALS
                if (!isLog && !isLeaf) continue

                // Skip ALL protected blocks — no chaining through any protected block
                if (pm.isProtected(neighbor.location)) {
                    visited.add(neighbor)
                    continue
                }

                if (isLog) {
                    visited.add(neighbor)
                    queue.add(neighbor)
                    neighbor.breakNaturally(tool)
                } else {
                    visited.add(neighbor)
                    neighbor.breakNaturally(tool)
                }
            }
        }
    }
}
