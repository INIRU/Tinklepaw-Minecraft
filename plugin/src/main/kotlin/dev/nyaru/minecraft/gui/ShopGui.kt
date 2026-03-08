package dev.nyaru.minecraft.gui

import dev.nyaru.minecraft.NyaruPlugin
import dev.nyaru.minecraft.model.ShopItem
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.util.concurrent.ConcurrentHashMap

private val HARVEST_TIME_KEY = NamespacedKey("nyaru", "harvest_time")
private val PURITY_KEY = NamespacedKey("nyaru", "purity")

class ShopGui(private val plugin: NyaruPlugin, private val player: Player) {

    companion object {
        val activeInventories = ConcurrentHashMap<Inventory, ShopGui>()

        // Category representative materials
        val CATEGORY_ICONS = mapOf(
            "광물" to Material.DIAMOND,
            "제련" to Material.IRON_INGOT,
            "작물" to Material.WHEAT,
            "씨앗" to Material.WHEAT_SEEDS,
            "목재" to Material.OAK_LOG,
            "물고기" to Material.COD,
            "전리품" to Material.ENDER_PEARL
        )

        val CATEGORY_SLOTS = listOf(20, 21, 22, 23, 24)

        val ITEM_SLOTS = listOf(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
        )

        // Calculator number pad slots → digit
        val NUMBER_PAD = mapOf(
            10 to 1, 11 to 2, 12 to 3,
            19 to 4, 20 to 5, 21 to 6,
            28 to 7, 29 to 8, 30 to 9,
            31 to 0
        )

        val CALC_CONFIRM_SLOT = 23
        val CALC_CLEAR_SLOT = 24
        val CALC_DELETE_SLOT = 15
        val CALC_CANCEL_SLOT = 32
        val CALC_DISPLAY_SLOT = 14
        val CALC_ITEM_SLOT = 4
    }

    enum class Mode { MAIN, CATEGORY, CALCULATOR }

    private val legacy = LegacyComponentSerializer.legacySection()
    private var mode = Mode.MAIN
    private lateinit var inventory: Inventory

    // Category view state
    private var currentCategory: String = ""
    private var categoryItems: List<ShopItem> = emptyList()

    // Calculator state
    private var calcItem: ShopItem? = null
    private var calcIsBuy: Boolean = true
    private var calcAmountStr: String = ""

    fun open() {
        showMain()
    }

    // ── Main Menu ──────────────────────────────────────────────────────────

    private fun showMain() {
        mode = Mode.MAIN
        inventory = Bukkit.createInventory(null, 54, legacy.deserialize("§6§l🛒 방울냥 상점"))

        val grayGlass = makeGlass(Material.GRAY_STAINED_GLASS_PANE)
        val headerGlass = makeGlass(Material.ORANGE_STAINED_GLASS_PANE)
        for (i in 0 until 54) inventory.setItem(i, grayGlass)
        for (i in 0..8) inventory.setItem(i, headerGlass)
        for (i in 45..53) inventory.setItem(i, headerGlass)

        val categories = plugin.shopManager.getCategories()
        val startSlot = when (categories.size) {
            1 -> 22
            2 -> 21
            3 -> 20
            4 -> 20
            else -> 20
        }

        for ((idx, category) in categories.withIndex()) {
            if (idx >= CATEGORY_SLOTS.size) break
            val slot = CATEGORY_SLOTS[idx]
            val items = plugin.shopManager.getItemsByCategory(category)
            val icon = CATEGORY_ICONS[category] ?: Material.CHEST
            val catItem = ItemStack(icon)
            catItem.editMeta { meta ->
                meta.displayName(legacy.deserialize("§e§l${category}"))
                meta.lore(listOf(
                    legacy.deserialize("§7아이템 수: §f${items.size}개"),
                    Component.empty(),
                    legacy.deserialize("§a▶ 클릭하여 이동")
                ))
            }
            inventory.setItem(slot, catItem)
        }

        // Info book
        val book = ItemStack(Material.BOOK)
        book.editMeta { meta ->
            meta.displayName(legacy.deserialize("§e§l상점 안내"))
            meta.lore(listOf(
                legacy.deserialize("§7좌클릭: 판매 / 우클릭: 구매"),
                legacy.deserialize("§7광물/제련: 순정도 보너스"),
                legacy.deserialize("§7작물: 신선도 보너스")
            ))
        }
        inventory.setItem(49, book)

        activeInventories[inventory] = this
        player.openInventory(inventory)
        player.playSound(player.location, Sound.BLOCK_CHEST_OPEN, 0.8f, 1.2f)
    }

    // ── Category View ──────────────────────────────────────────────────────

    private fun showCategory(category: String) {
        mode = Mode.CATEGORY
        currentCategory = category
        categoryItems = plugin.shopManager.getItemsByCategory(category)

        activeInventories.remove(inventory)
        inventory = Bukkit.createInventory(null, 54, legacy.deserialize("§6§l🛒 ${category}"))

        val grayGlass = makeGlass(Material.GRAY_STAINED_GLASS_PANE)
        val borderGlass = makeGlass(Material.ORANGE_STAINED_GLASS_PANE)
        for (i in 0 until 54) inventory.setItem(i, grayGlass)
        for (i in 0..8) inventory.setItem(i, borderGlass)
        for (i in 45..53) inventory.setItem(i, borderGlass)

        for ((idx, shopItem) in categoryItems.withIndex()) {
            if (idx >= ITEM_SLOTS.size) break
            val slot = ITEM_SLOTS[idx]
            val mat = runCatching { Material.valueOf(shopItem.material) }.getOrNull() ?: continue
            val stack = ItemStack(mat)
            stack.editMeta { meta ->
                meta.displayName(legacy.deserialize("§f§l${shopItem.displayName}"))
                val lore = mutableListOf<Component>()
                lore.add(Component.empty())
                if (shopItem.buyPrice > 0) {
                    lore.add(legacy.deserialize("§7구매: §e${shopItem.buyPrice}냥"))
                }
                if (shopItem.sellPrice > 0) {
                    lore.add(legacy.deserialize("§7판매: §e${shopItem.sellPrice}냥"))
                }
                lore.add(Component.empty())
                if (shopItem.sellPrice > 0) {
                    lore.add(legacy.deserialize("§f좌클릭: §a판매"))
                }
                if (shopItem.buyPrice > 0) {
                    lore.add(legacy.deserialize("§f우클릭: §b구매"))
                }
                meta.lore(lore)
            }
            inventory.setItem(slot, stack)
        }

        // Back button slot 45
        val back = ItemStack(Material.ARROW)
        back.editMeta { meta -> meta.displayName(legacy.deserialize("§f← 뒤로")) }
        inventory.setItem(45, back)

        activeInventories[inventory] = this
        player.openInventory(inventory)
        player.playSound(player.location, Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.0f)
    }

    // ── Calculator View ────────────────────────────────────────────────────

    private fun openCalculator(shopItem: ShopItem, isBuy: Boolean) {
        mode = Mode.CALCULATOR
        calcItem = shopItem
        calcIsBuy = isBuy
        calcAmountStr = ""

        activeInventories.remove(inventory)
        inventory = Bukkit.createInventory(null, 36, legacy.deserialize("§e§l🔢 수량 입력"))
        buildCalculatorUi()

        activeInventories[inventory] = this
        player.openInventory(inventory)
        player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.6f, 1.2f)
    }

    private fun buildCalculatorUi() {
        inventory.clear()

        val item = calcItem ?: return
        val isBuy = calcIsBuy
        val amount = calcAmountStr.toIntOrNull() ?: 0
        val unitPrice = if (isBuy) item.buyPrice else item.sellPrice
        val total = unitPrice * amount

        val grayGlass = makeGlass(Material.GRAY_STAINED_GLASS_PANE)
        for (i in 0 until 36) inventory.setItem(i, grayGlass)

        // Slot 4: Item preview
        val mat = runCatching { Material.valueOf(item.material) }.getOrNull() ?: Material.BARRIER
        val preview = ItemStack(mat)
        preview.editMeta { meta ->
            meta.displayName(legacy.deserialize("§f§l${item.displayName}"))
            val lore = mutableListOf<Component>()
            lore.add(legacy.deserialize(if (isBuy) "§b구매 모드" else "§a판매 모드"))
            lore.add(Component.empty())
            lore.add(legacy.deserialize("§7단가: §e${unitPrice}냥"))
            lore.add(legacy.deserialize("§7수량: §f${if (calcAmountStr.isEmpty()) "0" else calcAmountStr}"))
            lore.add(legacy.deserialize("§7합계: §e${total}냥"))
            meta.lore(lore)
        }
        inventory.setItem(CALC_ITEM_SLOT, preview)

        // Number pad
        for ((slot, digit) in NUMBER_PAD) {
            val numBlock = ItemStack(Material.GREEN_CONCRETE)
            numBlock.editMeta { meta ->
                meta.displayName(legacy.deserialize("§a§l${digit}"))
            }
            inventory.setItem(slot, numBlock)
        }

        // Slot 14: Display current amount
        val display = ItemStack(Material.PAPER)
        display.editMeta { meta ->
            meta.displayName(legacy.deserialize("§f수량: §e${if (calcAmountStr.isEmpty()) "0" else calcAmountStr}"))
        }
        inventory.setItem(CALC_DISPLAY_SLOT, display)

        // Slot 15: Delete last digit
        val delete = ItemStack(Material.RED_CONCRETE)
        delete.editMeta { meta ->
            meta.displayName(legacy.deserialize("§c⌫ 지우기"))
        }
        inventory.setItem(CALC_DELETE_SLOT, delete)

        // Slot 23: Confirm
        val confirmLore = mutableListOf<Component>()
        confirmLore.add(legacy.deserialize(if (isBuy) "§7구매 총액: §e${total}냥" else "§7판매 수익: §e${total}냥"))
        if (amount <= 0) confirmLore.add(legacy.deserialize("§c수량을 입력하세요."))
        val confirm = ItemStack(Material.LIME_CONCRETE)
        confirm.editMeta { meta ->
            meta.displayName(legacy.deserialize("§a§l✓ 확인"))
            meta.lore(confirmLore)
        }
        inventory.setItem(CALC_CONFIRM_SLOT, confirm)

        // Slot 24: Clear
        val clear = ItemStack(Material.ORANGE_CONCRETE)
        clear.editMeta { meta ->
            meta.displayName(legacy.deserialize("§6↺ 초기화"))
        }
        inventory.setItem(CALC_CLEAR_SLOT, clear)

        // Slot 32: Cancel/Back
        val cancel = ItemStack(Material.BARRIER)
        cancel.editMeta { meta ->
            meta.displayName(legacy.deserialize("§c✕ 취소"))
        }
        inventory.setItem(CALC_CANCEL_SLOT, cancel)
    }

    // ── Click Handling ─────────────────────────────────────────────────────

    fun handleClick(slot: Int, isRightClick: Boolean) {
        when (mode) {
            Mode.MAIN -> handleMainClick(slot)
            Mode.CATEGORY -> handleCategoryClick(slot, isRightClick)
            Mode.CALCULATOR -> handleCalculatorClick(slot)
        }
    }

    private fun handleMainClick(slot: Int) {
        val categories = plugin.shopManager.getCategories()
        val slotIdx = CATEGORY_SLOTS.indexOf(slot)
        if (slotIdx < 0 || slotIdx >= categories.size) return
        val category = categories[slotIdx]
        showCategory(category)
    }

    private fun handleCategoryClick(slot: Int, isRightClick: Boolean) {
        if (slot == 45) {
            player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.6f, 1.0f)
            showMain()
            return
        }

        val idx = ITEM_SLOTS.indexOf(slot)
        if (idx < 0 || idx >= categoryItems.size) return

        val shopItem = categoryItems[idx]

        if (!isRightClick) {
            // Left click = sell
            if (shopItem.sellPrice <= 0) {
                player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f)
                player.sendMessage("§c이 아이템은 판매할 수 없습니다.")
                return
            }
            val mat = runCatching { Material.valueOf(shopItem.material) }.getOrNull() ?: return
            val count = player.inventory.all(mat).values.sumOf { it.amount }
            if (count <= 0) {
                player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f)
                player.sendMessage("§c인벤토리에 §e${shopItem.displayName}§c이/가 없습니다.")
                return
            }
            openCalculator(shopItem, isBuy = false)
        } else {
            // Right click = buy
            if (shopItem.buyPrice <= 0) {
                player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f)
                player.sendMessage("§c이 아이템은 구매할 수 없습니다.")
                return
            }
            openCalculator(shopItem, isBuy = true)
        }
    }

    private fun handleCalculatorClick(slot: Int) {
        when (slot) {
            CALC_CANCEL_SLOT -> {
                player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.6f, 1.0f)
                showCategory(currentCategory)
                return
            }
            CALC_CLEAR_SLOT -> {
                calcAmountStr = ""
                buildCalculatorUi()
                player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.6f, 0.8f)
                return
            }
            CALC_DELETE_SLOT -> {
                if (calcAmountStr.isNotEmpty()) {
                    calcAmountStr = calcAmountStr.dropLast(1)
                    buildCalculatorUi()
                    player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.6f, 1.0f)
                }
                return
            }
            CALC_CONFIRM_SLOT -> {
                confirmTransaction()
                return
            }
            CALC_DISPLAY_SLOT, CALC_ITEM_SLOT -> return
        }

        val digit = NUMBER_PAD[slot] ?: return
        // Max 6 digits to prevent overflow
        if (calcAmountStr.length >= 6) return
        // Prevent leading zeros
        if (calcAmountStr.isEmpty() && digit == 0) return
        calcAmountStr += digit.toString()
        buildCalculatorUi()
        player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.6f, 1.2f)
    }

    private fun confirmTransaction() {
        val shopItem = calcItem ?: return
        val amount = calcAmountStr.toIntOrNull() ?: 0

        if (amount <= 0) {
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f)
            player.sendMessage("§c수량을 입력하세요.")
            return
        }

        if (calcIsBuy) {
            executeBuy(shopItem, amount)
        } else {
            executeSell(shopItem, amount)
        }
    }

    private fun executeBuy(shopItem: ShopItem, qty: Int) {
        val totalCost = shopItem.buyPrice * qty
        val balance = plugin.dataManager.getBalance(player.uniqueId)

        if (balance < totalCost) {
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f)
            player.sendMessage("§c냥이 부족합니다. (보유: §e${balance}냥 §c/ 필요: §e${totalCost}냥§c)")
            return
        }

        val mat = runCatching { Material.valueOf(shopItem.material) }.getOrNull()
        if (mat == null) {
            player.sendMessage("§c아이템 데이터 오류가 발생했습니다.")
            return
        }

        plugin.dataManager.spendBalance(player.uniqueId, totalCost)
        plugin.dataManager.save(player.uniqueId)

        // Give items (split into stacks of 64)
        var remaining = qty
        while (remaining > 0) {
            val stackSize = minOf(remaining, 64)
            player.inventory.addItem(ItemStack(mat, stackSize))
            remaining -= stackSize
        }

        player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f)
        player.sendMessage("§a${shopItem.displayName} §f${qty}개 구매! §c-${totalCost}냥")
        player.closeInventory()
    }

    private fun executeSell(shopItem: ShopItem, qty: Int) {
        val mat = runCatching { Material.valueOf(shopItem.material) }.getOrNull()
        if (mat == null) {
            player.sendMessage("§c아이템 데이터 오류가 발생했습니다.")
            return
        }

        val inv = player.inventory
        val matchingStacks = (0 until inv.size)
            .mapNotNull { i -> inv.getItem(i)?.takeIf { it.type == mat && it.amount > 0 }?.let { i to it } }

        val totalAvailable = matchingStacks.sumOf { (_, s) -> s.amount }
        if (totalAvailable < qty) {
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f)
            player.sendMessage("§c아이템이 부족합니다. (보유: §e${totalAvailable}개 §c/ 필요: §e${qty}개§c)")
            return
        }

        // Pick the best stack first (prefer stacks with PDC tags for bonus)
        val sortedStacks = matchingStacks.sortedByDescending { (_, s) ->
            val pdc = s.itemMeta?.persistentDataContainer
            when {
                pdc?.has(HARVEST_TIME_KEY, PersistentDataType.LONG) == true -> 2
                pdc?.has(PURITY_KEY, PersistentDataType.INTEGER) == true -> 1
                else -> 0
            }
        }

        // Gather PDC data from the first tagged stack (representative item for bonuses)
        val representativeStack = sortedStacks.firstOrNull()?.second
        val representativeMeta = representativeStack?.itemMeta

        val harvTime = representativeMeta?.persistentDataContainer?.get(HARVEST_TIME_KEY, PersistentDataType.LONG)
        val purity = representativeMeta?.persistentDataContainer?.get(PURITY_KEY, PersistentDataType.INTEGER)

        val freshnessPct: Double? = if (harvTime != null) {
            val minutesOld = (System.currentTimeMillis() - harvTime) / 60000.0
            when {
                minutesOld <= 10 -> 100.0
                minutesOld >= 30 -> 60.0
                else -> 100.0 - ((minutesOld - 10) / 20.0) * 40.0
            }
        } else null

        // Remove items from inventory
        var toRemove = qty
        for ((invSlot, stack) in sortedStacks) {
            if (toRemove <= 0) break
            val take = minOf(toRemove, stack.amount)
            if (take >= stack.amount) {
                inv.setItem(invSlot, null)
            } else {
                stack.amount -= take
            }
            toRemove -= take
        }

        // Calculate price with bonuses
        val basePrice = shopItem.sellPrice.toDouble()

        // 순정도 등급 보너스: 매우낮음(1)=x1.0, 낮음(2)=x1.1, 중간(3)=x1.25, 높은(4)=x1.5
        val purityMultiplier: Double = when (purity) {
            4 -> 1.5
            3 -> 1.25
            2 -> 1.1
            1 -> 1.0
            else -> 1.0
        }

        val purityBonus = basePrice * (if (purity != null) purityMultiplier else 1.0)

        val finalUnitPrice: Double = if (freshnessPct != null) {
            purityBonus * (0.6 + freshnessPct / 100.0 * 0.4)
        } else purityBonus

        val totalEarnings = (finalUnitPrice * qty).toInt()

        plugin.dataManager.addBalance(player.uniqueId, totalEarnings)
        plugin.dataManager.save(player.uniqueId)

        val purityName = when (purity) {
            4 -> "§d높은"
            3 -> "§b중간"
            2 -> "§a낮음"
            1 -> "§7매우 낮음"
            else -> null
        }

        player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f)
        player.sendMessage("§a${shopItem.displayName} §f${qty}개 판매! §6+${totalEarnings}냥")
        if (freshnessPct != null) {
            player.sendMessage("§7신선도: §e${String.format("%.0f", freshnessPct)}%")
        }
        if (purityName != null) {
            player.sendMessage("§7순정도: ${purityName}")
        }
        player.closeInventory()
    }

    private fun makeGlass(material: Material): ItemStack {
        val glass = ItemStack(material)
        glass.editMeta { meta -> meta.displayName(Component.text(" ")) }
        return glass
    }

    class ShopGuiListener(private val plugin: NyaruPlugin) : Listener {

        @EventHandler
        fun onInventoryClick(event: InventoryClickEvent) {
            val gui = activeInventories[event.inventory] ?: return
            if (event.whoClicked != gui.player) return
            event.isCancelled = true
            gui.handleClick(event.rawSlot, event.isRightClick)
        }

        @EventHandler
        fun onInventoryClose(event: InventoryCloseEvent) {
            activeInventories.remove(event.inventory)
        }
    }
}
