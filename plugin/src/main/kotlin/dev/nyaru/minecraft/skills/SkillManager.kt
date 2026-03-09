package dev.nyaru.minecraft.skills

import dev.nyaru.minecraft.NyaruPlugin
import dev.nyaru.minecraft.listeners.LOG_MATERIALS
import dev.nyaru.minecraft.listeners.LEAF_MATERIALS
import dev.nyaru.minecraft.model.Jobs
import dev.nyaru.minecraft.model.SkillData
import org.bukkit.Bukkit
import org.bukkit.Particle
import org.bukkit.Sound
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
        player.removePotionEffect(PotionEffectType.WATER_BREATHING)

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
                val waterBreathingLevel = skills.getLevel("water_breathing")
                if (waterBreathingLevel > 0) {
                    player.addPotionEffect(PotionEffect(
                        PotionEffectType.WATER_BREATHING,
                        Integer.MAX_VALUE,
                        waterBreathingLevel - 1,
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
                    player.world.spawnParticle(
                        Particle.HAPPY_VILLAGER,
                        player.location.add(0.0, 1.5, 0.0),
                        3, 0.4, 0.4, 0.4, 0.0
                    )
                }

                // Bark Armor: Resistance near trees
                val barkArmorLevel = skills.getLevel("bark_armor")
                if (barkArmorLevel > 0 && nearTree) {
                    player.addPotionEffect(PotionEffect(
                        PotionEffectType.RESISTANCE,
                        100,
                        barkArmorLevel - 1,
                        false, false, true
                    ))
                    player.world.spawnParticle(
                        Particle.WAX_ON,
                        player.location.add(0.0, 1.0, 0.0),
                        4, 0.3, 0.5, 0.3, 0.0
                    )
                }
            }
        }, 60L, 60L)
    }

    fun startOreSightCheck() {
        Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            for (player in Bukkit.getOnlinePlayers()) {
                val uuid = player.uniqueId
                val job = plugin.dataManager.getPlayer(uuid)?.job
                if (job != Jobs.MINER) continue
                val skills = skillCache[uuid] ?: continue
                val oreSightLevel = skills.getLevel("ore_sight")
                if (oreSightLevel <= 0) continue
                if (player.location.y < 60) {
                    player.addPotionEffect(PotionEffect(
                        PotionEffectType.NIGHT_VISION,
                        300, // 15 seconds (refreshed every 3s)
                        0,
                        false, false, true
                    ))
                    player.world.spawnParticle(
                        Particle.ENCHANT,
                        player.location.add(0.0, 2.0, 0.0),
                        5, 0.3, 0.2, 0.3, 0.5
                    )
                }
            }
        }, 60L, 60L)
    }

    fun startRainDancerCheck() {
        Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            for (player in Bukkit.getOnlinePlayers()) {
                val uuid = player.uniqueId
                val job = plugin.dataManager.getPlayer(uuid)?.job
                if (job != Jobs.FISHER) continue
                val skills = skillCache[uuid] ?: continue
                val rainLv = skills.getLevel("rain_dancer")
                if (rainLv <= 0) continue
                val world = player.world
                if (!world.hasStorm()) continue
                // Lv1: speed, Lv2: +strength, Lv3: +regeneration
                player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 300, 0, false, false, true))
                if (rainLv >= 2) {
                    player.addPotionEffect(PotionEffect(PotionEffectType.STRENGTH, 300, 0, false, false, true))
                }
                if (rainLv >= 3) {
                    player.addPotionEffect(PotionEffect(PotionEffectType.REGENERATION, 300, 0, false, false, true))
                }
                player.world.spawnParticle(
                    Particle.DRIPPING_WATER,
                    player.location.add(0.0, 2.0, 0.0),
                    6, 0.5, 0.3, 0.5, 0.0
                )
            }
        }, 60L, 60L)
    }

    fun startGreenThumbCheck() {
        Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            for (player in Bukkit.getOnlinePlayers()) {
                val uuid = player.uniqueId
                val job = plugin.dataManager.getPlayer(uuid)?.job
                if (job != Jobs.FARMER) continue
                val skills = skillCache[uuid] ?: continue
                val greenThumbLv = skills.getLevel("green_thumb")
                if (greenThumbLv <= 0) continue
                val chance = greenThumbLv * 0.10
                if (Math.random() >= chance) continue
                val loc = player.location
                val world = loc.world ?: continue
                var grew = false
                for (dx in -5..5) {
                    for (dz in -5..5) {
                        val block = world.getBlockAt(loc.blockX + dx, loc.blockY, loc.blockZ + dz)
                        val data = block.blockData
                        if (data is org.bukkit.block.data.Ageable && data.age < data.maximumAge) {
                            data.age = (data.age + 1).coerceAtMost(data.maximumAge)
                            block.blockData = data
                            world.spawnParticle(
                                Particle.HAPPY_VILLAGER,
                                block.location.add(0.5, 0.8, 0.5),
                                3, 0.2, 0.2, 0.2, 0.0
                            )
                            grew = true
                        }
                    }
                }
                if (grew) {
                    player.playSound(player.location, Sound.ITEM_BONE_MEAL_USE, 0.4f, 1.2f)
                }
            }
        }, 60L, 60L)
    }

    fun startScarecrowCheck() {
        Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            for (player in Bukkit.getOnlinePlayers()) {
                val uuid = player.uniqueId
                val job = plugin.dataManager.getPlayer(uuid)?.job
                if (job != Jobs.FARMER) continue
                val skills = skillCache[uuid] ?: continue
                val scarecrowLv = skills.getLevel("scarecrow")
                if (scarecrowLv <= 0) continue
                val range = when (scarecrowLv) { 1 -> 5.0; 2 -> 8.0; else -> 12.0 }
                val nearbyMobs = player.world.getNearbyEntities(player.location, range, range, range)
                    .filterIsInstance<org.bukkit.entity.Monster>()
                for (mob in nearbyMobs) {
                    mob.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 120, 0, false, false, false))
                    mob.world.spawnParticle(
                        Particle.SOUL,
                        mob.location.add(0.0, 1.0, 0.0),
                        2, 0.2, 0.3, 0.2, 0.02
                    )
                }
            }
        }, 100L, 100L)
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
