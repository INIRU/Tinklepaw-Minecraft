package dev.nyaru.minecraft.commands

import dev.nyaru.minecraft.NyaruPlugin
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class SpawnCommand(private val plugin: NyaruPlugin) : CommandExecutor {

    companion object {
        private const val COUNTDOWN_TICKS = 60L // 3 seconds
        private val legacy = LegacyComponentSerializer.legacySection()
    }

    private val pending = ConcurrentHashMap<UUID, Location>()

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) { sender.sendMessage("§c플레이어만 사용 가능합니다."); return true }
        val player = sender
        val uuid = player.uniqueId

        if (pending.containsKey(uuid)) {
            player.sendMessage("§c이미 귀환 중입니다.")
            return true
        }

        val cost = plugin.config.getInt("spawn-cost", 1000)
        val balance = plugin.dataManager.getBalance(uuid)
        if (balance < cost) {
            player.sendMessage("§c냥이 부족합니다. §7(현재 §e${balance}냥§7, 필요 §e${cost}냥§7)")
            return true
        }

        startCountdown(player, cost)
        return true
    }

    private fun startCountdown(player: Player, cost: Int) {
        val uuid = player.uniqueId
        val startLoc = player.location.clone()
        pending[uuid] = startLoc
        player.sendMessage("§a§l✦ §f스폰 귀환 §7— §e3초 동안 움직이지 마세요! §7(비용: §e${cost}냥§7)")
        player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.2f)

        var ticksLeft = COUNTDOWN_TICKS.toInt()

        object : BukkitRunnable() {
            override fun run() {
                if (!player.isOnline || !pending.containsKey(uuid)) { cancel(); return }

                if (player.location.distanceSquared(startLoc) > 0.09) {
                    pending.remove(uuid)
                    player.sendActionBar(legacy.deserialize("§c귀환 취소 — 이동 감지"))
                    player.sendMessage("§c이동이 감지되어 귀환이 취소되었습니다.")
                    player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f)
                    cancel()
                    return
                }

                ticksLeft -= 2
                val secsLeft = Math.ceil(ticksLeft / 20.0).toInt().coerceAtLeast(0)

                if (ticksLeft <= 0) {
                    pending.remove(uuid)
                    val spent = plugin.dataManager.spendBalance(uuid, cost)
                    if (!spent) {
                        player.sendMessage("§c냥이 부족합니다.")
                        cancel()
                        return
                    }
                    val spawn = player.world.spawnLocation
                    player.teleport(spawn)
                    player.playSound(spawn, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.0f)
                    player.sendMessage("§a§l✦ §f스폰으로 귀환했습니다! §7(§e-${cost}냥§7)")
                    cancel()
                    return
                }

                val bar = "§6§l" + "█".repeat(secsLeft) + "§8" + "█".repeat(3 - secsLeft)
                player.sendActionBar(legacy.deserialize("§f스폰 귀환 $bar §e${secsLeft}초 §7(${cost}냥)"))
            }
        }.runTaskTimer(plugin, 0L, 2L)
    }
}
