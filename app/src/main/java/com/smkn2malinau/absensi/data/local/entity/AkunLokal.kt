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
)
