package dev.nyaru.minecraft.gui

import dev.nyaru.minecraft.NyaruPlugin
import dev.nyaru.minecraft.model.Jobs
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

class JobSelectGui(private val plugin: NyaruPlugin, private val player: Player) {

    companion object {
        val activeInventories = ConcurrentHashMap<Inventory, Player>()
    }

    private val legacy = LegacyComponentSerializer.legacySection()

    fun open() {
        val currentJob = plugin.dataManager.getPlayer(player.uniqueId)?.job
        val isChange = currentJob != null
        val cost = plugin.config.getInt("job-change-cost", 500)

        val inv = Bukkit.createInventory(null, 45, legacy.deserialize("§6§l⚒ 직업 선택"))

        val glass = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
        val glassMeta = glass.itemMeta
        glassMeta.displayName(Component.text(" "))
        glass.itemMeta = glassMeta
        for (i in 0 until 45) inv.setItem(i, glass)

        // Slot 11: Miner
        inv.setItem(11, buildJobItem(
            Material.IRON_PICKAXE,
            "§b§l광부 (Miner)",
            listOf(
                "§7광물 채굴로 냥을 획득합니다.",
                Component.empty(),
                "§e[ 보너스 ]",
                "§8▸ §f심층부(Y -40↓): 순정도 보너스",
                "§8▸ §f다이아/에메랄드: 희귀 광물 보너스",
                "§8▸ §f철퇴 특수 능력 활성화"
            ),
            isChange, cost
        ))

        // Slot 13: Farmer
        inv.setItem(13, buildJobItem(
            Material.GOLDEN_HOE,
            "§a§l농부 (Farmer)",
            listOf(
                "§7작물 재배로 냥을 획득합니다.",
                Component.empty(),
                "§e[ 보너스 ]",
                "§8▸ §f수확 직후 신선도 100% → 최고가",
                "§8▸ §f신선한 작물: 최대 +40% 냥",
                "§8▸ §f괭이로 공격 시 피흡"
            ),
            isChange, cost
        ))

        // Slot 15: Warrior
        inv.setItem(15, buildJobItem(
            Material.DIAMOND_SWORD,
            "§c§l전사 (Warrior)",
            listOf(
                "§7몬스터 사냥으로 냥을 획득합니다.",
                Component.empty(),
                "§e[ 보너스 ]",
                "§8▸ §f검 강화로 추가 데미지",
                "§8▸ §f치명타 및 전투 스킬 특화",
                "§8▸ §f전투의 함성으로 적 약화"
            ),
            isChange, cost
        ))

        // Slot 29: Fisher
        inv.setItem(29, buildJobItem(
            Material.FISHING_ROD,
            "§3§l어부 (Fisher)",
            listOf(
                "§7낚시로 냥을 획득합니다.",
                Component.empty(),
                "§e[ 보너스 ]",
                "§8▸ §f보물 확률 증가",
                "§8▸ §f바다의 축복 — 수중 이동 강화",
                "§8▸ §f희귀 아이템 낚시 확률"
            ),
            isChange, cost
        ))

        // Slot 31: Woodcutter
        inv.setItem(31, buildJobItem(
            Material.DIAMOND_AXE,
            "§6§l벌목꾼 (Woodcutter)",
            listOf(
                "§7벌목으로 냥을 획득합니다.",
                Component.empty(),
                "§e[ 보너스 ]",
                "§8▸ §f한번에 연결된 나무 전체 베기",
                "§8▸ §f잎 자동 파괴",
                "§8▸ §f도끼 채굴 속도 증가"
            ),
            isChange, cost
        ))

        activeInventories[inv] = player
        player.openInventory(inv)
        player.playSound(player.location, Sound.BLOCK_CHEST_OPEN, 0.8f, 1.0f)
    }

    private fun buildJobItem(
        material: Material,
        name: String,
        descLines: List<Any>,
        isChange: Boolean,
        cost: Int
    ): ItemStack {
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
            if (isChange) {
                lore.add(legacy.deserialize("§c⚠ 직업 변경 시 레벨/경험치 초기화!"))
                lore.add(legacy.deserialize("§7변경 비용: §e${cost}냥"))
            }
            lore.add(legacy.deserialize("§a▶ 클릭하여 선택"))
            meta.lore(lore)
        }
        return item
    }

    class JobSelectListener(private val plugin: NyaruPlugin) : Listener {

        private val legacy = LegacyComponentSerializer.legacySection()

        @EventHandler
        fun onInventoryClick(event: InventoryClickEvent) {
            val player = activeInventories[event.inventory] ?: return
            if (event.whoClicked != player) return
            event.isCancelled = true

            val slot = event.rawSlot
            val jobKey = when (slot) {
                11 -> Jobs.MINER
                13 -> Jobs.FARMER
                15 -> Jobs.WARRIOR
                29 -> Jobs.FISHER
                31 -> Jobs.WOODCUTTER
                else -> return
            }
            val jobKr = Jobs.displayName(jobKey)
            val cost = plugin.config.getInt("job-change-cost", 500)
            val currentJob = plugin.dataManager.getPlayer(player.uniqueId)?.job

            // If changing job (not first time), charge cost
            if (currentJob != null) {
                if (!plugin.dataManager.spendBalance(player.uniqueId, cost)) {
                    player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f)
                    val balance = plugin.dataManager.getBalance(player.uniqueId)
                    player.sendMessage("§c냥이 부족합니다. (보유: §e${balance}냥 §c/ 필요: §e${cost}냥§c)")
                    player.closeInventory()
                    return
                }
            }

            player.closeInventory()
            plugin.dataManager.setJob(player.uniqueId, jobKey)
            plugin.dataManager.save(player.uniqueId)

            player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f)
            player.sendMessage("§a§l✓ 직업이 §e${jobKr}§a§l(으)로 설정되었습니다!")
        }

        @EventHandler
        fun onInventoryClose(event: InventoryCloseEvent) {
            activeInventories.remove(event.inventory)
        }
    }
}
