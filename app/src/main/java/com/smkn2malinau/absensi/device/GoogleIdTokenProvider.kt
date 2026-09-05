package com.smkn2malinau.absensi.device

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.smkn2malinau.absensi.BuildConfig

/**
 * Ambil Google ID token lewat Credential Manager (Google Sign-In).
 * Setara `mulai_google_oauth_flow_sync()` di client Windows, tapi native Android.
 */
class GoogleIdTokenProvider(
    context: Context,
    private val webClientId: String = BuildConfig.GOOGLE_WEB_CLIENT_ID,
) {
    private val appContext = context.applicationContext
    private val credentialManager = CredentialManager.create(appContext)

    val terkonfigurasi: Boolean get() = webClientId.isNotBlank()

    sealed class Hasil {
        data class Token(val idToken: String) : Hasil()
        object Dibatalkan : Hasil()
        object TidakAdaAkun : Hasil()
        data class Gagal(val pesan: String) : Hasil()
    }

    /**
     * @param filterByAuthorizedAccounts false = tampilkan semua akun Google di device
     *        (registrasi pertama kali biasanya butuh ini true→false fallback).
     */
    suspend fun ambilIdToken(
        activityContext: Context,
        filterByAuthorizedAccounts: Boolean = false,
    ): Hasil {
        if (!terkonfigurasi) {
            return Hasil.Gagal(
                "GOOGLE_WEB_CLIENT_ID belum diisi di local.properties — " +
                    "pakai input manual device_id + api_key."
            )
        }

        // 1. Coba jalur "one-tap" GetGoogleIdOption dulu (UI paling ringkas: bottom
        //    sheet kecil). Jalur ini RAPUH — sering melempar NoCredentialException
        //    atau bottom sheet-nya muncul lalu langsung tertutup sendiri
        //    (terbaca sebagai "dibatalkan") walau akun Google ADA: Play Services
        //    perlu update, belum ada kunci layar, akun belum "authorized" untuk
        //    one-tap, dsb.
        val idOption = GetGoogleIdOption.Builder()
            .setServerClientId(webClientId)
            .setFilterByAuthorizedAccounts(filterByAuthorizedAccounts)
            .setAutoSelectEnabled(false)
            .build()

        val hasilOneTap = cobaAmbil(activityContext, idOption, "GetGoogleIdOption")
        // Sukses / gagal-konfigurasi → langsung pakai. TidakAdaAkun & Dibatalkan
        // dari one-tap TIDAK final (sering auto-dismiss, bukan aksi user sadar) —
        // lanjut ke tombol "Sign in with Google" penuh yang selalu menampilkan
        // pemilih akun / "tambah akun".
        if (hasilOneTap is Hasil.Token || hasilOneTap is Hasil.Gagal) return hasilOneTap

        val signInOption = GetSignInWithGoogleOption.Builder(webClientId).build()
        return cobaAmbil(activityContext, signInOption, "GetSignInWithGoogleOption")
    }

    private suspend fun cobaAmbil(
        activityContext: Context,
        option: androidx.credentials.CredentialOption,
        tag: String,
    ): Hasil {
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val response = credentialManager.getCredential(activityContext, request)
            val cred = response.credential
            if (cred is androidx.credentials.CustomCredential &&
                cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                Hasil.Token(GoogleIdTokenCredential.createFrom(cred.data).idToken)
            } else {
                Hasil.Gagal("Kredensial bukan Google ID token (${cred.type})")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "$tag: dibatalkan — ${e.type} ${e.message}")
            Hasil.Dibatalkan
        } catch (e: NoCredentialException) {
            Log.w(TAG, "$tag: tidak ada kredensial — ${e.message}")
            Hasil.TidakAdaAkun
        } catch (e: GetCredentialProviderConfigurationException) {
            Log.e(TAG, "$tag: provider tak terkonfigurasi — ${e.message}", e)
            Hasil.Gagal(
                "Google Play Services tidak tersedia / perlu di-update di perangkat ini.",
            )
        } catch (e: GetCredentialException) {
            // e.type contoh: "android.credentials.GetCredentialException.TYPE_UNKNOWN"
            Log.e(TAG, "$tag: gagal — type=${e.type} msg=${e.message}", e)
            Hasil.Gagal("${ringkasType(e.type)} — ${e.message ?: e.javaClass.simpleName}")
        } catch (e: Exception) {
            Log.e(TAG, "$tag: error tak terduga", e)
            Hasil.Gagal(e.message ?: "Error tak dikenal")
        }
    }

    private fun ringkasType(type: String): String = type.substringAfterLast('.')

    companion object {
        private const val TAG = "GoogleIdToken"
    }
}
