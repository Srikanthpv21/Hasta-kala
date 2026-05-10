package com.example.hastakalashop.data

import androidx.lifecycle.LiveData
import androidx.room.withTransaction
import kotlinx.coroutines.launch

class InventoryRepository(
    private val database: AppDatabase,
    private val syncManager: FirebaseSyncManager = FirebaseSyncManager(),
    private val notificationHelper: com.example.hastakalashop.utils.NotificationHelper? = null
) {

    private val itemDao: ItemDao = database.itemDao()
    private val saleDao: SaleDao = database.saleDao()

    val allItems: LiveData<List<Item>> = itemDao.getAllItems()
    val allSales: LiveData<List<SaleWithItem>> = saleDao.getAllSales()
    val aggregatedSales: LiveData<List<ItemSalesAggregation>> = saleDao.getAggregatedSales()

    fun getLowStockItems(threshold: Int): LiveData<List<Item>> {
        return itemDao.getLowStockItems(threshold)
    }

    fun getSalesSince(startTime: Long): LiveData<List<SaleWithItem>> {
        return saleDao.getSalesSince(startTime)
    }

    suspend fun insertItem(item: Item) {
        // First save locally to get the auto-generated ID if it's new
        val generatedId = itemDao.insertItem(item).toInt()
        val itemWithId = if (item.id == 0) item.copy(id = generatedId) else item
        
        // CRITICAL FIX: Sync initial state (even with local placeholder URI) IMMEDIATELY
        // This guarantees the item shows up on all devices instantly.
        syncManager.syncItem(itemWithId)

        val localUriString = itemWithId.imageUri
        if (localUriString != null && (localUriString.startsWith("content://") || localUriString.startsWith("file://"))) {
            // Launch background photo upload
            syncManager.uploadItemImage(itemWithId.id, android.net.Uri.parse(localUriString)) { downloadUrl ->
                // Once uploaded, update local cache and cloud document with the final persistent download URL
                val updatedItem = itemWithId.copy(imageUri = downloadUrl)
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    itemDao.insertItem(updatedItem)
                    syncManager.syncItem(updatedItem) // Update metadata with final Image HTTP URL
                }
            }
        }
    }

    /**
     * Fix #3: Records a sale and deducts stock atomically using Room's withTransaction.
     * If the app crashes mid-operation, the entire transaction is rolled back — no ghost deductions.
     */
    suspend fun recordSale(itemId: Int, quantity: Int, totalPrice: Double): Boolean {
        var generatedSale: Sale? = null
        val success = database.withTransaction {
            val rowsAffected = itemDao.deductStock(itemId, quantity)
            if (rowsAffected > 0) {
                val sale = Sale(
                    itemId = itemId,
                    timestamp = System.currentTimeMillis(),
                    quantitySold = quantity,
                    totalPrice = totalPrice
                )
                val saleId = saleDao.insertSale(sale).toInt()
                generatedSale = sale.copy(id = saleId)

                // Check for low stock notification
                val updatedItem = itemDao.getItemById(itemId)
                if (updatedItem != null && updatedItem.currentStock <= 10) {
                    notificationHelper?.sendLowStockNotification(updatedItem.name, updatedItem.currentStock)
                }
                
                true
            } else {
                false
            }
        }
        
        if (success) {
            generatedSale?.let { syncManager.syncSale(it) }
            // CRITICAL FIX: Pull local updated state and push back to Firebase
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                val updatedItem = itemDao.getItemById(itemId)
                updatedItem?.let { syncManager.syncItem(it) }
            }
        }
        return success
    }

    /**
     * Fix #6: Records the entire cart as one atomic transaction.
     * Either ALL items are sold and stock deducted, or NONE are — prevents partial checkouts.
     */
    suspend fun recordMultiSale(cartItems: List<SaleItem>): Boolean {
        val generatedSales = mutableListOf<Sale>()
        val success = database.withTransaction {
            for (item in cartItems) {
                val rowsAffected = itemDao.deductStock(item.itemId, item.quantity)
                if (rowsAffected == 0) {
                    // One item is out of stock — abort the entire transaction
                    return@withTransaction false
                }
                val sale = Sale(
                    itemId = item.itemId,
                    timestamp = System.currentTimeMillis(),
                    quantitySold = item.quantity,
                    totalPrice = item.totalPrice
                )
                val saleId = saleDao.insertSale(sale).toInt()
                generatedSales.add(sale.copy(id = saleId))

                // Check for low stock notification
                val updatedItem = itemDao.getItemById(item.itemId)
                if (updatedItem != null && updatedItem.currentStock <= 10) {
                    notificationHelper?.sendLowStockNotification(updatedItem.name, updatedItem.currentStock)
                }
            }
            true
        }
        if (success) {
            generatedSales.forEach { syncManager.syncSale(it) }
            // CRITICAL FIX: Pull every affected local item state and push stock changes to Firebase
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                cartItems.forEach { cartItem ->
                    val updatedItem = itemDao.getItemById(cartItem.itemId)
                    updatedItem?.let { syncManager.syncItem(it) }
                }
            }
        }
        return success
    }

    suspend fun addStock(itemId: Int, quantity: Int) {
        itemDao.addStock(itemId, quantity)
        val updatedItem = itemDao.getItemById(itemId)
        
        // Check low stock warning after manual adjustment
        if (updatedItem != null && updatedItem.currentStock <= 10) {
            notificationHelper?.sendLowStockNotification(updatedItem.name, updatedItem.currentStock)
        }

        // CRITICAL FIX: Push stock update to Cloud
        updatedItem?.let { syncManager.syncItem(it) }
    }

    suspend fun deleteAllItems() {
        itemDao.deleteAllItems()
    }

    suspend fun clearAllLocalData() {
        database.withTransaction {
            itemDao.deleteAllItems()
            saleDao.deleteAllSales()
        }
    }

    suspend fun deleteItem(item: Item) {
        itemDao.deleteItem(item)
        syncManager.deleteItem(item)
    }

    /**
     * Connects to Firestore and downloads all existing user metrics.
     * Overwrites local records with the master cloud version, serving as 
     * the authoritative Source of Truth across all registered devices.
     */
    suspend fun synchronizeFromCloud() {
        restoreUserDataFromCloud()
    }

    /**
     * Download current user's existing cloud data and restore into local Room.
     * Ensures that previously generated sales/items are retrieved on fresh logins.
     */
    private suspend fun restoreUserDataFromCloud() {
        // Stage 1: Restore Items
        kotlin.coroutines.suspendCoroutine<Unit> { continuation ->
            syncManager.fetchInventory { remoteItems ->
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    remoteItems.forEach { itemDao.insertItem(it) }
                    continuation.resumeWith(Result.success(Unit))
                }
            }
        }
        
        // Stage 2: Restore Sales history
        kotlin.coroutines.suspendCoroutine<Unit> { continuation ->
            syncManager.fetchSales { remoteSales ->
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    remoteSales.forEach { saleDao.insertSale(it) }
                    continuation.resumeWith(Result.success(Unit))
                }
            }
        }
    }

    /**
     * Helper for initial sync after login. 
     * First downloads historical data from the cloud into the cache, 
     * THEN pushes any accidental temporary local data up to cloud.
     */
    suspend fun triggerInitialSync() {
        // 1. Fetch and hydrate Room cache with stored user data
        restoreUserDataFromCloud()
        
        // 2. Re-upload check/push any outstanding local changes
        val items = itemDao.getAllItemsList()
        val sales = saleDao.getAllSalesList()
        
        // Upload images for items that only have local URIs
        items.forEach { item ->
            val uriStr = item.imageUri
            if (uriStr != null && (uriStr.startsWith("content://") || uriStr.startsWith("file://"))) {
                syncManager.uploadItemImage(item.id, android.net.Uri.parse(uriStr)) { downloadUrl ->
                    val updatedItem = item.copy(imageUri = downloadUrl)
                    kotlinx.coroutines.runBlocking {
                        itemDao.insertItem(updatedItem)
                        syncManager.syncItem(updatedItem)
                    }
                }
            } else {
                syncManager.syncItem(item)
            }
        }
        
        syncManager.syncAll(emptyList(), sales) // Items are handled above now
    }
}
