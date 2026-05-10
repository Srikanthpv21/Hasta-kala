package com.example.hastakalashop.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

data class ItemSalesAggregation(
    val itemName: String,
    val color: String,
    val totalSold: Int
)

data class SaleWithItem(
    val saleId: Int,
    val itemName: String,
    val color: String,
    val timestamp: Long,
    val quantitySold: Int,
    val totalPrice: Double
)

@Dao
interface SaleDao {
    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertSale(sale: Sale): Long

    @Query("SELECT * FROM sales")
    suspend fun getAllSalesList(): List<Sale>

    @Query("SELECT s.id as saleId, i.name as itemName, i.color as color, s.timestamp, s.quantitySold, s.totalPrice FROM sales s INNER JOIN items i ON s.itemId = i.id ORDER BY s.timestamp DESC")
    fun getAllSales(): LiveData<List<SaleWithItem>>

    @Query("SELECT s.id as saleId, i.name as itemName, i.color as color, s.timestamp, s.quantitySold, s.totalPrice FROM sales s INNER JOIN items i ON s.itemId = i.id WHERE s.timestamp >= :startTime ORDER BY s.timestamp DESC")
    fun getSalesSince(startTime: Long): LiveData<List<SaleWithItem>>

    @Query("SELECT i.name as itemName, i.color as color, SUM(s.quantitySold) as totalSold FROM sales s INNER JOIN items i ON s.itemId = i.id GROUP BY s.itemId ORDER BY totalSold DESC")
    fun getAggregatedSales(): LiveData<List<ItemSalesAggregation>>

    @androidx.room.Delete
    suspend fun deleteSale(sale: Sale)

    @Query("DELETE FROM sales")
    suspend fun deleteAllSales()
}
