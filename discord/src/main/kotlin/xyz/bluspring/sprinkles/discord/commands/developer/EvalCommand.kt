package xyz.bluspring.sprinkles.discord.commands.developer

import dev.minn.jda.ktx.coroutines.await
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.emoji.Emoji
import xyz.artrinix.aviation.command.message.MessageContext
import xyz.artrinix.aviation.command.message.annotations.Greedy
import xyz.artrinix.aviation.command.message.annotations.MessageCommand
import xyz.artrinix.aviation.entities.Scaffold
import xyz.bluspring.sprinkles.discord.SprinklesDiscord
import java.util.concurrent.CompletableFuture
import javax.script.ScriptEngineManager
import javax.script.ScriptException

class EvalCommand : Scaffold {
    private val engine = ScriptEngineManager().getEngineByExtension("kts")

    @MessageCommand(developerOnly = true, hidden = true)
    suspend fun eval(ctx: MessageContext, @Greedy code: String) {
        val bindings = mutableMapOf(
            "ctx" to ctx,
            "client" to SprinklesDiscord.instance
        )

        if (engine == null) {
            ctx.message.reply("Kotlin Script Engine failed to start!").await()
            return
        }

        val stripped = code.replace("^```\\w+".toRegex(), "").removeSuffix("```")

        try {
            val result = engine.eval(stripped, engine.createBindings().apply { bindings.forEach(::put) })
                ?: return ctx.message.addReaction(Emoji.fromUnicode("👌")).queue()

            if (result is CompletableFuture<*>) {
                val m = sendMessage(ctx, "```\nCompletableFuture<Pending>```")
                result.whenComplete { r, exception ->
                    val post = exception ?: r
                    m.editMessage("```kotlin\n$post\n```").queue()
                }
            } else {
                sendMessage(ctx, "```kotlin\n${result.toString().take(1950)}\n```")
            }
        } catch (e: ScriptException) {
            sendMessage(ctx, "Invalid script provided!\n```kotlin\n${e.localizedMessage}\n```")
        } catch (e: Exception) {
            sendMessage(ctx, "An exception occurred.\n```kotlin\n${e.localizedMessage}\n```")
        }
    }

    private suspend fun sendMessage(ctx: MessageContext, content: String): Message {
        return if (ctx.message.isEdited) {
            val history = ctx.channel.history.retrievePast(3).await()
            val message = history.find { it.messageReference?.message?.author?.idLong == ctx.author.idLong }
            if (message == null) ctx.channel.sendMessage(content).setMessageReference(ctx.message).await()
            else message.editMessage(content).await()
        } else {
            ctx.channel.sendMessage(content).setMessageReference(ctx.message).await()
        }
    }
}