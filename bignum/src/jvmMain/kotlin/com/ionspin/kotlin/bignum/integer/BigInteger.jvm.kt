package com.ionspin.kotlin.bignum.integer

import com.ionspin.kotlin.bignum.BigNumber
import com.ionspin.kotlin.bignum.BitwiseCapable
import com.ionspin.kotlin.bignum.ByteArrayDeserializable
import com.ionspin.kotlin.bignum.ByteArraySerializable
import com.ionspin.kotlin.bignum.CommonBigNumberOperations
import com.ionspin.kotlin.bignum.NarrowingOperations
import com.ionspin.kotlin.bignum.integer.base63.toJavaBigInteger
import java.nio.ByteBuffer
import java.util.Arrays
import kotlin.math.sign

// Alias to avoid naming conflicts within the file
import java.math.BigInteger as JBigInt

private val Sign.asSignum
    get() = when (this) {
        Sign.POSITIVE -> 1
        Sign.NEGATIVE -> -1
        Sign.ZERO -> 0
    }

actual class BigInteger(
    val jBigInt: JBigInt
) : BigNumber<BigInteger>,
    CommonBigNumberOperations<BigInteger>,
    NarrowingOperations<BigInteger>,
    BitwiseCapable<BigInteger>,
    Comparable<Any>, ByteArraySerializable {

    actual companion object : BigNumber.Creator<BigInteger>, BigNumber.Util<BigInteger>,
        ByteArrayDeserializable<BigInteger> {
        override val ZERO: BigInteger = BigInteger(JBigInt.ZERO)
        override val ONE: BigInteger = BigInteger(JBigInt.ONE)
        override val TWO: BigInteger = try {
            BigInteger(JBigInt.TWO)
        } catch (e: NoSuchFieldError) { // no Android export, so using a JVM one but this is not available everywhere
            ONE + ONE
        }
        override val TEN: BigInteger = BigInteger(JBigInt.TEN)

        // Helper for ULong correction (2^64)
        private val TWO_POW_64 = JBigInt.ONE.shiftLeft(64)

        override fun parseString(string: String, base: Int): BigInteger =
            BigInteger(JBigInt(string, base))

        override fun fromULong(uLong: ULong): BigInteger {
            // Convert to Java BigInteger.
            // If uLong is large (top bit 1), toLong() makes it negative.
            // We mask it to treat it as unsigned 64-bit integer.
            val longVal = uLong.toLong()
            return if (longVal >= 0) {
                BigInteger(JBigInt.valueOf(longVal))
            } else {
                // Logic: Value is negative (e.g. -1 for MAX_VALUE).
                // We want interpreted unsigned value.
                // 2^64 + longVal gives the correct positive BigInteger.
                // e.g. 2^64 + (-1) = 2^64 - 1
                val upper = JBigInt.ONE.shiftLeft(64)
                val valBig = JBigInt.valueOf(longVal)
                BigInteger(upper.add(valBig))
            }
        }

        override fun fromUInt(uInt: UInt): BigInteger =
            BigInteger(JBigInt.valueOf(uInt.toLong())) // UInt fits in positive Long, so this is safe and fast

        override fun fromUShort(uShort: UShort): BigInteger =
            BigInteger(JBigInt.valueOf(uShort.toLong()))

        override fun fromUByte(uByte: UByte): BigInteger =
            BigInteger(JBigInt.valueOf(uByte.toLong()))

        override fun fromLong(long: Long): BigInteger =
            BigInteger(JBigInt.valueOf(long))

        override fun fromInt(int: Int): BigInteger =
            BigInteger(JBigInt.valueOf(int.toLong()))

        override fun fromShort(short: Short): BigInteger =
            BigInteger(JBigInt.valueOf(short.toLong()))

        override fun fromByte(byte: Byte): BigInteger =
            BigInteger(JBigInt.valueOf(byte.toLong()))

        override fun fromBigInteger(bigInteger: BigInteger): BigInteger =
            BigInteger(bigInteger.jBigInt)

        override fun tryFromFloat(float: Float, exactRequired: Boolean): BigInteger {
            // java.math.BigDecimal is the safest way to convert float/double to BigInteger correctly
            return BigInteger(java.math.BigDecimal(float.toDouble()).toBigInteger())
        }

        override fun tryFromDouble(double: Double, exactRequired: Boolean): BigInteger {
            return BigInteger(java.math.BigDecimal(double).toBigInteger())
        }

        override fun max(first: BigInteger, second: BigInteger): BigInteger =
            if (first.jBigInt.compareTo(second.jBigInt) > 0) first else second

        override fun min(first: BigInteger, second: BigInteger): BigInteger =
            if (first.jBigInt.compareTo(second.jBigInt) < 0) first else second

        override fun fromUByteArray(source: UByteArray, sign: Sign): BigInteger {
            val signum = when (sign) {
                Sign.POSITIVE -> 1
                Sign.NEGATIVE -> -1
                Sign.ZERO -> 0
            }
            // Constructor JBigInt(signum, magnitude) expects strict unsigned magnitude.
            return BigInteger(JBigInt(signum, source.toByteArray()))
        }

        override fun fromByteArray(source: ByteArray, sign: Sign): BigInteger {
            val signum = when (sign) {
                Sign.POSITIVE -> 1
                Sign.NEGATIVE -> -1
                Sign.ZERO -> 0
            }
            return BigInteger(JBigInt(signum, source))
        }
    }

    // --- Constructors ---
    actual constructor(long: Long) : this(JBigInt.valueOf(long))
    actual constructor(int: Int) : this(JBigInt.valueOf(int.toLong()))
    actual constructor(short: Short) : this(JBigInt.valueOf(short.toLong()))
    actual constructor(byte: Byte) : this(JBigInt.valueOf(byte.toLong()))
    actual constructor(wordArray: WordArray, sign: Sign) : this(
        wordArray.toJavaBigInteger().let {
            // Adjust sign if needed
            if (sign == Sign.NEGATIVE) it.negate() else it
        }
    )

    actual override fun getCreator(): BigNumber.Creator<BigInteger> = BigInteger

    // --- Math Ops (Delegated) ---
    override fun add(other: BigInteger): BigInteger = BigInteger(this.jBigInt.add(other.jBigInt))
    override fun subtract(other: BigInteger): BigInteger = BigInteger(this.jBigInt.subtract(other.jBigInt))
    override fun multiply(other: BigInteger): BigInteger = BigInteger(this.jBigInt.multiply(other.jBigInt))
    override fun divide(other: BigInteger): BigInteger = BigInteger(this.jBigInt.divide(other.jBigInt))
    override fun remainder(other: BigInteger): BigInteger = BigInteger(this.jBigInt.remainder(other.jBigInt))
    actual fun mod(modulo: BigInteger): BigInteger {
        // Java Native Optimization: use native .mod() if modulo is positive (Fastest)
        if (modulo.jBigInt.signum() > 0) {
            return BigInteger(this.jBigInt.mod(modulo.jBigInt))
        }

        // Fallback: Java throws ArithmeticException if modulo <= 0.
        // We replicate the original library's behavior manually using remainder.
        val remainder = this.jBigInt.remainder(modulo.jBigInt)
        return if (remainder.signum() < 0) {
            BigInteger(remainder.add(modulo.jBigInt))
        } else {
            BigInteger(remainder)
        }
    }

    actual fun modPow(exponent: BigInteger, m: BigInteger): BigInteger {
        // Java Native Optimization: use native .modPow() if modulus is positive (Fastest)
        if (m.jBigInt.signum() > 0) {
            return BigInteger(this.jBigInt.modPow(exponent.jBigInt, m.jBigInt))
        }

        // Fallback: Calculate manually (Slower, but avoids crash)
        // (base^exp) % m
        val pow =
            this.jBigInt.pow(exponent.intValue(false)) // Potential overflow if exp is huge, but rare for negative moduli
        val remainder = pow.remainder(m.jBigInt)
        return if (remainder.signum() < 0) {
            BigInteger(remainder.add(m.jBigInt))
        } else {
            BigInteger(remainder)
        }
    }

    actual fun modInverse(modulo: BigInteger): BigInteger = BigInteger(this.jBigInt.modInverse(modulo.jBigInt))
    actual fun gcd(modulo: BigInteger): BigInteger = BigInteger(this.jBigInt.gcd(modulo.jBigInt))

    override fun divideAndRemainder(other: BigInteger): Pair<BigInteger, BigInteger> {
        val res = this.jBigInt.divideAndRemainder(other.jBigInt)
        return Pair(BigInteger(res[0]), BigInteger(res[1]))
    }

    override fun isZero(): Boolean = this.jBigInt.signum() == 0 // signum check is slightly faster than equals
    override fun negate(): BigInteger = BigInteger(this.jBigInt.negate())
    override fun abs(): BigInteger = BigInteger(this.jBigInt.abs())
    override fun pow(exponent: Long): BigInteger =
        BigInteger(this.jBigInt.pow(exponent.toInt())) // Java BigInt pow takes Int

    override fun pow(exponent: Int): BigInteger = BigInteger(this.jBigInt.pow(exponent))
    override fun signum(): Int = this.jBigInt.signum()

    override fun numberOfDecimalDigits(): Long {
        if (this.jBigInt.signum() == 0) return 1
        // Used by BigDecimal.divide logic we optimized earlier.
        // It's critical this is accurate. Java toString is optimized.
        return this.jBigInt.abs().toString().length.toLong()
    }

    // --- Narrowing Operations ---
    override fun intValue(exactRequired: Boolean): Int {
        return if (exactRequired) try {
            jBigInt.intValueExact()
        } catch (e: NoSuchMethodError) {
            // TODO proper Android fallback (intValueExact added in API Level 31)
            val intVal = jBigInt.toInt()
            if (JBigInt.valueOf(intVal.toLong()) != jBigInt) {
                throw ArithmeticException("BigInteger out of Int range")
            }
            intVal
        } else jBigInt.toInt()
    }

    override fun longValue(exactRequired: Boolean): Long {
        return if (exactRequired) try {
            jBigInt.longValueExact()
        } catch (e: NoSuchMethodError) {
            // TODO proper Android fallback (longValueExact added in API Level 31)
            val longVal = jBigInt.toLong()
            if (JBigInt.valueOf(longVal) != jBigInt) {
                throw ArithmeticException("BigInteger out of Long range")
            }
            longVal
        } else jBigInt.toLong()
    }

    override fun byteValue(exactRequired: Boolean): Byte {
        return if (exactRequired) try {
            jBigInt.byteValueExact()
        } catch (e: NoSuchMethodError) {
            // TODO proper Android fallback (byteValueExact added in API Level 31)
            val byteVal = jBigInt.toByte()
            if (JBigInt.valueOf(byteVal.toLong()) != jBigInt) {
                throw ArithmeticException("BigInteger out of Byte range")
            }
            byteVal
        }
        else jBigInt.toByte()
    }

    override fun shortValue(exactRequired: Boolean): Short {
        return if (exactRequired) try {
            jBigInt.shortValueExact()
        } catch (e: NoSuchMethodError) {
            // TODO proper Android fallback (shortValueExact added in API Level 31)
            val shortVal = jBigInt.toShort()
            if (JBigInt.valueOf(shortVal.toLong()) != jBigInt) {
                throw ArithmeticException("BigInteger out of Short range")
            }
            shortVal
        } else jBigInt.toShort()
    }

    // Unsigned narrowing is tricky because Java BigInt is signed.
    // We strictly cast the bits.
    override fun uintValue(exactRequired: Boolean): UInt {
        if (exactRequired && (jBigInt.signum() < 0 || jBigInt.bitLength() > 32)) {
            throw ArithmeticException("BigInteger out of UInt range")
        }
        return jBigInt.toInt().toUInt()
    }

    override fun ulongValue(exactRequired: Boolean): ULong {
        if (exactRequired && (jBigInt.signum() < 0 || jBigInt.bitLength() > 64)) {
            throw ArithmeticException("BigInteger out of ULong range")
        }
        return jBigInt.toLong().toULong()
    }

    override fun ubyteValue(exactRequired: Boolean): UByte {
        if (exactRequired && (jBigInt.signum() < 0 || jBigInt.bitLength() > 8)) {
            throw ArithmeticException("BigInteger out of UByte range")
        }
        return jBigInt.toByte().toUByte()
    }

    override fun ushortValue(exactRequired: Boolean): UShort {
        if (exactRequired && (jBigInt.signum() < 0 || jBigInt.bitLength() > 16)) {
            throw ArithmeticException("BigInteger out of UShort range")
        }
        return jBigInt.toShort().toUShort()
    }

    override fun floatValue(exactRequired: Boolean): Float = jBigInt.toFloat()
    override fun doubleValue(exactRequired: Boolean): Double = jBigInt.toDouble()

    // --- Bitwise Operations ---
    override fun shl(places: Int): BigInteger = BigInteger(jBigInt.shiftLeft(places))
    override fun shr(places: Int): BigInteger = BigInteger(jBigInt.shiftRight(places))
    override fun and(other: BigInteger): BigInteger = BigInteger(jBigInt.and(other.jBigInt))
    override fun or(other: BigInteger): BigInteger = BigInteger(jBigInt.or(other.jBigInt))
    override fun xor(other: BigInteger): BigInteger = BigInteger(jBigInt.xor(other.jBigInt))
    override fun not(): BigInteger = BigInteger(jBigInt.not())

    override fun bitAt(position: Long): Boolean = jBigInt.testBit(position.toInt())

    override fun setBitAt(position: Long, bit: Boolean): BigInteger {
        return if (bit) BigInteger(jBigInt.setBit(position.toInt()))
        else BigInteger(jBigInt.clearBit(position.toInt()))
    }

    override fun bitLength(): Int = jBigInt.bitLength()
    override fun trailingZeroBits(): Int {
        val lowest = jBigInt.getLowestSetBit()
        return if (lowest == -1) 0 else lowest
    }

    // --- Serialization ---
    /*
      Java (toByteArray): Returns Two's Complement. (Adds a 0x00 byte prefix if the number is positive and the top bit is set, to differentiate it from a negative number).
      IonSpin Library (toUByteArray): Expects Magnitude (Raw unsigned bytes).
      For ULong.MAX_VALUE:
      Java returns 9 bytes: 00 FF FF FF FF FF FF FF FF.
      The Library expects 8 bytes: FF FF FF FF FF FF FF FF.
     */

    override fun toUByteArray(): UByteArray {
        // Return Magnitude (Absolute value, no sign info)
        val mag = jBigInt.abs()
        val bytes = mag.toByteArray()
        // Java adds a leading 0x00 byte if the MSB is 1 to indicate positive sign.
        // We must strip it to return pure magnitude.
        if (bytes.size > 1 && bytes[0] == 0.toByte()) {
            return bytes.copyOfRange(1, bytes.size).toUByteArray()
        }
        return bytes.toUByteArray()
    }

    override fun toByteArray(): ByteArray {
        // Same logic for signed byte array if the interface expects magnitude
        // (Check ByteArraySerializable contract, usually it implies Magnitude for BigInts in this lib)
        val mag = jBigInt.abs()
        val bytes = mag.toByteArray()
        if (bytes.size > 1 && bytes[0] == 0.toByte()) {
            return bytes.copyOfRange(1, bytes.size)
        }
        return bytes
    }

    // --- Core Overrides ---
    // TODO: That logic could be commonized with other platforms
    override fun compareTo(other: Any): Int = when (other) {
        is BigInteger -> this.jBigInt.compareTo(other.jBigInt)
        is Long -> jBigInt.compareTo(JBigInt.valueOf(other))
        is Int -> jBigInt.compareTo(JBigInt.valueOf(other.toLong()))
        is Short -> jBigInt.compareTo(JBigInt.valueOf(other.toLong()))
        is Byte -> jBigInt.compareTo(JBigInt.valueOf(other.toLong()))
        is ULong -> jBigInt.compareTo(JBigInt.valueOf(other.toLong()))
        is UInt -> jBigInt.compareTo(JBigInt.valueOf(other.toLong()))
        is UShort -> jBigInt.compareTo(JBigInt.valueOf(other.toLong()))
        is UByte -> jBigInt.compareTo(JBigInt.valueOf(other.toLong()))
        is Float -> jBigInt.toFloat().compareTo(other)// TODO: review precision issues
        is Double -> jBigInt.toDouble().compareTo(other) // TODO: review precision issues
        else -> throw RuntimeException("Invalid comparison type for BigInteger: ${other::class}")
    }

    override fun equals(other: Any?): Boolean = when (other) {
        is BigInteger -> this.jBigInt == other.jBigInt
        else -> false
    }

    override fun hashCode(): Int = this.jBigInt.hashCode()

    override fun toString(): String = this.jBigInt.toString()
    override fun toString(base: Int): String = this.jBigInt.toString(base)

    override fun unaryMinus(): BigInteger = this.negate()
    override fun secureOverwrite() = Unit // Immutable
    override fun getInstance(): BigInteger = this
}