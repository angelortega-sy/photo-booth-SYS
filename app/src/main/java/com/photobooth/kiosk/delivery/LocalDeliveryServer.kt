package com.photobooth.kiosk.delivery

import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class DeliverySession(
    val pageUrl: String,
    val token: String,
    val port: Int,
)

/**
 * Tiny HTTP server for the current photo session only. It serves a small
 * mobile page, the four JPEGs, and a ZIP with everything.
 *
 * It is intentionally dependency-free and only exposes the random session
 * token, never the gallery or arbitrary files.
 */
class LocalDeliveryServer {

    private val random = SecureRandom()
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null
    private var clients: ExecutorService? = null
    private var files: List<File> = emptyList()
    private var token: String = ""
    private var eventTitle: String = ""

    @Volatile
    private var running = false

    fun start(files: List<File>, eventTitle: String): DeliverySession? {
        if (files.isEmpty() || files.size > 4 || files.any { !it.isFile }) return null
        val address = LocalNetwork.ipv4Address() ?: return null

        stop()
        val socket = runCatching { ServerSocket(0) }.getOrElse {
            Log.e(TAG, "Could not open delivery socket", it)
            return null
        }

        this.files = files
        this.eventTitle = eventTitle.ifBlank { "Cabina de Fotos" }
        token = newToken()
        serverSocket = socket
        running = true
        clients = Executors.newCachedThreadPool()
        acceptThread = Thread({ acceptLoop(socket) }, "photo-delivery").apply {
            isDaemon = true
            start()
        }

        return DeliverySession(
            pageUrl = "http://$address:${socket.localPort}/d/$token",
            token = token,
            port = socket.localPort,
        )
    }

    fun stop() {
        running = false
        runCatching { serverSocket?.close() }
        serverSocket = null
        acceptThread?.interrupt()
        acceptThread = null
        clients?.shutdownNow()
        clients = null
        files = emptyList()
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (running) {
            val client = try {
                socket.accept()
            } catch (error: IOException) {
                if (running) Log.w(TAG, "Accept failed", error)
                break
            }
            runCatching { clients?.execute { handle(client) } }
                .onFailure { client.close() }
        }
    }

    private fun handle(socket: Socket) {
        socket.use { client ->
            client.soTimeout = 10_000
            val requestLine = try {
                client.getInputStream().bufferedReader(Charsets.ISO_8859_1).readLine()
            } catch (_: IOException) {
                null
            } ?: return

            val target = requestLine.split(' ').getOrNull(1).orEmpty()
            val path = target.substringBefore('?')
            val prefix = "/d/$token"

            when {
                path == "/favicon.ico" -> sendEmpty(client, 204)
                path == prefix || path == "$prefix/" -> send(
                    client,
                    200,
                    "text/html; charset=utf-8",
                    pageHtml().toByteArray(Charsets.UTF_8),
                )
                path == "$prefix/all.zip" -> send(
                    client,
                    200,
                    "application/zip",
                    zipBytes(),
                    "Content-Disposition: attachment; filename=\"cabina-de-fotos.zip\"\r\n",
                )
                path.startsWith("$prefix/i/") -> {
                    val index = path.removePrefix("$prefix/i/").toIntOrNull()
                    val file = index?.let { files.getOrNull(it) }
                    if (file == null || !file.isFile) {
                        sendText(client, 404, "Foto no encontrada")
                    } else {
                        send(
                            client,
                            200,
                            "image/jpeg",
                            file.readBytes(),
                            "Content-Disposition: inline; filename=\"${downloadName(index)}\"\r\n",
                        )
                    }
                }
                else -> sendText(client, 404, "No encontrado")
            }
        }
    }

    private fun pageHtml(): String {
        val title = escapeHtml(eventTitle)
        val single = files.size == 1
        val cards = files.mapIndexed { index, _ ->
            val label = when {
                single -> "Mi foto"
                index == 3 && files.size == 4 -> "Tira completa"
                else -> "Foto ${index + 1}"
            }
            """
            <a class="card" href="/d/$token/i/$index" download="${downloadName(index)}">
              <img src="/d/$token/i/$index" alt="$label">
              <span>$label</span>
            </a>
            """.trimIndent()
        }.joinToString("\n")

        return """
            <!doctype html>
            <html lang="es">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>$title</title>
              <style>
                :root { color-scheme: dark; }
                * { box-sizing: border-box; }
                body {
                  margin: 0; min-height: 100vh; padding: 28px 18px 40px;
                  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
                  color: #f3f8ff;
                  background:
                    radial-gradient(circle at 15% 0%, #0d3d78 0, transparent 45%),
                    radial-gradient(circle at 100% 100%, #0b63ce44 0, transparent 40%),
                    #050b14;
                }
                main { width: 100%; max-width: 520px; margin: 0 auto; }
                .brand { color: #5ea8ff; font-size: 12px; font-weight: 800; letter-spacing: 2.5px; text-transform: uppercase; }
                h1 { margin: 10px 0 6px; font-size: 30px; line-height: 1.1; }
                .subtle { margin: 0 0 22px; color: #9ac2f4; font-size: 14px; line-height: 1.5; }
                .grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
                .card {
                  display: block; overflow: hidden; border-radius: 18px;
                  background: #0b1727; border: 1px solid #5ea8ff33;
                  color: #f3f8ff; text-decoration: none;
                }
                .card img { display: block; width: 100%; aspect-ratio: 9 / 16; object-fit: cover; }
                .card span { display: block; padding: 10px 12px; font-size: 12px; font-weight: 700; }
                .zip {
                  display: block; margin-top: 16px; padding: 16px;
                  border-radius: 18px; background: #0b63ce; color: white;
                  text-align: center; text-decoration: none; font-weight: 800;
                }
                .hint { margin: 16px 0 0; color: #9ac2f4; font-size: 12px; line-height: 1.5; text-align: center; }
              </style>
            </head>
            <body>
              <main>
                <div class="brand">Cabina de Fotos</div>
                <h1>$title</h1>
                <p class="subtle">${if (single) "Tu foto está lista. Tócala para guardarla en tu teléfono." else "Tus recuerdos están listos. Toca una foto para guardarla en tu teléfono."}</p>
                <div class="grid">$cards</div>
                <a class="zip" href="/d/$token/all.zip">${if (single) "Descargar foto · JPG" else "Descargar todo · ZIP"}</a>
                <p class="hint">En iPhone, si la foto se abre en lugar de descargarse, mantén presionada la imagen y elige “Guardar en Fotos”.</p>
              </main>
            </body>
            </html>
        """.trimIndent()
    }

    private fun zipBytes(): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            files.forEachIndexed { index, file ->
                zip.putNextEntry(ZipEntry(downloadName(index)))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun downloadName(index: Int): String =
        if (index == 3 && files.size == 4) "cabina-tira.jpg" else "cabina-foto-${index + 1}.jpg"

    private fun sendText(socket: Socket, status: Int, text: String) {
        send(socket, status, "text/plain; charset=utf-8", text.toByteArray(Charsets.UTF_8))
    }

    private fun sendEmpty(socket: Socket, status: Int) {
        val head = "HTTP/1.1 $status No Content\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
        socket.getOutputStream().write(head.toByteArray(Charsets.US_ASCII))
    }

    private fun send(
        socket: Socket,
        status: Int,
        contentType: String,
        body: ByteArray,
        extraHeaders: String = "",
    ) {
        val head = buildString {
            append("HTTP/1.1 $status ${statusText(status)}\r\n")
            append("Content-Type: $contentType\r\n")
            append("Content-Length: ${body.size}\r\n")
            append("Cache-Control: no-store\r\n")
            append("Connection: close\r\n")
            append(extraHeaders)
            append("\r\n")
        }
        val output: OutputStream = socket.getOutputStream()
        output.write(head.toByteArray(Charsets.US_ASCII))
        output.write(body)
        output.flush()
    }

    private fun statusText(status: Int): String = when (status) {
        200 -> "OK"
        204 -> "No Content"
        404 -> "Not Found"
        else -> "Error"
    }

    private fun newToken(): String {
        val bytes = ByteArray(10)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun escapeHtml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private companion object {
        const val TAG = "PhotoDelivery"
    }
}

private object LocalNetwork {
    fun ipv4Address(): String? {
        val interfaces = mutableListOf<NetworkInterface>()
        val enumeration = NetworkInterface.getNetworkInterfaces() ?: return null
        while (enumeration.hasMoreElements()) interfaces += enumeration.nextElement()

        val candidates = interfaces.flatMap { network ->
            val name = network.name.lowercase()
            if (name.startsWith("rmnet") || name.startsWith("ccmni") ||
                name.startsWith("pdp") || name.startsWith("dummy") ||
                name.startsWith("tun")
            ) {
                emptyList()
            } else {
                network.inetAddresses.toList()
                    .filterIsInstance<Inet4Address>()
                    .filter { !it.isLoopbackAddress && it.isSiteLocalAddress }
                    .map { name to it.hostAddress.orEmpty() }
            }
        }.filter { it.second.isNotBlank() }

        val preferred = candidates.filter { (name, _) ->
            name.contains("wlan") || name.contains("ap") ||
                name.contains("eth") || name.contains("swlan")
        }
        return (preferred.ifEmpty { candidates }).firstOrNull()?.second
    }
}
