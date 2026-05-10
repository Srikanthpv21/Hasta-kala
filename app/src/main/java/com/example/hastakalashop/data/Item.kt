package com.example.hastakalashop.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "items")
data class Item(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String = "",
    val color: String = "",
    val price: Double = 0.0,
    val initialStock: Int = 0,
    val currentStock: Int = 0,
    val imageUri: String? = null,
    val category: String = "" // e.g. "Bags", "Keychains", "Mats"
)
