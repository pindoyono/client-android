package com.smkn2malinau.absensi.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.smkn2malinau.absensi.data.local.entity.*
import com.smkn2malinau.absensi.data.local.dao.*
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        SiswaCache::class,
        EmbeddingCache::class,
        JadwalCache::class,
        DispensasiCache::class,
        AbsensiLokal::class,
        JadwalOverrideLokal::class,
        SyncMetadata::class,
        DeviceAuditLog::class,
        LivenessLog::class,
        SyncEventLog::class,
        AkunLokal::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AbsensiDatabase : RoomDatabase() {
    abstract fun siswaDao(): SiswaDao
    abstract fun jadwalDao(): JadwalDao
    abstract fun absensiDao(): AbsensiDao
    abstract fun logDao(): LogDao
    abstract fun akunDao(): AkunDao

    companion object {
        @Volatile
        private var INSTANCE: AbsensiDatabase? = null

        /** v1 → v2: tabel `akun_lokal` untuk auth Panel Admin berbasis role. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Format HARUS sama persis dgn skema yang Room generate untuk AkunLokal
                // (tanpa DEFAULT SQL — Room validasi lewat hash skema).
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `akun_lokal` (" +
                        "`identitas` TEXT NOT NULL, `nama` TEXT NOT NULL, `role` TEXT NOT NULL, " +
                        "`password_hash` TEXT, `salt` TEXT, `siswa_id` INTEGER, " +
                        "`aktif` INTEGER NOT NULL, `diperbarui_pada` TEXT NOT NULL, " +
                        "PRIMARY KEY(`identitas`))"
                )
            }
        }

        /** v2 → v3: kolom `lokasi_mock` di `absensi_lokal` — tandai record dari
         *  lokasi mock (fake GPS). Nullable, TANPA DEFAULT SQL supaya cocok
         *  dengan skema yang Room generate (val lokasi_mock: Int? = null). */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `absensi_lokal` ADD COLUMN `lokasi_mock` INTEGER")
            }
        }

        /** v3 → v4: kolom `harus_ganti_sandi` di `akun_lokal` — paksa admin yang
         *  login dengan password baku (dari BuildConfig) mengganti passwordnya.
         *  Nullable, TANPA DEFAULT SQL (cocok dgn `val harus_ganti_sandi: Int?`). */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `akun_lokal` ADD COLUMN `harus_ganti_sandi` INTEGER")
            }
        }

        /** v4 → v5: `siswa_cache.enroll_mandiri_pending` — siswa daftar wajah
         *  sendiri, menunggu verifikasi admin (absensi ditolak selama itu).
         *  Nullable, TANPA DEFAULT SQL. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `siswa_cache` ADD COLUMN `enroll_mandiri_pending` INTEGER")
            }
        }

        fun getDatabase(context: Context, passphrase: ByteArray? = null): AbsensiDatabase {
            return INSTANCE ?: synchronized(this) {
                val builder = Room.databaseBuilder(
                    context.applicationContext,
                    AbsensiDatabase::class.java,
                    "absensi.db"
                )
                
                if (passphrase != null) {
                    System.loadLibrary("sqlcipher")
                    builder.openHelperFactory(SupportOpenHelperFactory(passphrase))
                }

                builder.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)

                val instance = builder.build()
                INSTANCE = instance
                instance
            }
        }
    }
}
