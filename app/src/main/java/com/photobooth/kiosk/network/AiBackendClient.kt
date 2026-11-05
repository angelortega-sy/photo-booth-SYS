package com.photobooth.kiosk.network

import android.util.Base64
import com.photobooth.kiosk.data.DeviceIdentityStore
import com.photobooth.kiosk.security.DeviceKeyStore
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID

class AiBackendException(
    message: String,
    val httpCode: Int? = null,
    val code: String? = null,
) : Exception(message)

data class BoothStyle(val id: String, val name: String)

data class BoothModel(
    val id: String,
    val name: String,
    val priceUsd: Double?,
    val priceUnit: String?,
    val provider: String,
    val overBudget: Boolean,
    val selectable: Boolean,
)

data class BoothSession(val id: String, val expiresAt: String)

data class StylizeResult(
    val costUsd: Double?,
    val estimatedCostUsd: Double?,
    val imageBase64: String,
)

data class UsageMonth(val month: String, val requestCount: Int, val totalCostUsd: Double)

data class UsageSummary(
    val month: String,
    val requestCount: Int,
    val totalCostUsd: Double,
)

data class UsageRequest(
    val id: String,
    val createdAt: String,
    val modelId: String,
    val styleId: String,
    val status: String,
    val priceUnit: String?,
    val costUsd: Double?,
    val estimatedCostUsd: Double?,
)

/**
 * Signed client for the booth server. Enrollment and health are the only
 * unsigned calls. The OpenRouter key is never sent.
 */
class AiBackendClient(
    private val identityStore: DeviceIdentityStore,
    private val keys: DeviceKeyStore,
) {

    fun health(): Boolean {
        val code = open("GET", "/healthz", ByteArray(0), null, sign = false).first
        return code in 200..299
    }

    fun enroll(code: String): String {
        val pem = keys.publicKeyPem()
        val body = """{"password":${jsonString(code.trim())},"publicKeyPem":${jsonString(pem)}}"""
            .toByteArray(Charsets.UTF_8)
        val (_, text) = open("POST", "/v1/devices/enroll", body, JSON, sign = false)
        val deviceId = JSONObject(text).optString("deviceId")
        if (!deviceId.startsWith("dev_")) {
            throw AiBackendException("Enrollment did not return a device id")
        }
        identityStore.saveDeviceId(deviceId)
        return deviceId
    }

    fun styles(): List<BoothStyle> {
        val (_, text) = open("GET", "/v1/styles", ByteArray(0), null, sign = true)
        val array = JSONObject(text).optJSONArray("styles") ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(BoothStyle(id = item.getString("id"), name = item.optString("name")))
            }
        }
    }

    fun models(): List<BoothModel> {
        val (_, text) = open("GET", "/v1/models", ByteArray(0), null, sign = true)
        val array = JSONObject(text).optJSONArray("models") ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    BoothModel(
                        id = item.getString("id"),
                        name = item.optString("name").ifBlank { item.getString("id") },
                        priceUsd = if (item.isNull("priceUsd")) null else item.optDouble("priceUsd"),
                        priceUnit = item.optString("priceUnit").ifBlank { null },
                        provider = item.optString("provider"),
                        overBudget = item.optBoolean("overBudget"),
                        selectable = item.optBoolean("selectable"),
                    ),
                )
            }
        }
    }

    fun usageMonths(): List<UsageMonth> {
        val (_, text) = open("GET", "/v1/usage/months", ByteArray(0), null, sign = true)
        val array = JSONObject(text).optJSONArray("months") ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    UsageMonth(
                        month = item.getString("month"),
                        requestCount = item.optInt("requestCount"),
                        totalCostUsd = item.optDouble("totalCostUsd"),
                    ),
                )
            }
        }
    }

    fun usageSummary(month: String): UsageSummary {
        val path = "/v1/usage/summary?month=$month"
        val (_, text) = open("GET", path, ByteArray(0), null, sign = true)
        val json = JSONObject(text)
        return UsageSummary(
            month = json.optString("month", month),
            requestCount = json.optInt("requestCount"),
            totalCostUsd = json.optDouble("totalCostUsd"),
        )
    }

    fun usageRequests(from: String, to: String): List<UsageRequest> {
        val path = "/v1/usage/requests?from=${query(from)}&to=${query(to)}&limit=50"
        val (_, text) = open("GET", path, ByteArray(0), null, sign = true)
        val array = JSONObject(text).optJSONArray("requests") ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    UsageRequest(
                        id = item.optString("id"),
                        createdAt = item.optString("createdAt"),
                        modelId = item.optString("modelId"),
                        styleId = item.optString("styleId"),
                        status = item.optString("status"),
                        priceUnit = item.optString("priceUnit").ifBlank { null },
                        costUsd = if (item.isNull("costUsd")) null else item.optDouble("costUsd"),
                        estimatedCostUsd = if (item.isNull("estimatedCostUsd")) {
                            null
                        } else {
                            item.optDouble("estimatedCostUsd")
                        },
                    ),
                )
            }
        }
    }

    fun createSession(): BoothSession {
        val body = "{}".toByteArray(Charsets.UTF_8)
        val (_, text) = open("POST", "/v1/sessions", body, JSON, sign = true)
        val json = JSONObject(text)
        return BoothSession(id = json.getString("id"), expiresAt = json.optString("expiresAt"))
    }

    fun uploadPhotos(sessionId: String, photo0: File, photo1: File, photo2: File, strip: File) {
        val boundary = "----Booth${UUID.randomUUID().toString().replace("-", "")}"
        val body = multipart(
            boundary,
            listOf(
                "photo0" to photo0,
                "photo1" to photo1,
                "photo2" to photo2,
                "strip" to strip,
            ),
        )
        open(
            "POST",
            "/v1/sessions/$sessionId/photos",
            body,
            "multipart/form-data; boundary=$boundary",
            sign = true,
        )
    }

    fun stylize(sessionId: String, modelId: String, styleId: String): StylizeResult {
        val body = """{"modelId":${jsonString(modelId)},"styleId":${jsonString(styleId)},"source":"strip"}"""
            .toByteArray(Charsets.UTF_8)
        val (_, text) = open(
            "POST",
            "/v1/sessions/$sessionId/stylize",
            body,
            JSON,
            sign = true,
        )
        val json = JSONObject(text)
        return StylizeResult(
            costUsd = if (json.isNull("costUsd")) null else json.optDouble("costUsd"),
            estimatedCostUsd = if (json.isNull("estimatedCostUsd")) {
                null
            } else {
                json.optDouble("estimatedCostUsd")
            },
            imageBase64 = json.optString("imageBase64"),
        )
    }

    fun finalize(sessionId: String): String {
        val body = "{}".toByteArray(Charsets.UTF_8)
        val (_, text) = open(
            "POST",
            "/v1/sessions/$sessionId/finalize",
            body,
            JSON,
            sign = true,
        )
        val shareUrl = JSONObject(text).optString("shareUrl")
        if (shareUrl.isBlank()) throw AiBackendException("Missing share URL")
        return shareUrl
    }

    private fun open(
        method: String,
        path: String,
        body: ByteArray,
        contentType: String?,
        sign: Boolean,
    ): Pair<Int, String> {
        val base = identityStore.load().baseUrl.trimEnd('/')
        val connection = (URL(base + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 120_000
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
            if (sign) applySignature(method, path, body)
            if (contentType != null) setRequestProperty("Content-Type", contentType)
            if (method != "GET" && method != "HEAD") {
                doOutput = true
                setFixedLengthStreamingMode(body.size)
            }
        }
        try {
            if (connection.doOutput) {
                connection.outputStream.use { it.write(body) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val parsed = runCatching { JSONObject(text).optJSONObject("error") }.getOrNull()
                throw AiBackendException(
                    message = parsed?.optString("message").orEmpty().ifBlank { "HTTP $code" },
                    httpCode = code,
                    code = parsed?.optString("code")?.ifBlank { null },
                )
            }
            return code to text
        } catch (error: AiBackendException) {
            throw error
        } catch (error: Exception) {
            throw AiBackendException(error.message ?: "network")
        } finally {
            connection.disconnect()
        }
    }

    private fun HttpURLConnection.applySignature(method: String, path: String, body: ByteArray) {
        val deviceId = identityStore.load().deviceId
        if (!deviceId.startsWith("dev_")) {
            throw AiBackendException("Device is not enrolled", code = "unknown_device")
        }
        val timestamp = (System.currentTimeMillis() / 1000L).toString()
        val nonce = UUID.randomUUID().toString()
        val canonical = canonicalString(method, path, timestamp, nonce, sha256Hex(body))
        val signature = keys.sign(canonical.toByteArray(Charsets.UTF_8))
        setRequestProperty("X-Device-Id", deviceId)
        setRequestProperty("X-Timestamp", timestamp)
        setRequestProperty("X-Nonce", nonce)
        setRequestProperty("X-Signature", signature)
    }

    private fun multipart(boundary: String, files: List<Pair<String, File>>): ByteArray {
        val out = ByteArrayOutputStream()
        for ((name, file) in files) {
            out.write("--$boundary\r\n".toByteArray(Charsets.UTF_8))
            out.write(
                "Content-Disposition: form-data; name=\"$name\"; filename=\"$name.jpg\"\r\n"
                    .toByteArray(Charsets.UTF_8),
            )
            out.write("Content-Type: image/jpeg\r\n\r\n".toByteArray(Charsets.UTF_8))
            out.write(file.readBytes())
            out.write("\r\n".toByteArray(Charsets.UTF_8))
        }
        out.write("--$boundary--\r\n".toByteArray(Charsets.UTF_8))
        return out.toByteArray()
    }

    companion object {
        private const val JSON = "application/json; charset=utf-8"

        fun canonicalString(
            method: String,
            resource: String,
            timestamp: String,
            nonce: String,
            bodyHash: String,
        ): String = listOf(method.uppercase(), resource, timestamp, nonce, bodyHash).joinToString("\n")

        fun sha256Hex(body: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(body)
            return digest.joinToString("") { "%02x".format(it) }
        }

        fun jsonString(value: String): String = buildString {
            append('"')
            value.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    else -> append(char)
                }
            }
            append('"')
        }

        fun query(value: String): String =
            java.net.URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
    }
}

/** Kept so call sites can decode the stylized JPEG without importing android.util twice. */
fun decodeImage(base64: String): ByteArray = Base64.decode(base64, Base64.DEFAULT)
