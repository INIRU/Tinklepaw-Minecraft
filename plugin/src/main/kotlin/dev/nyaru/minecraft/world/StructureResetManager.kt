package dev.nyaru.minecraft.world

import dev.nyaru.minecraft.NyaruPlugin
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Container
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.ItemFrame
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.world.LootGenerateEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.loot.Lootable
import java.io.File
import java.time.Instant

class StructureResetManager(private val plugin: NyaruPlugin) : Listener {

    data class LootEntry(val world: String, val x: Int, val y: Int, val z: Int, val lootTable: String)
    data class ElytraEntry(val world: String, val x: Int, val y: Int, val z: Int,
                           val facingX: Float, val facingY: Float)

    private val lootChests = mutableListOf<LootEntry>()
    private val elytraFrames = mutableListOf<ElytraEntry>()
    private val dataFile = File(plugin.dataFolder, "structure_loot.yml")
    private var lastReset: Long = 0L
    private val resetIntervalMs = 7L * 24 * 60 * 60 * 1000 // 7 days

    init {
        load()
        startAutoResetCheck()
    }

    // ── Track loot chests when players open them ─────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onLootGenerate(event: LootGenerateEvent) {
        val holder = event.inventoryHolder ?: return
        val loc = event.lootContext.location ?: return
        val world = loc.world ?: return

        // Only track Nether and End
        if (world.environment != World.Environment.NETHER &&
            world.environment != World.Environment.THE_END) return

        val entry = LootEntry(
            world.name, loc.blockX, loc.blockY, loc.blockZ,
            event.lootTable.key.toString()
        )

        // Avoid duplicates
        if (lootChests.none { it.world == entry.world && it.x == entry.x && it.y == entry.y && it.z == entry.z }) {
            lootChests.add(entry)
            save()
        }
    }

    // ── Track elytra item frames ─────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onItemFrameInteract(event: PlayerInteractEntityEvent) {
        val frame = event.rightClicked as? ItemFrame ?: return
        if (frame.world.environment != World.Environment.THE_END) return
        if (frame.item.type != Material.ELYTRA) return

        trackElytraFrame(frame)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onItemFrameBreak(event: EntityDamageByEntityEvent) {
        val frame = event.entity as? ItemFrame ?: return
        if (frame.world.environment != World.Environment.THE_END) return
        if (frame.item.type != Material.ELYTRA) return

        trackElytraFrame(frame)
    }

    private fun trackElytraFrame(frame: ItemFrame) {
        val loc = frame.location
        val entry = ElytraEntry(
            loc.world.name, loc.blockX, loc.blockY, loc.blockZ,
            loc.yaw, loc.pitch
        )
        if (elytraFrames.none { it.world == entry.world && it.x == entry.x && it.y == entry.y && it.z == entry.z }) {
            elytraFrames.add(entry)
            save()
        }
    }

    // ── Reset logic ──────────────────────────────────────────────────────

    fun resetLoot(environment: World.Environment?): Int {
        var count = 0

        // Reset loot chests
        for (entry in lootChests.toList()) {
            val world = Bukkit.getWorld(entry.world) ?: continue
            if (environment != null && world.environment != environment) continue

            val block = world.getBlockAt(entry.x, entry.y, entry.z)
            val state = block.state
            if (state is Container && state is Lootable) {
                state.inventory.clear()
                val lootTableKey = org.bukkit.NamespacedKey.fromString(entry.lootTable)
                if (lootTableKey != null) {
                    val lootTable = Bukkit.getLootTable(lootTableKey)
                    if (lootTable != null) {
                        state.lootTable = lootTable
                        state.seed = System.currentTimeMillis()
                        state.update()
                        count++
                    }
                }
            }
        }

        // Reset elytra item frames (End only)
        if (environment == null || environment == World.Environment.THE_END) {
            for (entry in elytraFrames.toList()) {
                val world = Bukkit.getWorld(entry.world) ?: continue
                if (world.environment != World.Environment.THE_END) continue

                val loc = org.bukkit.Location(world,
                    entry.x.toDouble() + 0.5, entry.y.toDouble() + 0.5, entry.z.toDouble() + 0.5,
                    entry.facingX, entry.facingY)

                // Check if chunk is loaded, load it temporarily
                val chunk = world.getChunkAt(entry.x shr 4, entry.z shr 4)
                val wasLoaded = chunk.isLoaded
                if (!wasLoaded) chunk.load()

                try {
                    // Find existing item frame at this location
                    val nearbyFrames = world.getNearbyEntities(loc, 1.0, 1.0, 1.0)
                        .filterIsInstance<ItemFrame>()

                    val existingFrame = nearbyFrames.firstOrNull {
                        it.location.blockX == entry.x &&
                        it.location.blockY == entry.y &&
                        it.location.blockZ == entry.z
                    }

                    if (existingFrame != null) {
                        // Frame exists — put elytra back if empty
                        if (existingFrame.item.type == Material.AIR) {
                            existingFrame.setItem(ItemStack(Material.ELYTRA))
                            count++
                        }
                    } else {
                        // Frame was destroyed — spawn a new one with elytra
                        world.spawn(loc, ItemFrame::class.java) { frame ->
                            frame.setItem(ItemStack(Material.ELYTRA))
                        }
                        count++
                    }
                } finally {
                    if (!wasLoaded) chunk.unload()
                }
            }
        }

        lastReset = System.currentTimeMillis()
        save()

        plugin.logger.info("구조물 전리품 초기화 완료: ${count}개 리셋됨")
        return count
    }

    // ── Also scan for un-opened loot chests (still have loot tables) ─────

    fun scanForLootChests(): Int {
        var found = 0
        for (world in Bukkit.getWorlds()) {
            if (world.environment != World.Environment.NETHER &&
                world.environment != World.Environment.THE_END) continue

            for (chunk in world.loadedChunks) {
                for (te in chunk.tileEntities) {
                    if (te is Container && te is Lootable) {
                        val lt = te.lootTable ?: continue
                        val loc = te.location
                        val entry = LootEntry(
                            world.name, loc.blockX, loc.blockY, loc.blockZ,
                            lt.key.toString()
                        )
                        if (lootChests.none { it.world == entry.world && it.x == entry.x && it.y == entry.y && it.z == entry.z }) {
                            lootChests.add(entry)
                            found++
                        }
                    }
                }

                // Also scan for elytra item frames
                if (world.environment == World.Environment.THE_END) {
                    for (entity in chunk.entities) {
                        if (entity is ItemFrame && entity.item.type == Material.ELYTRA) {
                            val loc = entity.location
                            val entry = ElytraEntry(
                                world.name, loc.blockX, loc.blockY, loc.blockZ,
                                loc.yaw, loc.pitch
                            )
                            if (elytraFrames.none { it.world == entry.world && it.x == entry.x && it.y == entry.y && it.z == entry.z }) {
                                elytraFrames.add(entry)
                                found++
                            }
                        }
                    }
                }
            }
        }

        if (found > 0) save()
        return found
    }

    // ── Auto reset timer ─────────────────────────────────────────────────

    private fun startAutoResetCheck() {
        // Check every hour (72000 ticks) if weekly reset is needed
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, Runnable {
            if (System.currentTimeMillis() - lastReset >= resetIntervalMs) {
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    val count = resetLoot(null)
                    Bukkit.broadcast(
                        net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                            .legacySection().deserialize(
                                "§6§l[서버] §e네더/엔드 구조물 전리품이 초기화되었습니다! §7(${count}개)"
                            )
                    )
                })
            }
        }, 72000L, 72000L) // check every hour

        // Also check on startup (delayed 1 minute for worlds to load)
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            scanForLootChests()
            if (System.currentTimeMillis() - lastReset >= resetIntervalMs) {
                val count = resetLoot(null)
                if (count > 0) {
                    Bukkit.broadcast(
                        net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                            .legacySection().deserialize(
                                "§6§l[서버] §e네더/엔드 구조물 전리품이 초기화되었습니다! §7(${count}개)"
                            )
                    )
                }
            }
        }, 1200L) // 1 minute after start
    }

    // ── Persistence ──────────────────────────────────────────────────────

    private fun load() {
        if (!dataFile.exists()) return
        val yaml = YamlConfiguration.loadConfiguration(dataFile)

        lastReset = yaml.getLong("lastReset", 0L)

        val chestList = yaml.getMapList("chests")
        for (map in chestList) {
            lootChests.add(LootEntry(
                map["world"] as? String ?: continue,
                (map["x"] as? Number)?.toInt() ?: continue,
                (map["y"] as? Number)?.toInt() ?: continue,
                (map["z"] as? Number)?.toInt() ?: continue,
                map["lootTable"] as? String ?: continue
            ))
        }

        val frameList = yaml.getMapList("elytraFrames")
        for (map in frameList) {
            elytraFrames.add(ElytraEntry(
                map["world"] as? String ?: continue,
                (map["x"] as? Number)?.toInt() ?: continue,
                (map["y"] as? Number)?.toInt() ?: continue,
                (map["z"] as? Number)?.toInt() ?: continue,
                (map["facingX"] as? Number)?.toFloat() ?: 0f,
                (map["facingY"] as? Number)?.toFloat() ?: 0f
            ))
        }
    }

    fun save() {
        val yaml = YamlConfiguration()
        yaml.set("lastReset", lastReset)

        val chestMaps = lootChests.map { entry ->
            mapOf(
                "world" to entry.world,
                "x" to entry.x,
                "y" to entry.y,
                "z" to entry.z,
                "lootTable" to entry.lootTable
            )
        }
        yaml.set("chests", chestMaps)

        val frameMaps = elytraFrames.map { entry ->
            mapOf(
                "world" to entry.world,
                "x" to entry.x,
                "y" to entry.y,
                "z" to entry.z,
                "facingX" to entry.facingX,
                "facingY" to entry.facingY
            )
        }
        yaml.set("elytraFrames", frameMaps)

        yaml.save(dataFile)
    }

    fun getStats(): String {
        val nextReset = if (lastReset > 0) {
            val remaining = resetIntervalMs - (System.currentTimeMillis() - lastReset)
            if (remaining > 0) {
                val days = remaining / (24 * 60 * 60 * 1000)
                val hours = (remaining % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000)
                "${days}일 ${hours}시간 후"
            } else "초기화 대기 중"
        } else "미실행"

        return "추적 상자: ${lootChests.size}개, 겉날개: ${elytraFrames.size}개, 다음 초기화: $nextReset"
    }
}
