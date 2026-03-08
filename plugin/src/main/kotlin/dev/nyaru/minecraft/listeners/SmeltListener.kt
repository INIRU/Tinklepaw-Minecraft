package dev.nyaru.minecraft.listeners

import dev.nyaru.minecraft.NyaruPlugin
import dev.nyaru.minecraft.skills.SkillManager
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.FurnaceSmeltEvent
import org.bukkit.persistence.PersistentDataType

class SmeltListener(private val plugin: NyaruPlugin, private val skillManager: SkillManager) : Listener {

    private val legacy = LegacyComponentSerializer.legacySection()

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onFurnaceSmelt(event: FurnaceSmeltEvent) {
        val source = event.source
        val sourceMeta = source.itemMeta ?: return

        val purity = sourceMeta.persistentDataContainer.get(PURITY_KEY, PersistentDataType.INTEGER)
            ?: return

        // Copy purity to the result item
        val result = event.result.clone()
        result.editMeta { meta ->
            meta.persistentDataContainer.set(PURITY_KEY, PersistentDataType.INTEGER, purity)

            val purityColor = when {
                purity >= 90 -> "§d"
                purity >= 75 -> "§b"
                purity >= 60 -> "§a"
                else -> "§7"
            }
            meta.lore(listOf(
                legacy.deserialize("${purityColor}✦ 순도: §f${purity}%"),
                legacy.deserialize("§7상점에서 높은 가격을 받습니다."),
                legacy.deserialize("§7Y좌표가 낮을수록 순도가 높아집니다.")
            ))
        }
        event.result = result
    }
}
