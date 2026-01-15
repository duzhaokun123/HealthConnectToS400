package io.github.duzhaokun123.s400scale

import java.util.Date

data class BodyComposition(
    val weight: Double,
    val impedance: Double?,
    val heartRate: Double? = null,
    val date: Date,
    val bmi: Double? = null,
    val proteinPercentage: Double? = null,
    val idealWeight: Double? = null,
    val bmr: Double? = null,
    val boneMass: Double? = null,
    val fat: Double? = null,
    val metabolicAge: Double? = null,
    val muscleMass: Double? = null,
    val visceralFat: Double? = null,
    val water: Double? = null,
    val bodyType: Int? = null,
    val bodyTypeName: String? = null
)