package dev.adrian.showdown

import java.io.IOException
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

class ShowdownLoginClient(private val httpClient: OkHttpClient = OkHttpClient()) {
    fun login(endpoint: ShowdownServerEndpoint, credentials: ShowdownCredentials, challenge: String, callback: (Result<String>) -> Unit) {
        val request = Request.Builder()
            .url(endpoint.loginUrl)
            .post(FormBody.Builder().add("name", credentials.username).add("pass", credentials.password).add("challstr", challenge).build())
            .build()
        httpClient.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, error: IOException) = callback(Result.failure(error))

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    val assertion = if (it.isSuccessful) it.body.string().let(ShowdownAuthentication::assertion) else null
                    callback(assertion?.let(Result.Companion::success) ?: Result.failure(IOException("Showdown rejected the login (HTTP ${it.code}).")))
                }
            }
        })
    }
}
