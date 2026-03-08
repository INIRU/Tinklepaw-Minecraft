package dev.nyaru.minecraft.listeners

import dev.nyaru.minecraft.model.PlayerInfo
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

class ChatTabListener(private val actionBarManager: ActionBarManager) : Listener {

    @EventHandler(ignoreCancelled = true)
    fun onChat(event: AsyncChatEvent) {
        val info = actionBarManager.getInfo(event.player.uniqueId) ?: return
        val title = info.title ?: return
        val titleComp = buildTitleComponent(title, info)
        event.renderer { _, sourceDisplayName, message, _ ->
            Component.text()
                .append(titleComp)
                .append(Component.text(" ", NamedTextColor.WHITE))
                .append(sourceDisplayName)
                .append(Component.text(": ", NamedTextColor.WHITE))
                .append(message)
                .build()
        }
    }

    fun updateTabName(player: Player, info: PlayerInfo?) {
        if (info?.linked == true && info.title != null) {
            player.playerListName(
                Component.text()
                    .append(buildTitleComponent(info.title, info))
                    .append(Component.text(" ${player.name}", NamedTextColor.WHITE))
                    .build()
            )
        } else {
            player.playerListName(null)
        }
    }

    private fun buildTitleComponent(title: String, info: PlayerInfo): Component {
        val color = info.titleColor?.let { TextColor.fromHexString(it) } ?: NamedTextColor.LIGHT_PURPLE
        var comp = Component.text("[$title]", color)
            .decoration(TextDecoration.BOLD, true)
        if (info.titleIconUrl != null) {
            comp = comp
                .clickEvent(ClickEvent.openUrl(info.titleIconUrl))
                .hoverEvent(HoverEvent.showText(
                    Component.text("역할 아이콘 보기", NamedTextColor.GRAY)
                ))
        }
        return comp
    }
}
