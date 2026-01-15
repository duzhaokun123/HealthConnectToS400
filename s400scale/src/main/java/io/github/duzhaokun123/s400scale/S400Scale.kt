@file:Suppress("ArrayInDataClass")

package io.github.duzhaokun123.s400scale

import org.spongycastle.crypto.engines.AESEngine
import org.spongycastle.crypto.modes.CCMBlockCipher
import org.spongycastle.crypto.params.AEADParameters
import org.spongycastle.crypto.params.KeyParameter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Date
import kotlin.math.roundToInt

data class MuscleMassScale(
    val min: Map<UserInfo.Sex, Double>,
    val female: DoubleArray,
    val male: DoubleArray
)

data class FatPercentageScale(
    val min: Int,
    val max: Int,
    val female: DoubleArray,
    val male: DoubleArray
)

object S400Scale {
    const val BUFFER_SIZE = 24
    const val WEIGHT = "weight"
    const val IMPEDANCE = "impedance"
    const val IMPEDANCE_LOW = "impedanceLow"
    const val HEART_RATE = "heartRate"

    private var height: Double = 0.0
    private var age: Int = 0
    private lateinit var sex: UserInfo.Sex
    private lateinit var data: ByteArray
    private lateinit var mac: ByteArray
    private lateinit var aesKey: ByteArray
    private var weight: Double = 0.0
    private var impedance: Double = 0.0
    val sensors = mutableMapOf<String, Double>()

    private val muscleMassScales = arrayOf(
        MuscleMassScale(
            min = mapOf(UserInfo.Sex.Male to 170.0, UserInfo.Sex.Female to 160.0),
            female = doubleArrayOf(36.5, 42.6),
            male = doubleArrayOf(49.4, 59.5)
        ),
        MuscleMassScale(
            min = mapOf(UserInfo.Sex.Male to 160.0, UserInfo.Sex.Female to 150.0),
            female = doubleArrayOf(32.9, 37.6),
            male = doubleArrayOf(44.0, 52.5)
        ),
        MuscleMassScale(
            min = mapOf(UserInfo.Sex.Male to 0.0, UserInfo.Sex.Female to 0.0),
            female = doubleArrayOf(29.1, 34.8),
            male = doubleArrayOf(38.5, 46.6)
        )
    )

    private val fatPercentageScales = arrayOf(
        FatPercentageScale(0, 12, doubleArrayOf(12.0, 21.0, 30.0, 34.0), doubleArrayOf(7.0, 16.0, 25.0, 30.0)),
        FatPercentageScale(12, 14, doubleArrayOf(15.0, 24.0, 33.0, 37.0), doubleArrayOf(7.0, 16.0, 25.0, 30.0)),
        FatPercentageScale(14, 16, doubleArrayOf(18.0, 27.0, 36.0, 40.0), doubleArrayOf(7.0, 16.0, 25.0, 30.0)),
        FatPercentageScale(16, 18, doubleArrayOf(20.0, 28.0, 37.0, 41.0), doubleArrayOf(7.0, 16.0, 25.0, 30.0)),
        FatPercentageScale(18, 40, doubleArrayOf(21.0, 28.0, 35.0, 40.0), doubleArrayOf(11.0, 17.0, 22.0, 27.0)),
        FatPercentageScale(40, 60, doubleArrayOf(22.0, 29.0, 36.0, 41.0), doubleArrayOf(12.0, 18.0, 23.0, 28.0)),
        FatPercentageScale(60, 100, doubleArrayOf(23.0, 30.0, 37.0, 42.0), doubleArrayOf(14.0, 20.0, 25.0, 30.0))
    )

    private val bodyTypeScale = arrayOf(
        "obese", "overweight", "thick-set", "lack-exerscise", "balanced", "balanced-muscular", "skinny",
        "balanced-skinny", "skinny-muscular"
    )

    fun getBodyComposition(userInfo: UserInfo, inputData: S400InputData): BodyComposition? {
        height = userInfo.height.toDouble()
        age = userInfo.age
        sex = userInfo.sex
        data = inputData.data ?: inputData.dataString.hexToByteArray()

        if (checkInput(userInfo) != true) {
            return null
        }

        mac = inputData.macBytes ?: inputData.macOriginal.replace(":", "").hexToByteArray()
        aesKey = inputData.aesKeyBytes ?: inputData.aesKey.hexToByteArray()

        parse(mac, aesKey)

        if (sensors.contains(WEIGHT) && sensors[WEIGHT] != 0.0) {
            val bc = getBodyCompositionInternal()
            return bc
        }

        return null
    }

    private fun getBodyCompositionInternal(): BodyComposition {
        val impedance = getSensorValue(IMPEDANCE)
        if (impedance == null || impedance == 0.0) {
            return BodyComposition(
                weight = getSensorValue(WEIGHT) ?: 0.0,
                date = Date(),
                impedance = null,
                heartRate = null
            )
        }

        val bodyType = getBodyType()
        val bodyComposition = BodyComposition(
            weight = getSensorValue(WEIGHT) ?: 0.0,
            bmi = getBmi().round(1),
            proteinPercentage = getProteinPercentage().round(1),
            idealWeight = getIdealWeight().round(2),
            bmr = getBmr().round(0),
            boneMass = getBoneMass().round(2),
            fat = getFatPercentage().round(1),
            metabolicAge = getMetabolicAge().round(0),
            muscleMass = getMuscleMass().round(2),
            visceralFat = getVisceralFat().round(2),
            water = getWater().round(1),
            bodyType = bodyType + 1,
            bodyTypeName = bodyTypeScale[bodyType],
            date = Date(),
            impedance = getSensorValue(IMPEDANCE),
            heartRate = getSensorValue(HEART_RATE)
        )

        return bodyComposition
    }

    private fun checkInput(userInfo: UserInfo): Boolean {
        if (data.size == (BUFFER_SIZE + 2)) {
            val fixedData = ByteArray(data.size - 2)
            System.arraycopy(data, 2, fixedData, 0, fixedData.size)
            data = fixedData
        }
        if (data.size != BUFFER_SIZE) {
            return false
        }
        return true
    }

    private fun parse(mac: ByteArray, binkey: ByteArray) {
        val xiaomiMac = mac
        val associatedData = byteArrayOf(0x11)
        val noice = xiaomiMac.reversedArray() + data.skip(2).take(3) + data.skip(data.size - 7).take(3)
        val mic = data.skip(data.size - 4).take(4)
        val i = 5
        val encryptedPayload = data.skip(i).take(data.size - i - 7)

        // AES-CCM Decryption
        val ccm = CCMBlockCipher(AESEngine())
        val parameters = AEADParameters(KeyParameter(binkey), 32, noice, associatedData)
        ccm.init(false, parameters)

        val cipherText = encryptedPayload + mic
        val decryptedPayload = ByteArray(ccm.getOutputSize(cipherText.size))
        val len = ccm.processBytes(cipherText, 0, cipherText.size, decryptedPayload, 0)
        ccm.doFinal(decryptedPayload, len)

        val obj = ByteArray(9) // 12 - 3 = 9 bytes
        System.arraycopy(decryptedPayload, 3, obj, 0, 9)

        // Extract bytes 1 to 4 from obj (4 bytes)
        val slice = ByteArray(4)
        System.arraycopy(obj, 1, slice, 0, 4)

        // Convert the 4 bytes to an integer (little-endian)
        val value = ByteBuffer.wrap(slice).order(ByteOrder.LITTLE_ENDIAN).int
        parseValue(value)
    }

    private fun parseValue(value: Int) {
        val dict = mutableMapOf<String, String>()
        val mass = value and 0x7FF
        val heartRate = (value shr 11) and 0x7F
        val impedance = (value shr 18)

        if (mass != 0) {
            val weight = (value and 0x7FF) / 10.0
            this.weight = weight
            updateSensor(WEIGHT, weight)
        }

        if (heartRate in 1 until 127) {
            updateSensor(HEART_RATE, heartRate + 50.0)
        }

        if (impedance != 0) {
            if (mass != 0) {
                this.impedance = impedance / 10.0
                updateSensor(IMPEDANCE, impedance / 10.0)
            } else {
                updateSensor(IMPEDANCE_LOW, impedance / 10.0)
            }
        }
    }

    private fun updateSensor(key: String, value: Double) {
        sensors[key] = value
    }

    private fun getSensorValue(key: String): Double? {
        return sensors[key]
    }

    private fun checkValueOverflow(value: Double, min: Double, max: Double): Double {
        return when {
            value < min -> min
            value > max -> max
            else -> value
        }
    }

    private fun getWater(): Double {
        val coefficient: Double
        val waterPercentage = (100 - getFatPercentage()) * 0.7

        coefficient = if (waterPercentage <= 50) 1.02 else 0.98

        var finalWater = waterPercentage * coefficient

        if (finalWater >= 65) {
            finalWater = 75.0
        }

        return checkValueOverflow(finalWater, 35.0, 75.0)
    }

    private fun getBodyType(): Int {
        val factor = when {
            getFatPercentage() > getFatPercentageScale()[2] -> 0
            getFatPercentage() < getFatPercentageScale()[1] -> 2
            else -> 1
        }

        val muscle = getMuscleMass()
        return when {
            muscle > getMuscleMassScale()[1] -> 2 + (factor * 3)
            muscle < getMuscleMassScale()[0] -> factor * 3
            else -> 1 + (factor * 3)
        }
    }

    private fun getIdealWeight(): Double {
        return when (sex) {
            UserInfo.Sex.Male -> (height - 80) * 0.7
            UserInfo.Sex.Female -> (height - 70) * 0.6
        }
    }

    private fun getMetabolicAge(): Double {
        val metabolicAge = when (sex) {
            UserInfo.Sex.Male -> (height * -0.7471) + (weight * 0.9161) + (age * 0.4184) + (impedance * 0.0517) + 54.2267
            UserInfo.Sex.Female -> (height * -1.1165) + (weight * 1.5784) + (age * 0.4615) + (impedance * 0.0415) + 83.2548
        }

        return checkValueOverflow(metabolicAge, 15.0, 80.0)
    }

    private fun getVisceralFat(): Double {
        val vfal = when (sex) {
            UserInfo.Sex.Female -> {
                if (weight > (13 - (height * 0.5)) * -1) {
                    val subsubcalc = ((height * 1.45) + (height * 0.1158) * height) - 120
                    val subcalc = weight * 500 / subsubcalc
                    (subcalc - 6) + (age * 0.07)
                } else {
                    val subcalc = 0.691 + (height * -0.0024) + (height * -0.0024)
                    (((height * 0.027) - (subcalc * weight)) * -1) + (age * 0.07) - age
                }
            }
            UserInfo.Sex.Male -> {
                if (height < weight * 1.6) {
                    val subcalc = ((height * 0.4) - (height * (height * 0.0826))) * -1
                    ((weight * 305) / (subcalc + 48)) - 2.9 + (age * 0.15)
                } else {
                    val subcalc = 0.765 + height * -0.0015
                    (((height * 0.143) - (weight * subcalc)) * -1) + (age * 0.15) - 5.0
                }
            }
        }

        return checkValueOverflow(vfal, 1.0, 50.0)
    }

    private fun getProteinPercentage(): Double {
        val proteinPercentage = (getMuscleMass() / weight) * 100 - getWaterPercentage()
        return checkValueOverflow(proteinPercentage, 5.0, 32.0)
    }

    private fun getWaterPercentage(): Double {
        val waterPercentage = (100 - getFatPercentage()) * 0.7
        val coefficient = if (waterPercentage <= 50) 1.02 else 0.98
        var finalWater = waterPercentage * coefficient
        if (finalWater >= 65) finalWater = 75.0
        return checkValueOverflow(finalWater, 35.0, 75.0)
    }

    private fun getBmi(): Double {
        val heightM = height / 100.0
        return checkValueOverflow(weight / (heightM * heightM), 10.0, 90.0)
    }

    private fun getBmr(): Double {
        val bmr = when (sex) {
            UserInfo.Sex.Male -> {
                var b = 877.8 + weight * 14.916 - height * 0.726 - age * 8.976
                if (b > 2322) b = 5000.0
                b
            }
            UserInfo.Sex.Female -> {
                var b = 864.6 + weight * 10.2036 - height * 0.39336 - age * 6.204
                if (b > 2996) b = 5000.0
                b
            }
        }
        return checkValueOverflow(bmr, 500.0, 10000.0)
    }

    private fun getFatPercentage(): Double {
        val value = when {
            sex == UserInfo.Sex.Female && age <= 49 -> 9.25
            sex == UserInfo.Sex.Female && age > 49 -> 7.25
            else -> 0.8
        }

        val lbm = getLbmCoefficient()
        var coefficient = 1.0

        when {
            sex == UserInfo.Sex.Male && weight < 61 -> coefficient = 0.98
            sex == UserInfo.Sex.Female && weight > 60 -> {
                coefficient = if (height > 160) 1.03 else 0.96
            }
            sex == UserInfo.Sex.Female && weight < 50 -> {
                coefficient = if (height > 160) 1.03 else 1.02
            }
        }

        var fatPercentage = (1.0 - (((lbm - value) * coefficient) / weight)) * 100

        if (fatPercentage > 63) fatPercentage = 75.0

        return checkValueOverflow(fatPercentage, 5.0, 75.0)
    }

    private fun getMuscleMass(): Double {
        var muscleMass = weight - ((getFatPercentage() * 0.01) * weight) - getBoneMass()
        when (sex) {
            UserInfo.Sex.Female -> if (muscleMass >= 84) muscleMass = 120.0
            UserInfo.Sex.Male -> if (muscleMass >= 93.5) muscleMass = 120.0
        }
        return checkValueOverflow(muscleMass, 10.0, 120.0)
    }

    private fun getBoneMass(): Double {
        val base = if (sex == UserInfo.Sex.Female) 0.245691014 else 0.18016894
        var boneMass = (base - (getLbmCoefficient() * 0.05158)) * -1
        boneMass += if (boneMass > 2.2) 0.1 else -0.1
        when (sex) {
            UserInfo.Sex.Female -> if (boneMass > 5.1) boneMass = 8.0
            UserInfo.Sex.Male -> if (boneMass > 5.2) boneMass = 8.0
        }
        return checkValueOverflow(boneMass, 0.5, 8.0)
    }

    private fun getLbmCoefficient(): Double {
        var lbm = (height * 9.058 / 100.0) * (height / 100)
        lbm += weight * 0.32 + 12.226
        lbm -= impedance * 0.0068
        lbm -= age * 0.0542
        return lbm
    }

    private fun getMuscleMassScale(): DoubleArray {
        val scale = muscleMassScales.firstOrNull { height >= it.min[sex]!! } ?: muscleMassScales.last()
        return when (sex) {
            UserInfo.Sex.Female -> scale.female
            UserInfo.Sex.Male -> scale.male
        }
    }

    private fun getFatPercentageScale(): DoubleArray {
        val scale = fatPercentageScales.firstOrNull { age >= it.min && age < it.max } ?: fatPercentageScales.last()
        return when (sex) {
            UserInfo.Sex.Female -> scale.female
            UserInfo.Sex.Male -> scale.male
        }
    }
}