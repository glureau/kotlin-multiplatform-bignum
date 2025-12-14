package com.ionspin.kotlin.bignum.integer

import kotlin.concurrent.Volatile

@Volatile
private var powersOfTenCache = listOf(BigInteger.ONE, BigInteger.TEN)
fun tenPow(exponent: Long): BigInteger {
    if (exponent < 0) throw ArithmeticException("Negative power of 10")
    if (exponent > Int.MAX_VALUE) return BigInteger.TEN.pow(exponent) // Fallback for insane sizes

    val expInt = exponent.toInt()

    // Fast path: return from cache
    if (expInt < powersOfTenCache.size) {
        return powersOfTenCache[expInt]
    }

    // Slow path: Fill cache up to needed exponent
    val newCache = powersOfTenCache.toMutableList()
    var current = newCache.last()
    for (i in newCache.size..expInt) {
        current *= BigInteger.TEN
        newCache.add(current)
    }
    // If another thread already updated that cache, don't overwrite with smaller one
    if (newCache.size > powersOfTenCache.size) {
        powersOfTenCache = newCache
    }
    return newCache[expInt]
}
