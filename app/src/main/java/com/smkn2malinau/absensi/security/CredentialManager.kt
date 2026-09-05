package com.smkn2malinau.absensi.security

import android.content.Context
import android.content.SharedPreferences
import com.smkn2malinau.absensi.BuildConfig
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Credential manager — simpan Device API Key & device ID di Android Keystore.
 * Setara Windows Credential Manager (PRD bagian 5).
 */
class CredentialManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("absensi_secure_prefs", Context.MODE_PRIVATE)

    private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private val KEY_ALIAS = "absensi_device_key"
    private val PREF_DEVICE_ID = "device_id"
    private val PREF_API_KEY = "api_key_enc"
    private val PREF_DB_PASSPHRASE = "db_passphrase_enc"
    private val PREF_ON_SITE_TESTING = "ON_SITE_TESTING_SELESAI"
    private val PREF_LOKASI_VALID = "lokasi_valid_terakhir"
    private val PREF_LOKASI_ALASAN = "lokasi_alasan_terakhir"
    private val PREF_LOKASI_JARAK = "lokasi_jarak_meter_terakhir"
    private val PREF_LOKASI_DIKONFIGURASI = "lokasi_dikonfigurasi"
    private val PREF_NAMA_LOKASI = "nama_lokasi"
    private val PREF_ADMIN_NAMA = "admin_nama"
    private val PREF_ADMIN_ROLE = "admin_role"
    private val PREF_PIN = "admin_pin_enc"
    private val PREF_PIN_SALAH = "admin_pin_salah"
    private val PREF_PIN_KUNCI_SAMPAI = "admin_pin_kunci_sampai"
    private val PREF_SERVER_URL = "server_base_url"
    private val PREF_LENSA_KAMERA = "lensa_kamera" // "depan" | "belakang"
    private val PREF_FACE_KEY = "face_encryption_key_enc"
    private val PREF_AMBANG_JARAK = "ambang_jarak_wajah"
    private val PREF_SESI = "sesi_pengguna_enc"        // "identitas|nama|role|siswaId"
    private val PREF_SESI_SAMPAI = "sesi_pengguna_sampai"

    /**
     * Simpan Device API Key (terenkripsi di Keystore).
     */
    fun saveApiKey(apiKey: String) {
        val encrypted = encrypt(apiKey)
        prefs.edit().putString(PREF_API_KEY, encrypted).apply()
    }

    fun getApiKey(): String? {
        val encrypted = prefs.getString(PREF_API_KEY, null) ?: return null
        return try {
            decrypt(encrypted)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Simpan device ID.
     */
    fun saveDeviceId(deviceId: String) {
        prefs.edit().putString(PREF_DEVICE_ID, deviceId).apply()
    }

    fun getDeviceId(): String? = prefs.getString(PREF_DEVICE_ID, null)

    /**
     * device_id stabil untuk perangkat ini. Dibuat sekali lalu dipertahankan
     * (setara generate-dari-hostname di client Windows). Dipakai sebagai usulan
     * saat registrasi; server tetap yang menentukan device_id final.
     */
    fun getOrCreateDeviceIdSeed(): String {
        prefs.getString(PREF_DEVICE_ID, null)?.let { return it }
        val seed = "android-" + java.util.UUID.randomUUID().toString().take(12)
        prefs.edit().putString(PREF_DEVICE_ID, seed).apply()
        return seed
    }

    fun hasCredentials(): Boolean = getDeviceId() != null && getApiKey() != null

    fun clearCredentials() {
        prefs.edit()
            .remove(PREF_DEVICE_ID).remove(PREF_API_KEY)
            .remove(PREF_ADMIN_NAMA).remove(PREF_ADMIN_ROLE)
            .remove(PREF_PIN).remove(PREF_PIN_SALAH).remove(PREF_PIN_KUNCI_SAMPAI)
            .apply()
    }

    fun saveNamaLokasi(nama: String) = prefs.edit().putString(PREF_NAMA_LOKASI, nama).apply()
    fun getNamaLokasi(): String = prefs.getString(PREF_NAMA_LOKASI, "") ?: ""

    fun saveAdminInfo(nama: String?, role: String?) {
        prefs.edit()
            .putString(PREF_ADMIN_NAMA, nama ?: "")
            .putString(PREF_ADMIN_ROLE, role ?: "")
            .apply()
    }

    fun getAdminNama(): String = prefs.getString(PREF_ADMIN_NAMA, "") ?: ""
    fun getAdminRole(): String = prefs.getString(PREF_ADMIN_ROLE, "") ?: ""

    // --- PIN Panel Admin (gerbang saat offline) ---

    fun savePin(pin: String) {
        prefs.edit()
            .putString(PREF_PIN, encrypt(pin))
            .putInt(PREF_PIN_SALAH, 0)
            .putLong(PREF_PIN_KUNCI_SAMPAI, 0L)
            .apply()
    }

    fun hasPin(): Boolean = prefs.getString(PREF_PIN, null) != null

    /**
     * Cek PIN dengan pembatasan percobaan (anti brute-force):
     * 5 salah → terkunci 30 dtk, tiap 5 salah berikutnya delay bertambah.
     * @return null = benar; angka = masih terkunci (detik tersisa).
     */
    fun cekPin(pin: String): Long? {
        val sisaKunci = sisaDetikTerkunci()
        if (sisaKunci > 0) return sisaKunci

        val enc = prefs.getString(PREF_PIN, null) ?: return null
        val benar = try {
            decrypt(enc) == pin
        } catch (e: Exception) {
            false
        }
        if (benar) {
            prefs.edit().putInt(PREF_PIN_SALAH, 0).putLong(PREF_PIN_KUNCI_SAMPAI, 0L).apply()
            return null
        }

        val salah = prefs.getInt(PREF_PIN_SALAH, 0) + 1
        prefs.edit().putInt(PREF_PIN_SALAH, salah).apply()
        if (salah % PIN_MAX_SALAH == 0) {
            val blok = salah / PIN_MAX_SALAH
            val detik = PIN_KUNCI_DASAR_DTK * (1L shl (blok - 1).coerceAtMost(6))
            prefs.edit()
                .putLong(PREF_PIN_KUNCI_SAMPAI, System.currentTimeMillis() + detik * 1000)
                .apply()
            return detik
        }
        return 0L
    }

    /** Detik tersisa terkunci; 0 bila tidak terkunci. */
    fun sisaDetikTerkunci(): Long {
        val sampai = prefs.getLong(PREF_PIN_KUNCI_SAMPAI, 0L)
        val sisa = (sampai - System.currentTimeMillis()) / 1000
        return if (sisa > 0) sisa else 0L
    }

    // --- Pengaturan runtime ---

    fun saveServerBaseUrl(url: String) {
        val v = url.trim().let { if (it.isEmpty() || it.endsWith("/")) it else "$it/" }
        prefs.edit().putString(PREF_SERVER_URL, v).apply()
    }

    /** null = pakai BuildConfig.SERVER_BASE_URL. */
    fun getServerBaseUrl(): String? = prefs.getString(PREF_SERVER_URL, null)?.takeIf { it.isNotBlank() }

    fun saveLensaKamera(depan: Boolean) =
        prefs.edit().putString(PREF_LENSA_KAMERA, if (depan) "depan" else "belakang").apply()

    /** true = kamera depan (default). */
    fun lensaKameraDepan(): Boolean = prefs.getString(PREF_LENSA_KAMERA, "depan") != "belakang"

    /**
     * Fernet key untuk embedding wajah (`FACE_ENCRYPTION_KEY` server). Runtime override
     * atas `BuildConfig.FACE_ENCRYPTION_KEY` — disimpan terenkripsi di Keystore.
     */
    fun saveFaceKey(key: String) {
        val bersih = key.trim()
        if (bersih.isEmpty()) prefs.edit().remove(PREF_FACE_KEY).apply()
        else prefs.edit().putString(PREF_FACE_KEY, encrypt(bersih)).apply()
    }

    /** Key tersimpan (runtime) → fallback BuildConfig. String kosong bila belum di-set di mana pun. */
    fun getFaceKey(): String {
        val enc = prefs.getString(PREF_FACE_KEY, null)
        if (enc != null) {
            runCatching { decrypt(enc) }.getOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return BuildConfig.FACE_ENCRYPTION_KEY.trim()
    }

    /**
     * Ambang *distance* face-matching (0.20–0.80). Default kalibrasi Windows 0.3542 —
     * dinaikkan bila wajah asli sering "tidak dikenali", diturunkan bila salah orang lolos.
     */
    fun getAmbangJarak(): Float {
        val v = prefs.getFloat(PREF_AMBANG_JARAK, AMBANG_JARAK_DEFAULT)
        return v.coerceIn(0.20f, 0.80f)
    }

    fun saveAmbangJarak(nilai: Float) =
        prefs.edit().putFloat(PREF_AMBANG_JARAK, nilai.coerceIn(0.20f, 0.80f)).apply()

    // --- Sesi login Panel Admin (TTL 8 jam) ---

    data class SesiTersimpan(val identitas: String, val nama: String, val role: String, val siswaId: Int?)

    fun saveSesi(identitas: String, nama: String, role: String, siswaId: Int?) {
        val payload = listOf(identitas, nama, role, siswaId?.toString() ?: "").joinToString("|")
        prefs.edit()
            .putString(PREF_SESI, encrypt(payload))
            .putLong(PREF_SESI_SAMPAI, System.currentTimeMillis() + SESI_TTL_MS)
            .apply()
    }

    fun getSesiTersimpan(): SesiTersimpan? {
        if (System.currentTimeMillis() > prefs.getLong(PREF_SESI_SAMPAI, 0L)) return null
        val enc = prefs.getString(PREF_SESI, null) ?: return null
        return try {
            val p = decrypt(enc).split("|")
            SesiTersimpan(p[0], p[1], p[2], p.getOrNull(3)?.toIntOrNull())
        } catch (e: Exception) {
            null
        }
    }

    fun clearSesi() = prefs.edit().remove(PREF_SESI).remove(PREF_SESI_SAMPAI).apply()

    /**
     * Simpan passphrase database (SQLCipher).
     */
    fun saveDbPassphrase(passphrase: String) {
        val encrypted = encrypt(passphrase)
        prefs.edit().putString(PREF_DB_PASSPHRASE, encrypted).apply()
    }

    fun getDbPassphrase(): ByteArray {
        val encrypted = prefs.getString(PREF_DB_PASSPHRASE, null)
        if (encrypted != null) {
            return try {
                decrypt(encrypted).toByteArray()
            } catch (e: Exception) {
                generateDefaultPassphrase()
            }
        }
        return generateDefaultPassphrase()
    }

    /**
     * Safety gate — PRD bagian 10.
     */
    fun isOnSiteTestingSelesai(): Boolean =
        prefs.getBoolean(PREF_ON_SITE_TESTING, false)

    fun setOnSiteTestingSelesai(value: Boolean) {
        prefs.edit().putBoolean(PREF_ON_SITE_TESTING, value).apply()
    }

    /**
     * Status geofencing terakhir — diperbarui SyncService tiap siklus sync
     * (best-effort, lihat SyncService step 7b), dibaca KioskViewModel untuk
     * memblokir layar kiosk. Fail-closed: default FALSE (belum pernah
     * berhasil sync ke server sama sekali) — begitu sync pertama berhasil,
     * nilai sungguhan dari server (termasuk kasus "lokasi belum diatur" =
     * tetap false) yang menentukan. Kiosk offline lama tidak kena efek ini
     * karena nilai TERAKHIR yang tersimpan dipakai terus, bukan direset ke
     * default setiap kali — default hanya berlaku sebelum sync pertama.
     */
    fun setStatusLokasi(valid: Boolean, alasan: String, jarakMeter: Double? = null, dikonfigurasi: Boolean = false) {
        val editor = prefs.edit()
            .putBoolean(PREF_LOKASI_VALID, valid)
            .putString(PREF_LOKASI_ALASAN, alasan)
            .putBoolean(PREF_LOKASI_DIKONFIGURASI, dikonfigurasi)
        if (jarakMeter != null) editor.putFloat(PREF_LOKASI_JARAK, jarakMeter.toFloat())
        else editor.remove(PREF_LOKASI_JARAK)
        editor.apply()
    }

    fun lokasiValid(): Boolean = prefs.getBoolean(PREF_LOKASI_VALID, false)
    fun lokasiAlasan(): String? = prefs.getString(PREF_LOKASI_ALASAN, null)
    /** Jarak (meter) ke titik acuan server saat pengecekan terakhir — null = lokasi belum diatur di server. */
    fun lokasiJarakMeter(): Double? =
        if (prefs.contains(PREF_LOKASI_JARAK)) prefs.getFloat(PREF_LOKASI_JARAK, 0f).toDouble() else null
    /** Admin sudah pasang titik acuan untuk device ini atau belum — untuk indikator ikon kiosk. */
    fun lokasiDikonfigurasi(): Boolean = prefs.getBoolean(PREF_LOKASI_DIKONFIGURASI, false)

    private fun generateDefaultPassphrase(): ByteArray {
        val pass = "absensi_smkn2_malinau_2024_secure_db"
        saveDbPassphrase(pass)
        return pass.toByteArray()
    }

    private fun getOrCreateKey(): SecretKey {
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return (keyStore.getKey(KEY_ALIAS, null) as SecretKey)
        }
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return keyGenerator.generateKey()
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val combined = iv + encrypted
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(encryptedText: String): String {
        val combined = Base64.decode(encryptedText, Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, 12)
        val encrypted = combined.copyOfRange(12, combined.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    private companion object {
        const val PIN_MAX_SALAH = 5
        const val PIN_KUNCI_DASAR_DTK = 30L
        /** Sama dengan `LivenessEvaluator.AMBANG_JARAK_DEFAULT` (kalibrasi Windows). */
        const val AMBANG_JARAK_DEFAULT = 0.3542f
        const val SESI_TTL_MS = 8L * 60 * 60 * 1000
    }
}
