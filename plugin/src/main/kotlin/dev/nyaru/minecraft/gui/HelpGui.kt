package dev.nyaru.minecraft.gui

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

private data class HelpEntry(
    val command: String,
    val material: Material,
    val description: String,
    val usage: String,
    val tips: List<String> = emptyList()
)

private val ENTRIES = listOf(
    HelpEntry("/연동", Material.NAME_TAG,
        "Discord 계정 연동",
        "/연동",
        listOf("Discord ID를 입력하면 OTP 코드 발급", "Discord에서 /연동확인 <코드> 입력")
    ),
    HelpEntry("/잔고", Material.GOLD_NUGGET,
        "포인트 잔고 확인",
        "/잔고",
        listOf("채굴·수확·판매로 포인트 획득")
    ),
    HelpEntry("/직업", Material.IRON_PICKAXE,
        "직업 슬롯 관리",
        "/직업",
        listOf("최대 3개 슬롯 (추가 구매 5,000냥)", "슬롯 전환 1일 1회 무료, 추가 10,000냥", "직업 변경 5,000냥")
    ),
    HelpEntry("/스킬", Material.ENCHANTED_BOOK,
        "스킬 확인 및 업그레이드",
        "/스킬",
        listOf("직업별 전용 스킬 존재", "레벨 조건 충족 시 해금")
    ),
    HelpEntry("/교환", Material.CHEST,
        "플레이어 간 아이템 교환",
        "/교환 <플레이어>",
        listOf("상대방에게 교환 요청 전송", "양쪽 모두 확인해야 교환 성사", "/교환 수락 · /교환 거절")
    ),
    HelpEntry("/팀", Material.CYAN_WOOL,
        "팀 관리 (블럭 보호 공유)",
        "/팀 <추가|제거|목록>",
        listOf("팀원은 내 보호 블럭 접근 가능", "/팀 추가 <플레이어>")
    ),
    HelpEntry("/보호", Material.SHIELD,
        "블럭 보호 모드 ON/OFF",
        "/보호",
        listOf("ON 상태에서 설치한 블럭만 보호됨", "액션바에 🔒/🔓 상태 표시")
    ),
    HelpEntry("/스폰", Material.COMPASS,
        "스폰으로 귀환",
        "/스폰",
        listOf("1,000냥 비용", "3초 카운트다운 (이동 시 취소)")
    ),
    HelpEntry("/집", Material.RED_BED,
        "집 설정/이동/삭제",
        "/집 <설정|가기|삭제>",
        listOf("설정 1,000냥 / 이동 500냥", "3초 카운트다운 (이동 시 취소)")
    ),
    HelpEntry("/배낭", Material.CHEST,
        "개인 배낭 (추가 보관함)",
        "/배낭 [업그레이드]",
        listOf("개방: 10,000냥 (27칸)", "업그레이드: 30,000냥 (54칸)", "사망 시 배낭 아이템 유지")
    ),
    HelpEntry("/도움말", Material.BOOK,
        "명령어 도움말 GUI",
        "/도움말",
        listOf("현재 보고 있는 화면입니다.")
    ),
)

class HelpGui(private val player: Player) {

    companion object {
        val activeInventories = ConcurrentHashMap<Inventory, Boolean>()
    }

    fun open() {
        val legacy = LegacyComponentSerializer.legacySection()
        val inv = Bukkit.createInventory(null, 54, legacy.deserialize("§6§l📖 방울냥 도움말"))

        // Glass filler
        val glass = ItemStack(Material.BLACK_STAINED_GLASS_PANE)
        val glassMeta = glass.itemMeta
        glassMeta.displayName(Component.text(" "))
        glass.itemMeta = glassMeta
        for (i in 0 until 54) inv.setItem(i, glass)

        // Command items — rows 1-2, starting slot 10
        val slots = listOf(10, 12, 14, 16, 19, 21, 23, 25, 37, 39, 41)
        ENTRIES.forEachIndexed { i, entry ->
            val slot = slots.getOrNull(i) ?: return@forEachIndexed
            val item = ItemStack(entry.material)
            val meta = item.itemMeta
            meta.displayName(legacy.deserialize("§e§l${entry.command}"))
            val lore = mutableListOf(
                legacy.deserialize("§7${entry.description}"),
                Component.empty(),
                legacy.deserialize("§8사용법: §f${entry.usage}")
            )
            if (entry.tips.isNotEmpty()) {
                lore.add(Component.empty())
                entry.tips.forEach { lore.add(legacy.deserialize("§8▸ §7$it")) }
            }
            meta.lore(lore)
            item.itemMeta = meta
            inv.setItem(slot, item)
        }

        activeInventories[inv] = true
        player.openInventory(inv)
        player.playSound(player.location, Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.0f)
    }

    class HelpGuiListener : Listener {
        @EventHandler
        fun onClick(event: InventoryClickEvent) {
            if (activeInventories.containsKey(event.inventory)) event.isCancelled = true
        }

        @EventHandler
        fun onClose(event: InventoryCloseEvent) {
            activeInventories.remove(event.inventory)
        }
    }
}
