package dev.nyaru.minecraft.listeners

import dev.nyaru.minecraft.NyaruPlugin
import dev.nyaru.minecraft.model.Jobs
import dev.nyaru.minecraft.skills.SkillManager
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Item
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerFishEvent
import org.bukkit.inventory.ItemStack

private val TREASURE_POOL = listOf(
    Material.DIAMOND,
    Material.EMERALD,
    Material.NAME_TAG,
    Material.SADDLE,
    Material.NAUTILUS_SHELL,
    Material.HEART_OF_THE_SEA
)

private val TREASURE_NAMES = mapOf(
    Material.DIAMOND to "다이아몬드",
    Material.EMERALD to "에메랄드",
    Material.NAME_TAG to "이름표",
    Material.SADDLE to "안장",
    Material.NAUTILUS_SHELL to "앵무조개 껍데기",
    Material.HEART_OF_THE_SEA to "바다의 심장"
)

class FishingListener(private val plugin: NyaruPlugin, private val skillManager: SkillManager) : Listener {

    private val legacy = LegacyComponentSerializer.legacySection()

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onFish(event: PlayerFishEvent) {
        if (event.state != PlayerFishEvent.State.CAUGHT_FISH) return

        val player = event.player
        val uuid = player.uniqueId

        if (plugin.dataManager.getPlayer(uuid)?.job != Jobs.FISHER) return

        val skills = skillManager.getSkills(uuid)

        // Grant 8 XP for catching a fish
        val result = plugin.dataManager.grantXp(uuid, 8)
        if (result != null) {
            plugin.actionBarManager.updateXp(uuid, result.level, result.xp)
            if (result.leveledUp) {
                dev.nyaru.minecraft.util.triggerLevelUp(plugin, player, result.level, result.newSkillPoints)
            }
        }

        // Treasure Hunter: chance to replace caught item with treasure
        val treasureLv = skills.getLevel("treasure_hunter")
        if (treasureLv > 0) {
            val chance = treasureLv * 0.05
            if (Math.random() < chance) {
                val treasure = TREASURE_POOL.random()
                val caught = event.caught
                if (caught is Item) {
                    caught.itemStack = ItemStack(treasure)
                    val itemName = TREASURE_NAMES[treasure] ?: treasure.name
                    player.sendMessage(legacy.deserialize("§3§l보물 발견! §f$itemName"))
                    player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f)
                }
            }
        }

        // Net Catch: extra fish drop
        val netCatchLv = skills.getLevel("net_catch")
        if (netCatchLv > 0) {
            val netChance = when (netCatchLv) { 1 -> 0.15; 2 -> 0.30; else -> 0.50 }
            if (Math.random() < netChance) {
                val caught = event.caught
                if (caught is Item) {
                    val extraFish = caught.itemStack.clone()
                    extraFish.amount = 1
                    player.world.dropItemNaturally(caught.location, extraFish)
                    player.sendActionBar(legacy.deserialize("§3§l🪤 그물 낚시! §7추가 아이템 획득"))
                }
            }
        }
    }
}
