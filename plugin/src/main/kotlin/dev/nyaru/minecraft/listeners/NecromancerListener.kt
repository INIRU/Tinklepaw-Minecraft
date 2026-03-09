package dev.nyaru.minecraft.listeners

import dev.nyaru.minecraft.NyaruPlugin
import dev.nyaru.minecraft.model.Jobs
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Creature
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

        // Only handle main hand to prevent double-fire
        if (event.hand != org.bukkit.inventory.EquipmentSlot.HAND) return

        val heldItem = player.inventory.itemInMainHand
        if (heldItem.type != Material.AIR) return

        event.isCancelled = true

        // Toggle: if minions exist → despawn all
        val existingMinions = plugin.minionManager.getMinions(uuid)
        if (existingMinions.isNotEmpty()) {
            plugin.minionManager.removeMinions(uuid)
            player.playSound(player.location, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 0.6f)
            player.world.spawnParticle(
                Particle.SMOKE,
                player.location.add(0.0, 1.0, 0.0),
                15, 0.5, 0.5, 0.5, 0.02
            )
            player.sendMessage("§5§l☠ 미니언을 소환 해제했습니다.")
            return
        }

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
            player.sendMessage("§c언데드 소환 또는 추가 소환 스킬이 필요합니다.")
            return
        }

        summonCooldown[uuid] = now

        val maxMinions = plugin.minionManager.getMaxMinions(summonUndeadLv, skeletonArcherLv)
        plugin.minionManager.summonZombie(player, maxMinions, soulEmpowerLv)

        player.playSound(player.location, Sound.ENTITY_WITHER_SPAWN, 0.6f, 1.5f)
        player.world.spawnParticle(
            Particle.SOUL,
            player.location.add(0.0, 1.0, 0.0),
            20, 0.5, 0.5, 0.5, 0.05
        )
        player.sendMessage("§5§l☠ 미니언을 소환했습니다! §7(좀비: ${maxMinions}마리)")
    }

    // ── Protect minions from player damage ─────────────────────────────────
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onMinionHurt(event: EntityDamageByEntityEvent) {
        val victim = event.entity as? Mob ?: return
        if (!plugin.minionManager.isAnyMinion(victim)) return
        // Cancel all player damage to minions
        if (event.damager is Player) {
            event.isCancelled = true
            return
        }
    }

    // ── Retaliation: when owner is hit, minions target the attacker ────────
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onOwnerDamaged(event: EntityDamageByEntityEvent) {
        val victim = event.entity as? Player ?: return
        val attacker = event.damager as? Mob ?: return
        if (plugin.minionManager.isAnyMinion(attacker)) return

        val uuid = victim.uniqueId
        val job = plugin.dataManager.getPlayer(uuid)?.job ?: return
        if (job != Jobs.NECROMANCER) return

        // Set all minions to target the attacker
        for (minion in plugin.minionManager.getMinions(uuid)) {
            if (minion.isDead) continue
            if (minion is Creature) {
                minion.target = attacker
            }
        }
    }

    // ── Life Siphon: minion deals damage → heal owner ──────────────────────
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onMinionDamage(event: EntityDamageByEntityEvent) {
        val damager = event.damager as? Mob ?: return
        val ownerUuidStr = damager.persistentDataContainer.get(ownerKey, PersistentDataType.STRING) ?: return
        val ownerUuid = runCatching { UUID.fromString(ownerUuidStr) }.getOrNull() ?: return

        // Prevent minion from attacking ANY player
        val victim = event.entity
        if (victim is Player) {
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

    // ── Soul Shield: minion absorbs damage for owner ──────────────────────
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onSoulShield(event: EntityDamageByEntityEvent) {
        val victim = event.entity as? Player ?: return
        val uuid = victim.uniqueId
        val job = plugin.dataManager.getPlayer(uuid)?.job ?: return
        if (job != Jobs.NECROMANCER) return

        val skills = plugin.dataManager.getSkills(uuid)
        val soulShieldLv = skills.getLevel("soul_shield")
        if (soulShieldLv <= 0) return

        val chance = when (soulShieldLv) { 1 -> 0.20; 2 -> 0.35; else -> 0.50 }
        if (Math.random() >= chance) return

        val minions = plugin.minionManager.getMinions(uuid)
        val aliveMinion = minions.firstOrNull { !it.isDead } ?: return

        // Transfer damage to minion
        val dmg = event.damage
        event.damage = 0.0
        aliveMinion.damage(dmg)

        victim.world.spawnParticle(Particle.SOUL, victim.location.add(0.0, 1.0, 0.0), 5, 0.3, 0.3, 0.3, 0.05)
        victim.sendActionBar(legacy.deserialize("§5§l🛡 영혼 방패! §7미니언이 대신 피해를 흡수했습니다."))
    }

    // ── Entity death: handle minion deaths, minion kills, and mind control ──
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onEntityDeath(event: EntityDeathEvent) {
        val dead = event.entity

        // Dead entity is a minion: clear drops and XP
        if (dead is Mob && plugin.minionManager.isAnyMinion(dead)) {
            // Death Explosion: AoE damage on minion death
            val minionOwnerStr = dead.persistentDataContainer.get(ownerKey, PersistentDataType.STRING)
            if (minionOwnerStr != null) {
                val mOwnerUuid = runCatching { UUID.fromString(minionOwnerStr) }.getOrNull()
                if (mOwnerUuid != null) {
                    val mSkills = plugin.dataManager.getSkills(mOwnerUuid)
                    val explosionLv = mSkills.getLevel("death_explosion")
                    if (explosionLv > 0) {
                        val dmg = when (explosionLv) { 1 -> 3.0; 2 -> 5.0; else -> 8.0 }
                        val range = when (explosionLv) { 1 -> 3.0; 2 -> 4.0; else -> 5.0 }
                        val loc = dead.location
                        loc.world?.getNearbyEntities(loc, range, range, range)
                            ?.filterIsInstance<LivingEntity>()
                            ?.filter { it !is Player && !plugin.minionManager.isAnyMinion(it) }
                            ?.forEach { it.damage(dmg) }
                        loc.world?.spawnParticle(Particle.EXPLOSION, loc, 1, 0.0, 0.0, 0.0, 0.0)
                        loc.world?.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.2f)
                    }
                }
            }
            event.drops.clear()
            event.droppedExp = 0
            return
        }

        // Find the necromancer owner — either direct player kill or minion kill
        var owner: Player? = null
        var ownerUuid: UUID? = null

        // Case 1: Player directly killed the mob
        val directKiller = dead.killer
        if (directKiller != null) {
            val job = plugin.dataManager.getPlayer(directKiller.uniqueId)?.job
            if (job == Jobs.NECROMANCER) {
                owner = directKiller
                ownerUuid = directKiller.uniqueId
            }
        }

        // Case 2: Minion killed the mob
        if (owner == null) {
            val lastDmg = dead.lastDamageCause as? EntityDamageByEntityEvent
            val damager = lastDmg?.damager as? Mob
            if (damager != null) {
                val minionOwnerStr = damager.persistentDataContainer.get(ownerKey, PersistentDataType.STRING)
                if (minionOwnerStr != null) {
                    ownerUuid = runCatching { UUID.fromString(minionOwnerStr) }.getOrNull()
                    owner = ownerUuid?.let { plugin.server.getPlayer(it) }
                }
            }
        }

        if (owner == null || ownerUuid == null) return

        // Grant XP for minion kills
        if (directKiller == null) {
            val result = plugin.dataManager.grantXp(ownerUuid, 8)
            if (result != null) {
                plugin.actionBarManager.updateXp(ownerUuid, result.level, result.xp)
                if (result.leveledUp) {
                    dev.nyaru.minecraft.util.triggerLevelUp(plugin, owner, result.level, result.newSkillPoints)
                }
            }
        }

        // Mind Control: chance to convert killed mob (all Mob types, not just Monster)
        if (dead !is Mob || dead is Player) return
        val skills = plugin.dataManager.getSkills(ownerUuid)
        val mindControlLv = skills.getLevel("mind_control")
        if (mindControlLv <= 0) return

        val chance = when (mindControlLv) {
            1 -> 0.30; 2 -> 0.50; 3 -> 1.0; else -> 0.0
        }
        if (Math.random() > chance) return

        val soulEmpowerLv = skills.getLevel("soul_empower")
        val deadType = dead.type
        val deadLoc = dead.location.clone()
        val finalOwner = owner
        plugin.server.scheduler.runTask(plugin, Runnable {
            plugin.minionManager.summonControlled(
                finalOwner, deadType, deadLoc, mindControlLv, soulEmpowerLv
            )
            finalOwner.world.spawnParticle(
                Particle.SOUL,
                deadLoc.add(0.0, 1.0, 0.0),
                10, 0.3, 0.3, 0.3, 0.05
            )
            finalOwner.sendActionBar(legacy.deserialize("§5§l🧠 정신지배! §7처치한 몹을 하수인으로 부활시켰습니다."))
        })
    }

    // ── Cleanup minions on player quit ─────────────────────────────────────
    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        plugin.minionManager.removeMinions(event.player.uniqueId)
    }
}
