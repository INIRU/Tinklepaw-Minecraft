package dev.nyaru.minecraft.data

import dev.nyaru.minecraft.model.*
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class DataManager(private val dataFolder: File) {

    private val playerDataDir = File(dataFolder, "playerdata").also { it.mkdirs() }

    private class CachedPlayer(
        var info: PlayerInfo,
        var slotSkills: MutableMap<Int, SkillData> = mutableMapOf(),
        var home: HomeLocation? = null,
        var lastDailyReward: String? = null,
        var dirty: Boolean = false
    )

    private val cache = ConcurrentHashMap<UUID, CachedPlayer>()

    // ── Player Loading ──

    fun loadPlayer(uuid: UUID, name: String): PlayerInfo {
        cache[uuid]?.let { return it.info }

        val file = File(playerDataDir, "$uuid.yml")
        if (!file.exists()) {
            val info = PlayerInfo(linked = false, minecraftName = name)
            cache[uuid] = CachedPlayer(info = info)
            return info
        }

        val cfg = YamlConfiguration.loadConfiguration(file)

        // Detect old format (has "job" key but no "job-slots")
        val slotSkills = mutableMapOf<Int, SkillData>()
        val jobSlots: List<JobSlot>
        val activeSlot: Int
        val maxSlots: Int
        val lastJobSwitch: String?

        if (cfg.contains("job") && !cfg.contains("job-slots")) {
            // Migrate old format
            val oldJob = cfg.getString("job")
            val oldLevel = cfg.getInt("level", 1)
            val oldXp = cfg.getInt("xp", 0)
            jobSlots = if (oldJob != null) listOf(JobSlot(oldJob, oldLevel, oldXp)) else emptyList()
            activeSlot = 0
            maxSlots = 1
            lastJobSwitch = null

            // Migrate old skills to slot 0
            val oldSkillLevels = mutableMapOf<String, Int>()
            cfg.getConfigurationSection("skills")?.getKeys(false)?.forEach { key ->
                oldSkillLevels[key] = cfg.getInt("skills.$key", 0)
            }
            slotSkills[0] = SkillData(
                skillPoints = cfg.getInt("skill-points", 0),
                levels = oldSkillLevels
            )
        } else {
            // New format
            val slots = mutableListOf<JobSlot>()
            val slotsSection = cfg.getConfigurationSection("job-slots")
            if (slotsSection != null) {
                val indices = slotsSection.getKeys(false).mapNotNull { it.toIntOrNull() }.sorted()
                for (idx in indices) {
                    val job = cfg.getString("job-slots.$idx.job") ?: ""
                    val level = cfg.getInt("job-slots.$idx.level", 1)
                    val xp = cfg.getInt("job-slots.$idx.xp", 0)
                    slots.add(JobSlot(job, level, xp))
                }
            }
            jobSlots = slots
            activeSlot = cfg.getInt("active-slot", 0)
            maxSlots = cfg.getInt("max-slots", 1)
            lastJobSwitch = cfg.getString("last-job-switch")

            // Load per-slot skills
            val skillsSection = cfg.getConfigurationSection("skills")
            if (skillsSection != null) {
                val slotIndices = skillsSection.getKeys(false).mapNotNull { it.toIntOrNull() }
                for (slotIdx in slotIndices) {
                    val levels = mutableMapOf<String, Int>()
                    cfg.getConfigurationSection("skills.$slotIdx")?.getKeys(false)?.forEach { key ->
                        levels[key] = cfg.getInt("skills.$slotIdx.$key", 0)
                    }
                    val sp = cfg.getInt("skill-points.$slotIdx", 0)
                    slotSkills[slotIdx] = SkillData(skillPoints = sp, levels = levels)
                }
            }
        }

        val info = PlayerInfo(
            linked = cfg.getBoolean("linked", false),
            discordUserId = cfg.getString("discord-user-id"),
            minecraftName = cfg.getString("minecraft-name", name),
            balance = cfg.getInt("balance", 0),
            jobSlots = jobSlots,
            activeSlot = activeSlot,
            maxSlots = maxSlots,
            lastJobSwitch = lastJobSwitch,
            title = cfg.getString("title"),
            titleColor = cfg.getString("title-color"),
            titleIconUrl = cfg.getString("title-icon-url")
        )

        val home = if (cfg.getBoolean("home.set", false)) {
            HomeLocation(
                world = cfg.getString("home.world", "world")!!,
                x = cfg.getDouble("home.x"),
                y = cfg.getDouble("home.y"),
                z = cfg.getDouble("home.z"),
                yaw = cfg.getDouble("home.yaw").toFloat(),
                pitch = cfg.getDouble("home.pitch").toFloat()
            )
        } else null

        cache[uuid] = CachedPlayer(
            info = info,
            slotSkills = slotSkills,
            home = home,
            lastDailyReward = cfg.getString("last-daily-reward")
        )
        return info
    }

    fun getPlayer(uuid: UUID): PlayerInfo? = cache[uuid]?.info

    fun getSkills(uuid: UUID): SkillData {
        val cached = cache[uuid] ?: return SkillData()
        val slot = cached.info.activeSlot
        return cached.slotSkills[slot] ?: SkillData()
    }

    fun isLinked(uuid: UUID): Boolean = cache[uuid]?.info?.linked ?: false

    // ── Link ──

    fun setLinked(uuid: UUID, discordId: String, name: String) {
        val cached = cache[uuid] ?: return
        cached.info = cached.info.copy(linked = true, discordUserId = discordId, minecraftName = name)
        cached.dirty = true
        save(uuid)
    }

    fun setUnlinked(uuid: UUID) {
        val cached = cache[uuid] ?: return
        cached.info = cached.info.copy(linked = false, discordUserId = null)
        cached.dirty = true
        save(uuid)
    }

    // ── Balance (냥) ──

    fun getBalance(uuid: UUID): Int = cache[uuid]?.info?.balance ?: 0

    fun addBalance(uuid: UUID, amount: Int): Int {
        val cached = cache[uuid] ?: return 0
        val newBalance = (cached.info.balance + amount).coerceAtLeast(0)
        cached.info = cached.info.copy(balance = newBalance)
        cached.dirty = true
        return newBalance
    }

    fun hasBalance(uuid: UUID, amount: Int): Boolean = getBalance(uuid) >= amount

    fun spendBalance(uuid: UUID, amount: Int): Boolean {
        if (!hasBalance(uuid, amount)) return false
        addBalance(uuid, -amount)
        return true
    }

    // ── Job Slots ──

    /** Set the job on a specific slot index, resetting level/xp/skills for that slot */
    fun setJobOnSlot(uuid: UUID, slotIndex: Int, job: String) {
        val cached = cache[uuid] ?: return
        val slots = cached.info.jobSlots.toMutableList()
        while (slots.size <= slotIndex) slots.add(JobSlot(""))
        slots[slotIndex] = JobSlot(job, 1, 0)
        cached.info = cached.info.copy(jobSlots = slots)
        cached.slotSkills[slotIndex] = SkillData(skillPoints = 0, levels = emptyMap())
        cached.dirty = true
        save(uuid)
    }

    /** Legacy setJob — sets job on the currently active slot */
    fun setJob(uuid: UUID, job: String) {
        val cached = cache[uuid] ?: return
        setJobOnSlot(uuid, cached.info.activeSlot, job)
    }

    /** Switch to a different active slot. Returns false if slot is invalid or unowned */
    fun switchActiveSlot(uuid: UUID, slotIndex: Int): Boolean {
        val cached = cache[uuid] ?: return false
        if (slotIndex >= cached.info.maxSlots) return false
        if (slotIndex >= cached.info.jobSlots.size) return false
        cached.info = cached.info.copy(
            activeSlot = slotIndex,
            lastJobSwitch = LocalDate.now().toString()
        )
        cached.dirty = true
        save(uuid)
        return true
    }

    /** Purchase an additional slot for 5000냥. Returns false if already at max or insufficient funds */
    fun buySlot(uuid: UUID): Boolean {
        val cached = cache[uuid] ?: return false
        if (cached.info.maxSlots >= 3) return false
        val cost = 5000
        if (!hasBalance(uuid, cost)) return false
        spendBalance(uuid, cost)
        cached.info = cached.info.copy(maxSlots = cached.info.maxSlots + 1)
        cached.dirty = true
        save(uuid)
        return true
    }

    /** Returns true if the player can switch slots today (1-day cooldown) */
    fun canSwitchJob(uuid: UUID): Boolean {
        val cached = cache[uuid] ?: return false
        val lastSwitch = cached.info.lastJobSwitch ?: return true
        return lastSwitch != LocalDate.now().toString()
    }

    fun grantXp(uuid: UUID, amount: Int): XpResult? {
        val cached = cache[uuid] ?: return null
        val slotIdx = cached.info.activeSlot
        val slot = cached.info.jobSlots.getOrNull(slotIdx) ?: return null

        var level = slot.level
        var xp = slot.xp + amount
        var leveledUp = false
        var newSkillPoints: Int? = null

        var required = LevelFormula.xpRequired(level)
        while (xp >= required) {
            xp -= required
            level++
            leveledUp = true
            required = LevelFormula.xpRequired(level)
        }

        if (leveledUp) {
            val skills = cached.slotSkills[slotIdx] ?: SkillData()
            val sp = skills.skillPoints + 1
            cached.slotSkills[slotIdx] = skills.copy(skillPoints = sp)
            newSkillPoints = sp
        }

        val slots = cached.info.jobSlots.toMutableList()
        slots[slotIdx] = slot.copy(level = level, xp = xp)
        cached.info = cached.info.copy(jobSlots = slots)
        cached.dirty = true

        return XpResult(
            level = level,
            xp = xp,
            leveledUp = leveledUp,
            xpToNextLevel = required,
            newSkillPoints = newSkillPoints
        )
    }

    fun setLevel(uuid: UUID, targetLevel: Int): Int {
        val cached = cache[uuid] ?: return 0
        val slotIdx = cached.info.activeSlot
        val slot = cached.info.jobSlots.getOrNull(slotIdx) ?: return 0
        val oldLevel = slot.level
        val levelsGained = (targetLevel - oldLevel).coerceAtLeast(0)
        val slots = cached.info.jobSlots.toMutableList()
        slots[slotIdx] = slot.copy(level = targetLevel, xp = 0)
        cached.info = cached.info.copy(jobSlots = slots)
        if (levelsGained > 0) {
            val skills = cached.slotSkills[slotIdx] ?: SkillData()
            cached.slotSkills[slotIdx] = skills.copy(skillPoints = skills.skillPoints + levelsGained)
        }
        cached.dirty = true
        save(uuid)
        return levelsGained
    }

    // ── Skills ──

    fun upgradeSkill(uuid: UUID, skillKey: String): Triple<Boolean, Int, Int>? {
        val cached = cache[uuid] ?: return null
        val def = SkillRegistry.get(skillKey) ?: return null
        val slotIdx = cached.info.activeSlot
        val skills = cached.slotSkills[slotIdx] ?: SkillData()
        val currentLv = skills.getLevel(skillKey)

        if (currentLv >= def.maxLevel) return null
        if (skills.skillPoints <= 0) return null

        val levelReq = def.levelReqs.getOrNull(currentLv) ?: return null
        if (cached.info.level < levelReq) return null

        val updated = skills.withUpgrade(skillKey)
        cached.slotSkills[slotIdx] = updated
        cached.dirty = true

        return Triple(true, currentLv + 1, updated.skillPoints)
    }

    fun updateSkills(uuid: UUID, skills: SkillData) {
        val cached = cache[uuid] ?: return
        val slotIdx = cached.info.activeSlot
        cached.slotSkills[slotIdx] = skills
        cached.dirty = true
    }

    // ── Home ──

    fun setHome(uuid: UUID, world: String, x: Double, y: Double, z: Double, yaw: Float, pitch: Float) {
        val cached = cache[uuid] ?: return
        cached.home = HomeLocation(world, x, y, z, yaw, pitch)
        cached.dirty = true
        save(uuid)
    }

    fun getHome(uuid: UUID): HomeLocation? = cache[uuid]?.home

    fun removeHome(uuid: UUID) {
        val cached = cache[uuid] ?: return
        cached.home = null
        cached.dirty = true
        save(uuid)
    }

    // ── Daily Reward ──

    fun canClaimDaily(uuid: UUID): Boolean {
        val cached = cache[uuid] ?: return false
        val today = LocalDate.now().toString()
        return cached.lastDailyReward != today
    }

    fun claimDaily(uuid: UUID, reward: Int): Boolean {
        val cached = cache[uuid] ?: return false
        val today = LocalDate.now().toString()
        if (cached.lastDailyReward == today) return false
        cached.lastDailyReward = today
        addBalance(uuid, reward)
        cached.dirty = true
        save(uuid)
        return true
    }

    // ── Info Update ──

    fun updatePlayerInfo(uuid: UUID, updater: (PlayerInfo) -> PlayerInfo) {
        val cached = cache[uuid] ?: return
        cached.info = updater(cached.info)
        cached.dirty = true
    }

    // ── Persistence ──

    fun save(uuid: UUID) {
        val cached = cache[uuid] ?: return
        if (!cached.dirty) return

        val file = File(playerDataDir, "$uuid.yml")
        val cfg = YamlConfiguration()

        cfg.set("linked", cached.info.linked)
        cfg.set("discord-user-id", cached.info.discordUserId)
        cfg.set("minecraft-name", cached.info.minecraftName)
        cfg.set("balance", cached.info.balance)
        cfg.set("active-slot", cached.info.activeSlot)
        cfg.set("max-slots", cached.info.maxSlots)
        cfg.set("last-job-switch", cached.info.lastJobSwitch)
        cfg.set("title", cached.info.title)
        cfg.set("title-color", cached.info.titleColor)
        cfg.set("title-icon-url", cached.info.titleIconUrl)
        cfg.set("last-daily-reward", cached.lastDailyReward)

        // Job slots
        for ((idx, slot) in cached.info.jobSlots.withIndex()) {
            cfg.set("job-slots.$idx.job", slot.job)
            cfg.set("job-slots.$idx.level", slot.level)
            cfg.set("job-slots.$idx.xp", slot.xp)
        }

        // Skills per slot
        for ((slotIdx, skills) in cached.slotSkills) {
            cfg.set("skill-points.$slotIdx", skills.skillPoints)
            for ((key, lv) in skills.levels) {
                cfg.set("skills.$slotIdx.$key", lv)
            }
        }

        if (cached.home != null) {
            cfg.set("home.set", true)
            cfg.set("home.world", cached.home!!.world)
            cfg.set("home.x", cached.home!!.x)
            cfg.set("home.y", cached.home!!.y)
            cfg.set("home.z", cached.home!!.z)
            cfg.set("home.yaw", cached.home!!.yaw.toDouble())
            cfg.set("home.pitch", cached.home!!.pitch.toDouble())
        } else {
            cfg.set("home.set", false)
        }

        cfg.save(file)
        cached.dirty = false
    }

    fun saveAll() {
        for (uuid in cache.keys) {
            save(uuid)
        }
    }

    fun unloadPlayer(uuid: UUID) {
        save(uuid)
        cache.remove(uuid)
    }
}
