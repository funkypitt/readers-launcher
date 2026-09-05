package com.freedomfighter.readerslauncher.net

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Minimal HTTP helper on HttpURLConnection — no client library needed. */
object Http {
    fun get(url: String, headers: Map<String, String> = emptyMap()): String =
        request("GET", url, null, headers)

    fun request(method: String, url: String, body: String?, headers: Map<String, String> = emptyMap(), contentType: String = "application/json"): String {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", "readers-launcher")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", contentType)
            }
        }
        try {
            if (body != null) c.outputStream.use { it.write(body.toByteArray()) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw HttpException(code, text)
            return text
        } finally {
            c.disconnect()
        }
    }
}

class HttpException(val code: Int, val body: String) : IOException("HTTP $code: ${body.take(200)}")
