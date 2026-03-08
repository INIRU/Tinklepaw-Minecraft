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

class EnhanceGui(private val plugin: NyaruPlugin, private val player: Player) {

    companion object {
        val activeInventories = ConcurrentHashMap<Inventory, EnhanceGui>()

        const val TOOL_SLOT = 22
        const val ENHANCE_BUTTON_SLOT = 31
        const val INFO_SLOT = 4
        const val INV_SIZE = 54

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
                legacy.deserialize("§c실패(50%): §f-1 ~ -3 강화"),
                legacy.deserialize("§7강화 수치는 최소 §f+0§7, 최대 §f+10"),
                Component.empty(),
                legacy.deserialize("§7강화 비용: §e(현재 강화 + 1) × 500냥")
            ))
        }
        inventory.setItem(INFO_SLOT, info)

        // Slot 31: Enhance button (recalculated when tool is placed)
        updateEnhanceButton()
    }

    private fun updateEnhanceButton() {
        val toolItem = inventory.getItem(TOOL_SLOT)
        val currentLevel = toolItem?.itemMeta?.persistentDataContainer
            ?.get(ENHANCE_LEVEL_KEY, PersistentDataType.INTEGER) ?: 0
        val cost = (currentLevel + 1) * 500

        val button = ItemStack(Material.ANVIL)
        button.editMeta { meta ->
            meta.displayName(legacy.deserialize("§d§l⚒ 강화하기"))
            meta.lore(listOf(
                legacy.deserialize("§7비용: §e${cost}냥"),
                legacy.deserialize("§7현재 강화: §f+${currentLevel}"),
                legacy.deserialize("§7결과: §a+1~3 §7또는 §c-1~3")
            ))
        }
        inventory.setItem(ENHANCE_BUTTON_SLOT, button)
    }

    fun handleClick(event: InventoryClickEvent) {
        val slot = event.rawSlot

        // Allow only the tool slot to receive items
        if (slot == TOOL_SLOT) {
            event.isCancelled = false
            // Schedule button update after item placement
            Bukkit.getScheduler().runTask(plugin, Runnable { updateEnhanceButton() })
            return
        }

        event.isCancelled = true

        if (slot == ENHANCE_BUTTON_SLOT) {
            performEnhance()
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
        val cost = (currentLevel + 1) * 500

        if (!plugin.dataManager.hasBalance(player.uniqueId, cost)) {
            val balance = plugin.dataManager.getBalance(player.uniqueId)
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f)
            player.sendMessage("§c냥이 부족합니다. (보유: §e${balance}냥 §c/ 필요: §e${cost}냥§c)")
            return
        }

        plugin.dataManager.spendBalance(player.uniqueId, cost)
        plugin.dataManager.save(player.uniqueId)

        // 50% success, 50% fail
        val success = Random.nextBoolean()
        val change = if (success) Random.nextInt(1, 4) else -Random.nextInt(1, 4)
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
