package dev.nyaru.minecraft.gui

import dev.nyaru.minecraft.NyaruPlugin
import dev.nyaru.minecraft.model.TitleRegistry
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack

class TitleGui(private val plugin: NyaruPlugin, private val player: Player) {

    companion object {
        private const val TITLE = "§5§l칭호 선택"
        private val legacy = LegacyComponentSerializer.legacySection()
    }

    fun open() {
        val titles = TitleRegistry.ALL
        val rows = ((titles.size + 1) / 9) + 2 // +1 for "remove" button, extra padding
        val size = (rows.coerceIn(3, 6)) * 9
        val inv = Bukkit.createInventory(null, size, legacy.deserialize(TITLE))

        val earned = plugin.titleManager.getEarnedTitles(player.uniqueId)
        val selected = plugin.titleManager.getSelectedTitle(player.uniqueId)

        // "Remove title" button at slot 0
        val removeItem = ItemStack(Material.BARRIER)
        removeItem.editMeta { meta ->
            meta.displayName(legacy.deserialize("§c§l칭호 해제"))
            meta.lore(listOf(
                legacy.deserialize("§7현재 칭호를 해제합니다.")
            ))
        }
        inv.setItem(0, removeItem)

        // Title items
        for ((index, def) in titles.withIndex()) {
            val slot = index + 1
            if (slot >= size) break

            val isEarned = def.id in earned
            val isSelected = def.id == selected

            val item = if (isEarned) ItemStack(def.icon) else ItemStack(Material.GRAY_DYE)
            item.editMeta { meta ->
                val namePrefix = if (isSelected) "§a\u25B6 " else ""
                meta.displayName(legacy.deserialize("$namePrefix${def.displayName}"))

                val lore = mutableListOf<net.kyori.adventure.text.Component>()
                lore.add(legacy.deserialize(def.description))
                lore.add(legacy.deserialize("§r"))
                if (isSelected) {
                    lore.add(legacy.deserialize("§a\u2714 현재 선택됨"))
                } else if (isEarned) {
                    lore.add(legacy.deserialize("§e\u25C6 클릭하여 선택"))
                } else {
                    lore.add(legacy.deserialize("§c\u2716 미획득"))
                }
                meta.lore(lore)

                if (isSelected) {
                    meta.setEnchantmentGlintOverride(true)
                }
            }
            inv.setItem(slot, item)
        }

        player.openInventory(inv)
    }

    class TitleGuiListener(private val plugin: NyaruPlugin) : Listener {

        private val legacy = LegacyComponentSerializer.legacySection()

        @EventHandler(ignoreCancelled = true)
        fun onClick(event: InventoryClickEvent) {
            val title = event.view.title()
            val expected = legacy.deserialize(TITLE)
            if (title != expected) return

            event.isCancelled = true
            val player = event.whoClicked as? Player ?: return
            val slot = event.rawSlot
            if (slot < 0) return

            val uuid = player.uniqueId

            // Slot 0 = remove title
            if (slot == 0) {
                plugin.titleManager.selectTitle(uuid, null)
                player.sendMessage("§c칭호를 해제했습니다.")
                player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.5f, 1.0f)
                player.closeInventory()
                return
            }

            val titleIndex = slot - 1
            val titles = TitleRegistry.ALL
            if (titleIndex < 0 || titleIndex >= titles.size) return

            val def = titles[titleIndex]
            val earned = plugin.titleManager.getEarnedTitles(uuid)

            if (def.id !in earned) {
                player.sendMessage("§c아직 획득하지 않은 칭호입니다.")
                player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f)
                return
            }

            if (plugin.titleManager.selectTitle(uuid, def.id)) {
                player.sendMessage(legacy.deserialize("§a칭호를 §r${def.displayName}§a(으)로 변경했습니다!"))
                player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.5f)
                player.closeInventory()
            }
        }
    }
}
