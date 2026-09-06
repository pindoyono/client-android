package com.smkn2malinau.absensi.repository

import com.smkn2malinau.absensi.auth.seedAkunSiswaDariRoster
import com.smkn2malinau.absensi.data.local.AbsensiDatabase
import com.smkn2malinau.absensi.data.local.entity.AbsensiLokal
import com.smkn2malinau.absensi.data.local.entity.JadwalCache
import com.smkn2malinau.absensi.data.local.entity.JadwalOverrideLokal
import com.smkn2malinau.absensi.data.local.entity.SiswaCache
import com.smkn2malinau.absensi.data.remote.ApiService
import com.smkn2malinau.absensi.data.remote.JadwalOverrideServerDto
import com.smkn2malinau.absensi.data.remote.JadwalStandarDto
import com.smkn2malinau.absensi.face.CryptoEmbedding
import java.time.LocalDateTime
import java.util.UUID

/**
 * Data & aksi untuk Panel Admin — setara section di `admin_window.py` (Windows).
 * Terpisah dari [AbsensiRepository] (jalur kiosk) supaya jalur absensi tidak ikut berubah.
 */
interface AdminRepository {
    suspend fun statistikSync(): StatistikSync
    suspend fun recordSyncTerbaru(limit: Int = 20): List<AbsensiLokal>

    suspend fun jadwalStandar(): List<JadwalCache>
    suspend fun overrideLokalSemua(): List<JadwalOverrideLokal>
    suspend fun simpanOverrideLokal(
        tanggal: String, kelas: String?, jamMasuk: String, jamPulang: String, alasan: String?
    )
    suspend fun hapusOverrideLokal(id: String)
    suspend fun resetOverrideDitolak(): Int

    /** Jadwal standar + override DARI SERVER (butuh Bearer JWT guru). Setara panel Windows. */
    suspend fun jadwalServer(api: ApiService, bearer: String): JadwalServerHasil
    /** Hapus satu override server (DELETE /jadwal/override/{id}) — butuh JWT admin/guru piket. */
    suspend fun hapusOverrideServer(api: ApiService, bearer: String, id: Int)

    suspend fun daftarSiswaLokal(): List<SiswaLokalRow>

    /**
     * Tarik roster siswa LENGKAP dari server (`GET /siswa`, device-auth) lalu
     * segarkan cache lokal — termasuk siswa yang belum enroll wajah (yang tidak
     * dikirim `GET /embeddings/sync`). Dipakai tombol "Tarik dari server" di
     * layar Data Siswa.
     */
    suspend fun tarikSiswaDariServer(api: ApiService): SiswaTarikHasil

    /** Uji `faceKey` terhadap embedding cache. @return (total, berhasil didekripsi). */
    suspend fun tesDekripsiEmbedding(faceKey: String): Pair<Int, Int>
}

data class JadwalServerHasil(
    val standar: List<JadwalStandarDto>,
    val override: List<JadwalOverrideServerDto>,
)

data class SiswaTarikHasil(
    /** Jumlah siswa aktif dari server yang disimpan/diperbarui di cache. */
    val disimpan: Int,
    /** Dari [disimpan], berapa yang sudah enroll wajah (menurut server). */
    val terEnroll: Int,
    /** Jumlah baris siswa lama yang dibuang (tak lagi di roster & belum enroll). */
    val dinonaktifkan: Int,
)

data class StatistikSync(
    val totalAbsensi: Int,
    val tersinkron: Int,
    val menunggu: Int,
    val gagal: Int,
    val siswaLokal: Int,
    val jadwalLokal: Int,
    val dispensasiLokal: Int,
    val syncTerakhir: String?,
    /** Status siklus sync terakhir: "success" | "failed" | null (belum pernah). */
    val syncTerakhirStatus: String? = null,
    /** Pesan error dari siklus sync terakhir bila gagal. */
    val syncTerakhirError: String? = null,
) {
    val persenTersinkron: Int
        get() = if (totalAbsensi == 0) 0 else (tersinkron * 100) / totalAbsensi
}

data class SiswaLokalRow(
    val siswaId: Int,
    val nis: String,
    val nama: String,
    val kelas: String,
    val terEnroll: Boolean,
    /** true bila baris ini hasil enroll lokal (belum ada di server). */
    val lokal: Boolean,
)

class AdminRepositoryImpl(private val db: AbsensiDatabase) : AdminRepository {

    override suspend fun statistikSync(): StatistikSync {
        val absensiDao = db.absensiDao()
        val eventTerakhir = db.logDao().sinkronTerakhir()
        return StatistikSync(
            totalAbsensi = absensiDao.countSemua(),
            tersinkron = absensiDao.countTersinkron(),
            menunggu = absensiDao.countMenunggu(),
            gagal = absensiDao.countGagal(),
            siswaLokal = db.siswaDao().countSiswa(),
            jadwalLokal = db.jadwalDao().countJadwalCache(),
            dispensasiLokal = db.jadwalDao().countDispensasiCache(),
            syncTerakhir = db.logDao().syncSuksesTerakhir(),
            syncTerakhirStatus = eventTerakhir?.status,
            syncTerakhirError = eventTerakhir?.takeIf { it.status == "failed" }?.error_message,
        )
    }

    override suspend fun recordSyncTerbaru(limit: Int): List<AbsensiLokal> =
        db.absensiDao().recordTerbaru(limit)

    override suspend fun jadwalStandar(): List<JadwalCache> = db.jadwalDao().getSemuaJadwalCache()

    override suspend fun overrideLokalSemua(): List<JadwalOverrideLokal> =
        db.jadwalDao().getSemuaOverrideLokal()

    override suspend fun simpanOverrideLokal(
        tanggal: String, kelas: String?, jamMasuk: String, jamPulang: String, alasan: String?
    ) {
        db.jadwalDao().insertOverride(
            JadwalOverrideLokal(
                id = UUID.randomUUID().toString(),
                tanggal = tanggal,
                kelas = kelas?.takeIf { it.isNotBlank() },
                jam_masuk = normalisasiJam(jamMasuk),
                jam_pulang = normalisasiJam(jamPulang),
                alasan = alasan?.takeIf { it.isNotBlank() },
                dibuat_pada = LocalDateTime.now().toString(),
            )
        )
    }

    override suspend fun hapusOverrideLokal(id: String) = db.jadwalDao().deleteOverrideLokal(id)

    override suspend fun resetOverrideDitolak(): Int = db.jadwalDao().resetOverrideDitolak()

    override suspend fun jadwalServer(api: ApiService, bearer: String) = JadwalServerHasil(
        standar = api.getJadwalStandarServer(bearer),
        override = api.getJadwalOverrideServer(bearer),
    )

    override suspend fun hapusOverrideServer(api: ApiService, bearer: String, id: Int) =
        api.hapusJadwalOverrideServer(bearer, id)

    override suspend fun tarikSiswaDariServer(api: ApiService): SiswaTarikHasil {
        val siswaDao = db.siswaDao()
        val roster = api.getSiswaRoster(null, null)

        siswaDao.insertSiswa(
            roster.map { SiswaCache(siswa_id = it.id, nis = it.nis, nama = it.nama, kelas = it.kelas) }
        )
        db.akunDao().seedAkunSiswaDariRoster(roster)

        // Buang baris versi-server (id > 0) yang tak lagi ada di roster DAN belum
        // pernah enroll (tak punya embedding). Yang ber-embedding diurus
        // /embeddings/sync lewat flag aktif=false.
        val idRoster = roster.map { it.id }.toSet()
        val punyaEmbedding = siswaDao.getSemuaEmbedding().map { it.siswa_id }.toSet()
        val dibuang = siswaDao.getSemuaSiswa()
            .filter { it.siswa_id > 0 && it.siswa_id !in idRoster && it.siswa_id !in punyaEmbedding }
            .map { it.siswa_id }
        dibuang.forEach { siswaDao.deleteSiswa(it) }

        return SiswaTarikHasil(
            disimpan = roster.size,
            terEnroll = roster.count { it.enrolled },
            dinonaktifkan = dibuang.size,
        )
    }

    override suspend fun tesDekripsiEmbedding(faceKey: String): Pair<Int, Int> {
        val rows = db.siswaDao().getSemuaEmbedding()
        var ok = 0
        for (r in rows) {
            val hasil = runCatching { CryptoEmbedding.decryptEmbedding(r.embedding_encrypted, faceKey) }
            if (hasil.getOrNull()?.isNotEmpty() == true) ok++
        }
        return rows.size to ok
    }

    override suspend fun daftarSiswaLokal(): List<SiswaLokalRow> {
        val idTerEnroll = db.siswaDao().getSemuaEmbedding().map { it.siswa_id }.toSet()
        return db.siswaDao().getSemuaSiswa()
            .sortedBy { it.nama.lowercase() }
            .map {
                SiswaLokalRow(
                    siswaId = it.siswa_id,
                    nis = it.nis,
                    nama = it.nama,
                    kelas = it.kelas,
                    terEnroll = it.siswa_id in idTerEnroll,
                    lokal = it.siswa_id < 0,
                )
            }
    }

    /** "7:00" / "07:00" -> "07:00:00" (jam selalu 2 digit, detik selalu ada). */
    private fun normalisasiJam(j: String): String {
        val bagian = j.trim().split(":")
        val jam = bagian.getOrElse(0) { "0" }.padStart(2, '0')
        val menit = bagian.getOrElse(1) { "00" }.padStart(2, '0')
        val detik = bagian.getOrElse(2) { "00" }.padStart(2, '0')
        return "$jam:$menit:$detik"
    }
}
