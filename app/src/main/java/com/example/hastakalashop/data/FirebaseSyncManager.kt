package com.example.hastakalashop.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import android.net.Uri

class FirebaseSyncManager {
    companion object {
        var appContext: android.content.Context? = null
    }
    private fun showToast(msg: String) {
        appContext?.let { ctx ->
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(ctx, "Sync Alert: $msg", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val TAG = "FirebaseSyncManager"

    private fun getBaseCollection() = auth.currentUser?.uid?.let { uid ->
        db.collection("users").document(uid)
    }

    private fun getStorageRef() = auth.currentUser?.uid?.let { uid ->
        storage.reference.child("users").child(uid)
    }

    fun syncItem(item: Item) {
        val base = getBaseCollection() ?: return
        base.collection("inventory").document(item.id.toString())
            .set(item, SetOptions.merge())
            .addOnFailureListener { 
                Log.e(TAG, "Failed to sync item: ${it.message}")
                showToast("Item update failed: ${it.message}")
            }
    }

    fun deleteItem(item: Item) {
        val base = getBaseCollection() ?: return
        base.collection("inventory").document(item.id.toString())
            .delete()
            .addOnFailureListener { 
                Log.e(TAG, "Failed to delete item from cloud: ${it.message}") 
                showToast("Item deletion failed")
            }
    }

    fun syncSale(sale: Sale) {
        val base = getBaseCollection() ?: return
        base.collection("sales").document(sale.id.toString())
            .set(sale, SetOptions.merge())
            .addOnFailureListener { 
                Log.e(TAG, "Failed to sync sale: ${it.message}")
                showToast("Sale upload failed")
            }
    }

    fun syncProfile(profileData: Map<String, Any>) {
        val base = getBaseCollection() ?: return
        base.set(profileData, SetOptions.merge())
            .addOnFailureListener { Log.e(TAG, "Failed to sync profile: ${it.message}") }
    }

    fun fetchProfile(onResult: (Map<String, Any>?) -> Unit) {
        val base = getBaseCollection() ?: return
        base.get()
            .addOnSuccessListener { document ->
                onResult(document.data)
            }
            .addOnFailureListener {
                Log.e(TAG, "Failed to fetch profile: ${it.message}")
                showToast("Failed to download profile from cloud")
                onResult(null)
            }
    }

    fun fetchInventory(onResult: (List<Item>) -> Unit) {
        val base = getBaseCollection() ?: return
        base.collection("inventory").get()
            .addOnSuccessListener { snapshot ->
                val items = snapshot.toObjects(Item::class.java)
                onResult(items)
            }
            .addOnFailureListener {
                Log.e(TAG, "Failed to fetch inventory: ${it.message}")
                showToast("Could not download remote inventory")
                onResult(emptyList())
            }
    }

    fun fetchSales(onResult: (List<Sale>) -> Unit) {
        val base = getBaseCollection() ?: return
        base.collection("sales").get()
            .addOnSuccessListener { snapshot ->
                val sales = snapshot.toObjects(Sale::class.java)
                onResult(sales)
            }
            .addOnFailureListener {
                Log.e(TAG, "Failed to fetch sales: ${it.message}")
                showToast("Could not download sales ledger")
                onResult(emptyList())
            }
    }

    fun uploadItemImage(itemId: Int, localUri: Uri, onSuccess: (String) -> Unit) {
        val ref = getStorageRef()?.child("inventory")?.child("$itemId.jpg") ?: return
        
        ref.putFile(localUri)
            .continueWithTask { task ->
                if (!task.isSuccessful) {
                    task.exception?.let { throw it }
                }
                ref.downloadUrl
            }
            .addOnSuccessListener { downloadUri ->
                onSuccess(downloadUri.toString())
            }
            .addOnFailureListener {
                val msg = "Image upload failed: ${it.message}"
                Log.e(TAG, msg)
                showToast(msg)
            }
    }

    /**
     * Uploads all local data to the cloud. 
     * Useful after a fresh login to ensure local work is preserved.
     */
    fun syncAll(items: List<Item>, sales: List<Sale>) {
        val base = getBaseCollection() ?: return
        
        // Use batch to upload efficiently
        val batch = db.batch()
        
        items.forEach { item ->
            val ref = base.collection("inventory").document(item.id.toString())
            batch.set(ref, item, SetOptions.merge())
        }
        
        sales.forEach { sale ->
            val ref = base.collection("sales").document(sale.id.toString())
            batch.set(ref, sale, SetOptions.merge())
        }
        
        batch.commit().addOnFailureListener {
            val msg = "Initial heavy sync failed: ${it.message}"
            Log.e(TAG, msg)
            showToast(msg)
        }
    }
}
