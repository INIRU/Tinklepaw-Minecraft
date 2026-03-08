package dev.nyaru.minecraft.listeners

import dev.nyaru.minecraft.logging.BlockAction
import dev.nyaru.minecraft.logging.BlockLogger
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent

class BlockLogListener(private val logger: BlockLogger) : Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlace(event: BlockPlaceEvent) {
        logger.log(event.player, BlockAction.PLACE, event.block.location, event.block.type.name)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBreak(event: BlockBreakEvent) {
        logger.log(event.player, BlockAction.BREAK, event.block.location, event.block.type.name)
    }
}
