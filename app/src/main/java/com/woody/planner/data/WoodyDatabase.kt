package com.woody.planner.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CategoryEntity::class,
        TaskEntity::class,
        CompletionEntity::class,
        SkipEntity::class,
        SubtaskEntity::class,
        SubtaskCheckEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class WoodyDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        fun create(context: Context): WoodyDatabase =
            Room.databaseBuilder(context, WoodyDatabase::class.java, "woody.db")
                .addCallback(SeedCallback)
                .build()
    }
}

/** Стартовые категории при первом создании базы. */
private object SeedCallback : RoomDatabase.Callback() {
    private val defaults = listOf(
        Triple("Спорт", 0xFF2F80ED.toInt(), "🏋️"),
        Triple("Покупки", 0xFFF2994A.toInt(), "🛒"),
        Triple("Дом", 0xFF27AE60.toInt(), "🏠"),
        Triple("Работа", 0xFF7B61FF.toInt(), "💼"),
        Triple("Здоровье", 0xFFE5484D.toInt(), "💊"),
    )

    override fun onCreate(db: SupportSQLiteDatabase) {
        defaults.forEachIndexed { index, (name, color, emoji) ->
            db.insert(
                "categories",
                SQLiteDatabase.CONFLICT_IGNORE,
                ContentValues().apply {
                    put("name", name)
                    put("colorArgb", color)
                    put("emoji", emoji)
                    put("sortOrder", index)
                },
            )
        }
    }
}
