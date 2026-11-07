package com.workhub.server

import com.workhub.shared.SendVerifyResponse
import io.ktor.http.HttpStatusCode
import java.util.Properties
import java.security.SecureRandom
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

/**
 * Mã xác thực đăng ký: 6 số, sống 5 phút, gửi qua SMTP.
 * Biến môi trường: SMTP_HOST, SMTP_PORT (mặc định 587), SMTP_USER, SMTP_PASSWORD, MAIL_FROM.
 * Chưa cấu hình SMTP thì in mã ra log máy chủ để chạy local.
 */
object Mailer {
    private const val TTL_MS = 5 * 60 * 1000L
    private const val RESEND_MS = 60_000L
    private const val MAX_ATTEMPTS = 5

    private data class Pending(
        val code: String,
        val expiresAt: Long,
        val sentAt: Long,
        var attempts: Int,
    )

    private val pending = HashMap<String, Pending>()
    private val random = SecureRandom()

    fun sentResponse() = SendVerifyResponse(expiresInSeconds = (TTL_MS / 1000).toInt())

    fun send(email: String) {
        val code = (100_000 + random.nextInt(900_000)).toString()
        val now = System.currentTimeMillis()
        synchronized(pending) {
            pending.filterValues { it.expiresAt <= now }.keys.toList().forEach { pending.remove(it) }
            val existing = pending[email]
            if (existing != null && now - existing.sentAt < RESEND_MS) {
                val wait = ((RESEND_MS - (now - existing.sentAt)) / 1000).coerceAtLeast(1)
                throw AppException(HttpStatusCode.TooManyRequests, "Hãy đợi ${wait}s rồi gửi lại mã")
            }
            pending[email] = Pending(code, now + TTL_MS, now, 0)
        }
        try {
            deliver(email, code)
        } catch (error: Exception) {
            synchronized(pending) {
                if (pending[email]?.code == code) pending.remove(email)
            }
            throw error
        }
    }

    fun requireValid(email: String, rawCode: String) {
        val code = rawCode.trim()
        if (code.length != 6 || code.any { !it.isDigit() }) {
            throw AppException(HttpStatusCode.BadRequest, "Hãy nhập mã xác thực 6 số")
        }
        synchronized(pending) {
            val row = pending[email]
                ?: throw AppException(HttpStatusCode.BadRequest, "Hãy gửi mã xác thực tới email này")
            if (System.currentTimeMillis() > row.expiresAt) {
                pending.remove(email)
                throw AppException(HttpStatusCode.BadRequest, "Mã đã hết hạn, hãy gửi lại")
            }
            if (row.code != code) {
                row.attempts += 1
                if (row.attempts >= MAX_ATTEMPTS) {
                    pending.remove(email)
                    throw AppException(HttpStatusCode.BadRequest, "Nhập sai quá nhiều lần, hãy gửi mã mới")
                }
                throw AppException(HttpStatusCode.BadRequest, "Mã xác thực không đúng")
            }
        }
    }

    fun consume(email: String) {
        synchronized(pending) { pending.remove(email) }
    }

    private fun deliver(email: String, code: String) {
        val host = System.getenv("SMTP_HOST")?.trim().orEmpty()
        val user = System.getenv("SMTP_USER")?.trim().orEmpty()
        val password = System.getenv("SMTP_PASSWORD")?.trim().orEmpty()
        if (host.isEmpty() || user.isEmpty() || password.isEmpty()) {
            println("WorkHub verify: SMTP chưa cấu hình. Mã cho $email là $code (hiệu lực 5 phút)")
            return
        }
        val port = System.getenv("SMTP_PORT")?.toIntOrNull() ?: 587
        val from = System.getenv("MAIL_FROM")?.trim()?.takeIf { it.isNotEmpty() } ?: user
        val ssl = port == 465
        val props = Properties().apply {
            put("mail.smtp.auth", "true")
            put("mail.smtp.host", host)
            put("mail.smtp.port", port.toString())
            put("mail.smtp.connectiontimeout", "15000")
            put("mail.smtp.timeout", "15000")
            put("mail.smtp.writetimeout", "15000")
            if (ssl) {
                put("mail.smtp.ssl.enable", "true")
            } else {
                put("mail.smtp.starttls.enable", "true")
            }
        }
        try {
            val session = Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication() = PasswordAuthentication(user, password)
            })
            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(from, "WorkHub", "UTF-8"))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(email))
                setSubject("Mã xác thực WorkHub", "UTF-8")
                setText(
                    "Mã xác thực tài khoản WorkHub của bạn là $code.\nMã có hiệu lực trong 5 phút.",
                    "UTF-8",
                )
            }
            Transport.send(message)
        } catch (error: Exception) {
            println("WorkHub verify: gửi mail thất bại — ${error.message}")
            throw AppException(HttpStatusCode.BadGateway, "Không gửi được email. Hãy thử lại sau.")
        }
    }
}
