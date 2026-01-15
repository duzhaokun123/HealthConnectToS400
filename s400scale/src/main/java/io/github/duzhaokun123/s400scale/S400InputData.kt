package io.github.duzhaokun123.s400scale

@Suppress("ArrayInDataClass")
data class S400InputData(
    val macOriginal: String,
    val macBytes: ByteArray? = null,
    val aesKey: String,
    val aesKeyBytes: ByteArray? = null,
    val dataString: String,
    val data: ByteArray? = null
)