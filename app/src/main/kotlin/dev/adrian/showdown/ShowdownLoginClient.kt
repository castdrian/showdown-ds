package dev.adrian.showdown

import java.io.IOException
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

class ShowdownLoginClient(private val httpClient: OkHttpClient = OkHttpClient()) {
    private val cookies = linkedMapOf<String, String>()

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

    fun changePassword(
        endpoint: ShowdownServerEndpoint,
        oldPassword: String,
        password: String,
        confirmation: String,
        callback: (Result<Unit>) -> Unit
    ) {
        postAction(
            endpoint.changePasswordUrl,
            FormBody.Builder()
                .add("oldpassword", oldPassword)
                .add("password", password)
                .add("cpassword", confirmation)
                .build(),
            "password change",
            callback
        )
    }

    fun clearSession() {
        synchronized(cookies) { cookies.clear() }
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
        val request = request(url, body)
        httpClient.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, error: IOException) = callback(Result.failure(error))

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    rememberCookies(it)
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

    private fun postAction(url: String, body: FormBody, operationName: String, callback: (Result<Unit>) -> Unit) {
        httpClient.newCall(request(url, body)).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, error: IOException) = callback(Result.failure(error))

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    rememberCookies(it)
                    val payload = it.body.string()
                    val success = it.isSuccessful && ShowdownAuthentication.actionSucceeded(payload) == true
                    val message = ShowdownAuthentication.actionError(payload)
                        ?: "Showdown rejected the $operationName (HTTP ${it.code})."
                    callback(if (success) Result.success(Unit) else Result.failure(IOException(message)))
                }
            }
        })
    }

    private fun request(url: String, body: FormBody): Request = Request.Builder().url(url).post(body).apply {
        synchronized(cookies) {
            if (cookies.isNotEmpty()) addHeader("Cookie", cookies.entries.joinToString("; ") { (name, value) -> "$name=$value" })
        }
    }.build()

    private fun rememberCookies(response: okhttp3.Response) {
        response.headers("Set-Cookie").forEach { header ->
            val value = header.substringBefore(';')
            val name = value.substringBefore('=', "").trim()
            val cookie = value.substringAfter('=', "").trim()
            if (name.isNotBlank() && cookie.isNotBlank()) synchronized(cookies) { cookies[name] = cookie }
        }
    }
}
