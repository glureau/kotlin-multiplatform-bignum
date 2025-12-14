/*
 *    Copyright 2019 Ugljesa Jovanovic
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 */

package com.ionspin.kotlin.bignum.integer

import com.ionspin.kotlin.bignum.BigNumber
import com.ionspin.kotlin.bignum.BitwiseCapable
import com.ionspin.kotlin.bignum.ByteArrayDeserializable
import com.ionspin.kotlin.bignum.ByteArraySerializable
import com.ionspin.kotlin.bignum.CommonBigNumberOperations
import com.ionspin.kotlin.bignum.NarrowingOperations
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.LOG_10_OF_2
import com.ionspin.kotlin.bignum.integer.base63.array.BigInteger63Arithmetic
import com.ionspin.kotlin.bignum.modular.ModularBigInteger
import kotlin.concurrent.Volatile
import kotlin.math.floor
import kotlin.math.log10



/**
 * Arbitrary precision integer arithmetic.
 *
 * Based on unsigned arrays, currently limited to [Int.MAX_VALUE] words.
 */

class CommonBigInteger internal constructor(wordArray: WordArray, sign: Sign) : BigNumber<CommonBigInteger>,
    CommonBigNumberOperations<CommonBigInteger>,
    NarrowingOperations<CommonBigInteger>,
    BitwiseCapable<CommonBigInteger>, Comparable<Any>,
    ByteArraySerializable {

    constructor(long: Long) : this(arithmetic.fromLong(long), determinSignFromNumber(long))
    constructor(int: Int) : this(arithmetic.fromInt(int), determinSignFromNumber(int))
    constructor(short: Short) : this(arithmetic.fromShort(short), determinSignFromNumber(short))
    constructor(byte: Byte) : this(arithmetic.fromByte(byte), determinSignFromNumber(byte))
    constructor(byteArray: ByteArray, sign: Sign) : this(arithmetic.fromByteArray(byteArray), sign)

    init {
        if (sign == Sign.ZERO) {
            require(isResultZero(wordArray)) {
                "sign should be Sign.ZERO iff magnitude has a value of 0"
            }
        }
    }

    override fun getCreator(): BigNumber.Creator<CommonBigInteger> {
        return CommonBigInteger
    }

    override fun getInstance(): CommonBigInteger {
        return this
    }

    fun getBackingArrayCopy(): WordArray {
        return magnitude.copyOf()
    }

    fun getSign(): Sign {
        return sign
    }

    companion object : BigNumber.Creator<CommonBigInteger>, BigNumber.Util<CommonBigInteger>, ByteArrayDeserializable<CommonBigInteger> {
        private val arithmetic: BigIntegerArithmetic = chosenArithmetic

        override val ZERO = CommonBigInteger(arithmetic.ZERO, Sign.ZERO)
        override val ONE = CommonBigInteger(arithmetic.ONE, Sign.POSITIVE)
        override val TWO = CommonBigInteger(arithmetic.TWO, Sign.POSITIVE)
        override val TEN = CommonBigInteger(arithmetic.TEN, Sign.POSITIVE)

        fun createFromWordArray(wordArray: WordArray, requestedSign: Sign): CommonBigInteger {
            return CommonBigInteger(wordArray, requestedSign)
        }

        override fun parseString(string: String, base: Int): CommonBigInteger {
            if (base < 2 || base > 36) {
                throw NumberFormatException("Unsupported base: $base. Supported base range is from 2 to 36")
            }
            val decimal = string.contains('.')
            if (decimal) {
                val bigDecimal = BigDecimal.parseString(string)
                val isActuallyDecimal = (bigDecimal - bigDecimal.floor()) > 0
                if (isActuallyDecimal) {
                    throw NumberFormatException("Supplied string is decimal, which cannot be converted to BigInteger without precision loss.")
                }
                return bigDecimal.toBigInteger() as CommonBigInteger
            }
            val signed = (string[0] == '-' || string[0] == '+')
            return if (signed) {
                if (string.length == 1) {
                    throw NumberFormatException("Invalid big integer: $string")
                }
                val isNegative = if (string[0] == '-') {
                    Sign.NEGATIVE
                } else {
                    Sign.POSITIVE
                }
                if (string.length == 2 && string[1] == '0') {
                    return ZERO
                }
                CommonBigInteger(
                    arithmetic.parseForBase(string.substring(startIndex = 1, endIndex = string.length), base),
                    isNegative
                )
            } else {
                if (string.length == 1 && string[0] == '0') {
                    return ZERO
                }
                CommonBigInteger(arithmetic.parseForBase(string, base), Sign.POSITIVE)
            }
        }

        internal fun fromWordArray(wordArray: WordArray, sign: Sign): CommonBigInteger {
            return CommonBigInteger(wordArray, sign)
        }

        private inline fun <reified T> determinSignFromNumber(number: Comparable<T>): Sign {
            return when (T::class) {
                Long::class -> {
                    number as Long
                    when {
                        number < 0 -> Sign.NEGATIVE
                        number > 0 -> Sign.POSITIVE
                        else -> Sign.ZERO
                    }
                }
                Int::class -> {
                    number as Int
                    when {
                        number < 0 -> Sign.NEGATIVE
                        number > 0 -> Sign.POSITIVE
                        else -> Sign.ZERO
                    }
                }
                Short::class -> {
                    number as Short
                    when {
                        number < 0 -> Sign.NEGATIVE
                        number > 0 -> Sign.POSITIVE
                        else -> Sign.ZERO
                    }
                }
                Byte::class -> {
                    number as Byte
                    when {
                        number < 0 -> Sign.NEGATIVE
                        number > 0 -> Sign.POSITIVE
                        else -> Sign.ZERO
                    }
                }
                else -> throw RuntimeException("Unsupported type ${T::class}")
            }
        }

        // BigIntegers are immutable so this is pointless, but the rest of creator implementations use this.
        //override fun fromBigInteger(bigInteger: CommonBigInteger): CommonBigInteger {
        //    return bigInteger
        //}

        override fun fromULong(uLong: ULong) = CommonBigInteger(arithmetic.fromULong(uLong), Sign.POSITIVE)
        override fun fromUInt(uInt: UInt) = CommonBigInteger(arithmetic.fromUInt(uInt), Sign.POSITIVE)
        override fun fromUShort(uShort: UShort) = CommonBigInteger(arithmetic.fromUShort(uShort), Sign.POSITIVE)
        override fun fromUByte(uByte: UByte) = CommonBigInteger(arithmetic.fromUByte(uByte), Sign.POSITIVE)
        override fun fromLong(long: Long) = CommonBigInteger(long)
        override fun fromInt(int: Int) = CommonBigInteger(int)
        override fun fromShort(short: Short) = CommonBigInteger(short)
        override fun fromByte(byte: Byte) = CommonBigInteger(byte)
        override fun fromBigInteger(bigInteger: BigInteger): CommonBigInteger {
            TODO("Not yet implemented")
        }

        override fun tryFromFloat(float: Float, exactRequired: Boolean): CommonBigInteger {
            val floatDecimalPart = float - floor(float)
            val bigDecimal = BigDecimal.fromFloat(floor(float), null)

            if (exactRequired) {
                if (floatDecimalPart > 0) {
                    throw ArithmeticException("Cant create BigInteger without precision loss, and exact  value was required")
                }
            }
            return bigDecimal.toBigInteger() as CommonBigInteger
        }

        override fun tryFromDouble(double: Double, exactRequired: Boolean): CommonBigInteger {
            val doubleDecimalPart = double - floor(double)
            val bigDecimal = BigDecimal.fromDouble(floor(double), null)

            if (exactRequired) {
                if (doubleDecimalPart > 0) {
                    throw ArithmeticException("Cant create BigInteger without precision loss, and exact  value was required")
                }
            }
            return bigDecimal.toBigInteger() as CommonBigInteger
        }

        override fun max(first: CommonBigInteger, second: CommonBigInteger): CommonBigInteger {
            return if (first > second) {
                first
            } else {
                second
            }
        }

        override fun min(first: CommonBigInteger, second: CommonBigInteger): CommonBigInteger {
            return if (first < second) {
                first
            } else {
                second
            }
        }

        override fun fromUByteArray(
            source: UByteArray,
            sign: Sign
        ): CommonBigInteger {
            val result = arithmetic.fromUByteArray(source)
            return CommonBigInteger(result, sign)
        }

        override fun fromByteArray(
            source: ByteArray,
            sign: Sign
        ): CommonBigInteger {
            val result = arithmetic.fromByteArray(source)
            return CommonBigInteger(result, sign)
        }
    }

    internal val magnitude: WordArray = BigInteger63Arithmetic.removeLeadingZeros(wordArray)

    internal val sign: Sign = if (isResultZero(magnitude)) {
        Sign.ZERO
    } else {
        sign
    }

    private fun isResultZero(resultMagnitude: WordArray): Boolean {
        return arithmetic.compare(resultMagnitude, arithmetic.ZERO) == 0
    }

    val numberOfWords = magnitude.size

    var stringRepresentation: String? = null

    override fun add(other: CommonBigInteger): CommonBigInteger {
        val comparison = arithmetic.compare(this.magnitude, other.magnitude)
        return if (other.sign == this.sign) {
            return CommonBigInteger(arithmetic.add(this.magnitude, other.magnitude), sign)
        } else {
            when {
                comparison > 0 -> {
                    CommonBigInteger(arithmetic.subtract(this.magnitude, other.magnitude), sign)
                }
                comparison < 0 -> {
                    CommonBigInteger(arithmetic.subtract(other.magnitude, this.magnitude), other.sign)
                }
                else -> {
                    ZERO
                }
            }
        }
    }

    override fun subtract(other: CommonBigInteger): CommonBigInteger {
        if (this.isZero()) {
            return other.negate()
        }
        if (other.isZero()) {
            return this
        }
        return if (other.sign == this.sign) {
            val comparison = arithmetic.compare(this.magnitude, other.magnitude)
            when {
                comparison > 0 -> {
                    CommonBigInteger(arithmetic.subtract(this.magnitude, other.magnitude), sign)
                }
                comparison < 0 -> {
                    CommonBigInteger(arithmetic.subtract(other.magnitude, this.magnitude), !sign)
                }
                else -> {
                    ZERO
                }
            }
        } else {
            return CommonBigInteger(arithmetic.add(this.magnitude, other.magnitude), sign)
        }
    }

    override fun multiply(other: CommonBigInteger): CommonBigInteger {
        if (this.isZero() || other.isZero()) {
            return ZERO
        }
        if (other == ONE) {
            return this
        }
        val sign = if (this.sign != other.sign) {
            Sign.NEGATIVE
        } else {
            Sign.POSITIVE
        }
        return if (sign == Sign.POSITIVE) {
            CommonBigInteger(arithmetic.multiply(this.magnitude, other.magnitude), sign)
        } else {
            CommonBigInteger(arithmetic.multiply(this.magnitude, other.magnitude), sign)
        }
    }

    override fun divide(other: CommonBigInteger): CommonBigInteger {
        if (other.isZero()) {
            throw ArithmeticException("Division by zero! $this / $other")
        }

        val result = arithmetic.divide(this.magnitude, other.magnitude).first
        return if (result == arithmetic.ZERO) {
            ZERO
        } else {
            val sign = if (this.sign != other.sign) {
                Sign.NEGATIVE
            } else {
                Sign.POSITIVE
            }
            CommonBigInteger(result, sign)
        }
    }

    /**
     * Returns the remainder of division operation. Uses truncating division, which means
     * that the sign of remainder will be same as sign of dividend
     */
    override fun remainder(other: CommonBigInteger): CommonBigInteger {
        if (other.isZero()) {
            throw ArithmeticException("Division by zero! $this / $other")
        }
        var sign = if (this.sign != other.sign) {
            Sign.NEGATIVE
        } else {
            Sign.POSITIVE
        }
        val result = arithmetic.divide(this.magnitude, other.magnitude).second
        if (result == arithmetic.ZERO) {
            sign = Sign.ZERO
        }

        return CommonBigInteger(result, sign)
    }

    override fun divideAndRemainder(other: CommonBigInteger): Pair<CommonBigInteger, CommonBigInteger> {
        if (other.isZero()) {
            throw ArithmeticException("Division by zero! $this / $other")
        }
        val sign = if (this.sign != other.sign) {
            Sign.NEGATIVE
        } else {
            Sign.POSITIVE
        }
        val result = arithmetic.divide(this.magnitude, other.magnitude)
        val quotient = if (result.first == arithmetic.ZERO) {
            ZERO
        } else {
            CommonBigInteger(result.first, sign)
        }
        val remainder = if (result.second == arithmetic.ZERO) {
            ZERO
        } else {
            CommonBigInteger(result.second, this.sign)
        }
        return Pair(
            quotient,
            remainder
        )
    }

    /**
     * D1Balanced reciprocal
     */
    private fun d1reciprocalRecursive(): CommonBigInteger {
        return CommonBigInteger(arithmetic.reciprocal(this.magnitude).first, sign)
    }

    fun sqrt(): CommonBigInteger {
        return CommonBigInteger(arithmetic.sqrt(magnitude).first, this.sign)
    }

    fun sqrtAndRemainder(): SqareRootAndRemainder {
        return SqareRootAndRemainder(
            CommonBigInteger(arithmetic.sqrt(magnitude).first, this.sign),
            CommonBigInteger(arithmetic.sqrt(magnitude).second, this.sign)
        )
    }

    fun modPow(exponent: CommonBigInteger, m: CommonBigInteger): CommonBigInteger {
        var e = exponent
        return if (m == ONE) {
            ZERO
        } else {
            var residue = ONE
            var base = residue
            while (e > 0) {
                if (e % 2 == ONE) {
                    residue = (residue * base) % m
                }
                e = e shr 1
                base = base.pow(2) % m
            }
            residue
        }
    }

    fun gcd(modulo: CommonBigInteger): CommonBigInteger {
        return CommonBigInteger(arithmetic.gcd(this.magnitude, modulo.magnitude), Sign.POSITIVE)
    }

    private fun naiveGcd(other: CommonBigInteger): CommonBigInteger {
        var u = this
        var v = other
        while (v != ZERO) {
            val tmpU = u
            u = v
            v = tmpU % v
        }
        return u
    }

    // https://en.wikipedia.org/wiki/Extended_Euclidean_algorithm#Modular_integers
    fun modInverse(modulo: CommonBigInteger): CommonBigInteger {
        // Ensure the numbers are coprime
        if (this.gcd(modulo) != ONE) {
            throw ArithmeticException("BigInteger is not invertible. This and modulus are not relatively prime (coprime).")
        }
        // Initialize variables for the Extended Euclidean Algorithm
        var t = ZERO
        var newT = ONE
        var r = modulo
        var newR = this

        // Loop until the remainder is zero
        while (newR != ZERO) {
            // Compute the quotient
            val quotient = r.divide(newR)

            // Update t and newT (coefficient)
            val tempT = t
            t = newT
            newT = tempT - quotient * newT

            // Update r and newR (remainder)
            val tempR = r
            r = newR
            newR = tempR - quotient * newR
        }

        // If r is greater than 1, this is not invertible
        if (r > ONE) throw ArithmeticException("BigInteger is not invertible.")

        // Ensure the result is positive
        if (t < ZERO) t += modulo

        return t
    }

    /**
     * Returns an always positive remainder of division operation
     */
    infix fun mod(modulo: CommonBigInteger): CommonBigInteger {
        val result = this % modulo
        return if (result < 0) {
            result + modulo
        } else {
            result
        }
    }

    fun compare(other: CommonBigInteger): Int {
        if (isZero() && other.isZero()) return 0
        if (other.isZero() && this.sign == Sign.POSITIVE) return 1
        if (other.isZero() && this.sign == Sign.NEGATIVE) return -1
        if (this.isZero() && other.sign == Sign.POSITIVE) return -1
        if (this.isZero() && other.sign == Sign.NEGATIVE) return 1
        if (sign != other.sign) return if (sign == Sign.POSITIVE) 1 else -1
        val result = arithmetic.compare(this.magnitude, other.magnitude)
        return if (this.sign == Sign.NEGATIVE && other.sign == Sign.NEGATIVE) {
            result * -1
        } else {
            result
        }
    }

    override fun isZero(): Boolean {
        return this.sign == Sign.ZERO ||
            chosenArithmetic.compare(this.magnitude, chosenArithmetic.ZERO) == 0
    }

    fun isPowerOfTen(): Boolean {
        if (this.isZero() || this.isNegative) return false
        if (this == ONE) return true
        // Quick check: last digit must be 0
        if ((this % TEN) != ZERO) return false
        var value = this
        while (value > TEN) {
            val divRem = value divrem TEN
            if (divRem.remainder != ZERO) return false
            value = divRem.quotient
        }
        return value == TEN || value == ONE
    }

    override fun negate(): CommonBigInteger {
        return CommonBigInteger(wordArray = this.magnitude, sign = sign.not())
    }

    override fun abs(): CommonBigInteger {
        return CommonBigInteger(wordArray = this.magnitude, sign = Sign.POSITIVE)
    }

    fun factorial(): CommonBigInteger {
        var result = ONE
        var element = ONE
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

    fun pow(exponent: CommonBigInteger): CommonBigInteger {
        if (exponent.isNegative)
            throw ArithmeticException("Negative exponent not supported with BigInteger")

        if (exponent <= Long.MAX_VALUE) {
            return pow(exponent.magnitude[0].toLong())
        }

        return exponentiationBySquaring(ONE, this, exponent)
    }

    private tailrec fun exponentiationBySquaring(y: CommonBigInteger, x: CommonBigInteger, n: CommonBigInteger): CommonBigInteger {
        return when {
            n.isZero() -> y
            n == ONE -> x * y
            n.mod(TWO).isZero() -> exponentiationBySquaring(y, x * x, n / 2)
            else -> exponentiationBySquaring(x * y, x * x, (n - 1) / 2)
        }
    }

    override fun pow(exponent: Long): CommonBigInteger {
        if (exponent < 0) {
            throw ArithmeticException("Negative exponent not supported with BigInteger")
        }
        return when {
            isZero() -> ZERO
            this == ONE -> ONE
            else -> {
                val sign = if (sign == Sign.NEGATIVE) {
                    if (exponent % 2 == 0L) {
                        Sign.POSITIVE
                    } else {
                        Sign.NEGATIVE
                    }
                } else {
                    Sign.POSITIVE
                }
                CommonBigInteger(arithmetic.pow(magnitude, exponent), sign)
            }
        }
    }

    override fun pow(exponent: Int): CommonBigInteger {
        return pow(exponent.toLong())
    }

    override fun signum(): Int = when (sign) {
        Sign.POSITIVE -> 1
        Sign.NEGATIVE -> -1
        Sign.ZERO -> 0
    }

    override fun bitAt(position: Long): Boolean {
        return arithmetic.bitAt(magnitude, position)
    }

    override fun setBitAt(position: Long, bit: Boolean): CommonBigInteger {
        return CommonBigInteger(arithmetic.setBitAt(magnitude, position, bit), sign)
    }

    override fun bitLength(): Int {
        return arithmetic.bitLength(magnitude)
    }

    override fun trailingZeroBits(): Int {
        return arithmetic.trailingZeroBits(magnitude)
    }

    override fun numberOfDecimalDigits(): Long {
        if (isZero()) return 1
        if (this.isNegative) return this.abs().numberOfDecimalDigits()

        // 1. Approximate using bit length.
        // log10(x) = log2(x) * log10(2)
        val bitLen = this.bitLength()
        val approximateDigits = floor((bitLen - 1) * BigInteger.LOG_10_OF_2).toLong() + 1

        // 2. We need 10^approximateDigits to verify.
        // (Note: Requires the Cache improvement below to be truly fast,
        // otherwise utilize a static/companion power function)
        val lowerBound = tenPow(approximateDigits - 1)

        return if (this < lowerBound) {
            approximateDigits - 1
        } else {
            // Check upper bound (power of 10 with approximateDigits)
            // Usually we don't need to check upper bound if we trust the floor logic,
            // but to be safe against edge cases where 10^k has same bit len:
            if (this >= lowerBound.multiply(BigInteger.TEN)) {
                approximateDigits + 1
            } else {
                approximateDigits
            }
        }
    }

    override infix fun shl(places: Int): CommonBigInteger {
        return CommonBigInteger(arithmetic.shiftLeft(this.magnitude, places), sign)
    }

    override infix fun shr(places: Int): CommonBigInteger {
        val result = CommonBigInteger(arithmetic.shiftRight(this.magnitude, places), sign)
        if (result.magnitude == arithmetic.ZERO) {
            return ZERO
        }
        return result
    }

    override operator fun unaryMinus(): CommonBigInteger = negate()

    override fun secureOverwrite() {
        for (i in 0 until magnitude.size) {
            magnitude[i] = 0U
        }
    }

    operator fun dec(): CommonBigInteger {
        return this - ONE
    }

    operator fun inc(): CommonBigInteger {
        return this + ONE
    }

    infix fun divrem(other: CommonBigInteger): QuotientAndRemainder {
        val result = divideAndRemainder(other)
        return QuotientAndRemainder(result.first, result.second)
    }

    override infix fun and(other: CommonBigInteger): CommonBigInteger {
        return CommonBigInteger(arithmetic.and(this.magnitude, other.magnitude), sign)
    }

    /** Returns a new BigInt with bits combining [this], [other] doing a bitwise `|`/`or` operation. Forces sign to positive. */
    // TODO Investigate what is expected behavior when one of the operand is negative. Is it considered to be two's complement?
    override infix fun or(other: CommonBigInteger): CommonBigInteger {
        val resultMagnitude = arithmetic.or(this.magnitude, other.magnitude)
        val resultSign = when {
            isResultZero(resultMagnitude) -> Sign.ZERO
            else -> Sign.POSITIVE
        }
        return CommonBigInteger(resultMagnitude, resultSign)
    }

    override infix fun xor(other: CommonBigInteger): CommonBigInteger {
        val resultMagnitude = arithmetic.xor(this.magnitude, other.magnitude)
        val resultSign = when {
            this.isNegative xor other.isNegative -> Sign.NEGATIVE
            isResultZero(resultMagnitude) -> Sign.ZERO
            else -> Sign.POSITIVE
        }
        return CommonBigInteger(resultMagnitude, resultSign)
    }

    /**
     * Inverts only up to chosen [arithmetic] [BigIntegerArithmetic.bitLength] bits.
     * This is different from Java biginteger which returns inverse in two's complement.
     *
     * I.e.: If the number was "1100" binary, not returns "0011" => "11" => 4 decimal
     */
    override fun not(): CommonBigInteger {
        return CommonBigInteger(arithmetic.not(this.magnitude), sign)
    }

    override fun compareTo(other: Any): Int {
        if (other is Number) {
            if (RuntimePlatform.currentPlatform() == Platform.JS) {
                return javascriptNumberComparison(other)
            }
        }
        return when (other) {
            is CommonBigInteger -> compare(other)
            is Long -> compare(fromLong(other))
            is Int -> compare(fromInt(other))
            is Short -> compare(fromShort(other))
            is Byte -> compare(fromByte(other))
            is ULong -> compare(fromULong(other))
            is UInt -> compare(fromUInt(other))
            is UShort -> compare(fromUShort(other))
            is UByte -> compare(fromUByte(other))
            is Float -> compareFloatAndBigInt(other) { compare(it) }
            is Double -> compareDoubleAndBigInt(other) { compare(it) }
            else -> throw RuntimeException("Invalid comparison type for BigInteger: ${other::class}")
        }
    }

    /**
     * Javascrpt doesn't have different types for float, integer, long, it's all just "number", so we need
     * to check if it's a decimal or integer number before comparing.
     */
    private fun javascriptNumberComparison(number: Number): Int {
        val double = number.toDouble()
        return when {
            double > Long.MAX_VALUE -> { compare(parseString(double.toString())) } // This whole block can be removed after 1.6.20 and https://github.com/JetBrains/kotlin/pull/4364
            double % 1 == 0.0 -> compare(fromLong(number.toLong()))
            else -> compareFloatAndBigInt(number.toFloat()) { compare(it) }
        }
    }

    fun compareFloatAndBigInt(float: Float, comparisonBlock: (CommonBigInteger) -> Int): Int {
        val withoutDecimalPart = floor(float)
        val hasDecimalPart = (float % 1 != 0f)
        return if (hasDecimalPart) {
            val comparisonResult = comparisonBlock.invoke(tryFromFloat(withoutDecimalPart + 1))
            if (comparisonResult == 0) {
                // They were equal with float incremented by one (because of decimal part) so the BigInt was larger
                1
            } else {
                comparisonResult
            }
        } else {
            comparisonBlock.invoke(tryFromFloat(withoutDecimalPart))
        }
    }

    fun compareDoubleAndBigInt(double: Double, comparisonBlock: (CommonBigInteger) -> Int): Int {
        val withoutDecimalPart = floor(double)
        val hasDecimalPart = (double % 1 != 0.0)
        return if (hasDecimalPart) {
            val comparisonResult = comparisonBlock.invoke(tryFromDouble(withoutDecimalPart + 1))
            if (comparisonResult == 0) {
                // They were equal with double incremented by one (because of decimal part) so the BigInt was larger
                1
            } else {
                comparisonResult
            }
        } else {
            comparisonBlock.invoke(tryFromDouble(withoutDecimalPart))
        }
    }

    override fun equals(other: Any?): Boolean {
        val comparison = when (other) {
            is CommonBigInteger -> compare(other)
            is Long -> compare(fromLong(other))
            is Int -> compare(fromInt(other))
            is Short -> compare(fromShort(other))
            is Byte -> compare(fromByte(other))
            is ULong -> compare(fromULong(other))
            is UInt -> compare(fromUInt(other))
            is UShort -> compare(fromUShort(other))
            is UByte -> compare(fromUByte(other))
            else -> -1
        }
        return comparison == 0
    }

    override fun hashCode(): Int {
        return magnitude.fold(0) { acc, uLong -> acc + uLong.hashCode() } + sign.hashCode()
    }

    override fun toString(): String {
        // TODO think about limiting the size of string, and offering a stream of characters instead of huge strings
//        if (stringRepresentation == null) {
//            stringRepresentation = toString(10)
//        }
//        return stringRepresentation!!

        // Linux build complains about mutating a frozen object, let's try without this representation caching
        return toString(10)
    }

    override fun toString(base: Int): String {
        val sign = if (sign == Sign.NEGATIVE) {
            "-"
        } else {
            ""
        }
        return sign + toStringWithoutSign(base)
    }

    internal fun toStringWithoutSign(base: Int): String {
        return arithmetic.toString(this.magnitude, base)
    }

    data class QuotientAndRemainder(val quotient: CommonBigInteger, val remainder: CommonBigInteger)

    data class SqareRootAndRemainder(val squareRoot: CommonBigInteger, val remainder: CommonBigInteger)

    // TODO eh
    operator fun times(char: Char): String {
        if (this.isNegative) {
            throw RuntimeException("Char cannot be multiplied with negative number")
        }
        var counter = this
        val stringBuilder = StringBuilder()
        while (counter > 0) {
            stringBuilder.append(char)
            counter--
        }
        return stringBuilder.toString()
    }

    fun toModularBigInteger(modulo: BigInteger): ModularBigInteger {
        val creator = ModularBigInteger.creatorForModulo(modulo)
        return creator.fromBigInteger(this as BigInteger)
    }

    override fun intValue(exactRequired: Boolean): Int {
        if (exactRequired && (this > Int.MAX_VALUE || this < Int.MIN_VALUE)) {
            throw ArithmeticException("Cannot convert to int and provide exact value")
        }
        return magnitude[0].toInt() * signum()
    }

    override fun longValue(exactRequired: Boolean): Long {
        if (exactRequired && (this > Long.MAX_VALUE || this < Long.MIN_VALUE)) {
            throw ArithmeticException("Cannot convert to long and provide exact value")
        }
        return if (magnitude.size > 1) {
            val firstBit = magnitude[1] shl 63
            (magnitude[0].toLong() or firstBit.toLong()) * signum()
        } else {
            return magnitude[0].toLong() * signum()
        }
    }

    override fun byteValue(exactRequired: Boolean): Byte {
        if (exactRequired && (this > Byte.MAX_VALUE || this < Byte.MIN_VALUE)) {
            throw ArithmeticException("Cannot convert to byte and provide exact value")
        }
        return (magnitude[0].toByte() * signum()).toByte()
    }

    override fun shortValue(exactRequired: Boolean): Short {
        if (exactRequired && (this > Short.MAX_VALUE || this < Short.MIN_VALUE)) {
            throw ArithmeticException("Cannot convert to short and provide exact value")
        }
        return (magnitude[0].toShort() * signum()).toShort()
    }

    override fun uintValue(exactRequired: Boolean): UInt {
        if (exactRequired && (this > UInt.MAX_VALUE || isNegative)) {
            throw ArithmeticException("Cannot convert to unsigned int and provide exact value")
        }
        return magnitude[0].toUInt()
    }

    override fun ulongValue(exactRequired: Boolean): ULong {
        if (exactRequired && (this > ULong.MAX_VALUE || isNegative)) {
            throw ArithmeticException("Cannot convert to unsigned long and provide exact value")
        }
        return if (magnitude.size > 1) {
            val firstBit = magnitude[1] shl 63
            magnitude[0] or firstBit
        } else {
            return magnitude[0]
        }
    }

    override fun ubyteValue(exactRequired: Boolean): UByte {
        if (exactRequired && (this > UByte.MAX_VALUE.toUInt() || isNegative)) {
            throw ArithmeticException("Cannot convert to unsigned byte and provide exact value")
        }
        return magnitude[0].toUByte()
    }

    override fun ushortValue(exactRequired: Boolean): UShort {
        if (exactRequired && this > UShort.MAX_VALUE.toUInt() || isNegative) {
            throw ArithmeticException("Cannot convert to unsigned short and provide exact value")
        }
        return magnitude[0].toUShort()
    }

    override fun floatValue(exactRequired: Boolean): Float {
        if (exactRequired && this.abs() > Float.MAX_VALUE) {
            throw ArithmeticException("Cannot convert to float and provide exact value")
        }
        return this.toString().toFloat()
    }

    override fun doubleValue(exactRequired: Boolean): Double {
        if (exactRequired && this.abs() > Double.MAX_VALUE) {
            println(this.abs())
            println(Double.MAX_VALUE)
            if (this.abs() > Double.MAX_VALUE) {
                println("huh")
            }
            throw ArithmeticException("Cannot convert to double and provide exact value")
        }
        return this.toString().toDouble()
    }

    override fun toUByteArray(): UByteArray {
        return arithmetic.toUByteArray(magnitude)
    }

    override fun toByteArray(): ByteArray {
        return arithmetic.toByteArray(magnitude)
    }

    operator fun rangeTo(other: CommonBigInteger) = BigIntegerRange(this, other)

    class BigIntegerRange(override val start: CommonBigInteger, override val endInclusive: CommonBigInteger) :
        ClosedRange<CommonBigInteger>, Iterable<CommonBigInteger> {

        override fun iterator(): Iterator<CommonBigInteger> {
            return BigIntegerIterator(start, endInclusive)
        }
    }

    class BigIntegerIterator(start: CommonBigInteger, private val endInclusive: CommonBigInteger) : Iterator<CommonBigInteger> {

        private var current = start

        override fun hasNext(): Boolean {
            return current <= endInclusive
        }

        override fun next(): CommonBigInteger {
            return current++
        }
    }
}
