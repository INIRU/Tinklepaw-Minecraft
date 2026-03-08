package dev.nyaru.minecraft.commands

import dev.nyaru.minecraft.logging.BlockAction
import dev.nyaru.minecraft.logging.BlockLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class LogCommand(
    private val blockLogger: BlockLogger,
    private val scope: CoroutineScope
) : CommandExecutor, Listener {

    // Players currently in inspect mode
    private val inspectMode = ConcurrentHashMap.newKeySet<UUID>()
    private val legacy = LegacyComponentSerializer.legacySection()

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (sender !is Player) { sender.sendMessage("§c플레이어만 사용 가능합니다."); return true }
        if (!sender.hasPermission("nyaru.admin")) { sender.sendMessage("§c권한이 없습니다."); return true }

        if (inspectMode.contains(sender.uniqueId)) {
            inspectMode.remove(sender.uniqueId)
            sender.sendActionBar(legacy.deserialize("§c🔍 로그 조사 모드 §7비활성화"))
        } else {
            inspectMode.add(sender.uniqueId)
            sender.sendActionBar(legacy.deserialize("§a🔍 로그 조사 모드 §7활성화 — 블럭 좌클릭으로 기록 조회"))
        }
        return true
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onLeftClick(event: PlayerInteractEvent) {
        val player = event.player
        if (!inspectMode.contains(player.uniqueId)) return
        if (event.action != Action.LEFT_CLICK_BLOCK) return
        val block = event.clickedBlock ?: return
        event.isCancelled = true

        val world = block.world.name
        val x = block.x; val y = block.y; val z = block.z

        player.sendMessage(legacy.deserialize("§8───── §e🔍 §f${world} §8(§7${x}, ${y}, ${z}§8) §8─────"))

        scope.launch(Dispatchers.IO) {
            val entries = blockLogger.readAtLocation(world, x, y, z, limit = 20)
            if (entries.isEmpty()) {
                player.sendMessage(legacy.deserialize("§7기록이 없습니다."))
            } else {
                entries.forEach { e ->
                    val color = if (e.action == BlockAction.PLACE) "§a" else "§c"
                    val symbol = if (e.action == BlockAction.PLACE) "+" else "-"
                    val time = e.timestamp.toString().substring(5, 16).replace('T', ' ')
                    val mat = e.material.lowercase().replace('_', ' ')
                    player.sendMessage(legacy.deserialize("${color}[$symbol] §f${e.playerName} §7$mat §8$time"))
                }
            }
            player.sendMessage(legacy.deserialize("§8──────────────────────────────"))
        }
    }
}
