package dev.nyaru.minecraft.listeners

import dev.nyaru.minecraft.NyaruPlugin
import dev.nyaru.minecraft.model.Jobs
import dev.nyaru.minecraft.skills.SkillManager
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Monster
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private val HOE_MATERIALS = setOf(
    Material.WOODEN_HOE, Material.STONE_HOE, Material.IRON_HOE,
    Material.GOLDEN_HOE, Material.DIAMOND_HOE, Material.NETHERITE_HOE
)

private val SWORD_MATERIALS = setOf(
    Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD,
    Material.GOLDEN_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD
)

private val AXE_MATERIALS = setOf(
    Material.WOODEN_AXE, Material.STONE_AXE, Material.IRON_AXE,
    Material.GOLDEN_AXE, Material.DIAMOND_AXE, Material.NETHERITE_AXE
)

class CombatListener(private val plugin: NyaruPlugin, private val skillManager: SkillManager) : Listener {

    private val legacy = LegacyComponentSerializer.legacySection()

    // War cry cooldown: UUID -> timestamp (ms) of last use
    private val warCryCooldown = ConcurrentHashMap<UUID, Long>()
    // Iron will cooldown: UUID -> timestamp (ms) of last proc
    private val ironWillCooldown = ConcurrentHashMap<UUID, Long>()
    // Dash cooldown: UUID -> timestamp (ms) of last use
    private val dashCooldown = ConcurrentHashMap<UUID, Long>()

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
        val damager = event.damager as? Player ?: return
        val uuid = damager.uniqueId
        val job = plugin.dataManager.getPlayer(uuid)?.job
        val skills = skillManager.getSkills(uuid)
        val heldItem = damager.inventory.itemInMainHand

        // ── Miner: Mace Master ─────────────────────────────────────────────
        if (heldItem.type == Material.MACE) {
            val maceLv = skills.getLevel("mace_master")
            if (maceLv >= 1) {
                // Cancel durability loss by restoring item damage
                val meta = heldItem.itemMeta
                if (meta is org.bukkit.inventory.meta.Damageable) {
                    val currentDamage = meta.damage
                    plugin.server.scheduler.runTaskLater(plugin, Runnable {
                        val item = damager.inventory.itemInMainHand
                        if (item.type == Material.MACE) {
                            item.editMeta(org.bukkit.inventory.meta.Damageable::class.java) { m ->
                                m.damage = currentDamage
                            }
                        }
                    }, 1L)
                }
            }
            if (maceLv >= 2) {
                // 위로 붕 뜨기 - 강하게 발사
                plugin.server.scheduler.runTask(plugin, Runnable {
                    damager.velocity = damager.velocity.setY(1.3)
                    damager.playSound(damager.location, Sound.ENTITY_BREEZE_JUMP, 1.0f, 0.8f)
                    damager.world.spawnParticle(
                        Particle.CLOUD,
                        damager.location,
                        10, 0.3, 0.1, 0.3, 0.05
                    )
                })
            }
        }

        // ── Farmer: Life Drain ─────────────────────────────────────────────
        if (job == Jobs.FARMER && heldItem.type in HOE_MATERIALS) {
            val drainLv = skills.getLevel("life_drain")
            if (drainLv > 0) {
                val healAmount = event.finalDamage * (drainLv * 0.1)
                val newHealth = (damager.health + healAmount).coerceAtMost(damager.maxHealth)
                damager.health = newHealth
                damager.playSound(damager.location, Sound.ENTITY_WITCH_DRINK, 0.8f, 1.2f)
                damager.world.spawnParticle(
                    Particle.HEART,
                    damager.location.add(0.0, 1.5, 0.0),
                    drainLv, 0.3, 0.3, 0.3, 0.0
                )
            }
        }

        // ── Fisher: Trident Master ───────────────────────────────────────────
        if (job == Jobs.FISHER && heldItem.type == Material.TRIDENT) {
            val tridentLv = skills.getLevel("trident_master")
            if (tridentLv > 0) {
                val bonus = when (tridentLv) { 1 -> 0.15; 2 -> 0.30; else -> 0.50 }
                event.damage = event.damage * (1.0 + bonus)
            }
        }

        // ── Woodcutter: Lumberjack Fury ──────────────────────────────────────
        if (job == Jobs.WOODCUTTER && heldItem.type in AXE_MATERIALS) {
            val furyLv = skills.getLevel("lumberjack_fury")
            if (furyLv > 0) {
                val bonus = when (furyLv) { 1 -> 0.15; 2 -> 0.30; else -> 0.50 }
                event.damage = event.damage * (1.0 + bonus)
            }
        }

        // ── Warrior combat skills ──────────────────────────────────────────
        if (job == Jobs.WARRIOR) {
            var damage = event.damage

            // Sword Mastery: bonus damage with sword
            if (heldItem.type in SWORD_MATERIALS) {
                val swordLv = skills.getLevel("sword_mastery")
                if (swordLv > 0) {
                    damage *= (1.0 + swordLv * 0.1)
                }
            }

            // Berserker: bonus damage when below 50% HP
            val berserkerLv = skills.getLevel("berserker")
            if (berserkerLv > 0 && damager.health < damager.maxHealth * 0.5) {
                val bonusMultiplier = when (berserkerLv) {
                    1 -> 0.15
                    2 -> 0.25
                    else -> 0.35
                }
                damage *= (1.0 + bonusMultiplier)
            }

            // Critical Strike: chance for 1.5x damage
            val critLv = skills.getLevel("critical_strike")
            if (critLv > 0) {
                val critChance = critLv * 0.10
                if (Math.random() < critChance) {
                    damage *= 1.5
                    damager.sendActionBar(legacy.deserialize("§c§l치명타!"))
                }
            }

            event.damage = damage

            // Lethal Strike: chance to apply Wither
            val lethalLv = skills.getLevel("lethal_strike")
            if (lethalLv > 0 && event.entity is LivingEntity) {
                val chance = lethalLv * 0.05 + 0.05 // 10%, 15%, 20%
                if (Math.random() < chance) {
                    val duration = lethalLv * 40 + 20 // 3s, 5s, 7s
                    (event.entity as LivingEntity).addPotionEffect(PotionEffect(
                        PotionEffectType.WITHER,
                        duration,
                        0,
                        false, true, true
                    ))
                    damager.sendActionBar(legacy.deserialize("§8§l☠ 치명적 일격!"))
                    damager.playSound(damager.location, Sound.ENTITY_WITHER_HURT, 0.5f, 1.5f)
                }
            }

            // War Cry: sneak + attack → Weakness to nearby hostiles
            val warCryLv = skills.getLevel("war_cry")
            if (warCryLv > 0 && damager.isSneaking) {
                val now = System.currentTimeMillis()
                val lastUse = warCryCooldown[uuid] ?: 0L
                if (now - lastUse >= 30_000L) {
                    warCryCooldown[uuid] = now
                    val durationTicks = warCryLv * 40 + 20
                    val nearbyHostiles = damager.world.getNearbyEntities(
                        damager.location, 8.0, 8.0, 8.0
                    ) { it is Monster && it != damager }.filterIsInstance<LivingEntity>()

                    for (mob in nearbyHostiles) {
                        mob.addPotionEffect(PotionEffect(
                            PotionEffectType.WEAKNESS,
                            durationTicks,
                            0,
                            false, true, true
                        ))
                    }
                    damager.playSound(damager.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.2f)
                    damager.sendActionBar(legacy.deserialize("§c§l전투의 함성! §7주변 적에게 약화 적용"))
                }
            }
        }
    }

    // ── Miner: Mace Master Lv3 — cancel fall damage ─────────────────────
    // ── Warrior: Iron Will — survive lethal damage ────────────────────────
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onPlayerDamage(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return

        // Mace Master Lv3: no fall damage
        if (event.cause == EntityDamageEvent.DamageCause.FALL) {
            val skills = skillManager.getSkills(player.uniqueId)
            if (skills.getLevel("mace_master") >= 3) {
                event.isCancelled = true
                return
            }
        }

        // Iron Will: survive lethal damage
        val job = plugin.dataManager.getPlayer(player.uniqueId)?.job
        if (job != Jobs.WARRIOR) return

        val skills = skillManager.getSkills(player.uniqueId)
        val ironWillLv = skills.getLevel("iron_will")
        if (ironWillLv <= 0) return

        // Would this damage kill the player?
        if (player.health - event.finalDamage > 0) return

        // Cooldown check (3 minutes)
        val now = System.currentTimeMillis()
        val lastProc = ironWillCooldown[player.uniqueId] ?: 0L
        if (now - lastProc < 180_000L) return

        // Chance check: 10%, 20%, 30%
        val chance = ironWillLv * 0.10
        if (Math.random() >= chance) return

        // Proc! Survive at 1 HP
        ironWillCooldown[player.uniqueId] = now
        event.isCancelled = true
        player.health = 1.0
        player.playSound(player.location, Sound.ITEM_TOTEM_USE, 1.0f, 1.0f)
        player.world.spawnParticle(
            Particle.TOTEM_OF_UNDYING,
            player.location.add(0.0, 1.0, 0.0),
            30, 0.5, 1.0, 0.5, 0.1
        )
        player.sendMessage(legacy.deserialize("§c§l강철 의지! §7치명적 피해를 버텨냈습니다! §8(쿨다운 3분)"))
    }

    // ── Warrior: Shield Master — reflect damage when blocking ─────────────
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onPlayerDamageByEntity(event: EntityDamageByEntityEvent) {
        val player = event.entity as? Player ?: return
        val uuid = player.uniqueId
        val job = plugin.dataManager.getPlayer(uuid)?.job ?: return

        if (job == Jobs.WARRIOR && player.isBlocking) {
            val skills = skillManager.getSkills(uuid)
            val shieldLv = skills.getLevel("shield_master")
            if (shieldLv > 0) {
                val reflectPct = when (shieldLv) { 1 -> 0.15; 2 -> 0.30; else -> 0.50 }
                val reflectDmg = event.damage * reflectPct
                val attacker = event.damager
                if (attacker is LivingEntity) {
                    plugin.server.scheduler.runTask(plugin, Runnable {
                        attacker.damage(reflectDmg)
                    })
                    player.world.spawnParticle(Particle.CRIT, player.location.add(0.0, 1.0, 0.0), 10, 0.3, 0.3, 0.3, 0.1)
                }
            }
        }
    }

    // ── Warrior: Dash — sneak + left-click air with empty hand ────────────
    @EventHandler(priority = EventPriority.NORMAL)
    fun onWarriorDash(event: PlayerInteractEvent) {
        if (event.action != Action.LEFT_CLICK_AIR) return
        val player = event.player
        if (!player.isSneaking) return
        if (player.inventory.itemInMainHand.type != Material.AIR) return

        val uuid = player.uniqueId
        val job = plugin.dataManager.getPlayer(uuid)?.job
        if (job != Jobs.WARRIOR) return

        val skills = skillManager.getSkills(uuid)
        val dashLv = skills.getLevel("dash")
        if (dashLv <= 0) return

        val now = System.currentTimeMillis()
        val lastDash = dashCooldown[uuid] ?: 0L
        if (now - lastDash < 10_000L) {
            val remaining = ((10_000L - (now - lastDash)) / 1000L)
            player.sendActionBar(legacy.deserialize("§c돌진 쿨다운 중 (${remaining}초)"))
            return
        }

        dashCooldown[uuid] = now
        val power = when (dashLv) { 1 -> 1.5; 2 -> 2.0; else -> 2.8 }
        player.velocity = player.location.direction.multiply(power)
        player.playSound(player.location, Sound.ENTITY_BREEZE_JUMP, 1.0f, 1.2f)
        player.world.spawnParticle(Particle.CLOUD, player.location, 10, 0.3, 0.1, 0.3, 0.05)
        player.sendActionBar(legacy.deserialize("§c§l💨 돌진!"))
    }

    // ── Warrior XP: killing a hostile mob ──────────────────────────────
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityDeath(event: EntityDeathEvent) {
        if (event.entity !is Monster) return
        val killer = event.entity.killer ?: return
        val uuid = killer.uniqueId
        if (plugin.dataManager.getPlayer(uuid)?.job != Jobs.WARRIOR) return

        val result = plugin.dataManager.grantXp(uuid, 10)
        if (result != null) {
            plugin.actionBarManager.updateXp(uuid, result.level, result.xp)
            if (result.leveledUp) {
                dev.nyaru.minecraft.util.triggerLevelUp(plugin, killer, result.level, result.newSkillPoints)
            }
        }
    }
}
