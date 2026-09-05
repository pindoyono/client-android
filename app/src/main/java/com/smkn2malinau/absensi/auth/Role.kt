package com.smkn2malinau.absensi.auth

/** Peran pengguna untuk gating fitur. Nilai string cocok dengan `Guru.role` server. */
enum class Role(val kode: String, val label: String) {
    ADMIN("admin", "Admin"),
    GURU_PIKET("guru_piket", "Guru Piket"),
    SISWA("siswa", "Siswa");

    companion object {
        fun dari(raw: String?): Role = when (raw?.trim()?.lowercase()?.replace(' ', '_')) {
            "admin" -> ADMIN
            "guru_piket" -> GURU_PIKET
            else -> SISWA
        }
    }
}

/** Fitur yang bisa dibatasi per role. */
enum class Fitur {
    PANEL_ADMIN,        // buka Panel Admin sama sekali
    SINKRONISASI,       // lihat + "Sync sekarang"
    JADWAL,             // kelola override jadwal lokal
    DATA_SISWA,         // lihat daftar siswa
    DAFTAR_WAJAH,       // enroll wajah
    PENGATURAN,         // server URL, face key, ambang, hapus kredensial
    KELOLA_AKUN,        // tambah/hapus akun & set password
    RIWAYAT_SENDIRI,    // layar riwayat absensi siswa
    BENCHMARK,          // uji performa device (deteksi/liveness/matching wajah)
}

/** Peta hak akses — sumber kebenaran tunggal untuk gating UI. */
object HakAkses {
    private val peta: Map<Role, Set<Fitur>> = mapOf(
        Role.ADMIN to Fitur.entries.toSet(),
        Role.GURU_PIKET to setOf(
            Fitur.PANEL_ADMIN, Fitur.SINKRONISASI, Fitur.JADWAL, Fitur.DATA_SISWA, Fitur.DAFTAR_WAJAH,
        ),
        Role.SISWA to setOf(Fitur.RIWAYAT_SENDIRI),
    )

    fun boleh(role: Role, fitur: Fitur): Boolean = peta[role]?.contains(fitur) == true
}

fun Role.boleh(fitur: Fitur): Boolean = HakAkses.boleh(this, fitur)
