package dev.nyaru.minecraft.listeners

import dev.nyaru.minecraft.gui.SOULBOUND_KEY
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent

class SoulboundListener : Listener {

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.entity
        val soulboundItems = event.drops.filter { item ->
            item.itemMeta?.persistentDataContainer?.has(SOULBOUND_KEY) == true
        }

        if (soulboundItems.isEmpty()) return

        // Remove soulbound items from drops
        event.drops.removeAll(soulboundItems.toSet())

        // Give them back after respawn
        val itemsCopy = soulboundItems.map { it.clone() }
        player.server.scheduler.runTaskLater(player.server.pluginManager.getPlugin("NyaruPlugin")!!, Runnable {
            for (item in itemsCopy) {
                val overflow = player.inventory.addItem(item)
                for (drop in overflow.values) {
                    player.world.dropItemNaturally(player.location, drop)
                }
            }
            player.sendMessage("§d✦ 소울바운드 아이템이 복구되었습니다!")
        }, 5L)
    }
}
