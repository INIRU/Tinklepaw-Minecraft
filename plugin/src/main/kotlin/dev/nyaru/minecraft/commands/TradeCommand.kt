package dev.nyaru.minecraft.commands

import dev.nyaru.minecraft.NyaruPlugin
import dev.nyaru.minecraft.gui.TradeGui
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class TradeCommand(private val plugin: NyaruPlugin) : CommandExecutor, TabCompleter {

    // Pending trade requests: target UUID -> requester UUID + timestamp
    private data class TradeRequest(val requester: UUID, val timestamp: Long)
    private val pendingRequests = ConcurrentHashMap<UUID, TradeRequest>()

    private val legacy = LegacyComponentSerializer.legacySection()

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) { sender.sendMessage("§c플레이어만 사용 가능합니다."); return true }
        val player = sender

        if (args.isEmpty()) {
            player.sendMessage("§e사용법: §f/교환 <플레이어> §7- 교환 요청")
            player.sendMessage("§e       §f/교환 수락 §7- 받은 교환 요청 수락")
            player.sendMessage("§e       §f/교환 거절 §7- 받은 교환 요청 거절")
            return true
        }

        when (args[0].lowercase()) {
            "수락" -> handleAccept(player)
            "거절" -> handleReject(player)
            else -> handleRequest(player, args[0])
        }

        return true
    }

    private fun handleRequest(player: Player, targetName: String) {
        if (TradeGui.playerTrades.containsKey(player.uniqueId)) {
            player.sendMessage("§c이미 교환 중입니다.")
            return
        }

        val target = Bukkit.getPlayer(targetName)
        if (target == null) {
            player.sendMessage("§c플레이어 '${targetName}'을 찾을 수 없습니다.")
            return
        }

        if (target.uniqueId == player.uniqueId) {
            player.sendMessage("§c자기 자신과 교환할 수 없습니다.")
            return
        }

        if (TradeGui.playerTrades.containsKey(target.uniqueId)) {
            player.sendMessage("§c${target.name}님은 이미 교환 중입니다.")
            return
        }

        // Check for existing pending request
        val existing = pendingRequests[target.uniqueId]
        if (existing != null && existing.requester == player.uniqueId &&
            System.currentTimeMillis() - existing.timestamp < 30_000) {
            player.sendMessage("§c이미 교환 요청을 보냈습니다. 30초 후 다시 시도하세요.")
            return
        }

        pendingRequests[target.uniqueId] = TradeRequest(player.uniqueId, System.currentTimeMillis())

        player.sendMessage("§a${target.name}님에게 교환 요청을 보냈습니다. §7(30초 내 수락 필요)")

        val acceptBtn = Component.text("[수락]")
            .color(NamedTextColor.GREEN)
            .decoration(TextDecoration.BOLD, true)
            .clickEvent(ClickEvent.runCommand("/교환 수락"))
            .hoverEvent(HoverEvent.showText(Component.text("클릭하여 교환 수락", NamedTextColor.GRAY)))

        val rejectBtn = Component.text("[거절]")
            .color(NamedTextColor.RED)
            .decoration(TextDecoration.BOLD, true)
            .clickEvent(ClickEvent.runCommand("/교환 거절"))
            .hoverEvent(HoverEvent.showText(Component.text("클릭하여 교환 거절", NamedTextColor.GRAY)))

        target.sendMessage(
            Component.text()
                .append(legacy.deserialize("§e${player.name}§f님이 교환을 요청했습니다! "))
                .append(acceptBtn)
                .append(Component.text(" "))
                .append(rejectBtn)
                .append(legacy.deserialize(" §7(30초)"))
                .build()
        )
    }

    private fun handleAccept(player: Player) {
        val request = pendingRequests.remove(player.uniqueId)
        if (request == null || System.currentTimeMillis() - request.timestamp > 30_000) {
            pendingRequests.remove(player.uniqueId)
            player.sendMessage("§c받은 교환 요청이 없거나 만료되었습니다.")
            return
        }

        val requester = Bukkit.getPlayer(request.requester)
        if (requester == null || !requester.isOnline) {
            player.sendMessage("§c상대방이 오프라인입니다.")
            return
        }

        if (TradeGui.playerTrades.containsKey(player.uniqueId)) {
            player.sendMessage("§c이미 교환 중입니다.")
            return
        }

        if (TradeGui.playerTrades.containsKey(requester.uniqueId)) {
            player.sendMessage("§c${requester.name}님은 이미 다른 교환 중입니다.")
            return
        }

        TradeGui(plugin, requester, player).open()
    }

    private fun handleReject(player: Player) {
        val request = pendingRequests.remove(player.uniqueId)
        if (request == null) {
            player.sendMessage("§c받은 교환 요청이 없습니다.")
            return
        }

        player.sendMessage("§c교환 요청을 거절했습니다.")
        val requester = Bukkit.getPlayer(request.requester)
        requester?.sendMessage("§c${player.name}님이 교환을 거절했습니다.")
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        if (args.size == 1) {
            val input = args[0].lowercase()
            val completions = mutableListOf("수락", "거절")
            Bukkit.getOnlinePlayers()
                .filter { it != sender }
                .forEach { completions.add(it.name) }
            return completions.filter { it.lowercase().startsWith(input) }
        }
        return emptyList()
    }
}
