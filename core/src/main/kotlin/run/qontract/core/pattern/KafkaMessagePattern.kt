package run.qontract.core.pattern

import run.qontract.core.Resolver
import run.qontract.core.Result
import run.qontract.core.breadCrumb
import run.qontract.core.value.KafkaMessage
import run.qontract.core.value.NullValue

data class KafkaMessagePattern(val target: String = "", val key: Pattern = NoContentPattern, val value: Pattern = StringPattern) {
    fun matches(message: KafkaMessage, resolver: Resolver): Result {
        return attempt("KAFKA-MESSAGE") { _matches(message, resolver).breadCrumb("KAFKA-MESSAGE") }
    }
    fun _matches(message: KafkaMessage, resolver: Resolver): Result {
        if(message.target != target)
            return Result.Failure("Expected target $target, got $message.target").breadCrumb("TARGET")

        val keyMatch = key.matches(message.key ?: NullValue, resolver)
        if(keyMatch !is Result.Success)
            return keyMatch.breadCrumb("KEY")

        return value.matches(message.value, resolver).breadCrumb("VALUE")
    }

    fun encompasses(other: KafkaMessagePattern, thisResolver: Resolver, otherResolver: Resolver): Result {
        return attempt("KAFKA-MESSAGE") { _encompasses(other, thisResolver, otherResolver).breadCrumb("KAFKA-MESSAGE") }
    }

    fun _encompasses(other: KafkaMessagePattern, thisResolver: Resolver, otherResolver: Resolver): Result {
        if(target != other.target)
            return Result.Failure("Expected target $target, got ${other.target}").breadCrumb("TARGET")

        val keyResult = key.encompasses2(other.key, otherResolver, thisResolver)
        if(keyResult is Result.Failure)
            return keyResult.breadCrumb("KEY")

        return value.encompasses2(other.value, otherResolver, thisResolver).breadCrumb("VALUE")
    }

    fun newBasedOn(row: Row, resolver: Resolver): List<KafkaMessagePattern> {
        val newKeys = key.newBasedOn(row, resolver)
        val newValues = value.newBasedOn(row, resolver)

        return newKeys.flatMap { newKey ->
            newValues.map { newValue ->
                KafkaMessagePattern(target, newKey, newValue)
            }
        }
    }
}
