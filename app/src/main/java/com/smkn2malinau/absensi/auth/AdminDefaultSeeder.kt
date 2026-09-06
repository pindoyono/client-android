package com.smkn2malinau.absensi.auth

import com.smkn2malinau.absensi.data.local.dao.AkunDao
import com.smkn2malinau.absensi.data.local.entity.AkunLokal
import com.smkn2malinau.absensi.security.PasswordHasher
import java.time.LocalDateTime

/**
 * Seed akun admin OFFLINE default dari BuildConfig (`DEFAULT_ADMIN_USER` /
 * `DEFAULT_ADMIN_PASS`, diisi di `local.properties` yang TIDAK di-commit).
 *
 * Dipanggil sekali saat aplikasi start. Kalau akun dengan identitas itu SUDAH
 * ADA (mis. admin sudah ganti passwordnya lewat aplikasi), TIDAK ditimpa.
 * Kosongkan `DEFAULT_ADMIN_*` untuk mematikan fitur ini (setup manual seperti
 * sebelumnya).
 */
suspend fun AkunDao.seedAdminDefault(user: String, pass: String) {
    val id = user.trim().lowercase()
    if (id.isBlank() || pass.length < 6) return
    if (getByIdentitasApaPun(id) != null) return

    val h = PasswordHasher.hash(pass)
    upsert(
        AkunLokal(
            identitas = id,
            nama = user.trim(),
            role = Role.ADMIN.kode,
            password_hash = h.hashB64,
            salt = h.saltB64,
            siswa_id = null,
            aktif = 1,
            diperbarui_pada = LocalDateTime.now().toString(),
        )
    )
}
