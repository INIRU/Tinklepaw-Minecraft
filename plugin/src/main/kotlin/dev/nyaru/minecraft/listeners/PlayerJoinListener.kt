package dev.nyaru.minecraft.listeners

import dev.nyaru.minecraft.NyaruPlugin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PlayerJoinListener(private val plugin: NyaruPlugin, private val actionBarManager: ActionBarManager) : Listener {

    private val frozenPlayers = ConcurrentHashMap.newKeySet<UUID>()
    private val discordInvite = plugin.config.getString("discord.invite_url") ?: "discord.gg/tinklepaw"

    init {
        startLinkCheckLoop()
    }

    fun freeze(player: org.bukkit.entity.Player) {
        frozenPlayers.add(player.uniqueId)
        player.isInvulnerable = true
    }

    fun unfreeze(player: org.bukkit.entity.Player) {
        frozenPlayers.remove(player.uniqueId)
        player.isInvulnerable = false
    }

    fun refreezeAndRequestLink(player: org.bukkit.entity.Player) {
        freeze(player)
        plugin.pluginScope.launch {
            val result = plugin.apiClient.requestLink(player.uniqueId.toString(), player.name)
            Bukkit.getScheduler().runTask(plugin, Runnable {
                sendWelcomeMessage(player, result?.otp)
            })
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val uuid = player.uniqueId

        // Load or initialise local player data synchronously
        plugin.dataManager.loadPlayer(uuid, player.name)

        // Load title data into TitleManager
        plugin.titleManager.loadPlayer(
            uuid,
            plugin.dataManager.getEarnedTitles(uuid),
            plugin.dataManager.getSelectedGameTitle(uuid)
        )

        if (plugin.dataManager.isLinked(uuid)) {
            // Grant daily reward if eligible
            val reward = plugin.config.getInt("daily-reward.amount", 100)
            if (plugin.dataManager.canClaimDaily(uuid)) {
                plugin.dataManager.claimDaily(uuid, reward)
                plugin.server.scheduler.runTaskLater(plugin, Runnable {
                    player.sendMessage("§a§l✓ 출석 보상! §e+${reward}냥")
                    player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f)
                }, 20L)
            }
        } else {
            // Not linked — freeze and request OTP via Discord bot API
            plugin.pluginScope.launch {
                val result = plugin.apiClient.requestLink(uuid.toString(), player.name)
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    freeze(player)
                    sendWelcomeMessage(player, result?.otp)
                })
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerMove(event: PlayerMoveEvent) {
        if (event.player.uniqueId !in frozenPlayers) return
        val from = event.from
        val to = event.to
        if (from.x != to.x || from.y != to.y || from.z != to.z) {
            event.to = Location(from.world, from.x, from.y, from.z, to.yaw, to.pitch)
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        if (event.player.uniqueId in frozenPlayers) {
            event.isCancelled = true
            event.player.sendActionBar(
                net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                    .deserialize("§cDiscord 연동 후 이용 가능합니다.")
            )
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val uuid = event.player.uniqueId
        frozenPlayers.remove(uuid)
        plugin.titleManager.unloadPlayer(uuid)
        plugin.dataManager.unloadPlayer(uuid)
    }

    private fun sendWelcomeMessage(player: org.bukkit.entity.Player, otp: String?) {
        player.sendMessage("§8§m                                        ")
        player.sendMessage("§r")
        player.sendMessage("§d§l[ 방울냥 Minecraft ]")
        player.sendMessage("§r")
        player.sendMessage("§f이 서버는 Discord 계정 연동이 필요합니다.")
        player.sendMessage("§r")
        if (otp != null) {
            player.sendMessage("§e연동 코드: §f§l$otp")
            player.sendMessage("§r")
            player.sendMessage("§71. §fDiscord 서버에 참여하세요")
            player.sendMessage("§7   §b$discordInvite")
            player.sendMessage("§72. §fDiscord에서 아래 명령어를 입력하세요:")
            player.sendMessage("§7   §a/연동확인 $otp")
        } else {
            player.sendMessage("§71. §fDiscord 서버에 참여하세요")
            player.sendMessage("§7   §b$discordInvite")
            player.sendMessage("§72. §fMinecraft에서 §a/연동§f 을 입력해 코드를 받으세요")
            player.sendMessage("§73. §fDiscord에서 §a/연동확인 <코드>§f 입력")
        }
        player.sendMessage("§r")
        player.sendMessage("§c연동 전까지 이동이 제한됩니다.")
        player.sendMessage("§r")
        player.sendMessage("§8§m                                        ")
    }

    private fun startLinkCheckLoop() {
        plugin.pluginScope.launch {
            while (isActive) {
                delay(10_000)
                val frozen = frozenPlayers.toSet()
                for (uuid in frozen) {
                    val player = Bukkit.getPlayer(uuid)
                    if (player == null) {
                        frozenPlayers.remove(uuid)
                        continue
                    }
                    // Poll the Discord bot API to see if the player has linked
                    val (linked, discordId) = plugin.apiClient.checkLink(uuid.toString())
                    if (linked) {
                        // Persist link locally
                        plugin.dataManager.setLinked(uuid, discordId ?: "", player.name)
                        val isNewPlayer = plugin.dataManager.getPlayer(uuid)?.let {
                            it.level == 1 && it.xp == 0 && it.job == null
                        } ?: false
                        Bukkit.getScheduler().runTask(plugin, Runnable {
                            unfreeze(player)
                            player.playSound(player.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f)
                            player.sendMessage("§a§l✓ Discord 연동 완료! 이동이 허용됩니다.")
                            if (isNewPlayer) {
                                dev.nyaru.minecraft.gui.JobSelectGui(plugin, player).open()
                            }
                        })
                        actionBarManager.refresh(uuid)
                    }
                }
            }
        }
    }
}
