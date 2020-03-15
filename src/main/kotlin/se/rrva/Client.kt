package se.rrva

import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.url
import io.ktor.network.sockets.ConnectTimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.debug.DebugProbes
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.util.concurrent.TimeoutException

class Client(val fooServiceUrl: String) {

    val client = HttpClient(Apache) {
        engine {
            connectTimeout = 1
            connectionRequestTimeout = 1
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

fun main() {
    DebugProbes.install()
    HttpServer.create(InetSocketAddress(9090), 0).apply {

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

    runBlocking {
        val requests = (1..100).map {
            async(Dispatchers.Default) {
                try {
                    client.fetchFoo(listOf(it.toString()))
                } catch (e: SocketTimeoutException) {
                    println("Connect timeout")
                    1
                }
                catch (e: TimeoutException) {
                    println("Connect lease timeout")
                }
            }
        }
        requests.forEach {
            println("Waiting for coroutine...")
            it.await()
            println("Done waiting")
        }
    }

    println("Done with all")

}