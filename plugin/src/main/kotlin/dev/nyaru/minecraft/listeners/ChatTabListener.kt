package dev.nyaru.minecraft.listeners

import dev.nyaru.minecraft.NyaruPlugin
import dev.nyaru.minecraft.model.PlayerInfo
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

class ChatTabListener(private val actionBarManager: ActionBarManager) : Listener {

    var plugin: NyaruPlugin? = null
    private val legacy = LegacyComponentSerializer.legacySection()

    @EventHandler(ignoreCancelled = true)
    fun onChat(event: AsyncChatEvent) {
        val uuid = event.player.uniqueId
        val info = actionBarManager.getInfo(uuid)

        // Build prefix: Discord title and/or game title
        val discordTitleComp = if (info?.title != null) buildDiscordTitleComponent(info.title, info) else null
        val gameTitleDisplay = plugin?.titleManager?.getSelectedTitleDisplay(uuid)
        val gameTitleComp = if (gameTitleDisplay != null) legacy.deserialize("§7[${gameTitleDisplay}§7]") else null

        if (discordTitleComp == null && gameTitleComp == null) return

        event.renderer { _, sourceDisplayName, message, _ ->
            val builder = Component.text()
            if (discordTitleComp != null) {
                builder.append(discordTitleComp)
                builder.append(Component.text(" ", NamedTextColor.WHITE))
            }
            if (gameTitleComp != null) {
                builder.append(gameTitleComp)
                builder.append(Component.text(" ", NamedTextColor.WHITE))
            }
            builder.append(sourceDisplayName)
            builder.append(Component.text(": ", NamedTextColor.WHITE))
            builder.append(message)
            builder.build()
        }
    }

    fun updateTabName(player: Player, info: PlayerInfo?) {
        val gameTitleDisplay = plugin?.titleManager?.getSelectedTitleDisplay(player.uniqueId)

        val hasDiscordTitle = info?.linked == true && info.title != null
        val hasGameTitle = gameTitleDisplay != null

        if (hasDiscordTitle || hasGameTitle) {
            val builder = Component.text()
            if (hasDiscordTitle) {
                builder.append(buildDiscordTitleComponent(info!!.title!!, info))
                builder.append(Component.text(" ", NamedTextColor.WHITE))
            }
            if (hasGameTitle) {
                builder.append(legacy.deserialize("§7[${gameTitleDisplay}§7]"))
                builder.append(Component.text(" ", NamedTextColor.WHITE))
            }
            builder.append(Component.text(player.name, NamedTextColor.WHITE))
            player.playerListName(builder.build())
        } else {
            player.playerListName(null)
        }
    }

    private fun buildDiscordTitleComponent(title: String, info: PlayerInfo): Component {
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
