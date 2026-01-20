package io.github.duzhaokun123.s400scale

import java.util.Date

data class BodyComposition(
    val weight: Double,
    val impedance: Double?,
    val heartRate: Double? = null,
    val date: Date
)