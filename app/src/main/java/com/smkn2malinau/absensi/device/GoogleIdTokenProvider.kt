package com.smkn2malinau.absensi.device

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
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

        val option = GetGoogleIdOption.Builder()
            .setServerClientId(webClientId)
            .setFilterByAuthorizedAccounts(filterByAuthorizedAccounts)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

        return try {
            val response = credentialManager.getCredential(activityContext, request)
            val cred = response.credential
            if (cred is androidx.credentials.CustomCredential &&
                cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleCred = GoogleIdTokenCredential.createFrom(cred.data)
                Hasil.Token(googleCred.idToken)
            } else {
                Hasil.Gagal("Kredensial bukan Google ID token")
            }
        } catch (e: GetCredentialCancellationException) {
            Hasil.Dibatalkan
        } catch (e: NoCredentialException) {
            Hasil.TidakAdaAkun
        } catch (e: GetCredentialException) {
            Hasil.Gagal(e.message ?: e.javaClass.simpleName)
        } catch (e: Exception) {
            Hasil.Gagal(e.message ?: "Error tak dikenal")
        }
    }
}
