package com.smkn2malinau.absensi.business

import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Port murni dari attendance_logic.py (Windows)
 * Menangani state machine absensi sesuai PRD Bagian 3.
 */
class AttendanceLogic {

    companion object {
        const val BATAS_AWAL_MASUK_JAM = 2L
        const val TOLERANSI_TERLAMBAT_MENIT = 5L
        private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }

    data class JadwalEfektif(
        val jamMasuk: LocalTime,
        val jamPulang: LocalTime
    )

    data class StatusAbsensi(
        val sudahMasuk: Boolean,
        val sudahPulang: Boolean,
        val jamMasukAktual: LocalTime? = null,
        val jamPulangAktual: LocalTime? = null
    )

    /**
     * Menghitung hasil absensi berdasarkan waktu sekarang dan history hari ini.
     * Mengikuti Tabel Skenario PRD 3.4.
     */
    fun hitungHasil(
        sekarang: LocalTime,
        jadwal: JadwalEfektif,
        status: StatusAbsensi,
        adaDispensasiPulangCepat: Boolean = false
    ): HasilAbsen {
        
        // 1. Cek State Machine (PRD 3.1)
        if (status.sudahMasuk && status.sudahPulang) {
            return HasilAbsen.DITOLAK_SUDAH_ABSEN_LENGKAP
        }

        if (!status.sudahMasuk) {
            // Logika MASUK
            val batasAwalMasuk = jadwal.jamMasuk.minusHours(BATAS_AWAL_MASUK_JAM)
            
            if (sekarang.isBefore(batasAwalMasuk)) {
                return HasilAbsen.DITOLAK_BELUM_WAKTUNYA_MASUK
            }

            val batasTerlambat = jadwal.jamMasuk.plusMinutes(TOLERANSI_TERLAMBAT_MENIT)
            return if (sekarang.isAfter(batasTerlambat)) {
                HasilAbsen.BERHASIL_MASUK_TERLAMBAT
            } else {
                HasilAbsen.BERHASIL_MASUK_NORMAL
            }
        } else {
            // Logika PULANG
            if (sekarang.isBefore(jadwal.jamPulang)) {
                return if (adaDispensasiPulangCepat) {
                    HasilAbsen.BERHASIL_PULANG_CEPAT
                } else {
                    HasilAbsen.DITOLAK_BELUM_WAKTUNYA_PULANG
                }
            }
            
            return HasilAbsen.BERHASIL_PULANG_NORMAL
        }
    }
}
