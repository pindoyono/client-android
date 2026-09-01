package com.smkn2malinau.absensi.validation

/**
 * Port pattern regex dari app/validation.py Windows (PRD bagian 9.5).
 */
object Validation {

    private val NIS_REGEX = Regex("^[0-9]{4,10}$")
    private val NAMA_REGEX = Regex("^[A-Za-z\\s.'-]{2,100}$")
    private val KELAS_REGEX = Regex("^[A-Za-z0-9\\s-]{2,30}$")
    private val DEVICE_ID_REGEX = Regex("^[A-Za-z0-9_-]{4,64}$")
    private val API_KEY_REGEX = Regex("^[A-Za-z0-9_-]{16,128}$")

    fun isValidNis(nis: String): Boolean = NIS_REGEX.matches(nis.trim())

    fun isValidNama(nama: String): Boolean = NAMA_REGEX.matches(nama.trim())

    fun isValidKelas(kelas: String): Boolean = KELAS_REGEX.matches(kelas.trim())

    fun isValidDeviceId(deviceId: String): Boolean = DEVICE_ID_REGEX.matches(deviceId.trim())

    fun isValidApiKey(apiKey: String): Boolean = API_KEY_REGEX.matches(apiKey.trim())
}
