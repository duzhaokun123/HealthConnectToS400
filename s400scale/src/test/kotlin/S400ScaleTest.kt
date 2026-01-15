import io.github.duzhaokun123.s400scale.S400InputData
import io.github.duzhaokun123.s400scale.S400Scale
import io.github.duzhaokun123.s400scale.UserInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class S400ScaleTest {

    var userInfo = UserInfo(182, 29, UserInfo.Sex.Male)
    var inputData = S400InputData(
        macOriginal = "84:46:93:64:A5:E6",
        aesKey = "58305740b64e4b425e518aa1f4e51339",
        dataString = "to be filled in tests"
    )

    @Test
    fun test1() {
        val expectedResult = 74.2
        val testData = inputData.copy(
            dataString = "4859d53b2d3314943c58b133638c7457a4000000c3e670dc".lowercase().replace(" ", "")
        )
        val bc = S400Scale.getBodyComposition(userInfo, testData)!!
        assertEquals(expectedResult, bc.weight)
    }

    @Test
    fun test26bytesHex() {
        val expectedResult = 73.2
        val testData = inputData.copy(
            dataString = "95FE4859D53B3BDE6BC8D05B51C0CDFD9021C9000000925C5039".lowercase().replace(" ", "")
        )
        val bc = S400Scale.getBodyComposition(userInfo, testData)!!
        assertEquals(expectedResult, bc.weight)
    }

    @Test
    fun test26bytes() {
        val expectedResult = 73.3
        val testData = inputData.copy(
            data = intArrayOf(
                149, 254,72,89,213,59,77,111,53,156,229,111,31,126,126,10,221,220,38,0,0,0,12,19,211,196
            ).map { it.toByte() }.toByteArray()
        )
        val bc = S400Scale.getBodyComposition(userInfo, testData)!!
        assertEquals(expectedResult, bc.weight)
    }

    @Test
    fun test26bytesOnlyWeight() {
        val testData = inputData.copy(
            data = intArrayOf(
                149, 254, 72, 89, 213, 59, 99, 187, 88, 121, 80, 225, 4, 44, 172, 28, 95, 24, 246, 0, 0, 0, 219, 233, 112, 52
            ).map { it.toByte() }.toByteArray()
        )
        val bc = S400Scale.getBodyComposition(userInfo, testData)!!
        assertEquals(true, bc.weight > 0 && bc.impedance == null)
    }

    @Test
    fun testJustMACAddress() {
        val testData = inputData.copy(
            dataString = "10 59 d5 3b 06 e6 a5 64 93 46 84".lowercase().replace(" ", "")
        )
        val bc = S400Scale.getBodyComposition(userInfo, testData)
        assertEquals(null, bc)
    }

    @Test
    fun testNoWeight() {
        val testData = inputData.copy(
            dataString = "4859d53b2e724a8c783dc8a392c10db411000000a8a7bad5".lowercase().replace(" ", "")
        )
        val bc = S400Scale.getBodyComposition(userInfo, testData)
        assertEquals(true, true)
    }

    @Test
    fun test2DataStrings() {
        val testData1 = inputData.copy(
            dataString = "4859d53b2e724a8c783dc8a392c10db411000000a8a7bad5".lowercase().replace(" ", "")
        )
        var bc = S400Scale.getBodyComposition(userInfo, testData1)
        val testData2 = inputData.copy(
            dataString = "4859d53b2d3314943c58b133638c7457a4000000c3e670dc".lowercase().replace(" ", "")
        )
        bc = S400Scale.getBodyComposition(userInfo, testData2)
        assertEquals(true, true)
    }

    @Test
    fun testFullData() {
        val userInfo = UserInfo(175, 22, UserInfo.Sex.Female)
        val inputData = S400InputData(
            macOriginal = "2C:19:5C:DE:8B:41",
            aesKey = "75bd5f0a284e9703223f5c87eeb7dfaf",
            data = intArrayOf(
                0x95,0xFE,0x48,0x59,0xD9,0x30,0xAB,0xE3,0x9F,0xEA,0x48,0x62,0x76,0x40,0x1B,0x6D,0x57,0xE8,0x70,0x02,0x00,0x00,0xB6,0xC0,0x5F,0x8B
            ).map { it.toByte() }.toByteArray(),
            dataString = "skipped"
        )
        val bc = S400Scale.getBodyComposition(userInfo, inputData)!!
        println(bc)
        assertEquals(55.4, bc.weight)
        assertEquals(601.0, bc.impedance)
        assertEquals(22.1, bc.fat)
        assertEquals(52.0, bc.heartRate)
    }
}