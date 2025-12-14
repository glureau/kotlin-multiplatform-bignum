package com.ionspin.kotlin.bignum.integer

import com.ionspin.kotlin.bignum.QuotientAndRemainder
import com.ionspin.kotlin.bignum.modular.ModularBigInteger
import kotlin.math.log10

val BigInteger.sign: Sign
    get() = when (this.signum()) {
        -1 -> Sign.NEGATIVE
        0 -> Sign.ZERO
        1 -> Sign.POSITIVE
        else -> throw RuntimeException("Unexpected signum")
    }

data class QuotientAndRemainder(val quotient: BigInteger, val remainder: BigInteger)

infix fun BigInteger.divrem(other: BigInteger): com.ionspin.kotlin.bignum.QuotientAndRemainder {
    val result = divideAndRemainder(other)
    return QuotientAndRemainder(result.first, result.second)
}

// Operators
operator fun BigInteger.dec(): BigInteger = this - BigInteger.ONE
operator fun BigInteger.inc(): BigInteger = this + BigInteger.ONE
operator fun BigInteger.times(other: BigInteger): BigInteger = this * other
operator fun BigInteger.times(other: Long): BigInteger = this.multiply(BigInteger(other))
operator fun BigInteger.times(other: Char): BigInteger = this.multiply(BigInteger(other.code))

val BigInteger.Companion.LOG_10_OF_2 get() = log10(2.0)

fun BigInteger.toStringWithoutSign(base: Int) = this.abs().toString(base)

fun BigInteger.factorial(): BigInteger {
    var result = BigInteger.ONE
    var element = BigInteger.ONE
    val abs = this.abs()
    while (element <= abs) {
        result *= element
        element = element.inc()
    }
    return if (this.isNegative) {
        -result
    } else {
        result
    }
}
operator fun BigInteger.rangeTo(other: BigInteger) = BigIntegerRange(this, other)

class BigIntegerRange(override val start: BigInteger, override val endInclusive: BigInteger) :
    ClosedRange<BigInteger>, Iterable<BigInteger> {
    override fun iterator(): Iterator<BigInteger> = BigIntegerIterator(start, endInclusive)
}

class BigIntegerIterator(start: BigInteger, private val endInclusive: BigInteger) : Iterator<BigInteger> {
    private var current = start
    override fun hasNext(): Boolean = current <= endInclusive
    override fun next(): BigInteger {
        return current++
    }
}

// Stub for ModularBigInteger if needed by project structure
fun BigInteger.toModularBigInteger(modulo: BigInteger): ModularBigInteger {
    val creator = ModularBigInteger.creatorForModulo(modulo)
    return creator.fromBigInteger(this)
}
