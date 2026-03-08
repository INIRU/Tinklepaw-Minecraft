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
    HelpEntry("/연동해제", Material.BARRIER,
        "Discord 연동 해제",
        "/연동해제",
        listOf("해제 후 재연동 가능")
    ),
    HelpEntry("/잔고", Material.GOLD_NUGGET,
        "포인트 잔고 확인",
        "/잔고",
        listOf("채굴·수확·판매로 포인트 획득")
    ),
    HelpEntry("/직업", Material.IRON_PICKAXE,
        "직업 정보 및 레벨 확인",
        "/직업",
        listOf("직업 NPC에서 광부/농부 선택", "레벨업 시 스킬 포인트 +1")
    ),
    HelpEntry("/스킬", Material.ENCHANTED_BOOK,
        "스킬 확인 및 업그레이드",
        "/스킬",
        listOf("직업별 전용 스킬 존재", "레벨 조건 충족 시 해금")
    ),
    HelpEntry("/시세", Material.EMERALD,
        "현재 아이템 시세 확인",
        "/시세",
        listOf("가격은 시장 상황에 따라 변동", "신선도·순정도로 추가 보너스")
    ),
    HelpEntry("/퀘스트", Material.MAP,
        "오늘의 일일 퀘스트",
        "/퀘스트",
        listOf("매일 초기화", "완료 시 보상 포인트 지급")
    ),
    HelpEntry("/거래소", Material.CHEST,
        "P2P 플레이어 거래소",
        "/거래소",
        listOf("다른 플레이어의 리스팅 구매", "내 아이템 판매 등록 가능")
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
    HelpEntry("/로그", Material.WRITABLE_BOOK,
        "블럭 로그 조사 모드 (관리자)",
        "/로그",
        listOf("ON 후 블럭 좌클릭 → 해당 블럭 기록 조회", "관리자 전용")
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
        val slots = listOf(10, 12, 14, 16, 20, 22, 24, 26, 31, 40)
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
