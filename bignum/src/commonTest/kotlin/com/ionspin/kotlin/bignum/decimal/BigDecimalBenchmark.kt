package com.ionspin.kotlin.bignum.decimal

import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.measureTime

@Ignore // Too long to run all the time
class BigDecimalBenchmark {

    private fun Random.randomStringDecimal(allowZero: Boolean = true): String =
        randomStringInteger(allowZero) + "." + randomStringInteger(allowZero)

    private fun Random.randomStringInteger(allowZero: Boolean = true): String {
        // Spread possibilities based of number of digits, which increases likelihood of smaller numbers
        // There will be roughly as many tests with 1 digit as with 20 digits
        val nbDigit = nextInt(0, 10)
        do {
            val value = nextInt(10f.pow(nbDigit).toInt() - 1, 10f.pow(nbDigit + 1).toInt())
            if (allowZero || value != 0)
                return value.toString()
        } while (true)
    }

    private fun Random.randomDecimalMode(
        allowInfinitePrecision: Boolean = true
    ): DecimalMode? {
        if (allowInfinitePrecision && nextBoolean()) return null
        var roundingMode = RoundingMode.entries.random(this)
        if (allowInfinitePrecision && roundingMode == RoundingMode.NONE)
            return DecimalMode(
                decimalPrecision = 0,
                roundingMode = roundingMode,
                scale = -1,
            )
        while (!allowInfinitePrecision && roundingMode == RoundingMode.NONE) {
            roundingMode = RoundingMode.entries.random(this)
        }
        val decimalPrecision = nextLong(1, 30)
        return DecimalMode(
            decimalPrecision = decimalPrecision,
            roundingMode = roundingMode,
            scale = if (nextBoolean()) -1 else nextLong(0, decimalPrecision),
        )
    }

    private fun Random.randomRoundingMode(allowNone: Boolean = true): RoundingMode {
        do {
            val mode = RoundingMode.entries.random(this)
            if (allowNone || mode != RoundingMode.NONE)
                return mode
        } while (true)
    }

    private fun Random.randomBigDecimal(allowZero: Boolean = true): BigDecimal =
        BigDecimal.parseStringWithMode(randomStringDecimal(allowZero), randomDecimalMode())

    private val benchmarkList = mutableListOf<BenchmarkRun>()

    private data class BenchmarkRun(val key: String, val runNumber: Int, val duration: Duration)

    private val times = 10_000_000
    private val runs = 5
    private fun <T> benchmark(
        key: String,
        // Prepare random (or not) values for the benchmark, this is not measured in the benchmark
        prepare: Random.() -> T,
        measure: (t: T) -> Unit
    ) {
        println("Preparing for $key (warmup)")
        repeat(100_000) { // Warmup (JVM is strongly impacted by JIT, result stability requires a short warmup)
            measure(prepare(Random))
        }
        println("Benchmarking for $key")
        // More than 1M of the different values requires too much RAM, 100k values looks enough
        val maxDataSize = min(100_000, times)
        repeat(runs) { run ->
            val random = Random(0)
            val data = (0..maxDataSize).map { random.prepare() }
            val duration = measureTime {
                repeat(times) { index ->
                    measure(data[index % maxDataSize])
                }
            }
            benchmarkList.add(BenchmarkRun(key, run, duration))
            println("Benchmark $run DONE for $key in $duration")
        }
    }

    @Test
    fun perfParseStringWithMode() {
        benchmark(
            key = "parseStringWithMode",
            prepare = { randomStringDecimal() to randomDecimalMode() },
            measure = { BigDecimal.parseStringWithMode(it.first, it.second) }
        )
        benchmark(
            key = "add",
            prepare = { randomBigDecimal() to randomBigDecimal() },
            measure = { (a, b) -> a + b }
        )
        benchmark(
            key = "subtract",
            prepare = { randomBigDecimal() to randomBigDecimal() },
            measure = { (a, b) -> a - b }
        )
        benchmark(
            key = "multiply",
            prepare = { randomBigDecimal() to randomBigDecimal() },
            measure = { (a, b) -> a * b }
        )
        benchmark(
            key = "divide",
            prepare = {
                Triple(
                    randomBigDecimal(),
                    randomBigDecimal(allowZero = false),
                    randomDecimalMode(allowInfinitePrecision = false)
                )
            },
            measure = { (a, b, mode) -> a.divide(b, mode) }
        )
        benchmark(
            key = "hashCode",
            prepare = { randomBigDecimal() },
            measure = { it.hashCode() }
        )
        benchmark(
            key = "toStringExpanded",
            prepare = { randomBigDecimal() },
            measure = { it.toStringExpanded() }
        )
        benchmark(
            key = "toPlainString",
            prepare = { randomBigDecimal() },
            measure = { it.toPlainString() }
        )
        benchmark(
            key = "roundToDigitPosition",
            prepare = { randomBigDecimal() to randomRoundingMode(allowNone = false) },
            measure = { (a, b) -> a.roundToDigitPosition(2, b) }
        )
        benchmark(
            key = "pow",
            prepare = { randomBigDecimal() to nextInt(0, 5) },
            measure = { (a, b) -> a.pow(b) }
        )
        printBenchmarkReport()
    }

    private fun printBenchmarkReport() {
        println("Benchmark report:")
        println()

        println("| Method | " + (1..runs).joinToString("|") { " #$it " } + " | Average |")
        println("|--------|-" + (1..runs).joinToString("|") { "------" } + " |---------|")
        benchmarkList.groupBy { it.key }
            .toList()
            .sortedBy { it.first }
            .forEach { (key, runs) ->
                println("| $key | " + runs.joinToString("|") { " ${it.duration} " } +
                        " | ${runs.map { it.duration }.avg()} |")
            }
    }

    private fun List<Duration>.avg(): Duration {
        val sum = this.fold(Duration.ZERO) { acc, duration -> acc + duration }
        return sum.div(this.size)
    }
}