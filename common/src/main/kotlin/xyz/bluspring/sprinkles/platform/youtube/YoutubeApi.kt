package xyz.bluspring.sprinkles.platform.youtube

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.LoggerFactory
import xyz.bluspring.sprinkles.SprinklesCore

object YoutubeApi {
    private val client = OkHttpClient()
    private val logger = LoggerFactory.getLogger(YoutubeApi::class.java)

    fun get(url: String): JsonObject? {
        val req = Request.Builder().url("$url&key=${SprinklesCore.instance.config.api.youtube.apiKey}")
            .apply {
                get()
            }
            .build()

        client.newCall(req).execute().use { res ->
            if (res.code != 200) {
                logger.error("Failed to GET $url: ${res.code} ${res.body?.string()}")
                return null
            }

            return JsonParser.parseReader(res.body!!.charStream()).asJsonObject
        }
    }
}