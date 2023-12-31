package xyz.bluspring.sprinkles.discord.modules.notifications.tiktok

import dev.minn.jda.ktx.messages.Embed
import dev.minn.jda.ktx.messages.MessageCreate
import xyz.bluspring.sprinkles.discord.SprinklesDiscord
import xyz.bluspring.sprinkles.discord.modules.notifications.NotificationHandler
import xyz.bluspring.sprinkles.platform.tiktok.TikTokApi
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

class TikTokNotificationHandler : NotificationHandler("TikTok") {
    override val loopTime: Duration
        get() = 10.minutes

    private val allUsernames = SprinklesDiscord.instance.config.notifications.tiktok.usernames
    val updateMessage = SprinklesDiscord.instance.config.notifications.tiktok.updateMessage

    override val isEnabled: Boolean
        get() = SprinklesDiscord.instance.config.notifications.tiktok.isEnabled
    override val updateChannelIds = SprinklesDiscord.instance.config.notifications.tiktok.updateChannels
    
    override suspend fun poll() {
        for (username in allUsernames) {
            val basicData = TikTokApi.getVideos(username)
            val prevMarked = this.getPreviousNotifications(username)

            video@for (video in basicData) {
                val timestamp = (video.id.toLong() shr 32) * 1000

                if (System.currentTimeMillis() - timestamp >= 3.days.inWholeMilliseconds)
                    continue@video

                if (prevMarked.contains(video.id))
                    continue@video

                val message = MessageCreate {
                    content = updateMessage
                        .replace("%displayName%", video.author.displayName)
                        .replace("%username%", video.author.username)

                    embeds += Embed {
                        title = video.description.take(253).run {
                            if (this.length != video.description.length)
                                return@run "$this..."

                            this
                        }

                        if (title!!.length != video.description.length) {
                            description = video.description.removePrefix(title!!.removeSuffix("..."))
                        }

                        author {
                            name = "${video.author.displayName} has uploaded a new TikTok!"
                            url = "https://tiktok.com/@${video.author.username}"
                            iconUrl = video.author.avatarUrl
                        }

                        url = "https://tiktok.com/@${video.author.username}/video/${video.id}"
                        this.timestamp = Instant.ofEpochMilli(timestamp)

                        color = 0xFFFFFF

                        image = video.thumbnail
                    }
                }

                for (updateChannel in updateChannels) {
                    updateChannel.sendMessage(message).queue()
                }

                markNotificationAsDone(username, video.id.toString())
            }
        }
    }
}