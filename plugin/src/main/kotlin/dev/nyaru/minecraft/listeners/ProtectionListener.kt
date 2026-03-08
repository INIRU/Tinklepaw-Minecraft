package dev.nyaru.minecraft.listeners

import dev.nyaru.minecraft.NyaruPlugin
import dev.nyaru.minecraft.protection.ProtectionManager
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.data.Bisected
import org.bukkit.block.data.type.Door
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.block.BlockPhysicsEvent
import org.bukkit.event.block.BlockPistonExtendEvent
import org.bukkit.event.block.BlockPistonRetractEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.player.PlayerInteractEvent

private val CONTAINER_MATERIALS = setOf(
    Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL,
    Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER,
    Material.HOPPER, Material.DROPPER, Material.DISPENSER,
    Material.BREWING_STAND,
    Material.SHULKER_BOX,
    Material.WHITE_SHULKER_BOX, Material.ORANGE_SHULKER_BOX,
    Material.MAGENTA_SHULKER_BOX, Material.LIGHT_BLUE_SHULKER_BOX,
    Material.YELLOW_SHULKER_BOX, Material.LIME_SHULKER_BOX,
    Material.PINK_SHULKER_BOX, Material.GRAY_SHULKER_BOX,
    Material.LIGHT_GRAY_SHULKER_BOX, Material.CYAN_SHULKER_BOX,
    Material.PURPLE_SHULKER_BOX, Material.BLUE_SHULKER_BOX,
    Material.BROWN_SHULKER_BOX, Material.GREEN_SHULKER_BOX,
    Material.RED_SHULKER_BOX, Material.BLACK_SHULKER_BOX
)

private val DOOR_MATERIALS = setOf(
    Material.OAK_DOOR, Material.SPRUCE_DOOR, Material.BIRCH_DOOR,
    Material.JUNGLE_DOOR, Material.ACACIA_DOOR, Material.DARK_OAK_DOOR,
    Material.MANGROVE_DOOR, Material.CHERRY_DOOR, Material.BAMBOO_DOOR,
    Material.IRON_DOOR,
    Material.OAK_TRAPDOOR, Material.SPRUCE_TRAPDOOR, Material.BIRCH_TRAPDOOR,
    Material.JUNGLE_TRAPDOOR, Material.ACACIA_TRAPDOOR, Material.DARK_OAK_TRAPDOOR,
    Material.MANGROVE_TRAPDOOR, Material.CHERRY_TRAPDOOR, Material.BAMBOO_TRAPDOOR,
    Material.IRON_TRAPDOOR,
    Material.OAK_FENCE_GATE, Material.SPRUCE_FENCE_GATE, Material.BIRCH_FENCE_GATE,
    Material.JUNGLE_FENCE_GATE, Material.ACACIA_FENCE_GATE, Material.DARK_OAK_FENCE_GATE,
    Material.MANGROVE_FENCE_GATE, Material.CHERRY_FENCE_GATE, Material.BAMBOO_FENCE_GATE
)

/** For two-tall doors: always check the bottom half's protection. */
private fun canonicalLocation(block: Block): Location {
    val data = block.blockData
    if (block.type in DOOR_MATERIALS && data is Door && data.half == Bisected.Half.TOP) {
        return block.location.subtract(0.0, 1.0, 0.0)
    }
    return block.location
}

class ProtectionListener(
    private val plugin: NyaruPlugin,
    private val pm: ProtectionManager
) : Listener {

    private val legacy = LegacyComponentSerializer.legacySection()
    private val deny get() = legacy.deserialize("§c🔒 보호된 블럭입니다.")

    // ── Place: protect only if player has protection mode ON ────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlace(event: BlockPlaceEvent) {
        val uuid = event.player.uniqueId.toString()
        if (!pm.isProtectionEnabled(uuid)) return
        pm.protect(event.block.location, uuid)
        // For tall doors, also protect the upper half
        if (event.block.type in DOOR_MATERIALS) {
            pm.protect(event.block.location.clone().add(0.0, 1.0, 0.0), uuid)
        }
    }

    // ── Break: check permission, remove protection on owner break ────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBreak(event: BlockBreakEvent) {
        val loc = event.block.location
        if (!pm.isProtected(loc)) return
        val uuid = event.player.uniqueId.toString()
        if (pm.canAccess(loc, uuid)) {
            // Owner / team member breaking — remove protection entry
            pm.unprotect(loc, pm.getOwner(loc)!!)
        } else {
            event.isCancelled = true
            event.player.sendActionBar(deny)
        }
    }

    // ── Explosions: remove protected blocks from blast list ──────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBlockExplode(event: BlockExplodeEvent) {
        event.blockList().removeIf { pm.isProtected(it.location) }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onEntityExplode(event: EntityExplodeEvent) {
        event.blockList().removeIf { pm.isProtected(it.location) }
    }

    // ── Pistons: cancel if any block in the move list is protected ───────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onPistonExtend(event: BlockPistonExtendEvent) {
        if (event.blocks.any { pm.isProtected(it.location) }) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onPistonRetract(event: BlockPistonRetractEvent) {
        if (event.blocks.any { pm.isProtected(it.location) }) event.isCancelled = true
    }

    // ── Containers & doors: check permission on interact ────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onInteract(event: PlayerInteractEvent) {
        val block = event.clickedBlock ?: return
        if (block.type !in CONTAINER_MATERIALS && block.type !in DOOR_MATERIALS) return
        val loc = canonicalLocation(block)
        if (!pm.isProtected(loc)) return
        val uuid = event.player.uniqueId.toString()
        if (!pm.canAccess(loc, uuid)) {
            event.isCancelled = true
            event.player.sendActionBar(deny)
        }
    }

    // ── Physics: clean up protection when block naturally disappears ─────────
    // (e.g. sand/gravel fall, crop withers, etc.)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPhysics(event: BlockPhysicsEvent) {
        val block = event.block
        // Only care if the block is about to disappear (becomes AIR)
        if (block.type == Material.AIR) {
            pm.unprotect(block.location, pm.getOwner(block.location) ?: return)
        }
    }
}
