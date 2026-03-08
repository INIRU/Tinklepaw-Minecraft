package dev.nyaru.minecraft.gui

import dev.nyaru.minecraft.NyaruPlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil
import kotlin.random.Random

private val ENHANCE_LEVEL_KEY = NamespacedKey("nyaru", "enhance_level")
val SOULBOUND_KEY = NamespacedKey("nyaru", "soulbound")

class EnhanceGui(private val plugin: NyaruPlugin, private val player: Player) {

    companion object {
        val activeInventories = ConcurrentHashMap<Inventory, EnhanceGui>()

        const val TOOL_SLOT = 22
        const val ENHANCE_BUTTON_SLOT = 31
        const val SOULBOUND_BUTTON_SLOT = 40
        const val INFO_SLOT = 4
        const val INV_SIZE = 54
        const val SOULBOUND_COST = 50000

        val VALID_TOOLS = setOf(
            Material.WOODEN_PICKAXE, Material.STONE_PICKAXE, Material.IRON_PICKAXE,
            Material.GOLDEN_PICKAXE, Material.DIAMOND_PICKAXE, Material.NETHERITE_PICKAXE,
            Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD,
            Material.GOLDEN_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD,
            Material.WOODEN_HOE, Material.STONE_HOE, Material.IRON_HOE,
            Material.GOLDEN_HOE, Material.DIAMOND_HOE, Material.NETHERITE_HOE,
            Material.WOODEN_AXE, Material.STONE_AXE, Material.IRON_AXE,
            Material.GOLDEN_AXE, Material.DIAMOND_AXE, Material.NETHERITE_AXE,
            Material.WOODEN_SHOVEL, Material.STONE_SHOVEL, Material.IRON_SHOVEL,
            Material.GOLDEN_SHOVEL, Material.DIAMOND_SHOVEL, Material.NETHERITE_SHOVEL,
            Material.MACE
        )

        fun isPickaxe(mat: Material) = mat.name.endsWith("_PICKAXE")
        fun isSword(mat: Material) = mat.name.endsWith("_SWORD")
        fun isHoe(mat: Material) = mat.name.endsWith("_HOE")
        fun isAxe(mat: Material) = mat.name.endsWith("_AXE")
        fun isShovel(mat: Material) = mat.name.endsWith("_SHOVEL")
        fun isMace(mat: Material) = mat == Material.MACE

        fun enhanceLevelColor(level: Int): String = when {
            level <= 3 -> "§a"
            level <= 6 -> "§b"
            level <= 9 -> "§d"
            else -> "§6§l"
        }
    }

    private lateinit var inventory: Inventory
    private val legacy = LegacyComponentSerializer.legacySection()

    fun open() {
        inventory = Bukkit.createInventory(null, INV_SIZE, legacy.deserialize("§5§l⚒ 강화"))
        buildUi()
        activeInventories[inventory] = this
        player.openInventory(inventory)
        player.playSound(player.location, Sound.BLOCK_ANVIL_PLACE, 0.8f, 1.0f)
    }

    private fun buildUi() {
        inventory.clear()

        val bgGlass = makeGlass(Material.PURPLE_STAINED_GLASS_PANE)
        for (i in 0 until INV_SIZE) inventory.setItem(i, bgGlass)

        // Slot 22: Open tool slot (leave empty / no glass)
        inventory.setItem(TOOL_SLOT, null)

        // Slot 4: Info book
        val info = ItemStack(Material.BOOK)
        info.editMeta { meta ->
            meta.displayName(legacy.deserialize("§e강화 안내"))
            meta.lore(listOf(
                legacy.deserialize("§7슬롯 §f22§7에 도구를 올려두세요."),
                legacy.deserialize("§7강화 버튼을 클릭하면 강화가 진행됩니다."),
                Component.empty(),
                legacy.deserialize("§a성공(50%): §f+1 ~ +3 강화"),
                legacy.deserialize("§c실패(50%): §f-0 ~ -2 강화"),
                legacy.deserialize("§7강화 수치는 최소 §f+0§7, 최대 §f+10"),
                Component.empty(),
                legacy.deserialize("§7강화 비용: §e(현재 강화 + 1) × 500냥")
            ))
        }
        inventory.setItem(INFO_SLOT, info)

        // Slot 31: Enhance button (recalculated when tool is placed)
        updateEnhanceButton()
        updateSoulboundButton()
    }

    private fun updateEnhanceButton() {
        val toolItem = inventory.getItem(TOOL_SLOT)
        val currentLevel = toolItem?.itemMeta?.persistentDataContainer
            ?.get(ENHANCE_LEVEL_KEY, PersistentDataType.INTEGER) ?: 0

        val button = ItemStack(Material.ANVIL)
        button.editMeta { meta ->
            if (currentLevel >= 10) {
                meta.displayName(legacy.deserialize("§6§l⚒ 최대 강화 달성!"))
                meta.lore(listOf(
                    legacy.deserialize("§7현재 강화: §6+${currentLevel}"),
                    legacy.deserialize("§c더 이상 강화할 수 없습니다.")
                ))
            } else {
                val cost = (currentLevel + 1) * 500
                meta.displayName(legacy.deserialize("§d§l⚒ 강화하기"))
                meta.lore(listOf(
                    legacy.deserialize("§7비용: §e${cost}냥"),
                    legacy.deserialize("§7현재 강화: §f+${currentLevel}"),
                    legacy.deserialize("§7결과: §a+1~3 §7또는 §c-0~2")
                ))
            }
        }
        inventory.setItem(ENHANCE_BUTTON_SLOT, button)
    }

    private fun updateSoulboundButton() {
        val toolItem = inventory.getItem(TOOL_SLOT)
        val isSoulbound = toolItem?.itemMeta?.persistentDataContainer
            ?.has(SOULBOUND_KEY) == true

        val button = ItemStack(if (isSoulbound) Material.NETHER_STAR else Material.ENDER_EYE)
        button.editMeta { meta ->
            if (isSoulbound) {
                meta.displayName(legacy.deserialize("§d§l✦ 소울바운드 적용됨"))
                meta.lore(listOf(
                    legacy.deserialize("§7이 아이템은 사망 시 유지됩니다."),
                    legacy.deserialize("§a이미 소울바운드 상태입니다.")
                ))
            } else {
                meta.displayName(legacy.deserialize("§d§l✦ 소울바운드 부여"))
                meta.lore(listOf(
                    legacy.deserialize("§7사망 시 이 아이템을 잃지 않습니다."),
                    legacy.deserialize("§7비용: §e50,000냥"),
                    Component.empty(),
                    legacy.deserialize("§e▶ 클릭하여 소울바운드 부여")
                ))
            }
        }
        inventory.setItem(SOULBOUND_BUTTON_SLOT, button)
    }

    private fun performSoulbound() {
        val toolItem = inventory.getItem(TOOL_SLOT)
        if (toolItem == null || toolItem.type == Material.AIR) {
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f)
            player.sendMessage("§c아이템을 슬롯에 올려두세요.")
            return
        }

        val meta = toolItem.itemMeta
        if (meta.persistentDataContainer.has(SOULBOUND_KEY)) {
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f)
            player.sendMessage("§c이미 소울바운드 상태입니다.")
            return
        }

        if (!plugin.dataManager.hasBalance(player.uniqueId, SOULBOUND_COST)) {
            val balance = plugin.dataManager.getBalance(player.uniqueId)
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f)
            player.sendMessage("§c냥이 부족합니다. (보유: §e${balance}냥 §c/ 필요: §e50,000냥§c)")
            return
        }

        plugin.dataManager.spendBalance(player.uniqueId, SOULBOUND_COST)
        plugin.actionBarManager.refresh(player.uniqueId)
        plugin.dataManager.save(player.uniqueId)

        // Apply soulbound tag
        meta.persistentDataContainer.set(SOULBOUND_KEY, PersistentDataType.BYTE, 1)

        // Add soulbound lore
        val existingLore = meta.lore()?.toMutableList() ?: mutableListOf()
        val soulboundLine = legacy.deserialize("§d✦ 소울바운드 §7(사망 시 유지)")
        val soulboundPattern = "소울바운드"
        if (existingLore.none { legacy.serialize(it).contains(soulboundPattern) }) {
            existingLore.add(soulboundLine)
        }
        meta.lore(existingLore)

        toolItem.itemMeta = meta
        inventory.setItem(TOOL_SLOT, toolItem)

        updateSoulboundButton()
        player.playSound(player.location, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f)
        player.sendMessage("§d§l✦ 소울바운드 부여 완료! §7이 아이템은 사망 시 유지됩니다. §e(50,000냥 차감)")
    }

    fun handleClick(event: InventoryClickEvent) {
        val slot = event.rawSlot

        // Allow clicks in the player's own inventory (pickup/place items)
        if (slot >= INV_SIZE) {
            Bukkit.getScheduler().runTask(plugin, Runnable { updateEnhanceButton(); updateSoulboundButton() })
            return
        }

        // Allow the tool slot to receive items
        if (slot == TOOL_SLOT) {
            event.isCancelled = false
            Bukkit.getScheduler().runTask(plugin, Runnable { updateEnhanceButton(); updateSoulboundButton() })
            return
        }

        event.isCancelled = true

        if (slot == ENHANCE_BUTTON_SLOT) {
            performEnhance()
        } else if (slot == SOULBOUND_BUTTON_SLOT) {
            performSoulbound()
        }
    }

    private fun performEnhance() {
        val toolItem = inventory.getItem(TOOL_SLOT)
        if (toolItem == null || toolItem.type == Material.AIR) {
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f)
            player.sendMessage("§c강화할 도구를 슬롯에 올려두세요.")
            return
        }

        if (!VALID_TOOLS.contains(toolItem.type)) {
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f)
            player.sendMessage("§c이 아이템은 강화할 수 없습니다.")
            return
        }

        val meta = toolItem.itemMeta
        val currentLevel = meta.persistentDataContainer.get(ENHANCE_LEVEL_KEY, PersistentDataType.INTEGER) ?: 0

        if (currentLevel >= 10) {
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f)
            player.sendMessage("§c이미 최대 강화(+10)입니다.")
            return
        }

        val cost = (currentLevel + 1) * 500

        if (!plugin.dataManager.hasBalance(player.uniqueId, cost)) {
            val balance = plugin.dataManager.getBalance(player.uniqueId)
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f)
            player.sendMessage("§c냥이 부족합니다. (보유: §e${balance}냥 §c/ 필요: §e${cost}냥§c)")
            return
        }

        plugin.dataManager.spendBalance(player.uniqueId, cost)
        plugin.actionBarManager.refresh(player.uniqueId)
        plugin.dataManager.save(player.uniqueId)

        // 50% success, 50% fail
        val success = Random.nextBoolean()
        val change = if (success) Random.nextInt(1, 4) else -Random.nextInt(0, 3)
        val newLevel = (currentLevel + change).coerceIn(0, 10)

        // Apply new level to item
        val updatedItem = applyEnhancement(toolItem, newLevel)
        inventory.setItem(TOOL_SLOT, updatedItem)

        updateEnhanceButton()

        if (success) {
            player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f)
            player.sendMessage("§a§l강화 성공! §f+${currentLevel} §a→ §f+${newLevel} §7(+${change})")
        } else {
            player.playSound(player.location, Sound.ENTITY_ITEM_BREAK, 1.0f, 0.8f)
            player.sendMessage("§c§l강화 실패! §f+${currentLevel} §c→ §f+${newLevel} §7(${change})")
        }
    }

    private fun applyEnhancement(original: ItemStack, newLevel: Int): ItemStack {
        val item = original.clone()
        val meta = item.itemMeta

        // Store level in PDC
        meta.persistentDataContainer.set(ENHANCE_LEVEL_KEY, PersistentDataType.INTEGER, newLevel)

        // Remove old enhancement-related enchantments before reapplying
        val mat = item.type

        val enchantsToRemove = mutableListOf<Enchantment>()
        if (isPickaxe(mat) || isAxe(mat) || isHoe(mat) || isShovel(mat)) {
            enchantsToRemove += Enchantment.EFFICIENCY
            enchantsToRemove += Enchantment.FORTUNE
            enchantsToRemove += Enchantment.UNBREAKING
        }
        if (isSword(mat)) {
            enchantsToRemove += Enchantment.SHARPNESS
            enchantsToRemove += Enchantment.LOOTING
            enchantsToRemove += Enchantment.UNBREAKING
        }
        if (isMace(mat)) {
            enchantsToRemove += Enchantment.DENSITY
            enchantsToRemove += Enchantment.BREACH
            enchantsToRemove += Enchantment.UNBREAKING
        }
        for (ench in enchantsToRemove) {
            meta.removeEnchant(ench)
        }

        // Apply enchantments based on new level
        if (newLevel > 0) {
            when {
                isPickaxe(mat) -> {
                    meta.addEnchant(Enchantment.EFFICIENCY, newLevel, true)
                    meta.addEnchant(Enchantment.FORTUNE, ceil(newLevel / 2.0).toInt(), true)
                    meta.addEnchant(Enchantment.UNBREAKING, ceil(newLevel / 3.0).toInt(), true)
                }
                isSword(mat) -> {
                    meta.addEnchant(Enchantment.SHARPNESS, newLevel, true)
                    meta.addEnchant(Enchantment.LOOTING, ceil(newLevel / 2.0).toInt(), true)
                    meta.addEnchant(Enchantment.UNBREAKING, ceil(newLevel / 3.0).toInt(), true)
                }
                isAxe(mat) -> {
                    meta.addEnchant(Enchantment.EFFICIENCY, newLevel, true)
                    meta.addEnchant(Enchantment.SHARPNESS, ceil(newLevel / 2.0).toInt(), true)
                    meta.addEnchant(Enchantment.UNBREAKING, ceil(newLevel / 3.0).toInt(), true)
                }
                isHoe(mat) -> {
                    meta.addEnchant(Enchantment.EFFICIENCY, newLevel, true)
                    meta.addEnchant(Enchantment.FORTUNE, ceil(newLevel / 2.0).toInt(), true)
                    meta.addEnchant(Enchantment.UNBREAKING, ceil(newLevel / 3.0).toInt(), true)
                }
                isMace(mat) -> {
                    meta.addEnchant(Enchantment.DENSITY, newLevel, true)
                    meta.addEnchant(Enchantment.BREACH, ceil(newLevel / 2.0).toInt(), true)
                    meta.addEnchant(Enchantment.UNBREAKING, ceil(newLevel / 3.0).toInt(), true)
                }
                isShovel(mat) -> {
                    meta.addEnchant(Enchantment.EFFICIENCY, newLevel, true)
                    meta.addEnchant(Enchantment.FORTUNE, ceil(newLevel / 2.0).toInt(), true)
                    meta.addEnchant(Enchantment.UNBREAKING, ceil(newLevel / 3.0).toInt(), true)
                }
            }
        }

        // Update display name with enhance prefix
        val levelColor = enhanceLevelColor(newLevel)
        val originalName = if (meta.hasDisplayName()) {
            legacy.serialize(meta.displayName()!!)
                .removePrefix("§d[+${meta.persistentDataContainer.get(ENHANCE_LEVEL_KEY, PersistentDataType.INTEGER) ?: 0}] ")
                .let { name ->
                    // Strip any existing enhance prefix pattern "§d[+N] "
                    val prefixRegex = Regex("§d\\[\\+\\d+] ")
                    prefixRegex.replace(name, "")
                }
        } else {
            mat.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
        }

        if (newLevel > 0) {
            meta.displayName(legacy.deserialize("§d[+${newLevel}] ${originalName}"))
        } else {
            meta.displayName(legacy.deserialize(originalName))
        }

        // Update lore: replace or add enhance line
        val existingLore = meta.lore()?.toMutableList() ?: mutableListOf()
        val enhanceLine = legacy.deserialize("${levelColor}✦ 강화: §f+${newLevel}")
        val enhanceLinePattern = "✦ 강화:"

        val enhanceIdx = existingLore.indexOfFirst { line ->
            legacy.serialize(line).contains(enhanceLinePattern)
        }
        if (enhanceIdx >= 0) {
            existingLore[enhanceIdx] = enhanceLine
        } else {
            existingLore.add(0, enhanceLine)
        }
        meta.lore(existingLore)

        item.itemMeta = meta
        return item
    }

    private fun makeGlass(material: Material): ItemStack {
        val glass = ItemStack(material)
        glass.editMeta { it.displayName(Component.text(" ")) }
        return glass
    }

    class EnhanceGuiListener(private val plugin: NyaruPlugin) : Listener {

        @EventHandler
        fun onInventoryClick(event: InventoryClickEvent) {
            val gui = activeInventories[event.inventory] ?: return
            if (event.whoClicked != gui.player) return
            gui.handleClick(event)
        }

        @EventHandler
        fun onInventoryClose(event: InventoryCloseEvent) {
            val gui = activeInventories.remove(event.inventory) ?: return
            // Return tool to player if still in the slot
            val tool = gui.inventory.getItem(EnhanceGui.TOOL_SLOT)
            if (tool != null && tool.type != Material.AIR) {
                gui.inventory.setItem(EnhanceGui.TOOL_SLOT, null)
                val overflow = gui.player.inventory.addItem(tool)
                if (overflow.isNotEmpty()) {
                    // Drop items at player location if inventory full
                    for (item in overflow.values) {
                        gui.player.world.dropItemNaturally(gui.player.location, item)
                    }
                }
            }
        }
    }
}
