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
        var skills: SkillData,
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
            cache[uuid] = CachedPlayer(info = info, skills = SkillData())
            return info
        }

        val cfg = YamlConfiguration.loadConfiguration(file)
        val skillLevels = mutableMapOf<String, Int>()
        cfg.getConfigurationSection("skills")?.getKeys(false)?.forEach { key ->
            skillLevels[key] = cfg.getInt("skills.$key", 0)
        }

        val info = PlayerInfo(
            linked = cfg.getBoolean("linked", false),
            discordUserId = cfg.getString("discord-user-id"),
            minecraftName = cfg.getString("minecraft-name", name),
            balance = cfg.getInt("balance", 0),
            job = cfg.getString("job"),
            level = cfg.getInt("level", 1),
            xp = cfg.getInt("xp", 0),
            title = cfg.getString("title"),
            titleColor = cfg.getString("title-color"),
            titleIconUrl = cfg.getString("title-icon-url")
        )

        val skills = SkillData(
            skillPoints = cfg.getInt("skill-points", 0),
            levels = skillLevels
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
            skills = skills,
            home = home,
            lastDailyReward = cfg.getString("last-daily-reward")
        )
        return info
    }

    fun getPlayer(uuid: UUID): PlayerInfo? = cache[uuid]?.info
    fun getSkills(uuid: UUID): SkillData = cache[uuid]?.skills ?: SkillData()
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

    // ── Job ──

    fun setJob(uuid: UUID, job: String) {
        val cached = cache[uuid] ?: return
        cached.info = cached.info.copy(job = job, level = 1, xp = 0)
        cached.skills = SkillData(skillPoints = 0, levels = emptyMap())
        cached.dirty = true
        save(uuid)
    }

    fun grantXp(uuid: UUID, amount: Int): XpResult? {
        val cached = cache[uuid] ?: return null
        var level = cached.info.level
        var xp = cached.info.xp + amount
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
            val sp = cached.skills.skillPoints + 1
            cached.skills = cached.skills.copy(skillPoints = sp)
            newSkillPoints = sp
        }

        cached.info = cached.info.copy(level = level, xp = xp)
        cached.dirty = true

        return XpResult(
            level = level,
            xp = xp,
            leveledUp = leveledUp,
            xpToNextLevel = required,
            newSkillPoints = newSkillPoints
        )
    }

    // ── Skills ──

    fun upgradeSkill(uuid: UUID, skillKey: String): Triple<Boolean, Int, Int>? {
        val cached = cache[uuid] ?: return null
        val def = SkillRegistry.get(skillKey) ?: return null
        val currentLv = cached.skills.getLevel(skillKey)

        if (currentLv >= def.maxLevel) return null
        if (cached.skills.skillPoints <= 0) return null

        val levelReq = def.levelReqs.getOrNull(currentLv) ?: return null
        if (cached.info.level < levelReq) return null

        cached.skills = cached.skills.withUpgrade(skillKey)
        cached.dirty = true

        return Triple(true, currentLv + 1, cached.skills.skillPoints)
    }

    fun updateSkills(uuid: UUID, skills: SkillData) {
        val cached = cache[uuid] ?: return
        cached.skills = skills
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
        cfg.set("job", cached.info.job)
        cfg.set("level", cached.info.level)
        cfg.set("xp", cached.info.xp)
        cfg.set("skill-points", cached.skills.skillPoints)
        cfg.set("title", cached.info.title)
        cfg.set("title-color", cached.info.titleColor)
        cfg.set("title-icon-url", cached.info.titleIconUrl)
        cfg.set("last-daily-reward", cached.lastDailyReward)

        for ((key, lv) in cached.skills.levels) {
            cfg.set("skills.$key", lv)
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
