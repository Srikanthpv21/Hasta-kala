package com.example.hastakalashop.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from version 2 → 3:
 * Adds the 'category' column to the items table with a default empty string.
 * This is a safe, non-destructive migration — no existing data is lost.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE items ADD COLUMN category TEXT NOT NULL DEFAULT ''")
    }
}

@Database(entities = [Item::class, Sale::class], version = 3, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
    abstract fun saleDao(): SaleDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "hastakala_database"
                )
                .addMigrations(MIGRATION_2_3) // Safe migration: adds category column
                .fallbackToDestructiveMigration() // Safety net for unexpected version gaps only
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
