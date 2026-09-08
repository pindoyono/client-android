package com.smkn2malinau.absensi.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "siswa_cache")
data class SiswaCache(
    @PrimaryKey val siswa_id: Int,
    val nis: String,
    val nama: String,
    val kelas: String,
    /** 1 = siswa baru daftar wajah SENDIRI, menunggu verifikasi admin.
     *  Wajah tetap di-cache untuk matching, TAPI absensi ditolak sampai
     *  admin mengonfirmasi. Nullable (tanpa DEFAULT SQL) supaya migrasi Room
     *  cukup `ADD COLUMN ... INTEGER` — lihat MIGRATION_2_3. null = 0. */
    val enroll_mandiri_pending: Int? = null,
)
