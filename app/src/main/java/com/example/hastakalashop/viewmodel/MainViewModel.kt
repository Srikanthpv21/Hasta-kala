package com.example.hastakalashop.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.example.hastakalashop.data.InventoryRepository
import com.example.hastakalashop.data.Item
import com.example.hastakalashop.data.ItemSalesAggregation
import com.example.hastakalashop.data.SaleItem
import com.example.hastakalashop.data.SaleWithItem
import kotlinx.coroutines.launch
import java.util.Calendar

class MainViewModel(private val repository: InventoryRepository) : ViewModel() {

    val allItems: LiveData<List<Item>> = repository.allItems
    val aggregatedSales: LiveData<List<ItemSalesAggregation>> = repository.aggregatedSales

    val lowStockItems: LiveData<List<Item>> = repository.getLowStockItems(10) // Threshold is 10

    // Fix #5: Sale result LiveData so the UI knows if a sale succeeded or failed
    private val _saleResult = MutableLiveData<Boolean?>()
    val saleResult: LiveData<Boolean?> = _saleResult

    fun clearSaleResult() {
        _saleResult.value = null
    }

    // For Income Log
    private val _incomeFilter = MutableLiveData<IncomeFilter>(IncomeFilter.ALL_TIME)
    val incomeFilter: LiveData<IncomeFilter> = _incomeFilter

    val filteredSales: LiveData<List<SaleWithItem>> = _incomeFilter.switchMap { filter ->
        when (filter) {
            IncomeFilter.THIS_WEEK -> {
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, -7)
                repository.getSalesSince(cal.timeInMillis)
            }
            IncomeFilter.THIS_MONTH -> {
                val cal = Calendar.getInstance()
                cal.add(Calendar.MONTH, -1)
                repository.getSalesSince(cal.timeInMillis)
            }
            IncomeFilter.ALL_TIME, null -> repository.allSales
        }
    }

    fun setIncomeFilter(filter: IncomeFilter) {
        _incomeFilter.value = filter
    }

    fun recordSale(itemId: Int, quantity: Int, totalPrice: Double) {
        viewModelScope.launch {
            val success = repository.recordSale(itemId, quantity, totalPrice)
            _saleResult.postValue(success)
        }
    }

    /**
     * Fix #6: Records the entire cart atomically. Emits false if ANY item fails stock check.
     */
    fun recordMultiSale(cartItems: List<SaleItem>) {
        viewModelScope.launch {
            val success = repository.recordMultiSale(cartItems)
            _saleResult.postValue(success)
        }
    }

    fun insertItem(item: Item) {
        viewModelScope.launch {
            repository.insertItem(item)
        }
    }

    fun addStock(itemId: Int, quantity: Int) {
        viewModelScope.launch {
            repository.addStock(itemId, quantity)
        }
    }

    fun deleteAllItems() {
        viewModelScope.launch {
            repository.deleteAllItems()
        }
    }

    fun clearAllLocalData() {
        viewModelScope.launch {
            repository.clearAllLocalData()
        }
    }

    fun deleteItem(item: Item) {
        viewModelScope.launch {
            repository.deleteItem(item)
        }
    }
}

enum class IncomeFilter {
    THIS_WEEK, THIS_MONTH, ALL_TIME
}
