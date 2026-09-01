package com.smkn2malinau.absensi.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.smkn2malinau.absensi.data.local.dao.*
import com.smkn2malinau.absensi.data.local.entity.*
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        SiswaCacheEntity::class,
        EmbeddingCacheEntity::class,
        JadwalCacheEntity::class,
        DispensasiCacheEntity::class,
        AbsensiLokalEntity::class,
        JadwalOverrideLokalEntity::class,
        SyncMetadataEntity::class,
        DeviceAuditLogEntity::class,
        LivenessLogEntity::class,
        SyncEventLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AbsensiDatabase : RoomDatabase() {
    abstract fun absensiDao(): AbsensiDao
    abstract fun siswaDao(): SiswaDao
    abstract fun jadwalDao(): JadwalDao
    abstract fun dispensasiDao(): DispensasiDao
    abstract fun logDao(): LogDao

    companion object {
        @Volatile
        private var INSTANCE: AbsensiDatabase? = null

        fun getDatabase(context: Context, passphrase: ByteArray): AbsensiDatabase {
            return INSTANCE ?: synchronized(this) {
                val factory = SupportOpenHelperFactory(passphrase)
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AbsensiDatabase::class.java,
                    "absensi_db"
                )
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration() // Sesuai PRD: Room migration pertama jalan bersih
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
