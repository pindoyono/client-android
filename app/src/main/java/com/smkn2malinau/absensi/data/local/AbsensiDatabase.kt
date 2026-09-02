package com.smkn2malinau.absensi.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
        SyncEventLog::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AbsensiDatabase : RoomDatabase() {
    abstract fun siswaDao(): SiswaDao
    abstract fun jadwalDao(): JadwalDao
    abstract fun absensiDao(): AbsensiDao
    abstract fun logDao(): LogDao

    companion object {
        @Volatile
        private var INSTANCE: AbsensiDatabase? = null

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

                val instance = builder.build()
                INSTANCE = instance
                instance
            }
        }
    }
}
