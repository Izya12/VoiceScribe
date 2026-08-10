package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.ModelDescriptorDao
import com.example.data.local.dao.SpeakerDao
import com.example.data.local.dao.TranscriptSegmentDao
import com.example.data.local.dao.TranscriptionJobDao
import com.example.data.local.entity.ModelDescriptorEntity
import com.example.data.local.entity.SpeakerEntity
import com.example.data.local.entity.TranscriptSegmentEntity
import com.example.data.local.entity.TranscriptionJobEntity

import com.example.data.local.converter.StringListConverter
import androidx.room.TypeConverters

@Database(
    entities = [
        TranscriptionJobEntity::class,
        TranscriptSegmentEntity::class,
        SpeakerEntity::class,
        ModelDescriptorEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(StringListConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transcriptionJobDao(): TranscriptionJobDao
    abstract fun transcriptSegmentDao(): TranscriptSegmentDao
    abstract fun speakerDao(): SpeakerDao
    abstract fun modelDescriptorDao(): ModelDescriptorDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "voicescribe_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
