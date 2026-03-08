package dev.nyaru.minecraft.commands

import dev.nyaru.minecraft.NyaruPlugin
import dev.nyaru.minecraft.model.ShopItem
import dev.nyaru.minecraft.npc.NpcType
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class AdminCommand(
    private val plugin: NyaruPlugin
) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("nyaru.admin")) {
            sender.sendMessage("§c권한이 없습니다.")
            return true
        }

        when (args.getOrNull(0)?.lowercase()) {
            "npc" -> handleNpc(sender, args)
            "상점" -> handleShop(sender, args)
            "지급" -> handleGive(sender, args)
            "차감" -> handleDeduct(sender, args)
            "설정" -> handleSet(sender, args)
            else -> sender.sendMessage("§f/나루관리 <npc|상점|지급|차감|설정>")
        }
        return true
    }

    private fun handleNpc(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) { sender.sendMessage("§c플레이어만 사용 가능합니다."); return }
        val createNpc = plugin.npcCreateFn
        if (createNpc == null) {
            sender.sendMessage("§cFancyNpcs 플러그인이 설치되지 않았습니다.")
            return
        }
        val type = when (args.getOrNull(1)) {
            "상점" -> NpcType.SHOP
            "직업" -> NpcType.JOB
            "강화" -> NpcType.ENHANCE
            else -> { sender.sendMessage("§c사용법: /나루관리 npc <상점|직업|강화>"); return }
        }
        createNpc(sender.location, type)
        sender.sendMessage("§aNPC가 현재 위치에 생성되었습니다.")
    }

    private fun handleShop(sender: CommandSender, args: Array<out String>) {
        when (args.getOrNull(1)) {
            "추가" -> {
                if (sender !is Player) { sender.sendMessage("§c플레이어만 사용 가능합니다."); return }
                val buyPrice = args.getOrNull(2)?.toIntOrNull()
                val sellPrice = args.getOrNull(3)?.toIntOrNull()
                val category = args.getOrNull(4)
                if (buyPrice == null || sellPrice == null || category == null) {
                    sender.sendMessage("§c사용법: /나루관리 상점 추가 <매입가> <판매가> <카테고리>")
                    sender.sendMessage("§7손에 아이템을 들고 사용하세요.")
                    return
                }
                val item = sender.inventory.itemInMainHand
                if (item.type.isAir) {
                    sender.sendMessage("§c손에 아이템을 들고 사용하세요.")
                    return
                }
                val material = item.type.name
                val id = material.lowercase()
                val displayName = item.itemMeta?.displayName()?.let {
                    LegacyComponentSerializer.legacySection().serialize(it)
                } ?: material.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
                plugin.shopManager.addItem(ShopItem(id, material, displayName, buyPrice, sellPrice, category))
                sender.sendMessage("§a상점에 아이템이 추가되었습니다! §7(${displayName}, 매입: ${buyPrice}냥, 판매: ${sellPrice}냥)")
            }
            "제거" -> {
                val itemId = args.getOrNull(2)
                if (itemId == null) {
                    sender.sendMessage("§c사용법: /나루관리 상점 제거 <아이템ID>")
                    return
                }
                val removed = plugin.shopManager.removeItem(itemId)
                if (removed) sender.sendMessage("§a상점에서 아이템이 제거되었습니다!")
                else sender.sendMessage("§c해당 아이템 ID를 찾을 수 없습니다: $itemId")
            }
            "목록" -> {
                val items = plugin.shopManager.getItems()
                if (items.isEmpty()) { sender.sendMessage("§7상점에 등록된 아이템이 없습니다."); return }
                sender.sendMessage("§6=== 상점 아이템 목록 ===")
                for (shopItem in items) {
                    sender.sendMessage(
                        "§e${shopItem.id} §7| §f${shopItem.displayName} §7| 매입: §a${shopItem.buyPrice}냥 §7| 판매: §c${shopItem.sellPrice}냥 §7| [${shopItem.category}]"
                    )
                }
            }
            else -> sender.sendMessage("§c사용법: /나루관리 상점 <추가|제거|목록>")
        }
    }

    private fun handleGive(sender: CommandSender, args: Array<out String>) {
        val targetName = args.getOrNull(1)
        val amount = args.getOrNull(2)?.toIntOrNull()
        if (targetName == null || amount == null || amount <= 0) {
            sender.sendMessage("§c사용법: /나루관리 지급 <플레이어> <금액>")
            return
        }
        val target = Bukkit.getPlayerExact(targetName)
        if (target == null) {
            sender.sendMessage("§c플레이어 §f${targetName}§c을(를) 찾을 수 없습니다.")
            return
        }
        plugin.dataManager.addBalance(target.uniqueId, amount)
        plugin.actionBarManager.refresh(target.uniqueId)
        sender.sendMessage("§a${target.name}에게 ${amount}냥을 지급했습니다!")
        target.sendMessage("§a관리자로부터 §e${amount}냥§a을 지급받았습니다!")
    }

    private fun handleDeduct(sender: CommandSender, args: Array<out String>) {
        val targetName = args.getOrNull(1)
        val amount = args.getOrNull(2)?.toIntOrNull()
        if (targetName == null || amount == null || amount <= 0) {
            sender.sendMessage("§c사용법: /나루관리 차감 <플레이어> <금액>")
            return
        }
        val target = Bukkit.getPlayerExact(targetName)
        if (target == null) {
            sender.sendMessage("§c플레이어 §f${targetName}§c을(를) 찾을 수 없습니다.")
            return
        }
        val success = plugin.dataManager.spendBalance(target.uniqueId, amount)
        if (success) {
            plugin.actionBarManager.refresh(target.uniqueId)
            sender.sendMessage("§a${target.name}에게서 ${amount}냥을 차감했습니다!")
            target.sendMessage("§c관리자에 의해 §e${amount}냥§c이 차감되었습니다.")
        } else {
            val balance = plugin.dataManager.getBalance(target.uniqueId)
            sender.sendMessage("§c${target.name}의 냥이 부족합니다. 현재: §e${balance}냥")
        }
    }

    private fun handleSet(sender: CommandSender, args: Array<out String>) {
        val targetName = args.getOrNull(1)
        val amount = args.getOrNull(2)?.toIntOrNull()
        if (targetName == null || amount == null || amount < 0) {
            sender.sendMessage("§c사용법: /나루관리 설정 <플레이어> <금액>")
            return
        }
        val target = Bukkit.getPlayerExact(targetName)
        if (target == null) {
            sender.sendMessage("§c플레이어 §f${targetName}§c을(를) 찾을 수 없습니다.")
            return
        }
        val currentBalance = plugin.dataManager.getBalance(target.uniqueId)
        val diff = amount - currentBalance
        plugin.dataManager.addBalance(target.uniqueId, diff)
        plugin.actionBarManager.refresh(target.uniqueId)
        sender.sendMessage("§a${target.name}의 냥을 §e${amount}냥§a으로 설정했습니다! §7(이전: ${currentBalance}냥)")
        target.sendMessage("§e관리자에 의해 냥이 §f${amount}냥§e으로 설정되었습니다.")
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        if (!sender.hasPermission("nyaru.admin")) return emptyList()
        return when (args.size) {
            1 -> listOf("npc", "상점", "지급", "차감", "설정").filter { it.startsWith(args[0]) }
            2 -> when (args[0].lowercase()) {
                "npc" -> listOf("상점", "직업", "강화").filter { it.startsWith(args[1]) }
                "상점" -> listOf("추가", "제거", "목록").filter { it.startsWith(args[1]) }
                "지급", "차감", "설정" -> Bukkit.getOnlinePlayers().map { it.name }.filter { it.startsWith(args[1]) }
                else -> emptyList()
            }
            3 -> when {
                args[0].lowercase() == "상점" && args[1] == "제거" ->
                    plugin.shopManager.getItems().map { it.id }.filter { it.startsWith(args[2]) }
                else -> emptyList()
            }
            else -> emptyList()
        }
    }
}
