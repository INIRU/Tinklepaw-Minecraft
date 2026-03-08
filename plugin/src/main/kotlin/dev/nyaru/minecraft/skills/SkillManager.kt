package dev.nyaru.minecraft.skills

import dev.nyaru.minecraft.NyaruPlugin
import dev.nyaru.minecraft.listeners.LOG_MATERIALS
import dev.nyaru.minecraft.listeners.LEAF_MATERIALS
import dev.nyaru.minecraft.model.Jobs
import dev.nyaru.minecraft.model.SkillData
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class SkillManager(private val plugin: NyaruPlugin) : Listener {

    private val skillCache = ConcurrentHashMap<UUID, SkillData>()

    fun getSkills(uuid: UUID): SkillData = skillCache[uuid] ?: SkillData()

    fun updateCache(uuid: UUID, skills: SkillData) {
        skillCache[uuid] = skills
    }

    fun refresh(uuid: UUID) {
        val skills = plugin.dataManager.getSkills(uuid)
        skillCache[uuid] = skills
        val player = plugin.server.getPlayer(uuid) ?: return
        plugin.server.scheduler.runTask(plugin, Runnable {
            applyPassiveEffects(uuid)
        })
    }

    fun applyPassiveEffects(uuid: UUID) {
        val player = plugin.server.getPlayer(uuid) ?: return
        val skills = skillCache[uuid] ?: return
        val job = plugin.dataManager.getPlayer(uuid)?.job

        // Remove all managed passives first, then re-apply what's needed
        player.removePotionEffect(PotionEffectType.HASTE)
        player.removePotionEffect(PotionEffectType.RESISTANCE)
        player.removePotionEffect(PotionEffectType.DOLPHINS_GRACE)
        player.removePotionEffect(PotionEffectType.LUCK)

        when (job) {
            Jobs.MINER -> {
                val hasteLevel = skills.getLevel("mining_speed")
                if (hasteLevel > 0) {
                    player.addPotionEffect(PotionEffect(
                        PotionEffectType.HASTE,
                        Integer.MAX_VALUE,
                        hasteLevel - 1,
                        false, false, false
                    ))
                }
                val resistLevel = skills.getLevel("stone_skin")
                if (resistLevel > 0) {
                    player.addPotionEffect(PotionEffect(
                        PotionEffectType.RESISTANCE,
                        Integer.MAX_VALUE,
                        resistLevel - 1,
                        false, false, false
                    ))
                }
            }
            Jobs.FISHER -> {
                val seaLevel = skills.getLevel("sea_blessing")
                if (seaLevel > 0) {
                    player.addPotionEffect(PotionEffect(
                        PotionEffectType.DOLPHINS_GRACE,
                        Integer.MAX_VALUE,
                        seaLevel - 1,
                        false, false, false
                    ))
                }
                val luckLevel = skills.getLevel("lucky_catch")
                if (luckLevel > 0) {
                    player.addPotionEffect(PotionEffect(
                        PotionEffectType.LUCK,
                        Integer.MAX_VALUE,
                        luckLevel - 1,
                        false, false, false
                    ))
                }
            }
            Jobs.WOODCUTTER -> {
                val axeLevel = skills.getLevel("axe_mastery")
                if (axeLevel > 0) {
                    player.addPotionEffect(PotionEffect(
                        PotionEffectType.HASTE,
                        Integer.MAX_VALUE,
                        axeLevel - 1,
                        false, false, false
                    ))
                }
            }
            Jobs.NECROMANCER -> {
                // All necromancer skills are active/triggered — no permanent passive effects
            }
        }
    }

    fun startForestBlessingCheck() {
        // Check every 3 seconds (60 ticks) if woodcutters are near trees
        Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            for (player in Bukkit.getOnlinePlayers()) {
                val uuid = player.uniqueId
                val job = plugin.dataManager.getPlayer(uuid)?.job
                if (job != Jobs.WOODCUTTER) continue

                val skills = skillCache[uuid] ?: continue
                val forestLevel = skills.getLevel("forest_blessing")
                if (forestLevel <= 0) continue

                // Check if near trees (5 block radius)
                val loc = player.location
                val world = loc.world ?: continue
                var nearTree = false
                outer@ for (dx in -5..5) {
                    for (dy in -3..5) {
                        for (dz in -5..5) {
                            val block = world.getBlockAt(loc.blockX + dx, loc.blockY + dy, loc.blockZ + dz)
                            if (block.type in LOG_MATERIALS || block.type in LEAF_MATERIALS) {
                                nearTree = true
                                break@outer
                            }
                        }
                    }
                }

                if (nearTree) {
                    // Apply Regeneration for 5 seconds (will be refreshed by next check)
                    player.addPotionEffect(PotionEffect(
                        PotionEffectType.REGENERATION,
                        100, // 5 seconds
                        forestLevel - 1,
                        false, false, true
                    ))
                }
            }
        }, 60L, 60L)
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val uuid = event.player.uniqueId
        val skills = plugin.dataManager.getSkills(uuid)
        skillCache[uuid] = skills
        plugin.server.scheduler.runTask(plugin, Runnable {
            applyPassiveEffects(uuid)
        })
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        skillCache.remove(event.player.uniqueId)
    }
}
