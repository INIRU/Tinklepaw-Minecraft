package dev.nyaru.minecraft.data

import dev.nyaru.minecraft.model.ShopItem
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

class ShopManager(private val dataFolder: File) {

    private val shopFile = File(dataFolder, "shop.yml")
    private val items = mutableListOf<ShopItem>()

    init {
        load()
    }

    fun getItems(): List<ShopItem> = items.toList()
    fun getItemsByCategory(category: String): List<ShopItem> = items.filter { it.category == category }
    fun getCategories(): List<String> = items.map { it.category }.distinct()

    fun addItem(item: ShopItem) {
        items.removeAll { it.id == item.id }
        items.add(item)
        save()
    }

    fun removeItem(id: String): Boolean {
        val removed = items.removeAll { it.id == id }
        if (removed) save()
        return removed
    }

    fun getItem(id: String): ShopItem? = items.find { it.id == id }
    fun getItemByMaterial(material: String): ShopItem? = items.find { it.material == material }

    private fun load() {
        if (!shopFile.exists()) {
            createDefaults()
            return
        }
        val cfg = YamlConfiguration.loadConfiguration(shopFile)
        items.clear()
        cfg.getMapList("items").forEach { map ->
            @Suppress("UNCHECKED_CAST")
            val m = map as Map<String, Any>
            items.add(ShopItem(
                id = m["id"] as? String ?: return@forEach,
                material = m["material"] as? String ?: return@forEach,
                displayName = m["display-name"] as? String ?: return@forEach,
                buyPrice = (m["buy-price"] as? Number)?.toInt() ?: 0,
                sellPrice = (m["sell-price"] as? Number)?.toInt() ?: 0,
                category = m["category"] as? String ?: "일반"
            ))
        }
    }

    fun save() {
        val cfg = YamlConfiguration()
        val list = items.map { item ->
            mapOf(
                "id" to item.id,
                "material" to item.material,
                "display-name" to item.displayName,
                "buy-price" to item.buyPrice,
                "sell-price" to item.sellPrice,
                "category" to item.category
            )
        }
        cfg.set("items", list)
        cfg.save(shopFile)
    }

    private fun createDefaults() {
        items.clear()
        items.addAll(listOf(
            // 광물 (판매 전용)
            ShopItem("coal", "COAL", "석탄", 0, 5, "광물"),
            ShopItem("raw_iron", "RAW_IRON", "철 원석", 0, 15, "광물"),
            ShopItem("raw_gold", "RAW_GOLD", "금 원석", 0, 25, "광물"),
            ShopItem("raw_copper", "RAW_COPPER", "구리 원석", 0, 8, "광물"),
            ShopItem("diamond", "DIAMOND", "다이아몬드", 0, 100, "광물"),
            ShopItem("emerald", "EMERALD", "에메랄드", 0, 80, "광물"),
            ShopItem("lapis_lazuli", "LAPIS_LAZULI", "청금석", 0, 10, "광물"),
            ShopItem("redstone", "REDSTONE", "레드스톤", 0, 8, "광물"),
            ShopItem("ancient_debris", "ANCIENT_DEBRIS", "고대 잔해", 0, 200, "광물"),
            // 제련 (판매 전용 - 순도 보너스 적용)
            ShopItem("iron_ingot", "IRON_INGOT", "철괴", 0, 20, "제련"),
            ShopItem("gold_ingot", "GOLD_INGOT", "금괴", 0, 35, "제련"),
            ShopItem("copper_ingot", "COPPER_INGOT", "구리괴", 0, 12, "제련"),
            ShopItem("netherite_scrap", "NETHERITE_SCRAP", "네더라이트 조각", 0, 250, "제련"),
            // 작물 (판매 전용)
            ShopItem("wheat", "WHEAT", "밀", 0, 3, "작물"),
            ShopItem("potato", "POTATO", "감자", 0, 3, "작물"),
            ShopItem("carrot", "CARROT", "당근", 0, 3, "작물"),
            ShopItem("beetroot", "BEETROOT", "비트", 0, 4, "작물"),
            ShopItem("melon_slice", "MELON_SLICE", "수박 조각", 0, 2, "작물"),
            ShopItem("pumpkin", "PUMPKIN", "호박", 0, 5, "작물"),
            ShopItem("sugar_cane", "SUGAR_CANE", "사탕수수", 0, 2, "작물"),
            ShopItem("cocoa_beans", "COCOA_BEANS", "코코아 콩", 0, 4, "작물"),
            // 씨앗 (구매 전용)
            ShopItem("wheat_seeds", "WHEAT_SEEDS", "밀 씨앗", 5, 0, "씨앗"),
            ShopItem("beetroot_seeds", "BEETROOT_SEEDS", "비트 씨앗", 5, 0, "씨앗"),
            ShopItem("melon_seeds", "MELON_SEEDS", "수박 씨앗", 10, 0, "씨앗"),
            ShopItem("pumpkin_seeds", "PUMPKIN_SEEDS", "호박 씨앗", 10, 0, "씨앗"),
            // 목재 (판매 전용)
            ShopItem("oak_log", "OAK_LOG", "참나무 원목", 0, 2, "목재"),
            ShopItem("birch_log", "BIRCH_LOG", "자작나무 원목", 0, 2, "목재"),
            ShopItem("spruce_log", "SPRUCE_LOG", "가문비나무 원목", 0, 2, "목재"),
            ShopItem("jungle_log", "JUNGLE_LOG", "정글나무 원목", 0, 3, "목재"),
            ShopItem("acacia_log", "ACACIA_LOG", "아카시아 원목", 0, 2, "목재"),
            ShopItem("dark_oak_log", "DARK_OAK_LOG", "짙은 참나무 원목", 0, 3, "목재"),
            ShopItem("cherry_log", "CHERRY_LOG", "벚나무 원목", 0, 4, "목재"),
            ShopItem("mangrove_log", "MANGROVE_LOG", "맹그로브 원목", 0, 3, "목재"),
            // 물고기 (판매 전용)
            ShopItem("cod", "COD", "대구", 0, 5, "물고기"),
            ShopItem("salmon", "SALMON", "연어", 0, 8, "물고기"),
            ShopItem("tropical_fish", "TROPICAL_FISH", "열대어", 0, 15, "물고기"),
            ShopItem("pufferfish", "PUFFERFISH", "복어", 0, 20, "물고기"),
            // 전리품 (판매 전용)
            ShopItem("rotten_flesh", "ROTTEN_FLESH", "썩은 살점", 0, 1, "전리품"),
            ShopItem("bone", "BONE", "뼈", 0, 3, "전리품"),
            ShopItem("string", "STRING", "실", 0, 3, "전리품"),
            ShopItem("spider_eye", "SPIDER_EYE", "거미 눈", 0, 5, "전리품"),
            ShopItem("ender_pearl", "ENDER_PEARL", "엔더 진주", 0, 30, "전리품"),
            ShopItem("blaze_rod", "BLAZE_ROD", "블레이즈 막대", 0, 25, "전리품"),
            ShopItem("ghast_tear", "GHAST_TEAR", "가스트 눈물", 0, 40, "전리품"),
            ShopItem("phantom_membrane", "PHANTOM_MEMBRANE", "팬텀 막", 0, 15, "전리품"),
            ShopItem("gunpowder", "GUNPOWDER", "화약", 0, 5, "전리품")
        ))
        save()
    }
}
