package dev.nyaru.minecraft.skills

import dev.nyaru.minecraft.NyaruPlugin
import dev.nyaru.minecraft.model.Jobs
import dev.nyaru.minecraft.model.SkillData
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
        }
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
