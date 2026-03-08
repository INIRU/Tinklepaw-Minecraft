package dev.nyaru.minecraft.gui

import dev.nyaru.minecraft.NyaruPlugin
import dev.nyaru.minecraft.model.JobSlot
import dev.nyaru.minecraft.model.Jobs
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import java.util.concurrent.ConcurrentHashMap

class JobSelectGui(private val plugin: NyaruPlugin, private val player: Player) {

    companion object {
        val activeInventories = ConcurrentHashMap<Inventory, JobGuiState>()

        // Inventory slot positions for each job slot (0, 1, 2)
        val SLOT_POSITIONS = listOf(11, 13, 15)

        // Job selection sub-gui state
        val jobSelectTarget = ConcurrentHashMap<Inventory, Int>() // inv -> target slot index
    }

    data class JobGuiState(val player: Player, val mode: Mode, val targetSlot: Int = -1) {
        enum class Mode { SLOTS, JOB_SELECT }
    }

    private val legacy = LegacyComponentSerializer.legacySection()

    fun open() {
        openSlotView()
    }

    private fun openSlotView() {
        val info = plugin.dataManager.getPlayer(player.uniqueId) ?: return
        val inv = Bukkit.createInventory(null, 27, legacy.deserialize("§6§l⚒ 직업 슬롯"))

        val glass = makeGlass(Material.GRAY_STAINED_GLASS_PANE)
        for (i in 0 until 27) inv.setItem(i, glass)

        for (slotIndex in 0..2) {
            val invPos = SLOT_POSITIONS[slotIndex]
            when {
                slotIndex >= info.maxSlots -> {
                    // Locked slot — offer to buy
                    inv.setItem(invPos, buildLockedSlotItem(slotIndex))
                }
                slotIndex < info.jobSlots.size && info.jobSlots[slotIndex].job.isNotEmpty() -> {
                    // Occupied slot
                    val jobSlot = info.jobSlots[slotIndex]
                    val isActive = slotIndex == info.activeSlot
                    inv.setItem(invPos, buildOccupiedSlotItem(slotIndex, jobSlot, isActive, info.lastJobSwitch))
                }
                else -> {
                    // Unlocked empty slot
                    inv.setItem(invPos, buildEmptySlotItem(slotIndex))
                }
            }
        }

        activeInventories[inv] = JobGuiState(player, JobGuiState.Mode.SLOTS)
        player.openInventory(inv)
        player.playSound(player.location, Sound.BLOCK_CHEST_OPEN, 0.8f, 1.0f)
    }

    private fun openJobSelectView(targetSlotIndex: Int) {
        val inv = Bukkit.createInventory(null, 45, legacy.deserialize("§6§l⚒ 직업 선택 (슬롯 ${targetSlotIndex + 1})"))

        val glass = makeGlass(Material.GRAY_STAINED_GLASS_PANE)
        for (i in 0 until 45) inv.setItem(i, glass)

        inv.setItem(11, buildJobItem(Material.IRON_PICKAXE, "§b§l광부 (Miner)", listOf(
            "§7광물 채굴로 냥을 획득합니다.",
            Component.empty(),
            "§e[ 보너스 ]",
            "§8▸ §f심층부(Y -40↓): 순정도 보너스",
            "§8▸ §f다이아/에메랄드: 희귀 광물 보너스",
            "§8▸ §f철퇴 특수 능력 활성화"
        )))
        inv.setItem(13, buildJobItem(Material.GOLDEN_HOE, "§a§l농부 (Farmer)", listOf(
            "§7작물 재배로 냥을 획득합니다.",
            Component.empty(),
            "§e[ 보너스 ]",
            "§8▸ §f수확 직후 신선도 100% → 최고가",
            "§8▸ §f신선한 작물: 최대 +40% 냥",
            "§8▸ §f괭이로 공격 시 피흡"
        )))
        inv.setItem(15, buildJobItem(Material.DIAMOND_SWORD, "§c§l전사 (Warrior)", listOf(
            "§7몬스터 사냥으로 냥을 획득합니다.",
            Component.empty(),
            "§e[ 보너스 ]",
            "§8▸ §f검 강화로 추가 데미지",
            "§8▸ §f치명타 및 전투 스킬 특화",
            "§8▸ §f전투의 함성으로 적 약화"
        )))
        inv.setItem(29, buildJobItem(Material.FISHING_ROD, "§3§l어부 (Fisher)", listOf(
            "§7낚시로 냥을 획득합니다.",
            Component.empty(),
            "§e[ 보너스 ]",
            "§8▸ §f보물 확률 증가",
            "§8▸ §f바다의 축복 — 수중 이동 강화",
            "§8▸ §f희귀 아이템 낚시 확률"
        )))
        inv.setItem(31, buildJobItem(Material.DIAMOND_AXE, "§6§l벌목꾼 (Woodcutter)", listOf(
            "§7벌목으로 냥을 획득합니다.",
            Component.empty(),
            "§e[ 보너스 ]",
            "§8▸ §f한번에 연결된 나무 전체 베기",
            "§8▸ §f잎 자동 파괴",
            "§8▸ §f도끼 채굴 속도 증가"
        )))

        // Back button
        val back = ItemStack(Material.ARROW)
        back.editMeta { meta ->
            meta.displayName(legacy.deserialize("§7◀ 뒤로"))
        }
        inv.setItem(0, back)

        activeInventories[inv] = JobGuiState(player, JobGuiState.Mode.JOB_SELECT, targetSlotIndex)
        player.openInventory(inv)
        player.playSound(player.location, Sound.BLOCK_CHEST_OPEN, 0.8f, 1.0f)
    }

    private fun buildOccupiedSlotItem(slotIndex: Int, jobSlot: JobSlot, isActive: Boolean, lastJobSwitch: String?): ItemStack {
        val material = jobMaterial(jobSlot.job)
        val item = ItemStack(material)
        item.editMeta { meta ->
            val color = Jobs.colorCode(jobSlot.job)
            val name = Jobs.displayName(jobSlot.job)
            meta.displayName(legacy.deserialize("${color}§l슬롯 ${slotIndex + 1}: $name"))
            val lore = mutableListOf<Component>()
            lore.add(legacy.deserialize("§7직업: $color$name"))
            lore.add(legacy.deserialize("§7레벨: §f${jobSlot.level}"))
            lore.add(legacy.deserialize("§7경험치: §f${jobSlot.xp}"))
            lore.add(Component.empty())
            if (isActive) {
                lore.add(legacy.deserialize("§a§l✓ 현재 활성 슬롯"))
            } else {
                val canSwitch = lastJobSwitch != java.time.LocalDate.now().toString()
                if (canSwitch) {
                    lore.add(legacy.deserialize("§e▶ 클릭하여 이 슬롯으로 전환"))
                } else {
                    lore.add(legacy.deserialize("§c⏳ 오늘은 슬롯 전환 불가"))
                    lore.add(legacy.deserialize("§7내일 다시 전환할 수 있습니다."))
                }
            }
            meta.lore(lore)
            if (isActive) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true)
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS)
            }
        }
        return item
    }

    private fun buildEmptySlotItem(slotIndex: Int): ItemStack {
        val item = ItemStack(Material.LIME_STAINED_GLASS_PANE)
        item.editMeta { meta ->
            meta.displayName(legacy.deserialize("§a§l슬롯 ${slotIndex + 1}: §7(비어 있음)"))
            meta.lore(listOf(
                legacy.deserialize("§7직업이 설정되지 않았습니다."),
                Component.empty(),
                legacy.deserialize("§e▶ 클릭하여 직업 선택")
            ))
        }
        return item
    }

    private fun buildLockedSlotItem(slotIndex: Int): ItemStack {
        val item = ItemStack(Material.RED_STAINED_GLASS_PANE)
        item.editMeta { meta ->
            meta.displayName(legacy.deserialize("§c§l슬롯 ${slotIndex + 1}: §7(잠김)"))
            meta.lore(listOf(
                legacy.deserialize("§7추가 직업 슬롯입니다."),
                Component.empty(),
                legacy.deserialize("§e비용: §f5,000냥"),
                legacy.deserialize("§e▶ 클릭하여 슬롯 구매")
            ))
        }
        return item
    }

    private fun buildJobItem(material: Material, name: String, descLines: List<Any>): ItemStack {
        val item = ItemStack(material)
        item.editMeta { meta ->
            meta.displayName(legacy.deserialize(name))
            val lore = mutableListOf<Component>()
            for (line in descLines) {
                when (line) {
                    is Component -> lore.add(line)
                    is String -> lore.add(legacy.deserialize(line))
                }
            }
            lore.add(Component.empty())
            lore.add(legacy.deserialize("§a▶ 클릭하여 선택"))
            meta.lore(lore)
        }
        return item
    }

    private fun makeGlass(material: Material): ItemStack {
        val glass = ItemStack(material)
        glass.editMeta { meta -> meta.displayName(Component.text(" ")) }
        return glass
    }

    private fun jobMaterial(job: String): Material = when (job) {
        Jobs.MINER -> Material.IRON_PICKAXE
        Jobs.FARMER -> Material.GOLDEN_HOE
        Jobs.WARRIOR -> Material.DIAMOND_SWORD
        Jobs.FISHER -> Material.FISHING_ROD
        Jobs.WOODCUTTER -> Material.DIAMOND_AXE
        else -> Material.BARRIER
    }

    class JobSelectListener(private val plugin: NyaruPlugin) : Listener {

        private val legacy = LegacyComponentSerializer.legacySection()

        @EventHandler
        fun onInventoryClick(event: InventoryClickEvent) {
            val state = activeInventories[event.inventory] ?: return
            if (event.whoClicked != state.player) return
            event.isCancelled = true

            val player = state.player
            val slot = event.rawSlot

            when (state.mode) {
                JobGuiState.Mode.SLOTS -> handleSlotViewClick(player, slot, event.inventory)
                JobGuiState.Mode.JOB_SELECT -> handleJobSelectClick(player, slot, state.targetSlot, event.inventory)
            }
        }

        private fun handleSlotViewClick(player: Player, slot: Int, inv: Inventory) {
            val slotIndex = when (slot) {
                11 -> 0
                13 -> 1
                15 -> 2
                else -> return
            }
            val info = plugin.dataManager.getPlayer(player.uniqueId) ?: return

            when {
                slotIndex >= info.maxSlots -> {
                    // Try to buy the slot
                    player.closeInventory()
                    val success = plugin.dataManager.buySlot(player.uniqueId)
                    if (success) {
                        player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f)
                        player.sendMessage("§a§l✓ 슬롯 ${slotIndex + 1}을 구매했습니다! §7(5,000냥 차감)")
                        plugin.actionBarManager.refresh(player.uniqueId)
                        JobSelectGui(plugin, player).open()
                    } else {
                        val balance = plugin.dataManager.getBalance(player.uniqueId)
                        if (info.maxSlots >= 3) {
                            player.sendMessage("§c이미 최대 슬롯(3개)입니다.")
                        } else {
                            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f)
                            player.sendMessage("§c냥이 부족합니다. (보유: §e${balance}냥 §c/ 필요: §e5,000냥§c)")
                        }
                    }
                }
                slotIndex < info.jobSlots.size && info.jobSlots[slotIndex].job.isNotEmpty() -> {
                    // Switch to this slot
                    if (slotIndex == info.activeSlot) {
                        player.sendMessage("§7이미 활성화된 슬롯입니다.")
                        return
                    }
                    if (!plugin.dataManager.canSwitchJob(player.uniqueId)) {
                        player.closeInventory()
                        player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f)
                        player.sendMessage("§c⏳ 오늘은 슬롯 전환이 불가합니다. 내일 다시 시도하세요.")
                        return
                    }
                    player.closeInventory()
                    val switched = plugin.dataManager.switchActiveSlot(player.uniqueId, slotIndex)
                    if (switched) {
                        player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f)
                        val jobName = Jobs.displayName(info.jobSlots[slotIndex].job)
                        player.sendMessage("§a§l✓ 슬롯 ${slotIndex + 1} (${jobName})으로 전환했습니다!")
                        plugin.actionBarManager.refresh(player.uniqueId)
                        plugin.skillManager.refresh(player.uniqueId)
                    } else {
                        player.sendMessage("§c슬롯 전환에 실패했습니다.")
                    }
                }
                else -> {
                    // Open job selection for this empty slot
                    player.closeInventory()
                    JobSelectGui(plugin, player).openJobSelectView(slotIndex)
                }
            }
        }

        private fun handleJobSelectClick(player: Player, slot: Int, targetSlotIndex: Int, inv: Inventory) {
            // Back button
            if (slot == 0) {
                player.closeInventory()
                JobSelectGui(plugin, player).open()
                return
            }

            val jobKey = when (slot) {
                11 -> Jobs.MINER
                13 -> Jobs.FARMER
                15 -> Jobs.WARRIOR
                29 -> Jobs.FISHER
                31 -> Jobs.WOODCUTTER
                else -> return
            }

            player.closeInventory()
            plugin.dataManager.setJobOnSlot(player.uniqueId, targetSlotIndex, jobKey)

            // If this is slot 0 or it's the only job, switch to it
            val info = plugin.dataManager.getPlayer(player.uniqueId)
            if (info != null && info.activeSlot == targetSlotIndex) {
                plugin.skillManager.refresh(player.uniqueId)
            } else if (info != null && info.jobSlots.size == 1) {
                plugin.dataManager.switchActiveSlot(player.uniqueId, targetSlotIndex)
                plugin.skillManager.refresh(player.uniqueId)
            }

            plugin.actionBarManager.refresh(player.uniqueId)
            val jobName = Jobs.displayName(jobKey)
            player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f)
            player.sendMessage("§a§l✓ 슬롯 ${targetSlotIndex + 1}에 직업 §e${jobName}§a이(가) 설정되었습니다!")
        }

        @EventHandler
        fun onInventoryClose(event: InventoryCloseEvent) {
            activeInventories.remove(event.inventory)
        }
    }
}
