package com.example.hastakalashop.data

/**
 * Represents a single cart entry to be recorded as a sale.
 * Used for the atomic multi-item checkout transaction.
 */
data class SaleItem(
    val itemId: Int,
    val quantity: Int,
    val totalPrice: Double
)
