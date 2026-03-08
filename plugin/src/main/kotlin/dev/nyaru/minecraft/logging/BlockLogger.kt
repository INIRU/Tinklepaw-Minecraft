package dev.nyaru.minecraft.logging

import org.bukkit.Location
import org.bukkit.entity.Player
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

enum class BlockAction { PLACE, BREAK }

data class BlockLogEntry(
    val timestamp: LocalDateTime,
    val action: BlockAction,
    val playerName: String,
    val playerUuid: String,
    val material: String,
    val world: String,
    val x: Int,
    val y: Int,
    val z: Int
) {
    fun toLine(): String {
        val ts = timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        return "$ts\t${action.name}\t$playerName\t$playerUuid\t$material\t$world\t$x\t$y\t$z"
    }

    companion object {
        fun fromLine(line: String): BlockLogEntry? = runCatching {
            val p = line.split("\t")
            BlockLogEntry(
                timestamp = LocalDateTime.parse(p[0], DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                action = BlockAction.valueOf(p[1]),
                playerName = p[2],
                playerUuid = p[3],
                material = p[4],
                world = p[5],
                x = p[6].toInt(),
                y = p[7].toInt(),
                z = p[8].toInt()
            )
        }.getOrNull()
    }
}

class BlockLogger(private val dataFolder: File) {

    private val logsDir = File(dataFolder, "logs").also { it.mkdirs() }
    private val queue = LinkedBlockingQueue<BlockLogEntry>(10_000)
    private val running = AtomicBoolean(true)

    init {
        // Background writer thread
        Thread({
            while (running.get() || queue.isNotEmpty()) {
                val entry = queue.poll(200, java.util.concurrent.TimeUnit.MILLISECONDS) ?: continue
                writeEntry(entry)
            }
        }, "nyaru-block-logger").also { it.isDaemon = true; it.start() }
    }

    fun log(player: Player, action: BlockAction, loc: Location, material: String) {
        queue.offer(BlockLogEntry(
            timestamp = LocalDateTime.now(),
            action = action,
            playerName = player.name,
            playerUuid = player.uniqueId.toString(),
            material = material,
            world = loc.world?.name ?: "unknown",
            x = loc.blockX, y = loc.blockY, z = loc.blockZ
        ))
    }

    /** Read the last [limit] entries at a specific block location (most recent first). */
    fun readAtLocation(world: String, x: Int, y: Int, z: Int, limit: Int = 50): List<BlockLogEntry> {
        val results = mutableListOf<BlockLogEntry>()
        val today = LocalDate.now()
        for (daysBack in 0..6) {
            val file = logFile(today.minusDays(daysBack.toLong()))
            if (!file.exists()) continue
            file.readLines().asReversed().forEach { line ->
                if (results.size >= limit) return results
                if (line.isBlank()) return@forEach
                val e = BlockLogEntry.fromLine(line) ?: return@forEach
                if (e.world == world && e.x == x && e.y == y && e.z == z) results.add(e)
            }
            if (results.size >= limit) return results
        }
        return results
    }

    /** Read the last [limit] entries, optionally filtered by playerName. */
    fun readRecent(limit: Int = 100, playerName: String? = null): List<BlockLogEntry> {
        val results = mutableListOf<BlockLogEntry>()
        val today = LocalDate.now()
        for (daysBack in 0..6) {
            val date = today.minusDays(daysBack.toLong())
            val file = logFile(date)
            if (!file.exists()) continue
            val lines = file.readLines().asReversed()
            for (line in lines) {
                if (line.isBlank()) continue
                val entry = BlockLogEntry.fromLine(line) ?: continue
                if (playerName == null || entry.playerName.equals(playerName, ignoreCase = true)) {
                    results.add(entry)
                }
                if (results.size >= limit) return results
            }
            if (results.size >= limit) break
        }
        return results
    }

    fun shutdown() {
        running.set(false)
        // Drain remaining queue
        while (queue.isNotEmpty()) {
            val entry = queue.poll() ?: break
            writeEntry(entry)
        }
    }

    private fun logFile(date: LocalDate): File {
        val name = "block-${date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))}.log"
        return File(logsDir, name)
    }

    private fun writeEntry(entry: BlockLogEntry) {
        runCatching {
            val file = logFile(LocalDate.now())
            BufferedWriter(FileWriter(file, true)).use { it.write(entry.toLine() + "\n") }
        }
    }
}
