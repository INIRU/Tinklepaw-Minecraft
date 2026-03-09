package dev.nyaru.minecraft.skills

import dev.nyaru.minecraft.NyaruPlugin
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Creature
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mob
import org.bukkit.entity.Monster
import org.bukkit.entity.Player
import org.bukkit.entity.Skeleton
import org.bukkit.entity.Zombie
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class MinionManager(private val plugin: NyaruPlugin) {

    private val minionMap = ConcurrentHashMap<UUID, MutableList<Mob>>()

    val ownerKey = NamespacedKey("nyaru", "minion_owner")
    val spawnTimeKey = NamespacedKey("nyaru", "minion_spawn_time")

    fun getMinions(uuid: UUID): List<Mob> = minionMap[uuid] ?: emptyList()

    fun getMaxZombies(summonUndeadLevel: Int): Int = summonUndeadLevel.coerceAtLeast(0)

    fun getMaxMinions(summonUndeadLevel: Int, skeletonArcherLevel: Int): Int =
        summonUndeadLevel.coerceAtLeast(0) + skeletonArcherLevel.coerceAtLeast(0)

    fun summonZombie(player: Player, count: Int, soulEmpowerLevel: Int) {
        repeat(count) {
            val loc = safeSpawnLocation(player.location)
            val zombie = player.world.spawn(loc, Zombie::class.java)
            setupMinion(zombie, player, soulEmpowerLevel)
            zombie.equipment?.setItemInMainHand(ItemStack(Material.IRON_SWORD))
            zombie.equipment?.helmet = ItemStack(Material.IRON_HELMET)
            zombie.customName(
                net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                    .deserialize("§5[미니언] §f${player.name}의 좀비")
            )
            minionMap.getOrPut(player.uniqueId) { mutableListOf() }.add(zombie)
        }
    }

    private fun setupMinion(mob: Mob, owner: Player, soulEmpowerLevel: Int) {
        mob.persistentDataContainer.set(ownerKey, PersistentDataType.STRING, owner.uniqueId.toString())
        mob.persistentDataContainer.set(spawnTimeKey, PersistentDataType.LONG, System.currentTimeMillis())
        mob.isCustomNameVisible = true
        mob.removeWhenFarAway = false
        mob.isPersistent = false
        mob.isSilent = true

        // Invisible body, armor still visible
        mob.addPotionEffect(PotionEffect(
            PotionEffectType.INVISIBILITY,
            Integer.MAX_VALUE,
            0,
            false, false, false
        ))

        // Fast movement speed (base 0.4, soul empower scales up to 0.5)
        val speedMultiplier = when (soulEmpowerLevel) {
            1 -> 1.1
            2 -> 1.25
            3 -> 1.5
            else -> 1.0
        }
        mob.getAttribute(Attribute.MOVEMENT_SPEED)?.baseValue = (0.4 * speedMultiplier).coerceAtMost(0.6)

        // Apply soul empower bonuses
        val hpMultiplier = when (soulEmpowerLevel) {
            1 -> 1.25
            2 -> 1.50
            3 -> 2.00
            else -> 1.0
        }
        val dmgMultiplier = when (soulEmpowerLevel) {
            1 -> 1.25
            2 -> 1.50
            3 -> 2.00
            else -> 1.0
        }

        val maxHp = 20.0 * hpMultiplier
        mob.getAttribute(Attribute.MAX_HEALTH)?.baseValue = maxHp
        mob.health = maxHp

        val dmgAttr = mob.getAttribute(Attribute.ATTACK_DAMAGE)
        if (dmgAttr != null) {
            dmgAttr.baseValue = 2.0 * dmgMultiplier
        }
    }

    fun summonControlled(player: Player, entityType: EntityType, deathLoc: Location, mindControlLevel: Int, soulEmpowerLevel: Int) {
        val mob = deathLoc.world?.spawn(deathLoc, entityType.entityClass as Class<out Mob>) ?: return
        mob.persistentDataContainer.set(ownerKey, PersistentDataType.STRING, player.uniqueId.toString())
        mob.persistentDataContainer.set(spawnTimeKey, PersistentDataType.LONG, System.currentTimeMillis())
        mob.isCustomNameVisible = true
        mob.removeWhenFarAway = false
        mob.isPersistent = false
        mob.isSilent = true
        mob.customName(
            net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                .deserialize("§5[지배] §f${player.name}의 하수인")
        )

        // Very low HP — dies in ~1 hit (2/3/4)
        val hp = (1.0 + mindControlLevel).coerceAtMost(4.0)
        mob.getAttribute(Attribute.MAX_HEALTH)?.baseValue = hp
        mob.health = hp

        // Moderate attack damage (3/4/5)
        mob.getAttribute(Attribute.ATTACK_DAMAGE)?.let { it.baseValue = 2.0 + mindControlLevel }

        // Speed
        val speedMultiplier = when (soulEmpowerLevel) {
            1 -> 1.1; 2 -> 1.25; 3 -> 1.5; else -> 1.0
        }
        mob.getAttribute(Attribute.MOVEMENT_SPEED)?.baseValue = (0.4 * speedMultiplier).coerceAtMost(0.6)

        // Short-lived: override spawn time so it expires in 30 seconds
        mob.persistentDataContainer.set(spawnTimeKey, PersistentDataType.LONG,
            System.currentTimeMillis() - 270_000L) // 300s - 30s = already 270s old

        minionMap.getOrPut(player.uniqueId) { mutableListOf() }.add(mob)
    }

    fun removeMinions(uuid: UUID) {
        val minions = minionMap.remove(uuid) ?: return
        for (mob in minions) {
            if (!mob.isDead) mob.remove()
        }
    }

    fun startMinionTask() {
        plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            val expireMs = 300_000L // 5 minutes
            val now = System.currentTimeMillis()

            for ((uuid, minions) in minionMap) {
                val owner = plugin.server.getPlayer(uuid)

                // Remove dead or expired minions
                minions.removeIf { mob ->
                    if (mob.isDead) return@removeIf true
                    val spawnTime = mob.persistentDataContainer.get(spawnTimeKey, PersistentDataType.LONG) ?: 0L
                    if (now - spawnTime >= expireMs) {
                        mob.remove()
                        return@removeIf true
                    }
                    false
                }

                if (owner == null || !owner.isOnline) continue

                for (mob in minions) {
                    if (mob.isDead) continue

                    val ownerLoc = owner.location
                    val mobLoc = mob.location

                    // Teleport if too far from owner
                    if (mobLoc.world == ownerLoc.world && mobLoc.distance(ownerLoc) > 20.0) {
                        mob.teleport(safeSpawnLocation(ownerLoc))
                        continue
                    }

                    // Clear natural AI target if it's a minion or player
                    if (mob is Creature) {
                        val currentTarget = mob.target
                        if (currentTarget != null && (currentTarget is Player || isAnyMinion(currentTarget))) {
                            mob.target = null
                        }
                    }

                    // Only auto-target hostile mobs (Monster), not passive mobs or other minions
                    val target = ownerLoc.world?.getNearbyEntities(ownerLoc, 12.0, 12.0, 12.0)
                        ?.filterIsInstance<Monster>()
                        ?.filter { !isAnyMinion(it) }
                        ?.minByOrNull { it.location.distanceSquared(ownerLoc) }

                    if (target != null) {
                        if (mob is Creature) {
                            mob.target = target as? LivingEntity
                        }
                    } else {
                        // No target — clear any stale target and pathfind toward owner
                        if (mob is Creature) mob.target = null
                        if (mobLoc.world == ownerLoc.world && mobLoc.distance(ownerLoc) > 4.0) {
                            mob.pathfinder.moveTo(ownerLoc)
                        }
                    }
                }
            }
        }, 20L, 20L)
    }

    fun isAnyMinion(entity: org.bukkit.entity.Entity): Boolean {
        if (entity !is Mob) return false
        return entity.persistentDataContainer.has(ownerKey, PersistentDataType.STRING)
    }

    fun isMinionOf(entity: org.bukkit.entity.Entity, ownerUuid: UUID): Boolean {
        if (entity !is Mob) return false
        val storedOwner = entity.persistentDataContainer.get(ownerKey, PersistentDataType.STRING) ?: return false
        return storedOwner == ownerUuid.toString()
    }

    private fun safeSpawnLocation(base: Location): Location {
        val offset = (Math.random() * 2.0 - 1.0)
        val offsetZ = (Math.random() * 2.0 - 1.0)
        return base.clone().add(offset, 0.0, offsetZ)
    }
}
