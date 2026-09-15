package com.offlineai.app.server

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.offlineai.app.MainActivity
import com.offlineai.app.R
import com.offlineai.app.inference.InferenceEngine
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Simple OpenAI-compatible local server so other devices / apps can call the AI on this phone.
 * Endpoints:
 *   GET  /v1/models
 *   POST /v1/chat/completions
 */
class LocalAIServerService : Service() {

    companion object {
        const val EXTRA_PORT = "port"
        const val EXTRA_HOST = "host"
        var engine: InferenceEngine? = null
        @Volatile var isRunning = false
        var currentPort: Int = 8080
        var currentHost: String = "0.0.0.0"
    }

    private var server: ApplicationEngine? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val port = intent?.getIntExtra(EXTRA_PORT, 8080) ?: 8080
        val host = intent?.getStringExtra(EXTRA_HOST) ?: "0.0.0.0"
        currentPort = port
        currentHost = host

        startForeground(1, buildNotification(port))
        startServer(host, port)
        isRunning = true
        return START_STICKY
    }

    private fun buildNotification(port: Int): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, "local_ai_server")
            .setContentTitle("Offline AI Server")
            .setContentText("Berjalan di port $port (OpenAI-compatible)")
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun startServer(host: String, port: Int) {
        server = embeddedServer(CIO, host = host, port = port) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
            install(CORS) {
                anyHost()
            }
            routing {
                get("/") {
                    call.respondText("Offline AI Local Server – OpenAI compatible\n/v1/models\n/v1/chat/completions")
                }
                get("/v1/models") {
                    val modelId = engine?.modelPath?.substringAfterLast('/') ?: "none"
                    call.respond(
                        mapOf(
                            "object" to "list",
                            "data" to listOf(
                                mapOf(
                                    "id" to modelId,
                                    "object" to "model",
                                    "owned_by" to "local"
                                )
                            )
                        )
                    )
                }
                post("/v1/chat/completions") {
                    val body = try {
                        call.receive<ChatCompletionRequest>()
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
                        return@post
                    }
                    val eng = engine
                    if (eng == null || !eng.isLoaded) {
                        call.respond(HttpStatusCode.ServiceUnavailable, mapOf("error" to "No model loaded"))
                        return@post
                    }
                    // Convert simple messages
                    val msgs = body.messages.map {
                        com.offlineai.app.data.ChatMessage(
                            role = when (it.role) {
                                "system" -> com.offlineai.app.data.MessageRole.SYSTEM
                                "assistant" -> com.offlineai.app.data.MessageRole.ASSISTANT
                                else -> com.offlineai.app.data.MessageRole.USER
                            },
                            content = it.content
                        )
                    }
                    val tokens = eng.generate(msgs, maxTokens = body.max_tokens ?: 512).toList()
                    val content = tokens.joinToString("")
                    call.respond(
                        ChatCompletionResponse(
                            id = "chatcmpl-local",
                            choices = listOf(
                                Choice(
                                    index = 0,
                                    message = Message(role = "assistant", content = content),
                                    finish_reason = "stop"
                                )
                            )
                        )
                    )
                }
            }
        }.start(wait = false)
    }

    override fun onDestroy() {
        isRunning = false
        server?.stop(1000, 2000)
        scope.cancel()
        super.onDestroy()
    }
}

@Serializable
data class ChatCompletionRequest(
    val model: String? = null,
    val messages: List<Message>,
    val max_tokens: Int? = 512,
    val temperature: Float? = 0.7f,
    val stream: Boolean? = false
)

@Serializable
data class Message(val role: String, val content: String)

@Serializable
data class ChatCompletionResponse(
    val id: String,
    val `object`: String = "chat.completion",
    val choices: List<Choice>
)

@Serializable
data class Choice(
    val index: Int,
    val message: Message,
    val finish_reason: String
)
