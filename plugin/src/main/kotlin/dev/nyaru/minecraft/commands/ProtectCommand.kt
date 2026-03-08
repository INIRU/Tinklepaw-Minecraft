package dev.nyaru.minecraft.commands

import dev.nyaru.minecraft.protection.ProtectionManager
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Sound
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class ProtectCommand(private val pm: ProtectionManager) : CommandExecutor {

    private val legacy = LegacyComponentSerializer.legacySection()

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (sender !is Player) { sender.sendMessage("§c플레이어만 사용 가능합니다."); return true }

        val enabled = pm.toggleProtection(sender.uniqueId.toString())
        if (enabled) {
            sender.sendActionBar(legacy.deserialize("§a🔒 블럭 보호 §aON §7— 설치하는 블럭이 보호됩니다"))
            sender.playSound(sender.location, Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.5f)
        } else {
            sender.sendActionBar(legacy.deserialize("§7🔓 블럭 보호 §cOFF §7— 보호 없이 설치됩니다"))
            sender.playSound(sender.location, Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 1.0f)
        }
        return true
    }
}
