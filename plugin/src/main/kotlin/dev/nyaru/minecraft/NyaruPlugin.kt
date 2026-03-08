package dev.nyaru.minecraft

import dev.nyaru.minecraft.api.ApiClient
import dev.nyaru.minecraft.commands.AdminCommand
import dev.nyaru.minecraft.commands.BalanceCommand
import dev.nyaru.minecraft.commands.HelpCommand
import dev.nyaru.minecraft.commands.HomeCommand
import dev.nyaru.minecraft.commands.JobCommand
import dev.nyaru.minecraft.commands.LinkCommand
import dev.nyaru.minecraft.commands.LogCommand
import dev.nyaru.minecraft.commands.ProtectCommand
import dev.nyaru.minecraft.commands.SkillCommand
import dev.nyaru.minecraft.commands.SpawnCommand
import dev.nyaru.minecraft.commands.TeamCommand
import dev.nyaru.minecraft.commands.UnlinkCommand
import dev.nyaru.minecraft.data.DataManager
import dev.nyaru.minecraft.data.ShopManager
import dev.nyaru.minecraft.commands.BackpackCommand
import dev.nyaru.minecraft.commands.TradeCommand
import dev.nyaru.minecraft.gui.BackpackGui
import dev.nyaru.minecraft.gui.EnhanceGui
import dev.nyaru.minecraft.gui.HelpGui
import dev.nyaru.minecraft.gui.JobSelectGui
import dev.nyaru.minecraft.gui.ShopGui
import dev.nyaru.minecraft.gui.SkillGui
import dev.nyaru.minecraft.gui.TradeGui
import dev.nyaru.minecraft.listeners.ActionBarManager
import dev.nyaru.minecraft.listeners.BlockBreakListener
import dev.nyaru.minecraft.listeners.BlockDropListener
import dev.nyaru.minecraft.listeners.BlockLogListener
import dev.nyaru.minecraft.listeners.BlockPlaceListener
import dev.nyaru.minecraft.listeners.ChatTabListener
import dev.nyaru.minecraft.listeners.CombatListener
import dev.nyaru.minecraft.listeners.FishingListener
import dev.nyaru.minecraft.listeners.PlayerJoinListener
import dev.nyaru.minecraft.listeners.ProtectionListener
import dev.nyaru.minecraft.listeners.SidebarManager
import dev.nyaru.minecraft.listeners.NecromancerListener
import dev.nyaru.minecraft.listeners.SoulboundListener
import dev.nyaru.minecraft.listeners.SmeltListener
import dev.nyaru.minecraft.listeners.WorldEventListener
import dev.nyaru.minecraft.skills.MinionManager
import dev.nyaru.minecraft.logging.BlockLogger
import dev.nyaru.minecraft.world.StructureResetManager
import dev.nyaru.minecraft.npc.NpcType
import dev.nyaru.minecraft.protection.ProtectionManager
import dev.nyaru.minecraft.skills.SkillManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.bukkit.plugin.java.JavaPlugin

class NyaruPlugin : JavaPlugin() {

    lateinit var apiClient: ApiClient
        private set

    lateinit var dataManager: DataManager
        private set

    lateinit var shopManager: ShopManager
        private set

    lateinit var playerJoinListener: PlayerJoinListener
        private set

    lateinit var actionBarManager: ActionBarManager
        private set

    lateinit var sidebarManager: SidebarManager
        private set

    lateinit var protectionManager: ProtectionManager
        private set

    lateinit var blockLogger: BlockLogger
        private set

    lateinit var skillManager: SkillManager
        private set

    lateinit var minionManager: MinionManager
        private set

    lateinit var structureResetManager: StructureResetManager
        private set

    val pluginScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onEnable() {
        saveDefaultConfig()

        // DataManager and ShopManager (local YAML storage)
        dataManager = DataManager(dataFolder)
        shopManager = ShopManager(dataFolder)

        // ApiClient — optional, used only for Discord linking
        val apiUrl = System.getenv("MINECRAFT_API_URL")
            ?: config.getString("api.url")
            ?: ""
        val apiKey = System.getenv("MINECRAFT_API_KEY")
            ?: config.getString("api.key")
            ?: ""

        if (apiUrl.isEmpty()) {
            logger.warning("api.url이 설정되지 않았습니다. Discord 연동 기능이 비활성화됩니다.")
        }

        apiClient = ApiClient(apiUrl, apiKey)

        // Protection and block logging
        protectionManager = ProtectionManager(dataFolder)
        server.pluginManager.registerEvents(ProtectionListener(this, protectionManager), this)

        blockLogger = BlockLogger(dataFolder)
        server.pluginManager.registerEvents(BlockLogListener(blockLogger), this)
        server.pluginManager.registerEvents(HelpGui.HelpGuiListener(), this)

        skillManager = SkillManager(this)
        skillManager.startForestBlessingCheck()
        minionManager = MinionManager(this)
        minionManager.startMinionTask()
        server.pluginManager.registerEvents(NecromancerListener(this), this)
        sidebarManager = SidebarManager(this, protectionManager)
        server.pluginManager.registerEvents(sidebarManager, this)
        actionBarManager = ActionBarManager(this, protectionManager)
        val chatTabListener = ChatTabListener(actionBarManager)
        actionBarManager.chatTabListener = chatTabListener

        server.pluginManager.registerEvents(actionBarManager, this)
        server.pluginManager.registerEvents(chatTabListener, this)
        server.pluginManager.registerEvents(skillManager, this)
        server.pluginManager.registerEvents(JobSelectGui.JobSelectListener(this), this)
        server.pluginManager.registerEvents(ShopGui.ShopGuiListener(this), this)
        server.pluginManager.registerEvents(SkillGui.SkillGuiListener(this), this)
        server.pluginManager.registerEvents(EnhanceGui.EnhanceGuiListener(this), this)
        server.pluginManager.registerEvents(TradeGui.TradeGuiListener(this), this)
        server.pluginManager.registerEvents(BackpackGui.BackpackGuiListener(this), this)
        server.pluginManager.registerEvents(SoulboundListener(), this)

        structureResetManager = StructureResetManager(this)
        server.pluginManager.registerEvents(structureResetManager, this)
        server.pluginManager.registerEvents(BlockBreakListener(this, skillManager), this)
        server.pluginManager.registerEvents(BlockDropListener(this, skillManager), this)
        server.pluginManager.registerEvents(BlockPlaceListener(this, skillManager), this)
        server.pluginManager.registerEvents(CombatListener(this, skillManager), this)
        server.pluginManager.registerEvents(SmeltListener(this, skillManager), this)
        server.pluginManager.registerEvents(FishingListener(this, skillManager), this)

        playerJoinListener = PlayerJoinListener(this, actionBarManager)
        server.pluginManager.registerEvents(playerJoinListener, this)

        val worldEventListener = WorldEventListener(this)
        server.pluginManager.registerEvents(worldEventListener, this)
        worldEventListener.onEnable()

        // FancyNpcs integration — checked 1 tick later to avoid load-order issues
        server.scheduler.runTask(this, Runnable {
            npcCreateFn = if (server.pluginManager.isPluginEnabled("FancyNpcs")) {
                val service = dev.nyaru.minecraft.npc.FancyNpcsService(this)
                fancyNpcsService = service
                server.pluginManager.registerEvents(service, this)
                logger.info("FancyNpcs 감지됨 — NPC 지원 활성화")
                val fn: (org.bukkit.Location, NpcType) -> Unit = { loc, type -> service.createNpc(loc, type) }
                fn
            } else {
                logger.info("FancyNpcs 없음 — NPC 명령어 비활성화")
                null
            }
        })

        // Auto-save DataManager every 5 minutes (6000 ticks)
        server.scheduler.runTaskTimerAsynchronously(this, Runnable {
            dataManager.saveAll()
        }, 6000L, 6000L)

        // Commands
        getCommand("연동")?.setExecutor(LinkCommand(this))
        getCommand("연동해제")?.setExecutor(UnlinkCommand(this, actionBarManager, playerJoinListener))
        getCommand("잔고")?.setExecutor(BalanceCommand(this))
        val jobCmd = JobCommand(this)
        getCommand("직업")?.setExecutor(jobCmd)
        getCommand("스킬")?.setExecutor(SkillCommand(this, skillManager))
        val adminCmd = AdminCommand(this)
        getCommand("나루관리")?.setExecutor(adminCmd)
        getCommand("나루관리")?.tabCompleter = adminCmd
        val teamCmd = TeamCommand(protectionManager)
        getCommand("팀")?.setExecutor(teamCmd)
        getCommand("팀")?.tabCompleter = teamCmd
        getCommand("도움말")?.setExecutor(HelpCommand())
        val logCmd = LogCommand(blockLogger, pluginScope)
        getCommand("로그")?.setExecutor(logCmd)
        server.pluginManager.registerEvents(logCmd, this)
        getCommand("보호")?.setExecutor(ProtectCommand(protectionManager))
        getCommand("스폰")?.setExecutor(SpawnCommand(this))
        getCommand("집")?.setExecutor(HomeCommand(this))
        val tradeCmd = TradeCommand(this)
        getCommand("교환")?.setExecutor(tradeCmd)
        getCommand("교환")?.tabCompleter = tradeCmd
        val backpackCmd = BackpackCommand(this)
        getCommand("배낭")?.setExecutor(backpackCmd)
        getCommand("배낭")?.tabCompleter = backpackCmd

        logger.info("NyaruPlugin 활성화 완료!")
    }

    var npcCreateFn: ((org.bukkit.Location, NpcType) -> Unit)? = null
    private var fancyNpcsService: dev.nyaru.minecraft.npc.FancyNpcsService? = null

    override fun onDisable() {
        pluginScope.cancel()
        if (::minionManager.isInitialized) {
            for (uuid in server.onlinePlayers.map { it.uniqueId }) {
                minionManager.removeMinions(uuid)
            }
        }
        if (::dataManager.isInitialized) dataManager.saveAll()
        if (::protectionManager.isInitialized) protectionManager.save()
        if (::blockLogger.isInitialized) blockLogger.shutdown()
        logger.info("NyaruPlugin 비활성화 완료.")
    }
}
