package dev.nyaru.minecraft.api

import com.google.gson.Gson
import com.google.gson.JsonParser
import dev.nyaru.minecraft.model.LinkRequestResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class ApiClient(private val baseUrl: String, private val apiKey: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val json = "application/json; charset=utf-8".toMediaType()

    private fun buildRequest(path: String): Request.Builder =
        Request.Builder()
            .url("$baseUrl/api/minecraft$path")
            .header("X-API-Key", apiKey)

    private suspend fun post(path: String, body: Any) = withContext(Dispatchers.IO) {
        val reqBody = gson.toJson(body).toRequestBody(json)
        val req = buildRequest(path).post(reqBody).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext null
            resp.body?.string()?.let { JsonParser.parseString(it).asJsonObject }
        }
    }

    suspend fun requestLink(uuid: String, minecraftName: String): LinkRequestResult? {
        if (baseUrl.isEmpty()) return null
        val data = post("/link/request", mapOf(
            "uuid" to uuid,
            "minecraftName" to minecraftName
        )) ?: return null
        return LinkRequestResult(
            otp = data.get("otp").asString,
            expiresAt = data.get("expiresAt").asString
        )
    }

    suspend fun checkLink(uuid: String): Pair<Boolean, String?> {
        if (baseUrl.isEmpty()) return Pair(false, null)
        return withContext(Dispatchers.IO) {
            val req = buildRequest("/player/$uuid").get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext Pair(false, null)
                val obj = resp.body?.string()?.let { JsonParser.parseString(it).asJsonObject }
                    ?: return@withContext Pair(false, null)
                val isLinked = obj.get("linked")?.asBoolean ?: false
                val discordUserId = obj.get("discordUserId")?.takeIf { !it.isJsonNull }?.asString
                Pair(isLinked, discordUserId)
            }
        }
    }
}
