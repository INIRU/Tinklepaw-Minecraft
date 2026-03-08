package dev.nyaru.minecraft.gui

import dev.nyaru.minecraft.NyaruPlugin
import dev.nyaru.minecraft.model.Jobs
import dev.nyaru.minecraft.model.SkillData
import dev.nyaru.minecraft.model.SkillDef
import dev.nyaru.minecraft.model.SkillRegistry
import dev.nyaru.minecraft.skills.SkillManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import java.util.concurrent.ConcurrentHashMap

class SkillGui(
    private val plugin: NyaruPlugin,
    private val player: Player,
    private val skillManager: SkillManager
) {

    companion object {
        val activeInventories = ConcurrentHashMap<Inventory, SkillGui>()

        // Skill slots for up to 5 skills per job
        val SKILL_SLOTS = listOf(19, 21, 23, 25, 29, 31)

        // Background glass per job
        fun bgGlassMaterial(job: String?): Material = when (job) {
            Jobs.MINER -> Material.BLUE_STAINED_GLASS_PANE
            Jobs.FARMER -> Material.GREEN_STAINED_GLASS_PANE
            Jobs.WARRIOR -> Material.RED_STAINED_GLASS_PANE
            Jobs.FISHER -> Material.CYAN_STAINED_GLASS_PANE
            Jobs.WOODCUTTER -> Material.ORANGE_STAINED_GLASS_PANE
            Jobs.NECROMANCER -> Material.PURPLE_STAINED_GLASS_PANE
            else -> Material.GRAY_STAINED_GLASS_PANE
        }

        // Accent glass per job
        fun accentGlassMaterial(job: String?): Material = when (job) {
            Jobs.MINER -> Material.CYAN_STAINED_GLASS_PANE
            Jobs.FARMER -> Material.LIME_STAINED_GLASS_PANE
            Jobs.WARRIOR -> Material.PINK_STAINED_GLASS_PANE
            Jobs.FISHER -> Material.LIGHT_BLUE_STAINED_GLASS_PANE
            Jobs.WOODCUTTER -> Material.YELLOW_STAINED_GLASS_PANE
            Jobs.NECROMANCER -> Material.MAGENTA_STAINED_GLASS_PANE
            else -> Material.WHITE_STAINED_GLASS_PANE
        }

        fun titleForJob(job: String?): String = when (job) {
            Jobs.MINER -> "§b§l⛏ 광부 스킬"
            Jobs.FARMER -> "§a§l🌾 농부 스킬"
            Jobs.WARRIOR -> "§c§l⚔ 전사 스킬"
            Jobs.FISHER -> "§3§l🎣 어부 스킬"
            Jobs.WOODCUTTER -> "§6§l🪓 벌목꾼 스킬"
            Jobs.NECROMANCER -> "§5§l☠ 네크로맨서 스킬"
            else -> "§7스킬"
        }
    }

    private lateinit var inventory: Inventory
    private var skills: SkillData = skillManager.getSkills(player.uniqueId)
    private var job: String? = null
    private var playerLevel: Int = 1

    private val legacy = LegacyComponentSerializer.legacySection()

    fun open() {
        val playerInfo = plugin.dataManager.getPlayer(player.uniqueId)
        if (playerInfo == null) {
            player.sendMessage("§c플레이어 데이터를 불러올 수 없습니다.")
            return
        }
        val jobVal = playerInfo.job
        if (jobVal == null) {
            player.sendMessage("§c직업을 먼저 선택해주세요. NPC에서 직업을 선택할 수 있습니다.")
            return
        }

        job = jobVal
        playerLevel = playerInfo.level
        skills = plugin.dataManager.getSkills(player.uniqueId)
        skillManager.updateCache(player.uniqueId, skills)

        val title = titleForJob(jobVal)
        inventory = Bukkit.createInventory(null, 54, legacy.deserialize(title))
        populate(jobVal)
        player.openInventory(inventory)
        activeInventories[inventory] = this
        player.playSound(player.location, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.0f)
    }

    private fun populate(currentJob: String?) {
        inventory.clear()

        val bgGlass = makeGlass(bgGlassMaterial(currentJob))
        val accentGlass = makeGlass(accentGlassMaterial(currentJob))
        for (i in 0 until 54) inventory.setItem(i, bgGlass)
        for (slot in listOf(0, 8, 45, 53)) inventory.setItem(slot, accentGlass)

        // Skill point display - slot 4
        inventory.setItem(4, buildSkillPointItem())

        // Populate skills dynamically from SkillRegistry
        val jobSkills = SkillRegistry.forJob(currentJob ?: return)
        for ((idx, def) in jobSkills.withIndex()) {
            if (idx >= SKILL_SLOTS.size) break
            val slot = SKILL_SLOTS[idx]
            val mat = runCatching { Material.valueOf(def.materialName) }.getOrNull() ?: Material.BARRIER
            val currentLv = skills.getLevel(def.key)
            inventory.setItem(slot, buildSkillItem(mat, def, currentLv))
        }
    }

    private fun buildSkillPointItem(): ItemStack {
        val emerald = ItemStack(Material.EMERALD)
        emerald.editMeta { meta ->
            meta.displayName(legacy.deserialize("§a스킬 포인트: §f${skills.skillPoints}"))
            meta.lore(listOf(
                legacy.deserialize("§7레벨업 시 스킬 포인트를 획득합니다."),
                legacy.deserialize("§7스킬 업그레이드에 1포인트가 필요합니다.")
            ))
        }
        return emerald
    }

    private fun buildSkillItem(material: Material, def: SkillDef, currentLv: Int): ItemStack {
        val item = ItemStack(material)
        item.editMeta { meta ->
            meta.displayName(legacy.deserialize("${def.displayName} §7[Lv.$currentLv/${def.maxLevel}]"))

            val lore = mutableListOf<Component>()
            lore.add(Component.empty())
            for (desc in def.description) {
                lore.add(legacy.deserialize(desc))
            }
            lore.add(Component.empty())

            val stars = buildString {
                append("§6레벨: ")
                for (i in 1..def.maxLevel) {
                    if (i <= currentLv) append("§e★") else append("§7☆")
                }
            }
            lore.add(legacy.deserialize(stars))
            lore.add(Component.empty())

            val nextLevelReq = def.levelReqs.getOrNull(currentLv) ?: 1
            val levelLocked = currentLv < def.maxLevel && playerLevel < nextLevelReq

            if (currentLv >= def.maxLevel) {
                lore.add(legacy.deserialize("§a§l최고 레벨!"))
            } else if (levelLocked) {
                lore.add(legacy.deserialize("§c🔒 레벨 §f${nextLevelReq} §c이상 필요"))
                lore.add(legacy.deserialize("§7현재 직업 레벨: §f${playerLevel}"))
            } else if (skills.skillPoints > 0) {
                lore.add(legacy.deserialize("§a▶ 클릭하여 업그레이드 (1 포인트)"))
            } else {
                lore.add(legacy.deserialize("§c스킬 포인트가 부족합니다."))
                lore.add(legacy.deserialize("§e업그레이드: §f1 스킬 포인트"))
            }

            meta.lore(lore)
        }
        return item
    }

    private fun makeGlass(material: Material): ItemStack {
        val glass = ItemStack(material)
        glass.editMeta { meta -> meta.displayName(Component.text(" ")) }
        return glass
    }

    fun handleClick(slot: Int) {
        val currentJob = job ?: return
        val jobSkills = SkillRegistry.forJob(currentJob)

        val slotIdx = SKILL_SLOTS.indexOf(slot)
        if (slotIdx < 0 || slotIdx >= jobSkills.size) return

        val def = jobSkills[slotIdx]
        val currentLv = skills.getLevel(def.key)

        if (currentLv >= def.maxLevel) {
            player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.8f)
            player.sendMessage("§c이미 최고 레벨입니다.")
            return
        }

        val nextLevelReq = def.levelReqs.getOrNull(currentLv) ?: 1
        if (playerLevel < nextLevelReq) {
            player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.8f)
            player.sendMessage("§c직업 레벨 §f${nextLevelReq} §c이상이 필요합니다. (현재: §f${playerLevel}§c)")
            return
        }

        if (skills.skillPoints <= 0) {
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f)
            player.sendMessage("§c스킬 포인트가 부족합니다.")
            return
        }

        val result = plugin.dataManager.upgradeSkill(player.uniqueId, def.key)
        if (result != null && result.first) {
            skills = plugin.dataManager.getSkills(player.uniqueId)
            skillManager.updateCache(player.uniqueId, skills)
            skillManager.applyPassiveEffects(player.uniqueId)
            populate(currentJob)
            player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f)
            player.sendMessage("§a§l✓ 스킬 업그레이드 완료! §7(Lv.${result.second})")
        } else {
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 0.8f, 0.8f)
            player.sendMessage("§c업그레이드 실패. 조건을 확인하세요.")
        }
    }

    class SkillGuiListener(private val plugin: NyaruPlugin) : Listener {

        @EventHandler
        fun onInventoryClick(event: InventoryClickEvent) {
            val gui = activeInventories[event.inventory] ?: return
            if (event.whoClicked != gui.player) return
            event.isCancelled = true
            gui.handleClick(event.rawSlot)
        }

        @EventHandler
        fun onInventoryClose(event: InventoryCloseEvent) {
            activeInventories.remove(event.inventory)
        }
    }
}
