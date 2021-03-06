package run.qontract.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import run.qontract.core.GherkinSection.Then
import run.qontract.core.pattern.parsedJSON
import run.qontract.core.pattern.parsedValue
import run.qontract.core.value.EmptyString
import run.qontract.core.value.JSONObjectValue
import run.qontract.core.value.StringValue
import java.util.*
import kotlin.test.assertEquals

internal class HttpResponseTest {
    @Test
    fun createANewResponseObjectWithInitialValues() {
        val response = HttpResponse(500, "ERROR", HashMap())
        Assertions.assertEquals(500, response.status)
        Assertions.assertEquals(StringValue("ERROR"), response.body)
    }

    @Test
    fun createANewResponseObjectWithoutInitialValues() {
        val response = HttpResponse.EMPTY
        Assertions.assertEquals(0, response.status)
        Assertions.assertEquals(EmptyString, response.body)
    }

    @Test
    fun `updating body with value should automatically set Content-Type header`() {
        HttpResponse.EMPTY.updateBodyWith(parsedValue("""{"name": "John Doe"}""")).let {
            val responseBody = it.body

            if(responseBody !is JSONObjectValue)
                throw AssertionError("Expected responseBody to be a JSON object, but got ${responseBody.javaClass.name}")

            assertEquals("John Doe", responseBody.jsonObject.getValue("name").toStringValue())
            assertEquals("application/json", it.headers.getOrDefault("Content-Type", ""))
        }
    }

    @Test
    fun `gherkin clauses from simple 200 response`() {
        val clauses = toGherkinClauses(HttpResponse.OK)

        assertThat(clauses.first).hasSize(1)
        assertThat(clauses.first.single().section).isEqualTo(Then)
        assertThat(clauses.first.single().content).isEqualTo("status 200")
    }

    @Test
    fun `gherkin clauses from response with headers`() {
        val clauses = toGherkinClauses(HttpResponse(200, headers = mapOf("X-Value" to "10"), body = EmptyString))

        assertThat(clauses.first).hasSize(2)
        assertThat(clauses.first.first().section).isEqualTo(Then)
        assertThat(clauses.first.first().content).isEqualTo("status 200")

        assertThat(clauses.first[1].section).isEqualTo(Then)
        assertThat(clauses.first[1].content).isEqualTo("response-header X-Value (number)")
    }

    @Test
    fun `gherkin clauses from response with body`() {
        val clauses = toGherkinClauses(HttpResponse(200, headers = emptyMap(), body = StringValue("response data")))

        assertThat(clauses.first).hasSize(2)
        assertThat(clauses.first.first().section).isEqualTo(Then)
        assertThat(clauses.first.first().content).isEqualTo("status 200")

        assertThat(clauses.first[1].section).isEqualTo(Then)
        assertThat(clauses.first[1].content).isEqualTo("response-body (string)")
    }

    @Test
    fun `gherkin clauses from response with number body`() {
        val clauses = toGherkinClauses(HttpResponse(200, headers = emptyMap(), body = StringValue("10")))

        assertThat(clauses.first).hasSize(2)
        assertThat(clauses.first.first().section).isEqualTo(Then)
        assertThat(clauses.first.first().content).isEqualTo("status 200")

        assertThat(clauses.first[1].section).isEqualTo(Then)
        assertThat(clauses.first[1].content).isEqualTo("response-body (number)")
    }

    @Test
    fun `gherkin clauses should contain no underscores when there are duplicate keys`() {
        val (clauses, _, examples) = toGherkinClauses(HttpResponse(200, body = parsedJSON("""[{"data": 1}, {"data": 2}]""")))

        assertThat(examples).isInstanceOf(DiscardExampleDeclarations::class.java)

        for(clause in clauses) {
            assertThat(clause.content).doesNotContain("_")
        }
    }
}