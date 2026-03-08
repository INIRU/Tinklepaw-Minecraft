package dev.nyaru.minecraft.npc

import de.oliver.fancynpcs.api.FancyNpcsPlugin
import de.oliver.fancynpcs.api.NpcData
import de.oliver.fancynpcs.api.utils.NpcEquipmentSlot
import de.oliver.fancynpcs.api.events.NpcInteractEvent
import dev.nyaru.minecraft.NyaruPlugin
import dev.nyaru.minecraft.gui.EnhanceGui
import dev.nyaru.minecraft.gui.JobSelectGui
import dev.nyaru.minecraft.gui.ShopGui
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.inventory.ItemStack
import java.util.UUID

class FancyNpcsService(private val plugin: NyaruPlugin) : Listener {

    fun createNpc(location: Location, type: NpcType) {
        val id = "nyaru_${type.name.lowercase()}_${System.currentTimeMillis()}"
        val displayName = when (type) {
            NpcType.SHOP -> "<gold><bold>[상점]</bold></gold>"
            NpcType.JOB -> "<green><bold>[직업 변경]</bold></green>"
            NpcType.ENHANCE -> "<light_purple><bold>[강화]</bold></light_purple>"
        }
        val data = NpcData(id, UUID.randomUUID(), location)
        data.setDisplayName(displayName)
        data.setTurnToPlayer(true)
        data.setTurnToPlayerDistance(8)

        val heldItem = when (type) {
            NpcType.SHOP -> ItemStack(Material.EMERALD)
            NpcType.JOB -> ItemStack(Material.BOOK)
            NpcType.ENHANCE -> ItemStack(Material.ANVIL)
        }
        data.addEquipment(NpcEquipmentSlot.MAINHAND, heldItem)

        val npc = FancyNpcsPlugin.get().npcAdapter.apply(data)
        FancyNpcsPlugin.get().npcManager.registerNpc(npc)
        npc.create()
        npc.spawnForAll()
    }

    @EventHandler
    fun onNpcInteract(event: NpcInteractEvent) {
        val name = event.npc.data.name
        val player = event.player
        when {
            name.startsWith("nyaru_shop") -> plugin.server.scheduler.runTask(plugin, Runnable {
                ShopGui(plugin, player).open()
            })
            name.startsWith("nyaru_job") -> plugin.server.scheduler.runTask(plugin, Runnable {
                JobSelectGui(plugin, player).open()
            })
            name.startsWith("nyaru_enhance") -> plugin.server.scheduler.runTask(plugin, Runnable {
                EnhanceGui(plugin, player).open()
            })
        }
    }
}
