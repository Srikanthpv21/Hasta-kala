package com.example.hastakalashop

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.hastakalashop.data.InventoryDao
import com.example.hastakalashop.data.InventoryDatabase
import com.example.hastakalashop.data.Item
import com.example.hastakalashop.data.Sale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class InventoryDatabaseTest {

    private lateinit var inventoryDao: InventoryDao
    private lateinit var db: InventoryDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, InventoryDatabase::class.java).build()
        inventoryDao = db.inventoryDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    @Throws(Exception::class)
    fun insertAndReadItem() = runBlocking {
        val item = Item(name = "Test Bag", color = "Blue", price = 100.0, currentStock = 10, category = "Bags")
        inventoryDao.insertItem(item)
        val allItems = inventoryDao.getAllItems().first()
        assertEquals(allItems[0].name, "Test Bag")
    }

    @Test
    @Throws(Exception::class)
    fun recordSaleUpdatesStock() = runBlocking {
        val item = Item(id = 1, name = "Test Mat", color = "Natural", price = 500.0, currentStock = 20, category = "Mats")
        inventoryDao.insertItem(item)
        
        val sale = Sale(itemId = 1, quantity = 5, totalPrice = 2500.0, timestamp = System.currentTimeMillis())
        inventoryDao.insertSale(sale)
        inventoryDao.updateStock(1, 5) // Subtracting 5

        val updatedItem = inventoryDao.getAllItems().first()[0]
        assertEquals(15, updatedItem.currentStock)
    }

    @Test
    @Throws(Exception::class)
    fun getLowStockItems() = runBlocking {
        val item1 = Item(name = "Low Item", color = "Red", price = 10.0, currentStock = 3, category = "Test")
        val item2 = Item(name = "High Item", color = "Green", price = 10.0, currentStock = 50, category = "Test")
        inventoryDao.insertItem(item1)
        inventoryDao.insertItem(item2)

        val lowStock = inventoryDao.getLowStockItems(10).first()
        assertEquals(1, lowStock.size)
        assertEquals("Low Item", lowStock[0].name)
    }
}
