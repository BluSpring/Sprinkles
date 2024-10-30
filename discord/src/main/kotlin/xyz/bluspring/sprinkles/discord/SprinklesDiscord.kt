package xyz.bluspring.sprinkles.discord

import dev.minn.jda.ktx.jdabuilder.intents
import dev.minn.jda.ktx.jdabuilder.light
import kotlinx.coroutines.runBlocking
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.requests.GatewayIntent
import xyz.artrinix.aviation.Aviation
import xyz.artrinix.aviation.AviationBuilder
import xyz.artrinix.aviation.events.AviationExceptionEvent
import xyz.artrinix.aviation.events.CommandFailedEvent
import xyz.artrinix.aviation.events.ParsingErrorEvent
import xyz.artrinix.aviation.internal.utils.on
import xyz.artrinix.aviation.ratelimit.DefaultRateLimitStrategy
import xyz.bluspring.sprinkles.SprinklesBotModule
import xyz.bluspring.sprinkles.discord.commands.CommandHelper
import xyz.bluspring.sprinkles.discord.config.DiscordConfig
import xyz.bluspring.sprinkles.discord.events.TwitchStartBroadcastingEvent
import xyz.bluspring.sprinkles.discord.events.TwitchStopBroadcastingEvent
import xyz.bluspring.sprinkles.discord.modules.ModuleHelper

class SprinklesDiscord : SprinklesBotModule<DiscordConfig>(NAME) {
    override val configSerializer = DiscordConfig.serializer()
    override val dummy = DiscordConfig()

    lateinit var jda: JDA
    lateinit var aviation: Aviation

    override fun start(): Unit = runBlocking {
        instance = this@SprinklesDiscord

        val defaultPresence = Activity.of(Activity.ActivityType.PLAYING, "with Zuite!", "https://youtube.com/@zuite_")

        jda = light(config.token, true) {
            intents += listOf(GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_MESSAGE_REACTIONS, GatewayIntent.MESSAGE_CONTENT)

            setActivity(defaultPresence)
        }.awaitReady()

        aviation = AviationBuilder()
            .apply {
                prefixes = listOf("!")
                ratelimitProvider = DefaultRateLimitStrategy()

                testGuilds += config.testGuilds
                developers += config.admins

                registerDefaultParsers()
            }
            .build()

        aviation.slashCommands.register(CommandHelper::class.java.packageName)
        aviation.modules.register(ModuleHelper::class.java.packageName)

        aviation.syncCommands(jda)

        aviation.on<AviationExceptionEvent> {
            logger.error("Aviation threw an exception!")
            this.error.printStackTrace()
        }

        aviation.on<CommandFailedEvent> {
            logger.error("A command failed to execute in Aviation!")
            this.error.printStackTrace()
        }

        aviation.on<ParsingErrorEvent> {
            logger.error("Aviation had a parsing error!")
            this.error.printStackTrace()
        }

        aviation.on<TwitchStartBroadcastingEvent> {
            if (login == "zuite") {
                jda.presence.activity = Activity.streaming("with Zuite!", "https://twitch.tv/zuite")
            }
        }

        aviation.on<TwitchStopBroadcastingEvent> {
            if (login == "zuite") {
                jda.presence.activity = defaultPresence
            }
        }

        jda.addEventListener(aviation)
    }

    override fun stop() {

    }

    companion object {
        lateinit var instance: SprinklesDiscord
        const val NAME = "Discord"
    }
}