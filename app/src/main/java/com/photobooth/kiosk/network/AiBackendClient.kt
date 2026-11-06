package com.photobooth.kiosk.network

import android.util.Base64
import com.photobooth.kiosk.data.DeviceIdentityStore
import com.photobooth.kiosk.security.DeviceKeyStore
import org.json.JSONArray
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
    cause: Throwable? = null,
) : Exception(message, cause)

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

data class UsageModelCost(val modelId: String, val requestCount: Int, val totalCostUsd: Double)

data class UsageDayCost(val day: String, val requestCount: Int, val totalCostUsd: Double)

data class UsageSummary(
    val month: String,
    val requestCount: Int,
    val successCount: Int,
    val failedCount: Int,
    val totalCostUsd: Double,
    val estimatedCount: Int,
    val byModel: List<UsageModelCost>,
    val byDay: List<UsageDayCost>,
)

/** Real OpenRouter account numbers: limit, spend and what is left. */
data class UsageLimits(
    val label: String?,
    val limitUsd: Double?,
    val usageUsd: Double,
    val remainingUsd: Double?,
    val percentUsed: Double?,
    val isFreeTier: Boolean,
    val rateLimitRequests: Int?,
    val rateLimitInterval: String?,
    val totalCreditsUsd: Double?,
    val totalUsageUsd: Double?,
)

/** One page of usage rows plus the cursor for the next one. */
data class UsagePage(val requests: List<UsageRequest>, val nextCursor: String?)

data class UsageRequest(
    val id: String,
    val createdAt: String,
    val modelId: String,
    val styleId: String,
    val deviceName: String?,
    val status: String,
    val priceUnit: String?,
    val costUsd: Double?,
    val estimatedCostUsd: Double?,
    val latencyMs: Int?,
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
            successCount = json.optInt("successCount"),
            failedCount = json.optInt("failedCount"),
            totalCostUsd = json.optDouble("totalCostUsd", 0.0),
            estimatedCount = json.optInt("estimatedCount"),
            byModel = readModelCosts(json.optJSONArray("byModel")),
            byDay = readDayCosts(json.optJSONArray("byDay")),
        )
    }

    /** Real OpenRouter account numbers; the server caches them for a minute. */
    fun usageLimits(): UsageLimits {
        val (_, text) = open("GET", "/v1/usage/limits", ByteArray(0), null, sign = true)
        val json = JSONObject(text)
        val rateLimit = json.optJSONObject("rateLimit")
        return UsageLimits(
            label = json.optString("label").ifBlank { null },
            limitUsd = json.optDoubleOrNull("limitUsd"),
            usageUsd = json.optDouble("usageUsd", 0.0),
            remainingUsd = json.optDoubleOrNull("remainingUsd"),
            percentUsed = json.optDoubleOrNull("percentUsed"),
            isFreeTier = json.optBoolean("isFreeTier"),
            rateLimitRequests = rateLimit?.optInt("requests"),
            rateLimitInterval = rateLimit?.optString("interval")?.ifBlank { null },
            totalCreditsUsd = json.optDoubleOrNull("totalCreditsUsd"),
            totalUsageUsd = json.optDoubleOrNull("totalUsageUsd"),
        )
    }

    /** One page of the usage log. Pass [cursor] back in to append the next page. */
    fun usageRequests(
        from: String,
        to: String,
        cursor: String? = null,
        limit: Int = DEFAULT_USAGE_PAGE,
    ): UsagePage {
        val path = buildString {
            append("/v1/usage/requests?from=").append(query(from))
            append("&to=").append(query(to))
            append("&limit=").append(limit)
            if (!cursor.isNullOrBlank()) append("&cursor=").append(query(cursor))
        }
        val (_, text) = open("GET", path, ByteArray(0), null, sign = true)
        val json = JSONObject(text)
        val array = json.optJSONArray("requests") ?: JSONArray()
        val requests = buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    UsageRequest(
                        id = item.optString("id"),
                        createdAt = item.optString("createdAt"),
                        modelId = item.optString("modelId"),
                        styleId = item.optString("styleId"),
                        deviceName = item.optString("deviceName").ifBlank { null },
                        status = item.optString("status"),
                        priceUnit = item.optString("priceUnit").ifBlank { null },
                        costUsd = item.optDoubleOrNull("costUsd"),
                        estimatedCostUsd = item.optDoubleOrNull("estimatedCostUsd"),
                        latencyMs = if (item.isNull("latencyMs")) null else item.optInt("latencyMs"),
                    ),
                )
            }
        }
        return UsagePage(
            requests = requests,
            nextCursor = json.optString("nextCursor").ifBlank { null },
        )
    }

    private fun readModelCosts(array: JSONArray?): List<UsageModelCost> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    UsageModelCost(
                        modelId = item.optString("modelId"),
                        requestCount = item.optInt("requestCount"),
                        totalCostUsd = item.optDouble("totalCostUsd", 0.0),
                    ),
                )
            }
        }
    }

    private fun readDayCosts(array: JSONArray?): List<UsageDayCost> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    UsageDayCost(
                        day = item.optString("day"),
                        requestCount = item.optInt("requestCount"),
                        totalCostUsd = item.optDouble("totalCostUsd", 0.0),
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

    fun uploadPhoto(sessionId: String, photo: File) {
        val boundary = "----Booth${UUID.randomUUID().toString().replace("-", "")}"
        val body = multipart(boundary, listOf("photo0" to photo))
        open(
            "POST",
            "/v1/sessions/$sessionId/photos",
            body,
            "multipart/form-data; boundary=$boundary",
            sign = true,
        )
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

    /** Uploads the exact background the guest picked so the server can keep it while restyling the person. */
    fun uploadBackground(sessionId: String, background: File) {
        val boundary = "----Booth${UUID.randomUUID().toString().replace("-", "")}"
        val body = multipart(boundary, listOf("background" to background))
        open(
            "POST",
            "/v1/sessions/$sessionId/background",
            body,
            "multipart/form-data; boundary=$boundary",
            sign = true,
        )
    }

    fun stylize(
        sessionId: String,
        modelId: String,
        styleId: String,
        source: String = "strip",
        backgroundId: String? = null,
        accessoryIds: List<String> = emptyList(),
        useCustomBackground: Boolean = false,
    ): StylizeResult {
        val payload = JSONObject()
            .put("modelId", modelId)
            .put("styleId", styleId)
            .put("source", source)
        if (useCustomBackground) {
            payload.put("useCustomBackground", true)
        } else if (!backgroundId.isNullOrBlank() && backgroundId != "none" && backgroundId != "original") {
            payload.put("backgroundId", backgroundId)
        }
        if (accessoryIds.isNotEmpty()) {
            payload.put("accessoryIds", JSONArray(accessoryIds))
        }
        val body = payload.toString().toByteArray(Charsets.UTF_8)
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

        /** Rows per usage page; keep the log short instead of loading a whole month. */
        const val DEFAULT_USAGE_PAGE = 40

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

/** null instead of NaN for absent or non-numeric JSON numbers. */
private fun JSONObject.optDoubleOrNull(name: String): Double? {
    if (isNull(name)) return null
    val value = optDouble(name)
    return if (value.isNaN() || value.isInfinite()) null else value
}
