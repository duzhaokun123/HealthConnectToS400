package io.github.duzhaokun123.s400scale

import kotlin.math.pow
import kotlin.math.roundToInt

fun ByteArray.skip(n: Int): ByteArray {
    return this.sliceArray(n until this.size)
}

fun ByteArray.take(n: Int): ByteArray {
    return this.sliceArray(0 until n)
}

fun Double.round(digits: Int): Double {
    val factor = 10.0.pow(digits)
    return (this * factor).roundToInt() / factor
}