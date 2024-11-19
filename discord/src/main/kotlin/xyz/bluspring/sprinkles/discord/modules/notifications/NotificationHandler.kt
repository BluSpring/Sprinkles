package xyz.bluspring.sprinkles.discord.modules.notifications

import com.charleskorn.kaml.Yaml
import dev.minn.jda.ktx.generics.getChannel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
import org.slf4j.LoggerFactory
import xyz.artrinix.aviation.entities.AbstractModule
import xyz.bluspring.sprinkles.SprinklesCore
import xyz.bluspring.sprinkles.discord.SprinklesDiscord
import java.io.File
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

abstract class NotificationHandler(val platform: String) : AbstractModule {
    private lateinit var task: TimerTask
    protected val logger = LoggerFactory.getLogger(this::class.java)

    open val loopTime: Duration = 5.minutes
    open val isEnabled = true

    val updateChannels = mutableListOf<GuildMessageChannel>()
    protected abstract val updateChannelIds: List<Long>

    override suspend fun onEnable() {
        if (!isEnabled) {
            logger.info("Notification handler module for platform $platform is currently disabled!")
            return
        }

        logger.info("Enabled notification handler module for platform $platform")

        loadMarkedNotifications()
        runTimer()

        updateChannelIds.forEach { id ->
            val channel = SprinklesDiscord.instance.jda.getChannel<GuildMessageChannel>(id) ?: return@forEach

            updateChannels.add(channel)
            logger.info("Registered update channel #${channel.name} (${channel.id})")
        }
    }

    open fun runTimer() {
        timer.scheduleAtFixedRate(object : TimerTask() {
            init {
                task = this
            }

            override fun run() {
                runBlocking {
                    try {
                        poll()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }, 0L, loopTime.inWholeMilliseconds)
    }

    override suspend fun onDisable() {
        logger.info("Disabled notification handler module for platform $platform")
        task.cancel()
    }

    protected val notificationStorage = File(SprinklesCore.instance.config.storage.notifications, "notifications_$platform.yml")
    private val markedNotifications = mutableListOf<MarkedNotification>()

    open fun getPreviousNotifications(username: String): List<String> {
        return markedNotifications.filter { it.username == username }.map { it.representation }
    }

    open fun markNotificationAsDone(username: String, representation: String) {
        markedNotifications.add(MarkedNotification(username, representation, System.currentTimeMillis()))
        clearOldNotifications()
        saveMarkedNotifications()
    }

    open fun clearOldNotifications() {
        if (markedNotifications.removeIf { (System.currentTimeMillis() - it.timestamp) >= 3.days.inWholeMilliseconds }) {
            saveMarkedNotifications()
        }
    }

    open fun loadMarkedNotifications() {
        if (!notificationStorage.exists())
            return

        val notifs = Yaml.default.decodeFromString(MarkedNotifications.serializer(), notificationStorage.readText())

        markedNotifications.clear()
        markedNotifications.addAll(notifs.notifications)
    }

    open fun saveMarkedNotifications() {
        if (!notificationStorage.exists())
            notificationStorage.createNewFile()

        notificationStorage.writeText(Yaml.default.encodeToString(MarkedNotifications.serializer(), MarkedNotifications(markedNotifications)))
    }

    abstract suspend fun poll()

    @Serializable
    private data class MarkedNotifications(
        val notifications: List<MarkedNotification>
    )

    @Serializable
    private data class MarkedNotification(
        val username: String,
        val representation: String,
        val timestamp: Long
    )

    companion object {
        private val timer = Timer("Notification Handler")
    }
}