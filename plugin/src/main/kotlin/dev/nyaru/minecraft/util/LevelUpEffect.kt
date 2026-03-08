package dev.nyaru.minecraft.util

import dev.nyaru.minecraft.NyaruPlugin
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.title.Title
import org.bukkit.Color
import org.bukkit.FireworkEffect
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

    // Firework at player feet
    @Suppress("DEPRECATION")
    val fw = player.world.spawnEntity(player.location.add(0.0, 3.0, 0.0), EntityType.FIREWORK_ROCKET) as Firework
    val fwMeta = fw.fireworkMeta
    fwMeta.addEffect(
        FireworkEffect.builder()
            .withColor(Color.YELLOW, Color.ORANGE)
            .withFade(Color.WHITE)
            .with(FireworkEffect.Type.BALL_LARGE)
            .withTrail()
            .build()
    )
    fwMeta.power = 0
    fw.fireworkMeta = fwMeta
    fw.detonate()

    // Skill point notification
    if (newSkillPoints != null) {
        player.sendMessage(legacy.deserialize("§a§l✦ §e스킬 포인트 +1 §a획득! §7(현재 §e${newSkillPoints}개§7) — §f/스킬 §7에서 사용"))
    }

    // Refresh actionbar immediately
    plugin.actionBarManager.refresh(player.uniqueId)
}
