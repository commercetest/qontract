package run.qontract.core.pattern

import run.qontract.core.Resolver
import run.qontract.core.Result
import run.qontract.core.value.StringValue
import run.qontract.core.value.Value
import run.qontract.core.value.XMLNode

interface Pattern {
    fun matches(sampleData: Value?, resolver: Resolver): Result
    fun generate(resolver: Resolver): Value
    fun newBasedOn(row: Row, resolver: Resolver): List<Pattern>
    fun parse(value: String, resolver: Resolver): Value

    fun patternSet(resolver: Resolver): List<Pattern> = listOf(this)

    fun encompasses(otherPattern: Pattern, thisResolver: Resolver, otherResolver: Resolver, typeStack: TypeStack = emptySet()): Result
    fun encompasses(others: List<Pattern>, thisResolver: Resolver, otherResolver: Resolver, lengthError: String, typeStack: TypeStack = emptySet()): ConsumeResult {
        val otherOne = others.firstOrNull()
                ?: return ConsumeResult(Result.Failure(lengthError), emptyList())

        val result = when {
            otherOne is ExactValuePattern && otherOne.pattern is StringValue -> ExactValuePattern(this.parse(otherOne.pattern.string, thisResolver))
            else -> otherOne
        }.let { otherOneAdjustedForExactValue -> this.encompasses(otherOneAdjustedForExactValue, thisResolver, otherResolver, typeStack) }

        return ConsumeResult(result, others.drop(1))
    }

    fun fitsWithin(otherPatterns: List<Pattern>, thisResolver: Resolver, otherResolver: Resolver, typeStack: TypeStack): Result {
        val myPatternSet = patternSet(thisResolver)

        val result = myPatternSet.map { myPattern ->
            val encompassResult = otherPatterns.asSequence().map { otherPattern ->
                biggerEncompassesSmaller(otherPattern, myPattern, thisResolver, otherResolver, typeStack)
            }

            encompassResult.find { it is Result.Success } ?: encompassResult.first()
        }

        return result.find { it is Result.Failure } ?: Result.Success()
    }

    fun listOf(valueList: List<Value>, resolver: Resolver): Value

    val typeAlias: String?
    val typeName: String
    val pattern: Any
}
