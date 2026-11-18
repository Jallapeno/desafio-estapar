package br.com.estapar.parking.infrastructure.web

import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.constructor.SafeConstructor

@MicronautTest(transactional = false)
class OpenApiIntegrationTest {
    @Inject @field:Client("/") lateinit var client: HttpClient

    private fun spec(): Map<String, Any> {
        val response = client.toBlocking().exchange(HttpRequest.GET<Any>("/swagger/estapar.yml"), String::class.java)
        assertEquals(200, response.code())
        return Yaml(SafeConstructor(LoaderOptions())).load(response.body())
    }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any>.node(key: String): Map<String, Any> {
        assertTrue(this[key] is Map<*, *>, "Missing object: $key; available keys: ${keys}")
        return this[key] as Map<String, Any>
    }

    @Test fun `should expose generated webhook contract with all event examples and responses`() {
        val document = spec()
        assertTrue(document["openapi"].toString().startsWith("3."))
        val operation = document.node("paths").node("/webhook").node("post")
        assertTrue(operation.node("responses").keys.containsAll(listOf("200", "400", "500")))
        val content = operation.node("requestBody").node("content").node("application/json")
        assertEquals(setOf("ENTRY", "PARKED", "EXIT"), content.node("examples").keys)
        val schema = document.node("components").node("schemas").node("WebhookEventRequest")
        assertTrue((schema["required"] as List<*>).containsAll(listOf("license_plate", "event_type")))
    }

    @Test fun `should document revenue query filters without a GET request body for browser clients`() {
        val document = spec()
        val operation = document.node("paths").node("/revenue").node("get")
        val params = operation["parameters"] as List<*>
        assertEquals(setOf("sector", "date"), params.map { (it as Map<*, *>)["name"] }.toSet())
        assertTrue(operation.node("responses").keys.containsAll(listOf("200", "400", "500")))
        assertFalse(operation.containsKey("requestBody"), "Browsers reject GET requests with a body")
        assertTrue(operation["description"].toString().contains("body GET"))
        val response = document.node("components").node("schemas").node("RevenueResponse").node("properties")
        assertEquals(setOf("amount", "currency", "timestamp"), response.keys)
    }

    @Test fun `should serve Swagger UI pointing to the generated specification`() {
        val html = client.toBlocking().retrieve("/swagger-ui/index.html")
        assertTrue(html.contains("SwaggerUIBundle"))
        assertTrue(html.contains("/swagger/estapar.yml"))
        for (asset in listOf("swagger-ui.css", "swagger-ui-bundle.js", "swagger-ui-standalone-preset.js")) {
            val response = client.toBlocking().exchange(HttpRequest.GET<Any>("/swagger-ui/res/$asset"), String::class.java)
            assertEquals(200, response.code(), "Swagger UI asset: $asset")
            assertFalse(response.body().isNullOrBlank())
        }
    }
}
