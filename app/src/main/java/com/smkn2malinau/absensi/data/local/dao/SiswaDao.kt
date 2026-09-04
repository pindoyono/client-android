package com.smkn2malinau.absensi.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.smkn2malinau.absensi.data.local.entity.SiswaCache
import com.smkn2malinau.absensi.data.local.entity.EmbeddingCache
import kotlinx.coroutines.flow.Flow

@Dao
interface SiswaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSiswa(siswa: List<SiswaCache>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmbedding(embeddings: List<EmbeddingCache>)

    @Query("SELECT * FROM siswa_cache WHERE siswa_id = :siswaId")
    suspend fun getSiswaById(siswaId: Int): SiswaCache?

    @Query("SELECT * FROM siswa_cache")
    suspend fun getSemuaSiswa(): List<SiswaCache>

    @Query("SELECT * FROM embedding_cache")
    fun getAllEmbeddings(): Flow<List<EmbeddingCache>>

    @Query("SELECT * FROM embedding_cache")
    suspend fun getSemuaEmbedding(): List<EmbeddingCache>

    @Query("SELECT * FROM embedding_cache WHERE siswa_id = :siswaId")
    suspend fun getEmbeddingById(siswaId: Int): EmbeddingCache?

    @Query("DELETE FROM siswa_cache WHERE siswa_id = :siswaId")
    suspend fun deleteSiswa(siswaId: Int)

    @Query("DELETE FROM embedding_cache WHERE siswa_id = :siswaId")
    suspend fun deleteEmbedding(siswaId: Int)

    @Query("SELECT COUNT(*) FROM siswa_cache")
    suspend fun countSiswa(): Int

    @Query("SELECT COUNT(*) FROM embedding_cache")
    suspend fun countEmbedding(): Int

    /** Timestamp embedding paling baru ditarik dari server (untuk badge kesegaran kiosk). */
    @Query("SELECT MAX(diperbarui_pada) FROM embedding_cache")
    suspend fun embeddingTerbaru(): String?

    // Enroll lokal (siswa_id < 0) yang NIS-nya kini sudah ada di baris versi server
    // (siswa_id > 0) → hapus supaya matching tidak dobel. Tanpa parameter list
    // (hindari batas 999 variabel SQLite) — server rows sudah di-insert lebih dulu.
    @Query(
        "DELETE FROM embedding_cache WHERE siswa_id IN (SELECT siswa_id FROM siswa_cache " +
            "WHERE siswa_id < 0 AND nis IN (SELECT nis FROM siswa_cache WHERE siswa_id > 0))"
    )
    suspend fun hapusEmbeddingEnrollLokalTertimpa()

    @Query(
        "DELETE FROM siswa_cache WHERE siswa_id < 0 AND nis IN " +
            "(SELECT nis FROM siswa_cache WHERE siswa_id > 0)"
    )
    suspend fun hapusSiswaEnrollLokalTertimpa()
}
