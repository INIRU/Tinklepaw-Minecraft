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
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class MinionManager(private val plugin: NyaruPlugin) {

    private val minionMap = ConcurrentHashMap<UUID, MutableList<Mob>>()

    val ownerKey = NamespacedKey("nyaru", "minion_owner")
    val spawnTimeKey = NamespacedKey("nyaru", "minion_spawn_time")

    fun getMinions(uuid: UUID): List<Mob> = minionMap[uuid] ?: emptyList()

    fun getMaxZombies(summonUndeadLevel: Int): Int = summonUndeadLevel.coerceAtLeast(0)

    fun getMaxSkeletons(skeletonArcherLevel: Int): Int = skeletonArcherLevel.coerceAtLeast(0)

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

    fun summonSkeleton(player: Player, count: Int, soulEmpowerLevel: Int) {
        repeat(count) {
            val loc = safeSpawnLocation(player.location)
            val skeleton = player.world.spawn(loc, Skeleton::class.java)
            setupMinion(skeleton, player, soulEmpowerLevel)
            skeleton.equipment?.setItemInMainHand(ItemStack(Material.BOW))
            skeleton.equipment?.helmet = ItemStack(Material.IRON_HELMET)
            skeleton.customName(
                net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                    .deserialize("§5[미니언] §f${player.name}의 스켈레톤")
            )
            minionMap.getOrPut(player.uniqueId) { mutableListOf() }.add(skeleton)
        }
    }

    private fun setupMinion(mob: Mob, owner: Player, soulEmpowerLevel: Int) {
        mob.persistentDataContainer.set(ownerKey, PersistentDataType.STRING, owner.uniqueId.toString())
        mob.persistentDataContainer.set(spawnTimeKey, PersistentDataType.LONG, System.currentTimeMillis())
        mob.isCustomNameVisible = true
        mob.removeWhenFarAway = false
        mob.isPersistent = false

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

                    // Find nearest hostile mob within 12 blocks of owner
                    val target = ownerLoc.world?.getNearbyEntities(ownerLoc, 12.0, 12.0, 12.0)
                        ?.filterIsInstance<Monster>()
                        ?.filter { it.uniqueId != uuid && !isMinionOf(it, uuid) }
                        ?.minByOrNull { it.location.distanceSquared(ownerLoc) }

                    if (target != null) {
                        if (mob is Creature) {
                            mob.target = target as? LivingEntity
                        }
                    } else {
                        // Pathfind toward owner
                        if (mobLoc.world == ownerLoc.world && mobLoc.distance(ownerLoc) > 4.0) {
                            mob.pathfinder.moveTo(ownerLoc)
                        }
                    }
                }
            }
        }, 20L, 20L)
    }

    private fun isMinionOf(entity: Mob, ownerUuid: UUID): Boolean {
        val storedOwner = entity.persistentDataContainer.get(ownerKey, PersistentDataType.STRING) ?: return false
        return storedOwner == ownerUuid.toString()
    }

    private fun safeSpawnLocation(base: Location): Location {
        val offset = (Math.random() * 2.0 - 1.0)
        val offsetZ = (Math.random() * 2.0 - 1.0)
        return base.clone().add(offset, 0.0, offsetZ)
    }
}
