package com.smkn2malinau.absensi.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Akun untuk login Panel Admin — terisi dari login Google sukses (email+nama+role,
 * tanpa password) atau dibuat admin. `password_hash` null = belum bisa login offline.
 * Untuk role siswa, `identitas` = NIS dan `siswa_id` menunjuk `siswa_cache`.
 */
@Entity(tableName = "akun_lokal")
data class AkunLokal(
    /** Email (lowercase) untuk guru/admin; NIS untuk siswa. */
    @PrimaryKey val identitas: String,
    val nama: String,
    val role: String, // "admin" | "guru_piket" | "siswa"
    val password_hash: String? = null,
    val salt: String? = null,
    val siswa_id: Int? = null,
    val aktif: Int = 1,
    val diperbarui_pada: String,
    /**
     * 1 = wajib ganti password sebelum sesi diberikan. Diset untuk akun admin
     * default hasil seed dari BuildConfig (`DEFAULT_ADMIN_PASS` — nilai yang
     * SAMA di setiap APK & bisa diekstrak dari APK). Setelah admin login
     * pertama & menggantinya, password baku itu tak berlaku lagi di device ini.
     * Nullable (bukan Int=0) supaya migrasi Room cukup `ADD COLUMN ... INTEGER`
     * tanpa DEFAULT — lihat catatan MIGRATION_2_3. null diperlakukan = 0.
     */
    val harus_ganti_sandi: Int? = null,
)
