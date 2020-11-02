package run.qontract

import org.assertj.core.api.Assertions.assertThat
import run.qontract.core.*
import run.qontract.core.pattern.AnyPattern
import run.qontract.core.pattern.DeferredPattern
import run.qontract.core.pattern.Pattern
import run.qontract.core.value.Value
import run.qontract.mock.ScenarioStub
import kotlin.test.assertFalse
import kotlin.test.assertTrue

fun optionalPattern(pattern: Pattern): AnyPattern = AnyPattern(listOf(DeferredPattern("(empty)"), pattern))

infix fun Value.shouldMatch(pattern: Pattern) {
    val result = pattern.matches(this, Resolver())
    if(!result.isTrue()) println(resultReport(result))
    assertTrue(result.isTrue())
}

infix fun Value.shouldNotMatch(pattern: Pattern) {
    assertFalse(pattern.matches(this, Resolver()).isTrue())
}

fun emptyPattern() = DeferredPattern("(empty)")

infix fun String.backwardCompatibleWith(oldContractGherkin: String) {
    val results = testBackwardCompatibility(oldContractGherkin)
    assertThat(results.success()).isTrue()
    assertThat(results.failureCount).isZero()

    stubsFrom(oldContractGherkin).workWith(this)
}

infix fun String.notBackwardCompatibleWith(oldContractGherkin: String) {
    val results = testBackwardCompatibility(oldContractGherkin)
    assertThat(results.success()).`as`("Should not have been compatible").isFalse
    assertThat(results.failureCount).`as`("Failure count should have been positive").isPositive

    stubsFrom(oldContractGherkin).breakOn(this)
}

fun String.testBackwardCompatibility(oldContractGherkin: String): Results {
    val oldFeature = Feature(oldContractGherkin)
    val newFeature = Feature(this)
    return testBackwardCompatibility(oldFeature, newFeature)
}

fun testStub(contractGherkin: String, stubRequest: HttpRequest, stubResponse: HttpResponse): HttpResponse {
    val feature = Feature(contractGherkin)
    val stub = ScenarioStub(stubRequest, stubResponse)
    val matchingStub = feature.matchingStub(stub)

    return run.qontract.stub.stubResponse(stubRequest, listOf(feature), listOf(matchingStub), true).let {
        it.response.copy(headers = it.response.headers - QONTRACT_RESULT_HEADER)
    }
}

fun stub(stubRequest: HttpRequest, stubResponse: HttpResponse): TestHttpStub =
        TestHttpStub(stubRequest, stubResponse)

private fun stubsFrom(oldContract: String): TestHttpStubData {
    val oldFeature = Feature(oldContract)

    val testScenarios = oldFeature.generateTestScenarios()

    return TestHttpStubData(oldContract, testScenarios.map { scenario ->
        val request = scenario.generateHttpRequest()
        val response = scenario.generateHttpResponse(emptyMap())

        TestHttpStub(stubRequest = request, stubResponse = response.copy(headers = response.headers.minus(QONTRACT_RESULT_HEADER)))
    })
}

private class TestHttpStubData(val oldContract: String, val stubs: List<TestHttpStub>) {
    fun breakOn(newContract: String) {
        for(stub in stubs) {
            stub.shouldWorkWith(oldContract)
        }

        assertThat(stubs.asSequence().map { stub ->
            stub.breaksWith(newContract)
        }.all { it }).isTrue
    }

    fun workWith(newContract: String) {
        for(stub in stubs) {
            stub.shouldWorkWith(oldContract)
            stub.shouldWorkWith(newContract)
        }
    }
}
