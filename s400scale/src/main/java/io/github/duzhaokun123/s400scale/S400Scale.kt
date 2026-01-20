@file:Suppress("ArrayInDataClass")

package io.github.duzhaokun123.s400scale

import org.spongycastle.crypto.engines.AESEngine
import org.spongycastle.crypto.modes.CCMBlockCipher
import org.spongycastle.crypto.params.AEADParameters
import org.spongycastle.crypto.params.KeyParameter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Date

object S400Scale {
    const val BUFFER_SIZE = 24
    const val WEIGHT = "weight"
    const val IMPEDANCE = "impedance"
    const val IMPEDANCE_LOW = "impedanceLow"
    const val HEART_RATE = "heartRate"

    private lateinit var data: ByteArray
    private lateinit var mac: ByteArray
    private lateinit var aesKey: ByteArray
    val sensors = mutableMapOf<String, Double>()

    fun getBodyComposition(userInfo: UserInfo, inputData: S400InputData): BodyComposition? {
        data = inputData.data ?: inputData.dataString.hexToByteArray()

        if (checkInput() != true) {
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

        return BodyComposition(
            weight = getSensorValue(WEIGHT) ?: 0.0,
            date = Date(),
            impedance = getSensorValue(IMPEDANCE),
            heartRate = getSensorValue(HEART_RATE)
        )
    }

    private fun checkInput(): Boolean {
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
            updateSensor(WEIGHT, weight)
        }

        if (heartRate in 1 until 127) {
            updateSensor(HEART_RATE, heartRate + 50.0)
        }

        if (impedance != 0) {
            if (mass != 0) {
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

}