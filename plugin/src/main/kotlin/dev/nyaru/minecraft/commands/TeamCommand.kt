package dev.nyaru.minecraft.commands

import dev.nyaru.minecraft.protection.ProtectionManager
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import java.util.UUID

class TeamCommand(private val pm: ProtectionManager) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (sender !is Player) { sender.sendMessage("§c플레이어만 사용 가능합니다."); return true }

        val ownerUuid = sender.uniqueId.toString()

        when (args.getOrNull(0)) {
            "추가" -> {
                val targetName = args.getOrNull(1) ?: run {
                    sender.sendMessage("§c사용법: /팀 추가 <플레이어>"); return true
                }
                val target = sender.server.getPlayerExact(targetName)
                    ?: sender.server.getOfflinePlayerIfCached(targetName)
                    ?: run {
                        sender.sendMessage("§c플레이어 §f$targetName §c을(를) 찾을 수 없습니다."); return true
                    }
                if (target.uniqueId == sender.uniqueId) {
                    sender.sendMessage("§c자기 자신을 팀에 추가할 수 없습니다."); return true
                }
                pm.addTeamMember(ownerUuid, target.uniqueId.toString())
                sender.sendMessage("§a§l✓ §f${target.name} §a님을 팀에 추가했습니다. 내 보호 블럭에 접근할 수 있습니다.")
            }

            "제거" -> {
                val targetName = args.getOrNull(1) ?: run {
                    sender.sendMessage("§c사용법: /팀 제거 <플레이어>"); return true
                }
                val target = sender.server.getPlayerExact(targetName)
                    ?: sender.server.getOfflinePlayerIfCached(targetName)
                    ?: run {
                        sender.sendMessage("§c플레이어 §f$targetName §c을(를) 찾을 수 없습니다."); return true
                    }
                val removed = pm.removeTeamMember(ownerUuid, target.uniqueId.toString())
                if (removed) sender.sendMessage("§a§l✓ §f${target.name} §a님을 팀에서 제거했습니다.")
                else sender.sendMessage("§c${target.name} 님은 팀원이 아닙니다.")
            }

            "목록" -> {
                val members = pm.getTeamMembers(ownerUuid)
                if (members.isEmpty()) {
                    sender.sendMessage("§7현재 팀원이 없습니다.")
                } else {
                    sender.sendMessage("§e§l팀원 목록 §7(${members.size}명):")
                    members.forEach { uuid ->
                        val name = sender.server.getOfflinePlayer(UUID.fromString(uuid)).name ?: uuid
                        sender.sendMessage("§7  ▸ §f$name")
                    }
                }
            }

            else -> {
                sender.sendMessage("§e§l팀 명령어")
                sender.sendMessage("§e/팀 추가 <플레이어> §7— 팀원 추가 (내 블럭 접근 허용)")
                sender.sendMessage("§e/팀 제거 <플레이어> §7— 팀원 제거")
                sender.sendMessage("§e/팀 목록 §7— 팀원 목록")
            }
        }
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, label: String, args: Array<String>): List<String> {
        if (args.size == 1) return listOf("추가", "제거", "목록").filter { it.startsWith(args[0]) }
        if (args.size == 2 && args[0] in listOf("추가", "제거")) {
            return sender.server.onlinePlayers.map { it.name }.filter { it.startsWith(args[1]) }
        }
        return emptyList()
    }
}
