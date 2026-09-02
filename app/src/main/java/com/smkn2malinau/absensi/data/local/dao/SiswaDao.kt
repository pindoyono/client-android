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
}
