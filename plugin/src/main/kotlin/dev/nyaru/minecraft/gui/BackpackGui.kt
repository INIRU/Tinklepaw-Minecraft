package dev.nyaru.minecraft.gui

import dev.nyaru.minecraft.NyaruPlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class BackpackGui(private val plugin: NyaruPlugin, private val player: Player) {

    companion object {
        val activeBackpacks = ConcurrentHashMap<Inventory, BackpackGui>()

        const val SMALL_SIZE = 27
        const val LARGE_SIZE = 54
        const val UNLOCK_COST = 10000
        const val UPGRADE_COST = 30000
    }

    private val legacy = LegacyComponentSerializer.legacySection()
    private lateinit var inventory: Inventory
    private var backpackSize: Int = 0

    fun open() {
        val info = getBackpackInfo(player.uniqueId)
        backpackSize = info.size

        if (backpackSize == 0) {
            // Not unlocked — ask to buy
            promptUnlock()
            return
        }

        inventory = Bukkit.createInventory(null, backpackSize,
            legacy.deserialize("§6§l🎒 배낭 §7(${backpackSize}칸)"))

        // Load saved items
        val items = loadItems(player.uniqueId)
        for ((slot, item) in items) {
            if (slot < backpackSize) {
                inventory.setItem(slot, item)
            }
        }

        activeBackpacks[inventory] = this
        player.openInventory(inventory)
        player.playSound(player.location, Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.8f, 1.0f)
    }

    private fun promptUnlock() {
        val balance = plugin.dataManager.getBalance(player.uniqueId)
        if (balance < UNLOCK_COST) {
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f)
            player.sendMessage("§c배낭을 열려면 §e${UNLOCK_COST}냥§c이 필요합니다. (보유: §e${balance}냥§c)")
            return
        }

        plugin.dataManager.spendBalance(player.uniqueId, UNLOCK_COST)
        plugin.actionBarManager.refresh(player.uniqueId)
        setBackpackSize(player.uniqueId, SMALL_SIZE)

        player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f)
        player.sendMessage("§a§l✓ 배낭이 개방되었습니다! §7(27칸, ${UNLOCK_COST}냥 차감)")
        player.sendMessage("§7/배낭 업그레이드 §f로 54칸으로 확장 가능 (${UPGRADE_COST}냥)")

        // Open immediately
        backpackSize = SMALL_SIZE
        inventory = Bukkit.createInventory(null, SMALL_SIZE,
            legacy.deserialize("§6§l🎒 배낭 §7(${SMALL_SIZE}칸)"))
        activeBackpacks[inventory] = this
        player.openInventory(inventory)
    }

    fun upgrade(): Boolean {
        val info = getBackpackInfo(player.uniqueId)
        if (info.size >= LARGE_SIZE) {
            player.sendMessage("§c이미 최대 크기(54칸)입니다.")
            return false
        }
        if (info.size == 0) {
            player.sendMessage("§c배낭을 먼저 구매하세요. §f/배낭")
            return false
        }
        if (!plugin.dataManager.hasBalance(player.uniqueId, UPGRADE_COST)) {
            val balance = plugin.dataManager.getBalance(player.uniqueId)
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f)
            player.sendMessage("§c냥이 부족합니다. (보유: §e${balance}냥 §c/ 필요: §e${UPGRADE_COST}냥§c)")
            return false
        }

        plugin.dataManager.spendBalance(player.uniqueId, UPGRADE_COST)
        plugin.actionBarManager.refresh(player.uniqueId)
        setBackpackSize(player.uniqueId, LARGE_SIZE)

        player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f)
        player.sendMessage("§a§l✓ 배낭이 54칸으로 업그레이드되었습니다! §7(${UPGRADE_COST}냥 차감)")
        return true
    }

    fun saveOnClose() {
        saveItems(player.uniqueId, inventory)
    }

    // ── Persistence ──────────────────────────────────────────────────────

    private data class BackpackInfo(val size: Int)

    private fun backpackDir(): File {
        val dir = File(plugin.dataFolder, "backpacks")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun backpackFile(uuid: UUID): File = File(backpackDir(), "$uuid.yml")

    private fun getBackpackInfo(uuid: UUID): BackpackInfo {
        val file = backpackFile(uuid)
        if (!file.exists()) return BackpackInfo(0)
        val yaml = YamlConfiguration.loadConfiguration(file)
        return BackpackInfo(yaml.getInt("size", 0))
    }

    private fun setBackpackSize(uuid: UUID, size: Int) {
        val file = backpackFile(uuid)
        val yaml = if (file.exists()) YamlConfiguration.loadConfiguration(file) else YamlConfiguration()
        yaml.set("size", size)
        yaml.save(file)
    }

    private fun loadItems(uuid: UUID): Map<Int, ItemStack> {
        val file = backpackFile(uuid)
        if (!file.exists()) return emptyMap()
        val yaml = YamlConfiguration.loadConfiguration(file)
        val items = mutableMapOf<Int, ItemStack>()
        val section = yaml.getConfigurationSection("items") ?: return emptyMap()
        for (key in section.getKeys(false)) {
            val slot = key.toIntOrNull() ?: continue
            val item = section.getItemStack(key) ?: continue
            items[slot] = item
        }
        return items
    }

    private fun saveItems(uuid: UUID, inv: Inventory) {
        val file = backpackFile(uuid)
        val yaml = if (file.exists()) YamlConfiguration.loadConfiguration(file) else YamlConfiguration()
        yaml.set("size", backpackSize)
        yaml.set("items", null) // clear old
        for (i in 0 until inv.size) {
            val item = inv.getItem(i)
            if (item != null && item.type != Material.AIR) {
                yaml.set("items.$i", item)
            }
        }
        yaml.save(file)
    }

    class BackpackGuiListener(private val plugin: NyaruPlugin) : Listener {

        @EventHandler
        fun onInventoryClick(event: InventoryClickEvent) {
            // Backpacks are fully interactive — no need to cancel anything
            // Just track that it's a backpack inventory
            activeBackpacks[event.inventory] ?: return
        }

        @EventHandler
        fun onInventoryClose(event: InventoryCloseEvent) {
            val gui = activeBackpacks.remove(event.inventory) ?: return
            gui.saveOnClose()
        }
    }
}
