package xyz.bluspring.sprinkles.platform.twitch

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.CacheControl
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory
import xyz.bluspring.sprinkles.SprinklesCore
import java.net.URI

object TwitchApi {
    private val logger = LoggerFactory.getLogger(TwitchApi::class.java)
    private val client = OkHttpClient()

    var accessToken: String? = null

    fun refreshAccessToken() {
        if (SprinklesCore.instance.config.api.twitch.clientId.isBlank())
            throw IllegalStateException("Client ID not provided!")

        if (SprinklesCore.instance.config.api.twitch.clientSecret.isBlank())
            throw IllegalStateException("Client Secret not provided!")

        val params = FormBody.Builder(Charsets.UTF_8).apply {
            addEncoded("client_id", SprinklesCore.instance.config.api.twitch.clientId)
            addEncoded("client_secret", SprinklesCore.instance.config.api.twitch.clientSecret)
            add("grant_type", "client_credentials")
        }.build()

        val req = Request.Builder()
            .url("https://id.twitch.tv/oauth2/token")
            .apply {
                post(params)
                header("Content-Type", "application/x-www-form-urlencoded")
                cacheControl(CacheControl.FORCE_NETWORK)
            }
            .build()

        client.newCall(req).execute().use { response ->
            if (response.code == 401) {
                accessToken = null

                return
            } else if (response.code != 200) {
                logger.error("Failed to refresh token! ${response.code} ${response.body?.string()}")

                return
            }

            val json = JsonParser.parseReader(response.body!!.charStream()).asJsonObject

            accessToken = json.get("access_token").asString
            logger.info("Successfully updated Twitch API access token!")
        }
    }

    fun post(uri: URI, json: JsonObject, secondTry: Boolean = false): JsonObject? {
        val req = Request.Builder().url(uri.toURL())
            .apply {
                post(json.toString().toRequestBody("application/json".toMediaType()))

                header("Client-Id", SprinklesCore.instance.config.api.twitch.clientId)
                header("Authorization", "Bearer $accessToken")
            }
            .build()

        client.newCall(req).execute().use { resp ->
            if (resp.code == 401) {
                if (secondTry) {
                    logger.error("Failed to POST $uri - ${resp.code} ${resp.body?.string()}")
                    return null
                }

                refreshAccessToken()
                return post(uri, json, true)
            }

            return JsonParser.parseReader(resp.body!!.charStream()).asJsonObject
        }
    }

    fun get(uri: URI, secondTry: Boolean = false): JsonObject? {
        val req = Request.Builder().url(uri.toURL())
            .apply {
                get()

                header("Client-Id", SprinklesCore.instance.config.api.twitch.clientId)
                header("Authorization", "Bearer $accessToken")
            }
            .build()

        client.newCall(req).execute().use { resp ->
            if (resp.code == 401) {
                if (secondTry) {
                    logger.error("Failed to GET $uri - ${resp.code} ${resp.body?.string()}")
                    return null
                }

                refreshAccessToken()
                return get(uri, true)
            }

            return JsonParser.parseReader(resp.body!!.charStream()).asJsonObject
        }
    }

    fun getUserIds(usernames: List<String>): Map<String, String> {
        val map = mutableMapOf<String, String>()

        for (usernameList in usernames.chunked(100)) {
            val json = get(URI.create("https://api.twitch.tv/helix/users?login=${usernameList.joinToString("&login=")}")) ?: continue

            if (!json.has("data")) {
                logger.error("Failed to load users: $json")
                continue
            }

            for (jsonElement in json.getAsJsonArray("data")) {
                val data = jsonElement.asJsonObject

                map[data.get("login").asString] = data.get("id").asString
            }
        }

        return map
    }
}