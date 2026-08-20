package dev.adrian.showdown

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ShowdownUserFetcher(
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
) : AutoCloseable {
    fun fetch(
        endpoint: ShowdownServerEndpoint,
        username: String,
        receiver: (Result<ShowdownUserDetails.Profile>) -> Unit
    ) {
        executor.execute {
            receiver(runCatching {
                val connection = (URL(endpoint.userUrl(username)).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8_000
                    readTimeout = 12_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Showdown-Android/0.1")
                }
                try {
                    if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                        throw IOException("User request failed with HTTP ${connection.responseCode}.")
                    }
                    val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                        reader.readText()
                    }
                    ShowdownUserDetails.parsePublicPayload(body)
                        ?: throw IOException("Showdown returned an invalid user profile.")
                } finally {
                    connection.disconnect()
                }
            })
        }
    }

    override fun close() {
        executor.shutdownNow()
    }
}
