package com.workhub.server

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.HttpStatusCode
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.Date
import java.util.concurrent.atomic.AtomicReference
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Android Management API client (service account → REST).
 * Env (Render / production):
 *   AMAPI_SERVICE_ACCOUNT_JSON — toàn bộ nội dung file workhub-amapi-key.json (khuyến nghị)
 *   AMAPI_ENTERPRISE_ID — e.g. enterprises/LC03vngi7h
 *   AMAPI_POLICY_NAME — optional full policy name
 * Local:
 *   AMAPI_KEY_FILE / GOOGLE_APPLICATION_CREDENTIALS / workhub-amapi-key.json
 *   amapi_config.json (enterprise_id) nếu không set AMAPI_ENTERPRISE_ID
 */
internal object Amapi {
    private val json = Json { ignoreUnknownKeys = true }
    private val cachedToken = AtomicReference<Pair<String, Long>?>(null)

    private const val SCOPE = "https://www.googleapis.com/auth/androidmanagement"
    private const val TOKEN_URL = "https://oauth2.googleapis.com/token"
    private const val API = "https://androidmanagement.googleapis.com/v1"

    fun enabled(): Boolean = hasCredentials() && enterpriseId().isNotBlank()

    fun enterpriseId(): String =
        System.getenv("AMAPI_ENTERPRISE_ID")?.trim()?.takeIf { it.isNotEmpty() }
            ?: config()?.enterpriseId
            ?: ""

    fun policyName(): String =
        System.getenv("AMAPI_POLICY_NAME")?.trim()?.takeIf { it.isNotEmpty() }
            ?: config()?.policyName
            ?: "${enterpriseId()}/policies/workhub_default_policy"

    fun ensureReady() {
        if (!enabled()) {
            throw AppException(
                HttpStatusCode.ServiceUnavailable,
                "AMAPI chưa cấu hình (thiếu AMAPI_SERVICE_ACCOUNT_JSON/key hoặc AMAPI_ENTERPRISE_ID)",
            )
        }
    }

    fun createEnrollmentToken(): EnrollmentTokenResponse {
        ensureReady()
        val body = buildJsonObject {
            put("policyName", policyName())
            put("duration", "86400s")
        }
        return post("$API/${enterpriseId()}/enrollmentTokens", body.toString())
            .let { json.decodeFromString(EnrollmentTokenResponse.serializer(), it) }
    }

    fun lockDevice(deviceName: String): CommandResponse {
        ensureReady()
        val name = normalizeDeviceName(deviceName)
        val body = """{"type":"LOCK"}"""
        return post("$API/$name:issueCommand", body)
            .let { json.decodeFromString(CommandResponse.serializer(), it) }
    }

    /** Factory-reset / unenroll via device delete (AMAPI wipe). */
    fun wipeDevice(deviceName: String) {
        ensureReady()
        val name = normalizeDeviceName(deviceName)
        request("DELETE", "$API/$name", null)
    }

    fun listDevices(): List<AmapiDevice> {
        ensureReady()
        val raw = request("GET", "$API/${enterpriseId()}/devices", null)
        val parsed = json.decodeFromString(DeviceListResponse.serializer(), raw)
        return parsed.devices
    }

    private fun normalizeDeviceName(raw: String): String {
        val value = raw.trim()
        if (value.startsWith("enterprises/")) return value
        return "${enterpriseId()}/devices/$value"
    }

    private fun post(url: String, body: String): String = request("POST", url, body)

    private fun request(method: String, url: String, body: String?): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("Authorization", "Bearer ${accessToken()}")
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = 20_000
            readTimeout = 30_000
            if (body != null) {
                doOutput = true
            }
        }
        if (body != null) {
            conn.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        conn.disconnect()
        if (code !in 200..299) {
            throw AppException(HttpStatusCode.BadGateway, "AMAPI lỗi $code: ${text.take(300)}")
        }
        return text.ifBlank { "{}" }
    }

    private fun accessToken(): String {
        cachedToken.get()?.let { (token, expMs) ->
            if (System.currentTimeMillis() < expMs - 60_000) return token
        }
        val sa = serviceAccount()
        val now = System.currentTimeMillis()
        val assertion = JWT.create()
            .withIssuer(sa.clientEmail)
            .withAudience(TOKEN_URL)
            .withClaim("scope", SCOPE)
            .withIssuedAt(Date(now))
            .withExpiresAt(Date(now + 3_600_000))
            .sign(Algorithm.RSA256(null, sa.privateKey))

        val form = "grant_type=" + URLEncoder.encode(
            "urn:ietf:params:oauth:grant-type:jwt-bearer",
            StandardCharsets.UTF_8,
        ) + "&assertion=" + URLEncoder.encode(assertion, StandardCharsets.UTF_8)

        val conn = (URL(TOKEN_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        conn.outputStream.use { it.write(form.toByteArray(StandardCharsets.UTF_8)) }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        conn.disconnect()
        if (code !in 200..299) {
            throw AppException(HttpStatusCode.BadGateway, "OAuth AMAPI lỗi $code: ${text.take(200)}")
        }
        val obj = json.parseToJsonElement(text) as JsonObject
        val token = obj["access_token"]?.jsonPrimitive?.content
            ?: throw AppException(HttpStatusCode.BadGateway, "OAuth AMAPI thiếu access_token")
        val expiresIn = obj["expires_in"]?.jsonPrimitive?.content?.toLongOrNull() ?: 3600L
        cachedToken.set(token to (System.currentTimeMillis() + expiresIn * 1000))
        return token
    }

    private fun hasCredentials(): Boolean =
        !System.getenv("AMAPI_SERVICE_ACCOUNT_JSON").isNullOrBlank() || keyFile()?.exists() == true

    private fun serviceAccountJson(): String {
        System.getenv("AMAPI_SERVICE_ACCOUNT_JSON")?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val file = keyFile()
            ?: throw AppException(HttpStatusCode.ServiceUnavailable, "Thiếu AMAPI_SERVICE_ACCOUNT_JSON hoặc file key")
        return file.readText(StandardCharsets.UTF_8)
    }

    private fun serviceAccount(): ServiceAccount {
        val obj = json.parseToJsonElement(serviceAccountJson()) as JsonObject
        val email = obj["client_email"]?.jsonPrimitive?.content
            ?: throw AppException(HttpStatusCode.InternalServerError, "Key AMAPI thiếu client_email")
        val pem = obj["private_key"]?.jsonPrimitive?.content
            ?: throw AppException(HttpStatusCode.InternalServerError, "Key AMAPI thiếu private_key")
        return ServiceAccount(email, loadPrivateKey(pem))
    }

    private fun loadPrivateKey(pem: String): RSAPrivateKey {
        val cleaned = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\n", "")
            .replace("\n", "")
            .replace("\r", "")
            .trim()
        val decoded = Base64.getDecoder().decode(cleaned)
        val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(decoded))
        return key as RSAPrivateKey
    }

    private fun keyFile(): File? {
        val candidates = listOfNotNull(
            System.getenv("AMAPI_KEY_FILE")?.trim()?.takeIf { it.isNotEmpty() },
            System.getenv("GOOGLE_APPLICATION_CREDENTIALS")?.trim()?.takeIf { it.isNotEmpty() },
            "workhub-amapi-key.json",
            "D:/Python/workhub-amapi-key.json",
        )
        return candidates.map(::File).firstOrNull { it.isFile }
    }

    private fun config(): AmapiConfigFile? {
        val file = listOf("amapi_config.json", "D:/Python/amapi_enterprise.json")
            .map(::File)
            .firstOrNull { it.isFile }
            ?: return null
        return runCatching {
            json.decodeFromString(AmapiConfigFile.serializer(), file.readText(StandardCharsets.UTF_8))
        }.getOrNull()
    }

    private data class ServiceAccount(val clientEmail: String, val privateKey: RSAPrivateKey)
}

@Serializable
internal data class AmapiConfigFile(
    @SerialName("enterprise_id") val enterpriseId: String = "",
    @SerialName("policy_name") val policyName: String = "",
    @SerialName("package_name") val packageName: String = "",
    @SerialName("project_id") val projectId: String = "",
)

@Serializable
internal data class EnrollmentTokenResponse(
    val name: String = "",
    val value: String = "",
    val qrCode: String = "",
    val expirationTimestamp: String = "",
    val policyName: String = "",
)

@Serializable
internal data class CommandResponse(
    val name: String = "",
    val type: String = "",
    val createTime: String = "",
    val duration: String = "",
)

@Serializable
internal data class DeviceListResponse(
    val devices: List<AmapiDevice> = emptyList(),
)

@Serializable
internal data class AmapiDevice(
    val name: String = "",
    val userName: String = "",
    val state: String = "",
    val appliedPolicyName: String = "",
    val enrollmentTime: String = "",
)
