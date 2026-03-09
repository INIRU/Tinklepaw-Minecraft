package dev.nyaru.minecraft.gui

import dev.nyaru.minecraft.NyaruPlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Particle
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
import kotlin.random.Random

private val ENHANCE_LEVEL_KEY = NamespacedKey("nyaru", "enhance_level")
val SOULBOUND_KEY = NamespacedKey("nyaru", "soulbound")

private val ENCHANT_NAMES = mapOf(
    Enchantment.EFFICIENCY to "효율",
    Enchantment.FORTUNE to "행운",
    Enchantment.UNBREAKING to "내구성",
    Enchantment.SILK_TOUCH to "섬세한 손길",
    Enchantment.MENDING to "수선",
    Enchantment.SHARPNESS to "날카로움",
    Enchantment.SMITE to "강타",
    Enchantment.FIRE_ASPECT to "발화",
    Enchantment.KNOCKBACK to "밀치기",
    Enchantment.SWEEPING_EDGE to "휩쓸기",
    Enchantment.LOOTING to "약탈",
    Enchantment.DENSITY to "밀도",
    Enchantment.BREACH to "관통",
    Enchantment.WIND_BURST to "바람 폭발"
)

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

        fun getApplicableEnchantments(mat: Material): List<Enchantment> = when {
            isPickaxe(mat) -> listOf(
                Enchantment.EFFICIENCY, Enchantment.FORTUNE, Enchantment.UNBREAKING,
                Enchantment.SILK_TOUCH, Enchantment.MENDING
            )
            isSword(mat) -> listOf(
                Enchantment.SHARPNESS, Enchantment.SMITE, Enchantment.FIRE_ASPECT,
                Enchantment.KNOCKBACK, Enchantment.SWEEPING_EDGE, Enchantment.LOOTING,
                Enchantment.UNBREAKING, Enchantment.MENDING
            )
            isAxe(mat) -> listOf(
                Enchantment.EFFICIENCY, Enchantment.SHARPNESS, Enchantment.SMITE,
                Enchantment.UNBREAKING, Enchantment.FORTUNE, Enchantment.MENDING
            )
            isHoe(mat) -> listOf(
                Enchantment.EFFICIENCY, Enchantment.FORTUNE, Enchantment.UNBREAKING,
                Enchantment.SILK_TOUCH, Enchantment.MENDING
            )
            isShovel(mat) -> listOf(
                Enchantment.EFFICIENCY, Enchantment.FORTUNE, Enchantment.UNBREAKING,
                Enchantment.SILK_TOUCH, Enchantment.MENDING
            )
            isMace(mat) -> listOf(
                Enchantment.DENSITY, Enchantment.BREACH, Enchantment.UNBREAKING,
                Enchantment.WIND_BURST, Enchantment.MENDING
            )
            else -> emptyList()
        }

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

        inventory.setItem(TOOL_SLOT, null)

        val info = ItemStack(Material.BOOK)
        info.editMeta { meta ->
            meta.displayName(legacy.deserialize("§e강화 안내"))
            meta.lore(listOf(
                legacy.deserialize("§7도구를 슬롯에 올려두세요."),
                legacy.deserialize("§7강화 버튼을 클릭하면 강화가 진행됩니다."),
                Component.empty(),
                legacy.deserialize("§a성공(50%):"),
                legacy.deserialize("§7  랜덤 인첸트 §f1~3개§7에 §a+1~3 레벨"),
                legacy.deserialize("§c실패(50%):"),
                legacy.deserialize("§7  기존 인첸트 §f1~3개§7에서 §c-0~3 레벨"),
                Component.empty(),
                legacy.deserialize("§e✦ 매번 다른 인첸트가 붙어 나만의 무기!"),
                legacy.deserialize("§7강화 비용: §e(현재 강화 + 1) × 500냥"),
                legacy.deserialize("§7최대 강화: §f+10")
            ))
        }
        inventory.setItem(INFO_SLOT, info)

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
                    Component.empty(),
                    legacy.deserialize("§a성공: §f랜덤 인첸트 1~3개 +1~3"),
                    legacy.deserialize("§c실패: §f기존 인첸트 1~3개 -0~3")
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

        meta.persistentDataContainer.set(SOULBOUND_KEY, PersistentDataType.BYTE, 1)

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

        if (slot >= INV_SIZE) {
            Bukkit.getScheduler().runTask(plugin, Runnable { updateEnhanceButton(); updateSoulboundButton() })
            return
        }

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

        val success = Random.nextBoolean()
        val mat = toolItem.type
        val applicable = getApplicableEnchantments(mat)
        val changes = mutableListOf<String>()

        if (success) {
            // Pick 1-3 random enchantments from pool, add +1~3 levels each
            val count = Random.nextInt(1, 4)
            val picked = applicable.shuffled().take(count)
            for (ench in picked) {
                val addLevels = Random.nextInt(1, 4)
                val currentEnchLevel = meta.getEnchantLevel(ench)
                val newEnchLevel = currentEnchLevel + addLevels
                meta.addEnchant(ench, newEnchLevel, true)
                val name = ENCHANT_NAMES[ench] ?: ench.key.key
                changes.add("§a  ${name} §f${currentEnchLevel} → ${newEnchLevel} §a(+${addLevels})")
            }
            val newLevel = (currentLevel + 1).coerceAtMost(10)
            meta.persistentDataContainer.set(ENHANCE_LEVEL_KEY, PersistentDataType.INTEGER, newLevel)
            updateDisplayName(meta, mat, newLevel)
            updateEnhanceLore(meta, newLevel)
            toolItem.itemMeta = meta
            inventory.setItem(TOOL_SLOT, toolItem)
            updateEnhanceButton()

            player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f)
            player.world.spawnParticle(
                Particle.ENCHANT,
                player.location.add(0.0, 1.5, 0.0),
                30, 0.5, 0.5, 0.5, 1.0
            )
            player.sendMessage("§a§l⚒ 강화 성공! §f+${currentLevel} §a→ §f+${newLevel}")
            for (line in changes) player.sendMessage(line)
        } else {
            // Pick 1-3 random existing enchantments on the item, reduce by 0~3 levels each
            val existingEnchants = meta.enchants.keys.toList()
            val newLevel = (currentLevel - 1).coerceAtLeast(0)

            if (existingEnchants.isNotEmpty()) {
                val count = Random.nextInt(1, 4).coerceAtMost(existingEnchants.size)
                val picked = existingEnchants.shuffled().take(count)
                for (ench in picked) {
                    val reduceLevels = Random.nextInt(0, 4)
                    val currentEnchLevel = meta.getEnchantLevel(ench)
                    val newEnchLevel = currentEnchLevel - reduceLevels
                    val name = ENCHANT_NAMES[ench] ?: ench.key.key
                    if (newEnchLevel <= 0) {
                        meta.removeEnchant(ench)
                        changes.add("§c  ${name} §f${currentEnchLevel} → 0 §c(-${currentEnchLevel}, 제거됨)")
                    } else {
                        meta.addEnchant(ench, newEnchLevel, true)
                        changes.add("§c  ${name} §f${currentEnchLevel} → ${newEnchLevel} §c(-${reduceLevels})")
                    }
                }
            }

            meta.persistentDataContainer.set(ENHANCE_LEVEL_KEY, PersistentDataType.INTEGER, newLevel)
            updateDisplayName(meta, mat, newLevel)
            updateEnhanceLore(meta, newLevel)
            toolItem.itemMeta = meta
            inventory.setItem(TOOL_SLOT, toolItem)
            updateEnhanceButton()

            player.playSound(player.location, Sound.ENTITY_ITEM_BREAK, 1.0f, 0.8f)
            player.sendMessage("§c§l⚒ 강화 실패! §f+${currentLevel} §c→ §f+${newLevel}")
            for (line in changes) player.sendMessage(line)
        }
    }

    private fun updateDisplayName(meta: org.bukkit.inventory.meta.ItemMeta, mat: Material, newLevel: Int) {
        val originalName = if (meta.hasDisplayName()) {
            val raw = legacy.serialize(meta.displayName()!!)
            val prefixRegex = Regex("§.[§l]*\\[\\+\\d+] ")
            prefixRegex.replace(raw, "")
        } else {
            mat.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
        }

        if (newLevel > 0) {
            val levelColor = enhanceLevelColor(newLevel)
            meta.displayName(legacy.deserialize("${levelColor}[+${newLevel}] §f${originalName}"))
        } else {
            meta.displayName(legacy.deserialize(originalName))
        }
    }

    private fun updateEnhanceLore(meta: org.bukkit.inventory.meta.ItemMeta, newLevel: Int) {
        val existingLore = meta.lore()?.toMutableList() ?: mutableListOf()
        val enhanceLinePattern = "✦ 강화:"
        val levelColor = enhanceLevelColor(newLevel)
        val enhanceLine = legacy.deserialize("${levelColor}✦ 강화: §f+${newLevel}")

        val enhanceIdx = existingLore.indexOfFirst { line ->
            legacy.serialize(line).contains(enhanceLinePattern)
        }
        if (newLevel > 0) {
            if (enhanceIdx >= 0) {
                existingLore[enhanceIdx] = enhanceLine
            } else {
                existingLore.add(0, enhanceLine)
            }
        } else {
            if (enhanceIdx >= 0) {
                existingLore.removeAt(enhanceIdx)
            }
        }
        meta.lore(existingLore)
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
            val tool = gui.inventory.getItem(EnhanceGui.TOOL_SLOT)
            if (tool != null && tool.type != Material.AIR) {
                gui.inventory.setItem(EnhanceGui.TOOL_SLOT, null)
                val overflow = gui.player.inventory.addItem(tool)
                if (overflow.isNotEmpty()) {
                    for (item in overflow.values) {
                        gui.player.world.dropItemNaturally(gui.player.location, item)
                    }
                }
            }
        }
    }
}
