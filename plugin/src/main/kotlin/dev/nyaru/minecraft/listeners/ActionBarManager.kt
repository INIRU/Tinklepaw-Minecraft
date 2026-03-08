package dev.nyaru.minecraft.listeners

import dev.nyaru.minecraft.NyaruPlugin
import dev.nyaru.minecraft.model.Jobs
import dev.nyaru.minecraft.model.LevelFormula
import dev.nyaru.minecraft.model.PlayerInfo
import dev.nyaru.minecraft.protection.ProtectionManager
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ActionBarManager(private val plugin: NyaruPlugin, private val pm: ProtectionManager? = null) : Listener {

    // Local display cache — always valid for immediate action bar rendering
    private val cache = ConcurrentHashMap<UUID, PlayerInfo>()
    var chatTabListener: ChatTabListener? = null

    fun getInfo(uuid: UUID): PlayerInfo? = cache[uuid]

    init {
        startRefreshLoop()
        startDisplayLoop()
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        // Delay slightly so dataManager.loadPlayer() has run in PlayerJoinListener first
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            val info = plugin.dataManager.getPlayer(player.uniqueId)
            if (info?.linked == true) {
                cache[player.uniqueId] = info
                chatTabListener?.updateTabName(player, info)
                plugin.sidebarManager.update(player, info)
            }
        }, 5L)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        val uuid = event.player.uniqueId
        cache.remove(uuid)
    }

    fun refresh(uuid: UUID) {
        val info = plugin.dataManager.getPlayer(uuid)
        val player = Bukkit.getPlayer(uuid)
        if (info?.linked == true) {
            cache[uuid] = info
            if (player != null) {
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    chatTabListener?.updateTabName(player, info)
                    plugin.sidebarManager.update(player, info)
                })
            }
        } else {
            cache.remove(uuid)
            if (player != null) {
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    chatTabListener?.updateTabName(player, null)
                })
            }
        }
    }

    /** Update balance locally without redundant reload */
    fun updateBalance(uuid: UUID, newBalance: Int) {
        val info = cache[uuid] ?: return
        cache[uuid] = info.copy(balance = newBalance)
    }

    /** Update XP/level locally */
    fun updateXp(uuid: UUID, level: Int, xp: Int) {
        val info = cache[uuid] ?: return
        cache[uuid] = info.copy(level = level, xp = xp)
    }

    /** Update job locally */
    fun updateJob(uuid: UUID, job: String) {
        val info = cache[uuid] ?: return
        cache[uuid] = info.copy(job = job, level = 1, xp = 0)
    }

    private fun buildActionBarText(info: PlayerInfo, uuid: UUID): net.kyori.adventure.text.Component {
        val jobColor = Jobs.colorCode(info.job)
        val jobKr = Jobs.displayName(info.job)
        val xpNeeded = LevelFormula.xpRequired(info.level).coerceAtLeast(1)
        val filledBars = (info.xp.toDouble() / xpNeeded * 8).toInt().coerceIn(0, 8)
        val xpBar = "§a" + "▌".repeat(filledBars) + "§8" + "▌".repeat(8 - filledBars)
        val protectIcon = if (pm?.isProtectionEnabled(uuid.toString()) == true) "§a🔒" else "§7🔓"
        val text = "${jobColor}${jobKr} §7Lv.${info.level} $xpBar §8| §e${info.balance}냥 §8| $protectIcon"
        return LegacyComponentSerializer.legacySection().deserialize(text)
    }

    private fun startDisplayLoop() {
        // 20틱(1초) 간격으로 전송해야 액션바가 끊기지 않음 (액션바는 ~2초 후 사라짐)
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, Runnable {
            for (player in Bukkit.getOnlinePlayers()) {
                val info = cache[player.uniqueId] ?: continue
                player.sendActionBar(buildActionBarText(info, player.uniqueId))
            }
        }, 20L, 20L)
    }

    private fun startRefreshLoop() {
        // Re-read from dataManager every 5 minutes (local, no network call)
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, Runnable {
            for (player in Bukkit.getOnlinePlayers()) {
                val info = plugin.dataManager.getPlayer(player.uniqueId)
                if (info?.linked == true) {
                    cache[player.uniqueId] = info
                    Bukkit.getScheduler().runTask(plugin, Runnable {
                        chatTabListener?.updateTabName(player, info)
                        plugin.sidebarManager.update(player, info)
                    })
                } else {
                    cache.remove(player.uniqueId)
                    Bukkit.getScheduler().runTask(plugin, Runnable {
                        chatTabListener?.updateTabName(player, null)
                    })
                }
            }
        }, 6000L, 6000L)
    }
}
