package com.example.disasterlink.data

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter fun roleToString(value: UserRole?) = value?.name
    @TypeConverter fun stringToRole(value: String?) = value?.let { runCatching { UserRole.valueOf(it) }.getOrNull() } ?: UserRole.CITIZEN
    @TypeConverter fun authorityToString(value: AuthorityType?) = value?.name
    @TypeConverter fun stringToAuthority(value: String?) = value?.let { runCatching { AuthorityType.valueOf(it) }.getOrNull() }
    @TypeConverter fun emergencyToString(value: EmergencyStatus) = value.name
    @TypeConverter fun stringToEmergency(value: String?) = value?.let { runCatching { EmergencyStatus.valueOf(it) }.getOrNull() } ?: EmergencyStatus.REPORTED
    @TypeConverter fun taskToString(value: TaskStatus) = value.name
    @TypeConverter fun stringToTask(value: String?) = value?.let { runCatching { TaskStatus.valueOf(it) }.getOrNull() } ?: TaskStatus.PENDING
}

@Database(
    entities = [UserEntity::class, EmergencyEntity::class, RescueTeamEntity::class, TaskEntity::class, FeedbackEntity::class],
    version = 6,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE emergencies ADD COLUMN reporterName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE emergencies ADD COLUMN reporterPhone TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE emergencies ADD COLUMN reporterGovernmentId TEXT NOT NULL DEFAULT ''")
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN clientId TEXT")
            }
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE emergencies ADD COLUMN clientId TEXT")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_emergencies_client_id ON emergencies(clientId) WHERE clientId IS NOT NULL")
            }
        }
    }
    abstract fun dao(): AppDao
}
