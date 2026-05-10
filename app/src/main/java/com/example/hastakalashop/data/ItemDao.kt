package com.example.hastakalashop.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ItemDao {
    @Query("SELECT * FROM items")
    fun getAllItems(): LiveData<List<Item>>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun getItemById(id: Int): Item?

    @Query("SELECT * FROM items")
    suspend fun getAllItemsList(): List<Item>

    @Query("SELECT * FROM items WHERE currentStock < :threshold")
    fun getLowStockItems(threshold: Int): LiveData<List<Item>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: Item): Long

    @Query("UPDATE items SET currentStock = currentStock - :quantity WHERE id = :itemId AND currentStock >= :quantity")
    suspend fun deductStock(itemId: Int, quantity: Int): Int

    @Query("UPDATE items SET currentStock = currentStock + :quantity WHERE id = :itemId")
    suspend fun addStock(itemId: Int, quantity: Int)

    @Query("DELETE FROM items")
    suspend fun deleteAllItems()

    @Delete
    suspend fun deleteItem(item: Item)
}
