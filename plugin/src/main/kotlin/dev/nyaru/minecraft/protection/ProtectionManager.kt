package dev.nyaru.minecraft.protection

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.bukkit.Location
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class ProtectionManager(private val dataFolder: File) {

    private val gson = Gson()
    private val protectionsFile = File(dataFolder, "protections.json")
    private val teamsFile = File(dataFolder, "teams.json")

    // "world,x,y,z" -> ownerUuid
    private val protections = ConcurrentHashMap<String, String>()
    // ownerUuid -> set of member uuids
    private val teams = ConcurrentHashMap<String, MutableSet<String>>()
    // players with protection mode ON (in-memory only, resets on restart)
    private val protectionEnabled = ConcurrentHashMap.newKeySet<String>()

    init {
        dataFolder.mkdirs()
        load()
    }

    fun locationKey(loc: Location) =
        "${loc.world?.name},${loc.blockX},${loc.blockY},${loc.blockZ}"

    fun protect(loc: Location, ownerUuid: String) {
        protections[locationKey(loc)] = ownerUuid
        saveAsync()
    }

    /** Returns true if the block was unprotected successfully (requester is owner). */
    fun unprotect(loc: Location, requesterUuid: String): Boolean {
        val key = locationKey(loc)
        val owner = protections[key] ?: return true // not protected, ok
        if (owner != requesterUuid) return false
        protections.remove(key)
        saveAsync()
        return true
    }

    fun getOwner(loc: Location): String? = protections[locationKey(loc)]

    fun isProtected(loc: Location) = protections.containsKey(locationKey(loc))

    fun canAccess(loc: Location, playerUuid: String): Boolean {
        val owner = getOwner(loc) ?: return true
        if (owner == playerUuid) return true
        return teams[owner]?.contains(playerUuid) == true
    }

    fun addTeamMember(ownerUuid: String, memberUuid: String) {
        teams.getOrPut(ownerUuid) { mutableSetOf() }.add(memberUuid)
        saveAsync()
    }

    fun removeTeamMember(ownerUuid: String, memberUuid: String): Boolean {
        val removed = teams[ownerUuid]?.remove(memberUuid) ?: false
        if (removed) saveAsync()
        return removed
    }

    fun getTeamMembers(ownerUuid: String): Set<String> = teams[ownerUuid] ?: emptySet()

    fun isProtectionEnabled(uuid: String) = protectionEnabled.contains(uuid)

    /** Returns the new state (true = enabled, false = disabled). */
    fun toggleProtection(uuid: String): Boolean {
        return if (protectionEnabled.remove(uuid)) false
        else { protectionEnabled.add(uuid); true }
    }

    private fun load() {
        if (protectionsFile.exists()) {
            runCatching {
                val type = object : TypeToken<Map<String, String>>() {}.type
                val data: Map<String, String> = gson.fromJson(protectionsFile.readText(), type) ?: emptyMap()
                protections.putAll(data)
            }
        }
        if (teamsFile.exists()) {
            runCatching {
                val type = object : TypeToken<Map<String, Set<String>>>() {}.type
                val data: Map<String, Set<String>> = gson.fromJson(teamsFile.readText(), type) ?: emptyMap()
                data.forEach { (k, v) -> teams[k] = v.toMutableSet() }
            }
        }
    }

    fun save() {
        runCatching { protectionsFile.writeText(gson.toJson(protections.toMap())) }
        runCatching { teamsFile.writeText(gson.toJson(teams.mapValues { it.value.toSet() })) }
    }

    private fun saveAsync() {
        Thread(::save, "nyaru-protection-save").also { it.isDaemon = true }.start()
    }
}
