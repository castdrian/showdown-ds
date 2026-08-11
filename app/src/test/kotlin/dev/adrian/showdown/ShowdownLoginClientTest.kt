package dev.adrian.showdown

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowdownLoginClientTest {
    @Test
    fun registrationPostsTheOfficialFieldsAndReturnsTheAssertion() {
        val request = AtomicReference<Request>()
        val result = AtomicReference<Result<String>>()
        val completed = CountDownLatch(1)
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            request.set(chain.request())
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("]{\"actionsuccess\":true,\"assertion\":\"token\"}".toResponseBody("application/json".toMediaType()))
                .build()
        }.build()

        ShowdownLoginClient(client).register(
            ShowdownServerEndpoint("Test", "ws://test", "https://test/api/login"),
            ShowdownCredentials("Adrian", "secret"),
            "secret",
            "Pikachu",
            "1|challenge",
        ) {
            result.set(it)
            completed.countDown()
        }

        assertTrue(completed.await(2, TimeUnit.SECONDS))
        assertEquals("token", result.get().getOrThrow())
        assertEquals("https://test/api/register", request.get().url.toString())
        val body = Buffer()
        request.get().body!!.writeTo(body)
        val encoded = body.readUtf8()
        assertTrue(encoded.contains("username=Adrian"))
        assertTrue(encoded.contains("password=secret"))
        assertTrue(encoded.contains("cpassword=secret"))
        assertTrue(encoded.contains("captcha=Pikachu"))
        assertTrue(encoded.contains("challstr=1%7Cchallenge"))
    }

    @Test
    fun registrationSurfacesLoginServerErrors() {
        val completed = CountDownLatch(1)
        val result = AtomicReference<Result<String>>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("]{\"actionsuccess\":false,\"error\":\"That username is already taken.\"}".toResponseBody("application/json".toMediaType()))
                .build()
        }.build()

        ShowdownLoginClient(client).register(
            ShowdownServerEndpoint("Test", "ws://test", "https://test/api/login"),
            ShowdownCredentials("Adrian", "secret"),
            "secret",
            "Pikachu",
            "1|challenge",
        ) {
            result.set(it)
            completed.countDown()
        }

        assertTrue(completed.await(2, TimeUnit.SECONDS))
        assertEquals("That username is already taken.", result.get().exceptionOrNull()?.message)
    }
}
