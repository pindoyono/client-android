package com.smkn2malinau.absensi.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.smkn2malinau.absensi.data.local.entity.AkunLokal

@Dao
interface AkunDao {

    @Query("SELECT * FROM akun_lokal WHERE identitas = :identitas AND aktif = 1")
    suspend fun getByIdentitas(identitas: String): AkunLokal?

    /** Tanpa filter aktif — untuk seed roster (pertahankan password akun yang di-reaktivasi). */
    @Query("SELECT * FROM akun_lokal WHERE identitas = :identitas")
    suspend fun getByIdentitasApaPun(identitas: String): AkunLokal?

    @Query("SELECT * FROM akun_lokal WHERE aktif = 1 ORDER BY role, nama")
    suspend fun getSemua(): List<AkunLokal>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(akun: AkunLokal)

    @Query("UPDATE akun_lokal SET aktif = 0, diperbarui_pada = :waktu WHERE identitas = :identitas")
    suspend fun nonaktifkan(identitas: String, waktu: String)

    @Query("UPDATE akun_lokal SET password_hash = :hash, salt = :salt, diperbarui_pada = :waktu WHERE identitas = :identitas")
    suspend fun setPassword(identitas: String, hash: String, salt: String, waktu: String)

    @Query("SELECT COUNT(*) FROM akun_lokal WHERE role = 'admin' AND aktif = 1")
    suspend fun countAdminAktif(): Int

    @Query("SELECT COUNT(*) FROM akun_lokal WHERE aktif = 1")
    suspend fun countAktif(): Int
}
