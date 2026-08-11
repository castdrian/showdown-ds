package dev.adrian.showdown

import java.io.IOException
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

class ShowdownLoginClient(private val httpClient: OkHttpClient = OkHttpClient()) {
    fun login(endpoint: ShowdownServerEndpoint, credentials: ShowdownCredentials, challenge: String, callback: (Result<String>) -> Unit) {
        post(
            endpoint.loginUrl,
            FormBody.Builder()
                .add("name", credentials.username)
                .add("pass", credentials.password)
                .add("challstr", challenge)
                .build(),
            "login",
            callback
        )
    }

    fun register(
        endpoint: ShowdownServerEndpoint,
        credentials: ShowdownCredentials,
        confirmation: String,
        captcha: String,
        challenge: String,
        callback: (Result<String>) -> Unit
    ) {
        post(
            endpoint.registrationUrl,
            FormBody.Builder()
                .add("username", credentials.username)
                .add("password", credentials.password)
                .add("cpassword", confirmation)
                .add("captcha", captcha)
                .add("challstr", challenge)
                .build(),
            "registration",
            callback
        )
    }

    private fun post(url: String, body: FormBody, operationName: String, callback: (Result<String>) -> Unit) {
        val request = Request.Builder().url(url).post(body).build()
        httpClient.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, error: IOException) = callback(Result.failure(error))

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    val payload = it.body.string()
                    val assertion = if (it.isSuccessful && ShowdownAuthentication.actionSucceeded(payload) != false) {
                        ShowdownAuthentication.assertion(payload)
                    } else {
                        null
                    }
                    val message = ShowdownAuthentication.actionError(payload)
                        ?: "Showdown rejected the $operationName (HTTP ${it.code})."
                    callback(assertion?.let(Result.Companion::success) ?: Result.failure(IOException(message)))
                }
            }
        })
    }
}
