package xyz.bluspring.sprinkles.twitch.auth

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory
import xyz.bluspring.sprinkles.SprinklesCore
import xyz.bluspring.sprinkles.twitch.SprinklesTwitch
import xyz.bluspring.sprinkles.twitch.auth.callback.TwitchCallbackServer
import java.io.File
import java.net.URI

object TwitchUserAuth {
    private val logger = LoggerFactory.getLogger(TwitchUserAuth::class.java)
    private val STORAGE_FILE = File(SprinklesCore.instance.config.storage.auth, "twitch_auth.json")
    private val client = OkHttpClient()

    var twitchUsername: String? = null
    var displayName: String? = null

    var accessToken: String? = null
    var refreshToken: String? = null
    var expiryTimestamp: Long = 0L

    fun loadPrevious() {
        if (!STORAGE_FILE.exists()) {
            getNewAccessToken()

            return
        }
        
        val json = JsonParser.parseString(STORAGE_FILE.readText()).asJsonObject

        accessToken = json.get("access_token").asString
        refreshToken = json.get("refresh_token").asString
        expiryTimestamp = json.get("expiry_timestamp").asLong
        updateTwitchUserInfo()
    }

    fun save() {
        if (!STORAGE_FILE.exists())
            STORAGE_FILE.createNewFile()

        val json = JsonObject()

        json.addProperty("access_token", accessToken)
        json.addProperty("refresh_token", refreshToken)
        json.addProperty("expiry_timestamp", expiryTimestamp)

        STORAGE_FILE.writeText(json.toString())
    }

    fun getAuthUrl(): String {
        return "https://id.twitch.tv/oauth2/authorize?client_id=${SprinklesCore.instance.config.api.twitch.clientId}&redirect_uri=${SprinklesTwitch.instance.config.redirectUri}&response_type=code&scope=chat%3Aread+chat%3Aedit+channel%3Amoderate+moderator%3Aread%3Afollowers"
    }

    fun getNewAccessToken() {
        if (SprinklesCore.instance.config.api.twitch.clientId.isBlank())
            throw IllegalStateException("Client ID not provided!")

        if (SprinklesCore.instance.config.api.twitch.clientSecret.isBlank())
            throw IllegalStateException("Client Secret not provided!")

        // TODO: how do i use coroutines properly for this
        val thread = Thread({
            TwitchCallbackServer.start()
        }, "TwitchAuth Server")
        thread.start()

        logger.info("Log into Twitch API - ${getAuthUrl()}")
    }

    fun authorizeAccessToken(code: String) {
        if (SprinklesCore.instance.config.api.twitch.clientId.isBlank())
            throw IllegalStateException("Client ID not provided!")

        if (SprinklesCore.instance.config.api.twitch.clientSecret.isBlank())
            throw IllegalStateException("Client Secret not provided!")

        val params = FormBody.Builder().apply {
            addEncoded("client_id", SprinklesCore.instance.config.api.twitch.clientId)
            addEncoded("client_secret", SprinklesCore.instance.config.api.twitch.clientSecret)
            addEncoded("code", code)
            add("grant_type", "authorization_code")
            addEncoded("redirect_uri", SprinklesTwitch.instance.config.redirectUri)
        }
            .build()

        val req = Request.Builder()
            .apply {
                url("https://id.twitch.tv/oauth2/token")
                post(params)
                header("Content-Type", "application/x-www-form-urlencoded")
            }
            .build()

        client.newCall(req).execute().use { response ->
            if (response.code != 200) {
                throw IllegalStateException("Failed to authenticate with Twitch! ${response.code} ${response.body?.string()}")
            }

            val json = JsonParser.parseReader(response.body?.charStream()).asJsonObject

            accessToken = json.get("access_token").asString
            refreshToken = json.get("refresh_token").asString
            updateTwitchUserInfo()
            save()
        }
    }

    fun refreshToken() {
        if (SprinklesCore.instance.config.api.twitch.clientId.isBlank())
            throw IllegalStateException("Client ID not provided!")

        if (SprinklesCore.instance.config.api.twitch.clientSecret.isBlank())
            throw IllegalStateException("Client Secret not provided!")

        val params = FormBody.Builder().apply {
            addEncoded("client_id", SprinklesCore.instance.config.api.twitch.clientId)
            addEncoded("client_secret", SprinklesCore.instance.config.api.twitch.clientSecret)
            add("grant_type", "refresh_token")
            addEncoded("refresh_token", refreshToken!!)
        }.build()

        val req = Request.Builder()
            .apply {
                url("https://id.twitch.tv/oauth2/token")
                post(params)
                header("Content-Type", "application/x-www-form-urlencoded")
            }
            .build()

        client.newCall(req).execute().use { response ->
            if (response.code == 401) {
                accessToken = null
                refreshToken = null
                getNewAccessToken()

                return
            } else if (response.code != 200) {
                logger.error("Failed to refresh token! ${response.code} ${response.body?.string()}")

                return
            }

            val json = JsonParser.parseReader(response.body?.charStream()).asJsonObject

            accessToken = json.get("access_token").asString
            refreshToken = json.get("refresh_token").asString
            updateTwitchUserInfo()
            save()
        }
    }

    fun updateTwitchUserInfo() {
        if (accessToken == null)
            return

        val req = Request.Builder().apply {
            url("https://api.twitch.tv/helix/users")
            get()
            header("Client-Id", SprinklesCore.instance.config.api.twitch.clientId)
            header("Authorization", "Bearer $accessToken")
        }.build()

        client.newCall(req).execute().use { response ->
            if (response.code == 401) {
                refreshToken()

                return
            }

            val json = JsonParser.parseReader(response.body?.charStream()).asJsonObject

            val userData = json.getAsJsonArray("data")[0].asJsonObject

            twitchUsername = userData.get("login").asString
            displayName = userData.get("display_name").asString

            SprinklesTwitch.instance.startIrc()
        }
    }

    fun post(uri: URI, json: JsonObject, secondTry: Boolean = false): JsonObject? {
        val req = Request.Builder()
            .apply {
                url(uri.toURL())
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

                refreshToken()
                return post(uri, json, true)
            }

            return JsonParser.parseReader(resp.body?.charStream()).asJsonObject
        }
    }

    fun get(uri: URI, secondTry: Boolean = false): JsonObject? {
        val req = Request.Builder()
            .apply {
                url(uri.toURL())
                get()

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

                refreshToken()
                return get(uri, true)
            }

            return JsonParser.parseReader(resp.body?.charStream()).asJsonObject
        }
    }
}