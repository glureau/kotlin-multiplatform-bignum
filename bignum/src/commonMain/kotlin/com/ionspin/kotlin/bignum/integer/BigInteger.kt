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

import com.ionspin.kotlin.bignum.*


expect class BigInteger : BigNumber<BigInteger>,
    CommonBigNumberOperations<BigInteger>,
    NarrowingOperations<BigInteger>,
    BitwiseCapable<BigInteger>, Comparable<Any>,
    ByteArraySerializable {
    // Define the constructors and methods BigDecimal needs
    companion object : BigNumber.Creator<BigInteger>, BigNumber.Util<BigInteger>, ByteArrayDeserializable<BigInteger>

    constructor(long: Long)
    constructor(int: Int)
    constructor(short: Short)
    constructor(byte: Byte)
    // constructor(byteArray: ByteArray, sign: Sign)
    constructor(wordArray: WordArray, sign: Sign)

    override fun getCreator(): BigNumber.Creator<BigInteger>

    fun mod(modulo: BigInteger): BigInteger
    fun modPow(exponent: BigInteger, m: BigInteger): BigInteger
    fun modInverse(modulo: BigInteger): BigInteger
    fun gcd(modulo: BigInteger): BigInteger
}
