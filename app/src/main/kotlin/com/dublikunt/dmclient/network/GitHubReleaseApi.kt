package com.dublikunt.dmclient.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class ReleaseAsset(val name: String, @SerialName("browser_download_url") val downloadUrl: String)

@Serializable
data class ReleaseInfo(
    val name: String,
    @SerialName("html_url") val htmlUrl: String,
    val body: String? = null,
    @SerialName("tag_name") val tagName: String = name,
    val assets: List<ReleaseAsset> = emptyList(),
)

@Singleton
class GitHubReleaseApi @Inject constructor(private val client: OkHttpClient) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun latest(): ReleaseInfo = withContext(Dispatchers.IO) {
        withRetries {
            val request = Request.Builder().url("https://api.github.com/repos/DublikuntMux/DMClient/releases/latest")
                .header("Accept", "application/vnd.github+json").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw ApiException.Http(response.code)
                try { json.decodeFromString<ReleaseInfo>(response.body.string()) }
                catch (error: Exception) { throw ApiException.Parse(error) }
            }
        }
    }

    suspend fun download(url: String, target: File, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val partial = File(target.parentFile, "${target.name}.part")
        try {
            withRetries {
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) throw ApiException.Http(response.code)
                    val length = response.body.contentLength()
                    var downloaded = 0L
                    onProgress(0f)
                    response.body.byteStream().use { input -> partial.outputStream().use { output ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            downloaded += count
                            if (length > 0) onProgress((downloaded.toFloat() / length).coerceIn(0f, 1f))
                        }
                    } }
                    if (downloaded == 0L || (length >= 0 && downloaded != length)) throw IOException("Incomplete APK download")
                }
            }
            currentCoroutineContext().ensureActive()
            if (!partial.renameTo(target)) throw IOException("Cannot save APK")
            onProgress(1f)
            target
        } finally { partial.delete() }
    }
}
