package dev.nyaru.minecraft.commands

import dev.nyaru.minecraft.NyaruPlugin
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Sound
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class HomeCommand(private val plugin: NyaruPlugin) : CommandExecutor, TabCompleter {

    private val pending = ConcurrentHashMap<UUID, org.bukkit.Location>()
    private val legacy = LegacyComponentSerializer.legacySection()

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) { sender.sendMessage("§c플레이어만 사용 가능합니다."); return true }
        val player = sender
        val uuid = player.uniqueId

        when (args.getOrNull(0)) {
            "설정" -> {
                val loc = player.location
                plugin.dataManager.setHome(
                    uuid,
                    loc.world?.name ?: "world",
                    loc.x, loc.y, loc.z,
                    loc.yaw, loc.pitch
                )
                player.sendMessage("§a§l✓ 집이 설정되었습니다!")
            }
            "가기" -> {
                if (pending.containsKey(uuid)) {
                    player.sendMessage("§c이미 이동 중입니다.")
                    return true
                }
                val home = plugin.dataManager.getHome(uuid)
                if (home == null) {
                    player.sendMessage("§c집이 설정되지 않았습니다. §f/집 설정§c을 먼저 해주세요.")
                    return true
                }
                val cost = plugin.config.getInt("home-cost", 1000)
                val balance = plugin.dataManager.getBalance(uuid)
                if (balance < cost) {
                    player.sendMessage("§c냥이 부족합니다. 현재: §e${balance}냥 §c/ 필요: §e${cost}냥")
                    return true
                }
                val startLoc = player.location.clone()
                pending[uuid] = startLoc
                player.sendMessage("§e3초 후 집으로 이동합니다... 움직이면 취소됩니다.")

                object : BukkitRunnable() {
                    var ticksLeft = 60

                    override fun run() {
                        if (!player.isOnline || !pending.containsKey(uuid)) { cancel(); return }

                        if (player.location.distanceSquared(startLoc) > 0.09) {
                            pending.remove(uuid)
                            player.sendActionBar(legacy.deserialize("§c이동 감지 — 집 이동 취소"))
                            player.sendMessage("§c이동이 감지되어 집 이동이 취소되었습니다.")
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
                            plugin.actionBarManager.refresh(uuid)
                            val world = org.bukkit.Bukkit.getWorld(home.world)
                            if (world == null) {
                                player.sendMessage("§c집 월드를 찾을 수 없습니다.")
                                cancel()
                                return
                            }
                            val dest = org.bukkit.Location(world, home.x, home.y, home.z, home.yaw, home.pitch)
                            player.teleport(dest)
                            player.playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.0f)
                            player.sendMessage("§a§l✓ 집으로 이동했습니다! §7(-${cost}냥)")
                            cancel()
                            return
                        }

                        val bar = "§6§l" + "█".repeat(secsLeft) + "§8" + "█".repeat(3 - secsLeft)
                        player.sendActionBar(legacy.deserialize("§f집으로 이동 $bar §e${secsLeft}초 §7(${cost}냥)"))
                    }
                }.runTaskTimer(plugin, 0L, 2L)
            }
            "삭제" -> {
                plugin.dataManager.removeHome(uuid)
                player.sendMessage("§a집이 삭제되었습니다.")
            }
            else -> {
                player.sendMessage("§e/집 <설정|가기|삭제>")
            }
        }
        return true
    }

    override fun onTabComplete(
        sender: CommandSender, command: Command, alias: String, args: Array<out String>
    ): List<String> {
        if (args.size == 1) {
            return listOf("설정", "가기", "삭제").filter { it.startsWith(args[0]) }
        }
        return emptyList()
    }
}
