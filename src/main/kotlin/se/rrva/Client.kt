package se.rrva

import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.url
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.util.concurrent.CompletableFuture

class Client(val fooServiceUrl: String) {

    val client = HttpClient(CIO) {
        engine {
            requestTimeout = 500
            endpoint.connectTimeout = 100
            endpoint.connectRetryAttempts = 2
        }
        install(JsonFeature) {
            serializer = KotlinxSerializer(
                Json(
                    JsonConfiguration(
                        isLenient = true,
                        ignoreUnknownKeys = true,
                        serializeSpecialFloatingPointValues = true,
                        useArrayPolymorphism = true
                    )
                )
            )
        }
    }

    suspend fun fetchFoo(ids: List<String>): List<Foo> {
            return client.request {
                header("User-Agent", "Foo")
                parameter("input", ids.joinToString(","))
                url("$fooServiceUrl/foo")
            }
    }

}

@Serializable
data class Foo(val id: String)
val client = Client("http://localhost:9090")

fun fetchFooAsync(ids:List<String>): CompletableFuture<List<Foo>> {
    return CoroutineScope(Job() + Dispatchers.IO + MDCContext()).async {
        try {
            client.fetchFoo(ids)
        } catch (e: Throwable) {
            println(e.message)
            listOf<Foo>(Foo("timeout"))
        }
    }.asCompletableFuture()
}


fun main() {
    HttpServer.create(InetSocketAddress(9090), 1000).apply {

        createContext("/foo") { http ->
            http.responseHeaders.add("Content-type", "application/json")
            http.sendResponseHeaders(200, 0)
            PrintWriter(http.responseBody).use { out ->
                out.println("""[{"id":"1"}]""")
            }
        }

        start()
    }
    println("Start")

    val requests = (1..100).map {
        fetchFooAsync(listOf(it.toString()))
    }
    for (request in requests) {
        println("Waiting for coroutine...")
        println(request.get())
        println("Done waiting")
    }

    println("Done with all")

}