package com.photobooth.kiosk.network

import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/**
 * HTTP client for the provisional AI backend. The app never talks to
 * OpenRouter directly and never holds its API key; all generation happens
 * server-side. Uses only HttpURLConnection plus org.json, no extra deps.
 *
 * Contract (provisional backend notes for the teammate):
 * - POST {baseUrl}/v1/sessions -> {"sessionId":"..."}
 * - POST {baseUrl}/v1/sessions/{sessionId}/photos?index=0..2 (raw JPEG body:
 *   the ORIGINAL guest photo, never a local composite)
 * - POST {baseUrl}/v1/sessions/{sessionId}/backgrounds?index=N (raw JPEG body:
 *   exact custom background reference, optional per photo) ->
 *   {"refId":"..."} (if the backend has no such route it may answer 404/405;
 *   the app then falls back to embedding the background as base64 in generate)
 * - POST {baseUrl}/v1/sessions/{sessionId}/generate -> {"jobId":"..."}
 * - GET  {baseUrl}/v1/jobs/{jobId} -> {status, progress, cost, results, error}
 *
 * Generate body: {styleId, stylePrompt, modelId, eventId, accessories:[ids],
 * accessoriesPrompt, photos:[{index, backgroundMode:"none"|"prompt"|"custom",
 * backgroundId, backgroundPrompt?, backgroundRefId?, backgroundData?}]}
 * - "none": person photo only; backend generates a complete styled scene.
 * - "prompt": person photo + English backgroundPrompt; backend generates the
 *   full scene (person + described backdrop) in the chosen style.
 * - "custom": person photo + exact custom background image (uploaded reference
 *   via backgroundRefId, or inline data-URL JPEG via backgroundData); backend
 *   must keep the custom background exactly and transform only the person.
 * The backend is expected to make 3 OpenRouter image calls (one per photo).
 */
class AiBackendException(
    message: String,
    val httpCode: Int? = null,
    cause: Throwable? = null,
) : IOException(message, cause)

data class AiGeneratePhoto(
    val index: Int,
    val backgroundId: String,
    val backgroundMode: String,
    val backgroundPrompt: String? = null,
    val backgroundRefId: String? = null,
    val backgroundDataBase64: String? = null,
)

data class AiJobImage(
    val index: Int,
    val url: String,
)

data class AiJobSnapshot(
    val status: String,
    val progress: Int,
    val cost: Double?,
    val results: List<AiJobImage>,
    val error: String?,
)

class AiBackendClient {

    fun createSession(baseUrl: String, token: String): String {
        val body = JSONObject()
        val response = postJson(baseUrl, "/v1/sessions", token, body)
        val sessionId = response.optString("sessionId").trim()
        if (sessionId.isEmpty()) throw AiBackendException("Backend session response is missing sessionId")
        return sessionId
    }

    fun uploadPhoto(baseUrl: String, token: String, sessionId: String, index: Int, jpeg: File) {
        if (!jpeg.isFile) throw AiBackendException("Styled input photo is missing")
        val url = endpoint(baseUrl, "/v1/sessions/$sessionId/photos?index=$index")
        val connection = openConnection(url, token, "POST", "image/jpeg")
        try {
            connection.setFixedLengthStreamingMode(jpeg.length())
            jpeg.inputStream().use { input ->
                connection.outputStream.use { output -> input.copyTo(output) }
            }
            throwOnError(connection, readBody(connection))
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Uploads an exact custom background reference for one photo. Returns the
     * backend refId when the route exists; throws [AiBackendException] with
     * httpCode 404/405 when the backend has no such route, so the caller can
     * fall back to inline base64 in the generate payload.
     */
    fun uploadBackground(baseUrl: String, token: String, sessionId: String, index: Int, jpeg: File): String {
        if (!jpeg.isFile) throw AiBackendException("Custom background reference is missing")
        val url = endpoint(baseUrl, "/v1/sessions/$sessionId/backgrounds?index=$index")
        val connection = openConnection(url, token, "POST", "image/jpeg")
        try {
            connection.setFixedLengthStreamingMode(jpeg.length())
            jpeg.inputStream().use { input ->
                connection.outputStream.use { output -> input.copyTo(output) }
            }
            val body = readBody(connection)
            throwOnError(connection, body)
            val refId = runCatching { JSONObject(body).optString("refId").trim() }.getOrDefault("")
            return refId.ifEmpty { "background-$index" }
        } finally {
            connection.disconnect()
        }
    }

    fun requestGenerate(
        baseUrl: String,
        token: String,
        sessionId: String,
        styleId: String,
        stylePrompt: String,
        modelId: String,
        eventId: String,
        photos: List<AiGeneratePhoto>,
        accessoryIds: List<String> = emptyList(),
        accessoriesPrompt: String = "",
    ): String {
        val body = JSONObject().apply {
            put("styleId", styleId)
            if (stylePrompt.isNotBlank()) put("stylePrompt", stylePrompt)
            put("modelId", modelId.ifBlank { DEFAULT_MODEL_ID })
            put("eventId", eventId)
            if (accessoryIds.isNotEmpty()) {
                put("accessories", JSONArray(accessoryIds))
            }
            if (accessoriesPrompt.isNotBlank()) put("accessoriesPrompt", accessoriesPrompt)
            put(
                "photos",
                JSONArray().apply {
                    photos.forEach { photo ->
                        put(
                            JSONObject().apply {
                                put("index", photo.index)
                                put("backgroundId", photo.backgroundId)
                                put("backgroundMode", photo.backgroundMode)
                                if (photo.backgroundPrompt != null) {
                                    put("backgroundPrompt", photo.backgroundPrompt)
                                }
                                if (photo.backgroundRefId != null) {
                                    put("backgroundRefId", photo.backgroundRefId)
                                }
                                if (photo.backgroundDataBase64 != null) {
                                    put("backgroundData", photo.backgroundDataBase64)
                                }
                            },
                        )
                    }
                },
            )
        }
        val response = postJson(baseUrl, "/v1/sessions/$sessionId/generate", token, body)
        val jobId = response.optString("jobId").trim()
        if (jobId.isEmpty()) throw AiBackendException("Backend generate response is missing jobId")
        return jobId
    }

    /**
     * Polls the job until it reaches done/error. Cooperative with coroutine
     * cancellation so the guest can cancel from the UI.
     */
    suspend fun awaitJob(
        baseUrl: String,
        token: String,
        jobId: String,
        expectedPhotos: Int = EXPECTED_PHOTOS,
        onProgress: (Int) -> Unit,
    ): AiJobSnapshot {
        repeat(MAX_POLL_ATTEMPTS) {
            coroutineContext.ensureActive()
            val snapshot = fetchJob(baseUrl, token, jobId)
            onProgress(snapshot.progress.coerceIn(0, 100))
            when (snapshot.status) {
                "done" -> {
                    if (snapshot.results.size < expectedPhotos) {
                        throw AiBackendException("Backend job finished without all styled photos")
                    }
                    return snapshot
                }
                "error" -> throw AiBackendException(
                    "Backend job failed: ${snapshot.error?.take(200) ?: "unknown error"}",
                )
            }
            delay(POLL_INTERVAL_MS)
        }
        throw AiBackendException("Backend job did not finish in time")
    }

    fun downloadToFile(baseUrl: String, url: String, token: String, target: File) {
        val absolute = if (url.startsWith("/")) {
            baseUrl.trim().trimEnd('/') + url
        } else {
            url
        }
        val connection = openConnection(URL(absolute), token, "GET")
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw AiBackendException("Styled photo download failed", httpCode = code)
            }
            (connection.inputStream ?: throw AiBackendException("Styled photo download is empty")).use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            }
            if (!target.isFile || target.length() == 0L) {
                target.delete()
                throw AiBackendException("Styled photo download is empty")
            }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Connectivity check for the admin screen: GET {baseUrl}/health with a
     * short timeout. Sends no API key; the provisional backend answers
     * 200 {"ok":true,...}. Returns the trimmed body on success.
     */
    fun testConnection(baseUrl: String): Result<String> {
        return try {
            val base = baseUrl.trim().trimEnd('/')
            if (base.isEmpty()) {
                return Result.failure(AiBackendException("Backend URL is empty"))
            }
            if (!base.startsWith("http://") && !base.startsWith("https://")) {
                return Result.failure(AiBackendException("Backend URL must start with http:// or https://"))
            }
            val connection = (URL("$base/health").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = HEALTH_TIMEOUT_MS
                readTimeout = HEALTH_TIMEOUT_MS
                doInput = true
                setRequestProperty("Accept", "application/json")
            }
            try {
                val body = readBody(connection)
                val code = try {
                    connection.responseCode
                } catch (error: IOException) {
                    return Result.failure(AiBackendException("Could not reach the AI backend", cause = error))
                }
                if (code !in 200..299) {
                    Log.w(TAG, "Health check HTTP $code")
                    return Result.failure(
                        AiBackendException("Backend request failed (HTTP $code)", httpCode = code),
                    )
                }
                Result.success(body.trim().take(300))
            } finally {
                connection.disconnect()
            }
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private fun fetchJob(baseUrl: String, token: String, jobId: String): AiJobSnapshot {
        val url = endpoint(baseUrl, "/v1/jobs/$jobId")
        val connection = openConnection(url, token, "GET")
        try {
            val body = readBody(connection)
            throwOnError(connection, body)
            val json = runCatching { JSONObject(body) }.getOrElse {
                throw AiBackendException("Backend job response is not valid JSON", cause = it)
            }
            val results = mutableListOf<AiJobImage>()
            val array = json.optJSONArray("results")
            if (array != null) {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val imageUrl = item.optString("url").trim()
                    if (imageUrl.isNotEmpty()) {
                        results += AiJobImage(index = item.optInt("index", i), url = imageUrl)
                    }
                }
            }
            val cost = json.optDouble("cost", Double.NaN).takeUnless { it.isNaN() }
            return AiJobSnapshot(
                status = json.optString("status", "running").trim().lowercase(),
                progress = json.optInt("progress", 0),
                cost = cost,
                results = results,
                error = json.optString("error").trim().takeIf { it.isNotEmpty() },
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun postJson(baseUrl: String, path: String, token: String, body: JSONObject): JSONObject {
        val connection = openConnection(endpoint(baseUrl, path), token, "POST", "application/json")
        try {
            val bytes = body.toString().toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.outputStream.use { it.write(bytes) }
            val responseBody = readBody(connection)
            throwOnError(connection, responseBody)
            return runCatching { JSONObject(responseBody) }.getOrElse {
                throw AiBackendException("Backend response is not valid JSON", cause = it)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun endpoint(baseUrl: String, path: String): URL {
        val base = baseUrl.trim().trimEnd('/')
        if (!base.startsWith("http://") && !base.startsWith("https://")) {
            throw AiBackendException("Backend URL must start with http:// or https://")
        }
        return URL("$base$path")
    }

    private fun openConnection(
        url: URL,
        token: String,
        method: String,
        contentType: String? = null,
    ): HttpURLConnection {
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doInput = true
            if (method == "POST") doOutput = true
            setRequestProperty("Accept", "application/json")
            if (contentType != null) setRequestProperty("Content-Type", contentType)
            if (token.isNotBlank()) setRequestProperty("Authorization", "Bearer $token")
        }
        return connection
    }

    private fun readBody(connection: HttpURLConnection): String {
        val stream = try {
            connection.inputStream
        } catch (_: IOException) {
            connection.errorStream
        } ?: return ""
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun throwOnError(connection: HttpURLConnection, body: String) {
        val code = try {
            connection.responseCode
        } catch (error: IOException) {
            throw AiBackendException("Could not reach the AI backend", cause = error)
        }
        if (code !in 200..299) {
            val detail = runCatching { JSONObject(body).optString("error") }.getOrDefault("").trim()
            Log.w(TAG, "Backend HTTP $code: ${detail.take(200)}")
            throw AiBackendException(
                "Backend request failed (HTTP $code)",
                httpCode = code,
            )
        }
    }

    companion object {
        const val DEFAULT_MODEL_ID = "openai/gpt-image-2.6-sunburst"
        private const val TAG = "AiBackend"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val HEALTH_TIMEOUT_MS = 10_000
        private const val POLL_INTERVAL_MS = 2_000L
        private const val MAX_POLL_ATTEMPTS = 180
        private const val EXPECTED_PHOTOS = 3
    }
}
