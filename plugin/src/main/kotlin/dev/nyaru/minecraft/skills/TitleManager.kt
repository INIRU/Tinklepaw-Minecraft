package dev.nyaru.minecraft.skills

import dev.nyaru.minecraft.NyaruPlugin
import dev.nyaru.minecraft.model.TitleCondition
import dev.nyaru.minecraft.model.TitleRegistry
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Particle
import org.bukkit.Sound
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class TitleManager(private val plugin: NyaruPlugin) {

    // uuid -> earned title IDs
    private val earnedTitles = ConcurrentHashMap<UUID, MutableSet<String>>()
    // uuid -> selected game title ID
    private val selectedTitles = ConcurrentHashMap<UUID, String>()

    private val legacy = LegacyComponentSerializer.legacySection()

    fun loadPlayer(uuid: UUID, earned: Set<String>, selected: String?) {
        earnedTitles[uuid] = earned.toMutableSet()
        if (selected != null) selectedTitles[uuid] = selected
    }

    fun unloadPlayer(uuid: UUID) {
        earnedTitles.remove(uuid)
        selectedTitles.remove(uuid)
    }

    fun getEarnedTitles(uuid: UUID): Set<String> = earnedTitles[uuid] ?: emptySet()

    fun getSelectedTitle(uuid: UUID): String? = selectedTitles[uuid]

    fun getSelectedTitleDisplay(uuid: UUID): String? {
        val titleId = selectedTitles[uuid] ?: return null
        return TitleRegistry.get(titleId)?.displayName
    }

    fun selectTitle(uuid: UUID, titleId: String?): Boolean {
        if (titleId == null) {
            selectedTitles.remove(uuid)
            plugin.dataManager.updatePlayerTitles(uuid, getEarnedTitles(uuid), null)
            return true
        }
        if (titleId !in (earnedTitles[uuid] ?: emptySet())) return false
        selectedTitles[uuid] = titleId
        plugin.dataManager.updatePlayerTitles(uuid, getEarnedTitles(uuid), titleId)
        return true
    }

    fun awardTitle(uuid: UUID, titleId: String): Boolean {
        val set = earnedTitles.getOrPut(uuid) { mutableSetOf() }
        if (!set.add(titleId)) return false // already earned
        plugin.dataManager.updatePlayerTitles(uuid, set, selectedTitles[uuid])
        return true
    }

    fun checkAndAwardTitles(uuid: UUID) {
        val player = plugin.server.getPlayer(uuid) ?: return
        for (def in TitleRegistry.ALL) {
            if (def.id in (earnedTitles[uuid] ?: emptySet())) continue

            val earned = when (val cond = def.condition) {
                is TitleCondition.FirstJoin -> true
                is TitleCondition.Balance -> {
                    plugin.dataManager.getBalance(uuid) >= cond.amount
                }
                is TitleCondition.JobLevel -> {
                    val info = plugin.dataManager.getPlayer(uuid) ?: continue
                    info.jobSlots.any { it.job == cond.job && it.level >= cond.level }
                }
                is TitleCondition.TotalSkillPoints -> {
                    false // reserved for future
                }
            }

            if (earned) {
                awardTitle(uuid, def.id)
                player.sendMessage(legacy.deserialize("§6§l\u2605 칭호 획득! §r${def.displayName}"))
                player.playSound(player.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f)
                player.world.spawnParticle(
                    Particle.TOTEM_OF_UNDYING,
                    player.location.add(0.0, 1.5, 0.0),
                    20, 0.5, 0.5, 0.5, 0.1
                )
            }
        }
    }

    fun startPeriodicCheck() {
        Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            for (player in Bukkit.getOnlinePlayers()) {
                checkAndAwardTitles(player.uniqueId)
            }
        }, 100L, 200L) // Check every 10 seconds
    }
}
