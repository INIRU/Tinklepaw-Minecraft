package dev.nyaru.minecraft.commands

import dev.nyaru.minecraft.NyaruPlugin
import dev.nyaru.minecraft.gui.TitleGui
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class TitleCommand(private val plugin: NyaruPlugin) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("§c플레이어만 사용할 수 있습니다.")
            return true
        }
        TitleGui(plugin, sender).open()
        return true
    }
}
