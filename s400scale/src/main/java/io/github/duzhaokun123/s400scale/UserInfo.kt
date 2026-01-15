package io.github.duzhaokun123.s400scale

data class UserInfo(
    val height: Int,
    val age: Int,
    val sex: Sex,
) {
    enum class Sex(val code: Int) {
        Male(0),
        Female(1),
    }
}