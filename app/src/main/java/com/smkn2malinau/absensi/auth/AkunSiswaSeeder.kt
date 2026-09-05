package com.smkn2malinau.absensi.auth

import com.smkn2malinau.absensi.data.local.dao.AkunDao
import com.smkn2malinau.absensi.data.local.entity.AkunLokal
import com.smkn2malinau.absensi.data.remote.SiswaRosterDto
import com.smkn2malinau.absensi.security.PasswordHasher
import java.time.LocalDateTime

/**
 * Auto-provisioning akun login siswa dari roster server: identitas & password
 * awal = NIS masing-masing (siswa ganti sendiri password-nya setelah login
 * pertama, lihat [AuthRepository.setPassword]). Akun yang password-nya sudah
 * pernah diganti siswa tidak ditimpa ulang di sini.
 */
suspend fun AkunDao.seedAkunSiswaDariRoster(roster: List<SiswaRosterDto>) {
    val now = LocalDateTime.now().toString()
    for (s in roster) {
        val id = s.nis.trim().lowercase()
        if (id.isBlank()) continue
        val lama = getByIdentitasApaPun(id)
        val defaultHash = if (lama?.password_hash == null) PasswordHasher.hash(id) else null
        upsert(
            AkunLokal(
                identitas = id,
                nama = s.nama.ifBlank { id },
                role = Role.SISWA.kode,
                password_hash = lama?.password_hash ?: defaultHash?.hashB64,
                salt = lama?.salt ?: defaultHash?.saltB64,
                siswa_id = s.id,
                aktif = 1,
                diperbarui_pada = now,
            )
        )
    }
}
