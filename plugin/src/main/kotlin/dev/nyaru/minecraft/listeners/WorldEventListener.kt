package dev.nyaru.minecraft.listeners

import org.bukkit.GameRule
import org.bukkit.entity.EntityType
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntitySpawnEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.Plugin

class WorldEventListener(private val plugin: Plugin) : Listener {

    @EventHandler
    fun onEntitySpawn(event: EntitySpawnEvent) {
        if (event.entityType == EntityType.PHANTOM) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        if (plugin.server.onlinePlayers.size == 1) {
            setWorldPaused(false)
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            if (plugin.server.onlinePlayers.isEmpty()) {
                setWorldPaused(true)
            }
        }, 1L)
    }

    private fun setWorldPaused(paused: Boolean) {
        for (world in plugin.server.worlds) {
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, !paused)
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, !paused)
            world.setGameRule(GameRule.RANDOM_TICK_SPEED, if (paused) 0 else 3)
        }
        if (paused) plugin.logger.info("No players online — world time frozen")
        else plugin.logger.info("Player joined — world time resumed")
    }

    fun onEnable() {
        if (plugin.server.onlinePlayers.isEmpty()) {
            setWorldPaused(true)
        }
    }
}
