package com.brahma.mobile.llm

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object Http {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    /** Cancellation-aware network call. Returns the body, throws IOException on non-2xx. */
    suspend fun send(req: Request): String = suspendCancellableCoroutine { cont ->
        val call = client.newCall(req)
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { r ->
                    val body = r.body?.string().orEmpty()
                    if (r.isSuccessful) cont.resume(body)
                    else cont.resumeWithException(IOException("HTTP ${r.code}: ${body.take(300)}"))
                }
            }
        })
    }

    suspend fun get(url: String): String =
        send(Request.Builder().url(url).header("User-Agent", "BrahmaMobile/1.0").build())

    fun jsonBody(s: String) = s.toRequestBody("application/json".toMediaType())
}
