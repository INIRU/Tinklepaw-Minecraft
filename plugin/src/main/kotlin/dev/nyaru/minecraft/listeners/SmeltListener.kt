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

        // Copy purity tier to the result item
        val result = event.result.clone()
        result.editMeta { meta ->
            meta.persistentDataContainer.set(PURITY_KEY, PersistentDataType.INTEGER, purity)

            val (tierName, tierColor) = when (purity) {
                4 -> "높은" to "§d"
                3 -> "중간" to "§b"
                2 -> "낮음" to "§a"
                else -> "매우 낮음" to "§7"
            }
            meta.lore(listOf(
                legacy.deserialize("${tierColor}✦ 순도: ${tierName}"),
                legacy.deserialize("§7상점에서 높은 가격을 받습니다.")
            ))
        }
        event.result = result
    }
}
