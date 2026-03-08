package dev.nyaru.minecraft.util

import dev.nyaru.minecraft.NyaruPlugin
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.title.Title
import org.bukkit.Color
import org.bukkit.FireworkEffect
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.EntityType
import org.bukkit.entity.Firework
import org.bukkit.entity.Player
import java.time.Duration

fun triggerLevelUp(plugin: NyaruPlugin, player: Player, newLevel: Int, newSkillPoints: Int?) {
    val legacy = LegacyComponentSerializer.legacySection()

    // Title + subtitle
    player.showTitle(
        Title.title(
            legacy.deserialize("§6§l⬆ LEVEL UP!"),
            legacy.deserialize("§eLv.§6$newLevel §e달성!"),
            Title.Times.times(
                Duration.ofMillis(200),
                Duration.ofMillis(3000),
                Duration.ofMillis(600)
            )
        )
    )

    // Sounds
    player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f)
    player.playSound(player.location, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.8f, 1.2f)
    player.playSound(player.location, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 0.6f, 1.0f)

    // Particle effects instead of real firework (no damage)
    val loc = player.location.add(0.0, 1.5, 0.0)
    player.world.spawnParticle(Particle.FIREWORK, loc, 40, 0.5, 0.8, 0.5, 0.1)
    player.world.spawnParticle(Particle.TOTEM_OF_UNDYING, loc, 25, 0.4, 0.6, 0.4, 0.15)

    // Skill point notification
    if (newSkillPoints != null) {
        player.sendMessage(legacy.deserialize("§a§l✦ §e스킬 포인트 +1 §a획득! §7(현재 §e${newSkillPoints}개§7) — §f/스킬 §7에서 사용"))
    }

    // Refresh actionbar immediately
    plugin.actionBarManager.refresh(player.uniqueId)
}
