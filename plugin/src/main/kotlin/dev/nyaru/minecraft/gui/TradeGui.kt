package dev.nyaru.minecraft.gui

import dev.nyaru.minecraft.NyaruPlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class TradeGui(private val plugin: NyaruPlugin, val player1: Player, val player2: Player) {

    companion object {
        val activeTrades = ConcurrentHashMap<Inventory, TradeGui>()
        val playerTrades = ConcurrentHashMap<UUID, TradeGui>()

        // Player 1 item slots (left side: columns 1-3, rows 0-3)
        val P1_SLOTS = listOf(1, 2, 3, 10, 11, 12, 19, 20, 21, 28, 29, 30)
        // Player 2 item slots (right side: columns 5-7, rows 0-3)
        val P2_SLOTS = listOf(5, 6, 7, 14, 15, 16, 23, 24, 25, 32, 33, 34)

        const val P1_CONFIRM = 48
        const val P2_CONFIRM = 50
        const val P1_CURRENCY = 37 // bottom-left currency button
        const val P2_CURRENCY = 43 // bottom-right currency button
        const val FEE_INFO = 49     // center bottom - fee info
        const val INV_SIZE = 54
    }

    private val legacy = LegacyComponentSerializer.legacySection()
    private lateinit var inventory: Inventory
    var p1Confirmed = false
    var p2Confirmed = false
    var p1Currency = 0
    var p2Currency = 0
    private var completed = false

    fun open() {
        inventory = Bukkit.createInventory(null, INV_SIZE,
            legacy.deserialize("§6§l🤝 교환: ${player1.name} ↔ ${player2.name}"))

        buildUi()

        activeTrades[inventory] = this
        playerTrades[player1.uniqueId] = this
        playerTrades[player2.uniqueId] = this

        player1.openInventory(inventory)
        player2.openInventory(inventory)

        player1.playSound(player1.location, Sound.BLOCK_CHEST_OPEN, 0.8f, 1.0f)
        player2.playSound(player2.location, Sound.BLOCK_CHEST_OPEN, 0.8f, 1.0f)
    }

    private fun buildUi() {
        val glass = makeGlass(Material.GRAY_STAINED_GLASS_PANE)
        for (i in 0 until INV_SIZE) inventory.setItem(i, glass)

        // Clear item slots
        for (slot in P1_SLOTS) inventory.setItem(slot, null)
        for (slot in P2_SLOTS) inventory.setItem(slot, null)

        // Separator column (column 4)
        val sep = makeGlass(Material.BLACK_STAINED_GLASS_PANE)
        for (row in 0..5) inventory.setItem(row * 9 + 4, sep)

        // Player heads / labels
        val p1Head = ItemStack(Material.PLAYER_HEAD)
        p1Head.editMeta { meta ->
            meta.displayName(legacy.deserialize("§b§l${player1.name}"))
            meta.lore(listOf(legacy.deserialize("§7왼쪽에 교환할 아이템을 넣으세요.")))
        }
        inventory.setItem(0, p1Head)

        val p2Head = ItemStack(Material.PLAYER_HEAD)
        p2Head.editMeta { meta ->
            meta.displayName(legacy.deserialize("§d§l${player2.name}"))
            meta.lore(listOf(legacy.deserialize("§7오른쪽에 교환할 아이템을 넣으세요.")))
        }
        inventory.setItem(8, p2Head)

        // Fee info
        updateFeeInfo()

        // Currency and confirm buttons
        updateCurrencyButtons()
        updateConfirmButtons()
    }

    private fun updateCurrencyButtons() {
        val p1Balance = plugin.dataManager.getBalance(player1.uniqueId)
        val p1Item = ItemStack(Material.GOLD_INGOT)
        p1Item.editMeta { meta ->
            meta.displayName(legacy.deserialize("§e§l💰 냥 제시: §f${formatNumber(p1Currency)}냥"))
            meta.lore(listOf(
                legacy.deserialize("§7보유: §e${formatNumber(p1Balance)}냥"),
                Component.empty(),
                legacy.deserialize("§a좌클릭: §f+100냥"),
                legacy.deserialize("§a쉬프트+좌클릭: §f+1,000냥"),
                legacy.deserialize("§c우클릭: §f-100냥"),
                legacy.deserialize("§c쉬프트+우클릭: §f-1,000냥")
            ))
        }
        inventory.setItem(P1_CURRENCY, p1Item)

        val p2Balance = plugin.dataManager.getBalance(player2.uniqueId)
        val p2Item = ItemStack(Material.GOLD_INGOT)
        p2Item.editMeta { meta ->
            meta.displayName(legacy.deserialize("§e§l💰 냥 제시: §f${formatNumber(p2Currency)}냥"))
            meta.lore(listOf(
                legacy.deserialize("§7보유: §e${formatNumber(p2Balance)}냥"),
                Component.empty(),
                legacy.deserialize("§a좌클릭: §f+100냥"),
                legacy.deserialize("§a쉬프트+좌클릭: §f+1,000냥"),
                legacy.deserialize("§c우클릭: §f-100냥"),
                legacy.deserialize("§c쉬프트+우클릭: §f-1,000냥")
            ))
        }
        inventory.setItem(P2_CURRENCY, p2Item)
    }

    private fun updateFeeInfo() {
        val totalCurrency = p1Currency + p2Currency
        val fee = (totalCurrency * 0.1).toInt()
        val info = ItemStack(Material.PAPER)
        info.editMeta { meta ->
            meta.displayName(legacy.deserialize("§7§l수수료 안내"))
            meta.lore(listOf(
                legacy.deserialize("§7냥 교환 시 총액의 §c10%§7 수수료가 차감됩니다."),
                Component.empty(),
                legacy.deserialize("§e${player1.name}: §f${formatNumber(p1Currency)}냥"),
                legacy.deserialize("§e${player2.name}: §f${formatNumber(p2Currency)}냥"),
                if (totalCurrency > 0) legacy.deserialize("§c예상 수수료: §f${formatNumber(fee)}냥") else Component.empty()
            ))
        }
        inventory.setItem(FEE_INFO, info)
    }

    private fun updateConfirmButtons() {
        val p1Mat = if (p1Confirmed) Material.LIME_WOOL else Material.RED_WOOL
        val p1Label = if (p1Confirmed) "§a§l✓ 확인 완료" else "§c§l✗ 교환 확인"
        val p1Button = ItemStack(p1Mat)
        p1Button.editMeta { meta ->
            meta.displayName(legacy.deserialize(p1Label))
            meta.lore(listOf(
                legacy.deserialize("§7${player1.name}"),
                if (p1Confirmed) legacy.deserialize("§a교환에 동의했습니다.")
                else legacy.deserialize("§e클릭하여 교환을 확인하세요.")
            ))
        }
        inventory.setItem(P1_CONFIRM, p1Button)

        val p2Mat = if (p2Confirmed) Material.LIME_WOOL else Material.RED_WOOL
        val p2Label = if (p2Confirmed) "§a§l✓ 확인 완료" else "§c§l✗ 교환 확인"
        val p2Button = ItemStack(p2Mat)
        p2Button.editMeta { meta ->
            meta.displayName(legacy.deserialize(p2Label))
            meta.lore(listOf(
                legacy.deserialize("§7${player2.name}"),
                if (p2Confirmed) legacy.deserialize("§a교환에 동의했습니다.")
                else legacy.deserialize("§e클릭하여 교환을 확인하세요.")
            ))
        }
        inventory.setItem(P2_CONFIRM, p2Button)
    }

    fun handleClick(event: InventoryClickEvent) {
        val clicker = event.whoClicked as? Player ?: return
        val slot = event.rawSlot

        // Allow clicks in player inventory (below the trade GUI)
        if (slot >= INV_SIZE) return

        val isPlayer1 = clicker.uniqueId == player1.uniqueId

        // Item slots — only allow the owning player to modify their side
        if (slot in P1_SLOTS) {
            if (!isPlayer1) {
                event.isCancelled = true
                return
            }
            resetConfirmations()
            return
        }

        if (slot in P2_SLOTS) {
            if (isPlayer1) {
                event.isCancelled = true
                return
            }
            resetConfirmations()
            return
        }

        event.isCancelled = true

        // Currency buttons
        if (slot == P1_CURRENCY && isPlayer1) {
            handleCurrencyClick(player1, event.isLeftClick, event.isShiftClick, isP1 = true)
            return
        }
        if (slot == P2_CURRENCY && !isPlayer1) {
            handleCurrencyClick(player2, event.isLeftClick, event.isShiftClick, isP1 = false)
            return
        }

        // Confirm buttons
        if (slot == P1_CONFIRM && isPlayer1) {
            p1Confirmed = !p1Confirmed
            updateConfirmButtons()
            clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 0.8f, 1.0f)
            if (p1Confirmed && p2Confirmed) executeTrade()
        } else if (slot == P2_CONFIRM && !isPlayer1) {
            p2Confirmed = !p2Confirmed
            updateConfirmButtons()
            clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 0.8f, 1.0f)
            if (p1Confirmed && p2Confirmed) executeTrade()
        }
    }

    private fun handleCurrencyClick(player: Player, isLeft: Boolean, isShift: Boolean, isP1: Boolean) {
        val amount = if (isShift) 1000 else 100
        val balance = plugin.dataManager.getBalance(player.uniqueId)
        val current = if (isP1) p1Currency else p2Currency

        val newAmount = if (isLeft) {
            // Add currency
            val maxAdd = balance - current // can't offer more than you have
            (current + amount).coerceAtMost(balance).coerceAtLeast(0)
        } else {
            // Remove currency
            (current - amount).coerceAtLeast(0)
        }

        if (isP1) p1Currency = newAmount else p2Currency = newAmount

        resetConfirmations()
        updateCurrencyButtons()
        updateFeeInfo()
        player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f)
    }

    fun resetConfirmations() {
        if (p1Confirmed || p2Confirmed) {
            p1Confirmed = false
            p2Confirmed = false
            Bukkit.getScheduler().runTask(plugin, Runnable { updateConfirmButtons() })
        }
    }

    private fun executeTrade() {
        // Verify both players still have enough currency
        if (p1Currency > 0 && !plugin.dataManager.hasBalance(player1.uniqueId, p1Currency)) {
            player1.sendMessage("§c냥이 부족합니다!")
            p1Confirmed = false
            updateConfirmButtons()
            return
        }
        if (p2Currency > 0 && !plugin.dataManager.hasBalance(player2.uniqueId, p2Currency)) {
            player2.sendMessage("§c냥이 부족합니다!")
            p2Confirmed = false
            updateConfirmButtons()
            return
        }

        completed = true

        // Collect items from each side
        val p1Items = P1_SLOTS.mapNotNull { inventory.getItem(it)?.clone() }
        val p2Items = P2_SLOTS.mapNotNull { inventory.getItem(it)?.clone() }

        // Clear the trade inventory
        for (slot in P1_SLOTS + P2_SLOTS) inventory.setItem(slot, null)

        // Process currency with 10% fee
        if (p1Currency > 0) {
            val fee = (p1Currency * 0.1).toInt()
            val received = p1Currency - fee
            plugin.dataManager.spendBalance(player1.uniqueId, p1Currency)
            plugin.dataManager.addBalance(player2.uniqueId, received)
            player1.sendMessage("§e${formatNumber(p1Currency)}냥§f을 보냈습니다. §7(수수료: ${formatNumber(fee)}냥)")
            player2.sendMessage("§e${formatNumber(received)}냥§f을 받았습니다. §7(수수료: ${formatNumber(fee)}냥)")
        }
        if (p2Currency > 0) {
            val fee = (p2Currency * 0.1).toInt()
            val received = p2Currency - fee
            plugin.dataManager.spendBalance(player2.uniqueId, p2Currency)
            plugin.dataManager.addBalance(player1.uniqueId, received)
            player2.sendMessage("§e${formatNumber(p2Currency)}냥§f을 보냈습니다. §7(수수료: ${formatNumber(fee)}냥)")
            player1.sendMessage("§e${formatNumber(received)}냥§f을 받았습니다. §7(수수료: ${formatNumber(fee)}냥)")
        }

        // Give P1's items to P2 and P2's items to P1
        val p1Overflow = mutableListOf<ItemStack>()
        val p2Overflow = mutableListOf<ItemStack>()

        for (item in p2Items) {
            val leftover = player1.inventory.addItem(item)
            p1Overflow.addAll(leftover.values)
        }
        for (item in p1Items) {
            val leftover = player2.inventory.addItem(item)
            p2Overflow.addAll(leftover.values)
        }

        // Drop overflows at player locations
        for (item in p1Overflow) {
            player1.world.dropItemNaturally(player1.location, item)
        }
        for (item in p2Overflow) {
            player2.world.dropItemNaturally(player2.location, item)
        }

        plugin.actionBarManager.refresh(player1.uniqueId)
        plugin.actionBarManager.refresh(player2.uniqueId)

        player1.playSound(player1.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f)
        player2.playSound(player2.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f)
        player1.sendMessage("§a§l✓ 교환이 완료되었습니다!")
        player2.sendMessage("§a§l✓ 교환이 완료되었습니다!")

        player1.closeInventory()
        player2.closeInventory()
    }

    fun handleClose(player: Player) {
        if (completed) {
            cleanup()
            return
        }

        // Return items to their owners
        for (slot in P1_SLOTS) {
            val item = inventory.getItem(slot) ?: continue
            inventory.setItem(slot, null)
            val leftover = player1.inventory.addItem(item)
            for (drop in leftover.values) {
                player1.world.dropItemNaturally(player1.location, drop)
            }
        }
        for (slot in P2_SLOTS) {
            val item = inventory.getItem(slot) ?: continue
            inventory.setItem(slot, null)
            val leftover = player2.inventory.addItem(item)
            for (drop in leftover.values) {
                player2.world.dropItemNaturally(player2.location, drop)
            }
        }

        // Currency is never deducted until trade completes, so nothing to return

        val other = if (player.uniqueId == player1.uniqueId) player2 else player1
        player.sendMessage("§c교환이 취소되었습니다.")
        other.sendMessage("§c${player.name}님이 교환을 취소했습니다.")
        other.closeInventory()

        cleanup()
    }

    private fun cleanup() {
        activeTrades.remove(inventory)
        playerTrades.remove(player1.uniqueId)
        playerTrades.remove(player2.uniqueId)
    }

    private fun makeGlass(material: Material): ItemStack {
        val glass = ItemStack(material)
        glass.editMeta { it.displayName(Component.text(" ")) }
        return glass
    }

    private fun formatNumber(n: Int): String =
        java.text.NumberFormat.getNumberInstance(java.util.Locale.US).format(n)

    class TradeGuiListener(private val plugin: NyaruPlugin) : Listener {

        @EventHandler
        fun onInventoryClick(event: InventoryClickEvent) {
            val gui = activeTrades[event.inventory] ?: return
            gui.handleClick(event)
        }

        @EventHandler
        fun onInventoryDrag(event: InventoryDragEvent) {
            val gui = activeTrades[event.inventory] ?: return
            val player = event.whoClicked as? Player ?: return
            val isPlayer1 = player.uniqueId == gui.player1.uniqueId
            val allowedSlots = if (isPlayer1) P1_SLOTS else P2_SLOTS

            // Only allow dragging into own slots
            for (slot in event.rawSlots) {
                if (slot < INV_SIZE && slot !in allowedSlots) {
                    event.isCancelled = true
                    return
                }
            }
            gui.resetConfirmations()
        }

        @EventHandler
        fun onInventoryClose(event: InventoryCloseEvent) {
            val gui = activeTrades[event.inventory] ?: return
            val player = event.player as? Player ?: return
            // Delay to avoid ConcurrentModification when closing both inventories
            Bukkit.getScheduler().runTask(plugin, Runnable {
                if (activeTrades.containsKey(event.inventory)) {
                    gui.handleClose(player)
                }
            })
        }
    }
}
