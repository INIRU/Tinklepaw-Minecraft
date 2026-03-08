package dev.nyaru.minecraft.commands

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.ConsoleCommandSender

class SyncCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        // Only allow console/RCON execution
        if (sender !is ConsoleCommandSender) {
            sender.sendMessage("This command can only be executed by console/RCON.")
            return true
        }

        if (args.isEmpty()) {
            sender.sendMessage("Usage: nyaru-invalidate <uuid>")
            return true
        }

        // Player data is now managed by DataManager (local YAML); no external cache to invalidate.
        sender.sendMessage("OK: player data is managed locally by DataManager.")
        return true
    }
}
