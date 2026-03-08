package dev.nyaru.minecraft.listeners

import dev.nyaru.minecraft.NyaruPlugin
import dev.nyaru.minecraft.model.Jobs
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mob
import org.bukkit.entity.Monster
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.persistence.PersistentDataType
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class NecromancerListener(private val plugin: NyaruPlugin) : Listener {

    private val legacy = LegacyComponentSerializer.legacySection()

    // ownerKey / spawnTimeKey delegated from MinionManager
    private val ownerKey get() = plugin.minionManager.ownerKey

    // Summon cooldown: UUID -> last summon timestamp ms
    private val summonCooldown = ConcurrentHashMap<UUID, Long>()

    // Dark aura cooldown: UUID -> last use timestamp ms
    private val darkAuraCooldown = ConcurrentHashMap<UUID, Long>()

    // ── Summon minions: sneak + right-click empty hand ─────────────────────
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        val uuid = player.uniqueId

        val job = plugin.dataManager.getPlayer(uuid)?.job ?: return
        if (job != Jobs.NECROMANCER) return
        if (!player.isSneaking) return

        val action = event.action
        if (action != org.bukkit.event.block.Action.RIGHT_CLICK_AIR &&
            action != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return

        val heldItem = player.inventory.itemInMainHand
        if (heldItem.type != Material.AIR) return

        val now = System.currentTimeMillis()
        val lastSummon = summonCooldown[uuid] ?: 0L
        if (now - lastSummon < 30_000L) {
            val remaining = ((30_000L - (now - lastSummon)) / 1000L)
            player.sendMessage("§c소환 쿨다운 중입니다. (${remaining}초 남음)")
            return
        }

        val skills = plugin.dataManager.getSkills(uuid)
        val summonUndeadLv = skills.getLevel("summon_undead")
        val skeletonArcherLv = skills.getLevel("skeleton_archer")
        val soulEmpowerLv = skills.getLevel("soul_empower")

        if (summonUndeadLv == 0 && skeletonArcherLv == 0) {
            player.sendMessage("§c언데드 소환 또는 해골 궁수 스킬이 필요합니다.")
            return
        }

        event.isCancelled = true
        summonCooldown[uuid] = now

        val existingMinions = plugin.minionManager.getMinions(uuid)
        val existingZombies = existingMinions.count { it is org.bukkit.entity.Zombie }
        val existingSkeletons = existingMinions.count { it is org.bukkit.entity.Skeleton }

        val maxZombies = plugin.minionManager.getMaxZombies(summonUndeadLv)
        val maxSkeletons = plugin.minionManager.getMaxSkeletons(skeletonArcherLv)

        val zombiesToSummon = (maxZombies - existingZombies).coerceAtLeast(0)
        val skeletonsToSummon = (maxSkeletons - existingSkeletons).coerceAtLeast(0)

        if (zombiesToSummon > 0) {
            plugin.minionManager.summonZombie(player, zombiesToSummon, soulEmpowerLv)
        }
        if (skeletonsToSummon > 0) {
            plugin.minionManager.summonSkeleton(player, skeletonsToSummon, soulEmpowerLv)
        }

        val totalZombies = existingZombies + zombiesToSummon
        val totalSkeletons = existingSkeletons + skeletonsToSummon

        player.playSound(player.location, Sound.ENTITY_WITHER_SPAWN, 0.6f, 1.5f)
        player.world.spawnParticle(
            Particle.SOUL,
            player.location.add(0.0, 1.0, 0.0),
            20, 0.5, 0.5, 0.5, 0.05
        )
        player.sendMessage("§5§l☠ 미니언을 소환했습니다! §7(좀비: ${totalZombies}마리, 스켈레톤: ${totalSkeletons}마리)")
    }

    // ── Life Siphon: minion deals damage → heal owner ──────────────────────
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onMinionDamage(event: EntityDamageByEntityEvent) {
        val damager = event.damager as? Mob ?: return
        val ownerUuidStr = damager.persistentDataContainer.get(ownerKey, PersistentDataType.STRING) ?: return
        val ownerUuid = runCatching { UUID.fromString(ownerUuidStr) }.getOrNull() ?: return

        // Prevent friendly fire: cancel if minion attacks its own owner
        val victim = event.entity
        if (victim is Player && victim.uniqueId == ownerUuid) {
            event.isCancelled = true
            return
        }

        // Prevent minion-on-minion damage (any minion hitting any minion)
        if (plugin.minionManager.isAnyMinion(victim)) {
            event.isCancelled = true
            return
        }

        val owner = plugin.server.getPlayer(ownerUuid) ?: return

        val skills = plugin.dataManager.getSkills(ownerUuid)
        val siphonLv = skills.getLevel("life_siphon")
        if (siphonLv <= 0) return

        val healAmount = event.finalDamage * (siphonLv * 0.05)
        val newHealth = (owner.health + healAmount).coerceAtMost(
            owner.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
        )
        owner.health = newHealth
        owner.world.spawnParticle(
            Particle.HEART,
            owner.location.add(0.0, 1.5, 0.0),
            siphonLv, 0.3, 0.3, 0.3, 0.0
        )
    }

    // ── Dark Aura: necromancer attacks → apply slow+darkness to nearby mobs ─
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onNecromancerAttack(event: EntityDamageByEntityEvent) {
        val damager = event.damager as? Player ?: return
        val uuid = damager.uniqueId

        val job = plugin.dataManager.getPlayer(uuid)?.job ?: return
        if (job != Jobs.NECROMANCER) return

        val skills = plugin.dataManager.getSkills(uuid)
        val darkAuraLv = skills.getLevel("dark_aura")
        if (darkAuraLv <= 0) return

        val now = System.currentTimeMillis()
        val lastUse = darkAuraCooldown[uuid] ?: 0L
        if (now - lastUse < 15_000L) return

        darkAuraCooldown[uuid] = now
        val durationTicks = darkAuraLv * 40 + 20 // 3s/5s/7s

        val nearbyHostiles = damager.world.getNearbyEntities(
            damager.location, 8.0, 8.0, 8.0
        ) { it is Monster }.filterIsInstance<LivingEntity>()

        for (mob in nearbyHostiles) {
            mob.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, durationTicks, 0, false, true, true))
            mob.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, durationTicks, 0, false, true, true))
        }

        damager.playSound(damager.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 0.8f)
        damager.world.spawnParticle(
            Particle.SCULK_SOUL,
            damager.location.add(0.0, 1.0, 0.0),
            20, 2.0, 1.0, 2.0, 0.05
        )
        damager.sendActionBar(legacy.deserialize("§5§l🌑 암흑 오라 발동! §7주변 적에게 슬로우+어둠 적용"))
    }

    // ── Mind Control: necromancer kills a mob → chance to convert it ────────
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onNecromancerKill(event: EntityDeathEvent) {
        val dead = event.entity
        if (dead !is Monster) return
        // Skip minions
        if (plugin.minionManager.isAnyMinion(dead)) return

        val killer = dead.killer ?: return
        val uuid = killer.uniqueId
        val job = plugin.dataManager.getPlayer(uuid)?.job ?: return
        if (job != Jobs.NECROMANCER) return

        val skills = plugin.dataManager.getSkills(uuid)
        val mindControlLv = skills.getLevel("mind_control")
        if (mindControlLv <= 0) return

        val chance = when (mindControlLv) {
            1 -> 0.30; 2 -> 0.50; 3 -> 0.70; else -> 0.0
        }
        if (Math.random() > chance) return

        val soulEmpowerLv = skills.getLevel("soul_empower")
        plugin.server.scheduler.runTask(plugin, Runnable {
            plugin.minionManager.summonControlled(
                killer, dead.type, dead.location, mindControlLv, soulEmpowerLv
            )
            killer.world.spawnParticle(
                org.bukkit.Particle.SOUL,
                dead.location.add(0.0, 1.0, 0.0),
                10, 0.3, 0.3, 0.3, 0.05
            )
            killer.sendActionBar(legacy.deserialize("§5§l🧠 정신지배! §7처치한 몹을 하수인으로 부활시켰습니다."))
        })
    }

    // ── XP grant and drop prevention when minion kills a mob ───────────────
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onEntityDeath(event: EntityDeathEvent) {
        val dead = event.entity
        val ownerUuidStr = dead.persistentDataContainer.get(ownerKey, PersistentDataType.STRING)

        // Dead entity is a minion: clear drops and XP
        if (ownerUuidStr != null) {
            event.drops.clear()
            event.droppedExp = 0
            return
        }

        // Dead entity was killed by a minion
        val killer = dead.killer ?: return
        // killer is a Player — check if they are the minion owner via the damager entity
        // EntityDeathEvent gives us the killer Player, not the minion
        // We need to check the last damager via EntityDamageByEntityEvent stored on the entity
        val lastDamageCause = dead.lastDamageCause as? EntityDamageByEntityEvent ?: return
        val minionDamager = lastDamageCause.damager as? Mob ?: return
        val minionOwnerStr = minionDamager.persistentDataContainer.get(ownerKey, PersistentDataType.STRING) ?: return
        val ownerUuid = runCatching { UUID.fromString(minionOwnerStr) }.getOrNull() ?: return

        val owner = plugin.server.getPlayer(ownerUuid) ?: return

        val result = plugin.dataManager.grantXp(ownerUuid, 8)
        if (result != null) {
            plugin.actionBarManager.updateXp(ownerUuid, result.level, result.xp)
            if (result.leveledUp) {
                dev.nyaru.minecraft.util.triggerLevelUp(plugin, owner, result.level, result.newSkillPoints)
            }
        }
    }

    // ── Cleanup minions on player quit ─────────────────────────────────────
    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        plugin.minionManager.removeMinions(event.player.uniqueId)
    }
}
