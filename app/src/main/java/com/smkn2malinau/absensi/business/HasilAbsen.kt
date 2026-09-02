package com.smkn2malinau.absensi.business

enum class HasilAbsen {
    BERHASIL_MASUK_NORMAL,
    BERHASIL_MASUK_TERLAMBAT,
    BERHASIL_PULANG_NORMAL,
    BERHASIL_PULANG_CEPAT,
    DITOLAK_BELUM_WAKTUNYA_MASUK,
    DITOLAK_BELUM_WAKTUNYA_PULANG,
    DITOLAK_SUDAH_ABSEN_LENGKAP,
    DITOLAK_TIDAK_DIKENAL,
    ERROR_DATABASE;

    /** true untuk hasil yang seharusnya menghasilkan baris absensi baru di DB. */
    fun berhasil(): Boolean = this == BERHASIL_MASUK_NORMAL ||
        this == BERHASIL_MASUK_TERLAMBAT ||
        this == BERHASIL_PULANG_NORMAL ||
        this == BERHASIL_PULANG_CEPAT
}
