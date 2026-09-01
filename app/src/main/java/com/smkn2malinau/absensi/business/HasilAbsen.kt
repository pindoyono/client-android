package com.smkn2malinau.absensi.business

/**
 * Enum hasil absensi — sesuai PRD bagian 3.
 */
enum class HasilAbsen {
    BERHASIL_MASUK,
    BERHASIL_PULANG,
    DITOLAK_SUDAH_ABSEN,
    DITOLAK_BELUM_WAKTUNYA_MASUK,
    DITOLAK_BELUM_WAKTUNYA_PULANG
}

/**
 * Detail hasil keputusan absensi.
 */
data class DetailHasilAbsen(
    val hasil: HasilAbsen,
    val kategoriWaktu: KategoriWaktu? = null,
    val kategoriDispensasi: String? = null,
    val catatan: String? = null
)

/**
 * Kategori waktu kehadiran.
 */
enum class KategoriWaktu {
    NORMAL,
    TERLAMBAT,
    DISPENSASI
}

/**
 * Status absensi harian siswa.
 */
enum class StatusAbsen {
    BELUM_ABSEN,
    SUDAH_MASUK,
    SUDAH_PULANG
}
