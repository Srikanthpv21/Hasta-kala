package com.example.hastakalashop.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@androidx.annotation.Keep
@Entity(tableName = "sales")
data class Sale(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val itemId: Int = 0,
    val timestamp: Long = 0L,
    val quantitySold: Int = 0,
    val totalPrice: Double = 0.0
)
