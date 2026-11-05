package com.workhub.server

import com.workhub.shared.UploadResult
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import java.io.File
import java.util.UUID

private val allowedExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "pdf", "txt", "doc", "docx", "xls", "xlsx", "zip")
private const val MAX_UPLOAD_BYTES = 8 * 1024 * 1024

fun uploadDirectory(): File {
    val dir = File(System.getenv("UPLOAD_DIR")?.ifBlank { null } ?: "uploads")
    dir.mkdirs()
    return dir
}

suspend fun receiveUpload(call: ApplicationCall) {
    var stored: File? = null
    var original = "file"
    call.receiveMultipart().forEachPart { part ->
        if (part is PartData.FileItem && stored == null) {
            original = part.originalFileName?.substringAfterLast('/')?.substringAfterLast('\\') ?: "file"
            val ext = original.substringAfterLast('.', "").lowercase().filter { it.isLetterOrDigit() }
            if (ext !in allowedExtensions) {
                part.dispose()
                throw AppException(HttpStatusCode.BadRequest, "Định dạng tệp không được hỗ trợ")
            }
            val dest = File(uploadDirectory(), "${UUID.randomUUID()}.$ext")
            part.streamProvider().use { input ->
                dest.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_UPLOAD_BYTES) {
                            dest.delete()
                            throw AppException(HttpStatusCode.BadRequest, "Tệp tối đa 8 MB")
                        }
                        output.write(buffer, 0, read)
                    }
                }
            }
            stored = dest
        }
        part.dispose()
    }
    val file = stored ?: throw AppException(HttpStatusCode.BadRequest, "Thiếu tệp")
    val kind = if (file.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif")) "IMAGE" else "FILE"
    val proto = call.request.headers["X-Forwarded-Proto"] ?: call.request.local.scheme
    val host = call.request.headers["X-Forwarded-Host"] ?: call.request.headers["Host"] ?: call.request.local.serverHost
    call.respond(UploadResult("$proto://$host/uploads/${file.name}", original.take(200), kind))
}
