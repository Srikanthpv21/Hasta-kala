package com.example.hastakalashop.ui.inventory

import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import android.widget.AutoCompleteTextView
import android.net.Uri
import android.widget.ImageView
import android.widget.ImageButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.hastakalashop.R
import com.example.hastakalashop.data.Item
import com.example.hastakalashop.databinding.FragmentInventoryBinding
import com.example.hastakalashop.viewmodel.MainViewModel
import com.bumptech.glide.Glide
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import android.util.Log

class InventoryFragment : Fragment() {

    private var _binding: FragmentInventoryBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: MainViewModel
    private var currentPhotoUri: Uri? = null
    private var currentDialogImageView: ImageView? = null
    private var fullItemList = listOf<Item>()
    private var currentQuery = ""
    private var currentSortOrder = SortOrder.NONE

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            currentPhotoUri?.let { uri ->
                currentDialogImageView?.setImageURI(uri)
            }
        } else {
            currentPhotoUri = null
        }
    }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val localCachedUri = copyUriToCache(it)
            currentPhotoUri = localCachedUri
            currentDialogImageView?.setImageURI(localCachedUri)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentInventoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]

        val adapter = InventoryAdapter(
            onAddStockClick = { item -> showAddStockDialog(item) },
            onDeleteClick = { item -> showDeleteDialog(item) }
        )
        
        binding.recyclerViewInventory.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewInventory.adapter = adapter

        viewModel.allItems.observe(viewLifecycleOwner) { items ->
            fullItemList = items
            applyFiltersAndSort(adapter)
            
            // Hide shimmer and show recyclerview
            binding.shimmerViewContainer.stopShimmer()
            binding.shimmerViewContainer.visibility = View.GONE
            binding.recyclerViewInventory.visibility = View.VISIBLE
        }

        // Fix: apply theme-aware text color to SearchView (XML android:textColor is ignored by SearchView)
        val typedValue = TypedValue()
        requireContext().theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
        val onSurfaceColor = typedValue.data
        val searchEditText = binding.searchViewInventory
            .findViewById<EditText>(androidx.appcompat.R.id.search_src_text)
        searchEditText?.setTextColor(onSurfaceColor)
        searchEditText?.setHintTextColor(onSurfaceColor and 0x80FFFFFF.toInt())

        binding.searchViewInventory.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                currentQuery = newText ?: ""
                applyFiltersAndSort(adapter)
                return true
            }
        })

        binding.btnSortInventory.setOnClickListener {
            showSortMenu(adapter)
        }

        binding.fabAddItem.setOnClickListener {
            showCreateItemDialog()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun applyFiltersAndSort(adapter: InventoryAdapter) {
        lifecycleScope.launch {
            val filteredList = withContext(Dispatchers.Default) {
                var list = if (currentQuery.isEmpty()) {
                    fullItemList
                } else {
                    fullItemList.filter { 
                        it.name.contains(currentQuery, ignoreCase = true) || 
                        it.color.contains(currentQuery, ignoreCase = true) 
                    }
                }

                when (currentSortOrder) {
                    SortOrder.PRICE_HIGH -> list.sortedByDescending { it.price }
                    SortOrder.PRICE_LOW -> list.sortedBy { it.price }
                    SortOrder.STOCK_HIGH -> list.sortedByDescending { it.currentStock }
                    SortOrder.STOCK_LOW -> list.sortedBy { it.currentStock }
                    SortOrder.NONE -> list
                }
            }
            adapter.submitList(filteredList)
        }
    }

    private fun showSortMenu(adapter: InventoryAdapter) {
        val options = arrayOf("Price: High to Low", "Price: Low to High", "Stock: High to Low", "Stock: Low to High", "Default")
        AlertDialog.Builder(requireContext())
            .setTitle("Sort By")
            .setItems(options) { _, which ->
                currentSortOrder = when (which) {
                    0 -> SortOrder.PRICE_HIGH
                    1 -> SortOrder.PRICE_LOW
                    2 -> SortOrder.STOCK_HIGH
                    3 -> SortOrder.STOCK_LOW
                    else -> SortOrder.NONE
                }
                applyFiltersAndSort(adapter)
            }
            .show()
    }

    enum class SortOrder {
        NONE, PRICE_HIGH, PRICE_LOW, STOCK_HIGH, STOCK_LOW
    }

    private fun showCreateItemDialog() {
        currentPhotoUri = null
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_item, null)
        val editName     = dialogView.findViewById<EditText>(R.id.edit_item_name)
        val editCategory = dialogView.findViewById<AutoCompleteTextView>(R.id.edit_item_category)
        val editColor    = dialogView.findViewById<EditText>(R.id.edit_item_color)
        val editPrice    = dialogView.findViewById<EditText>(R.id.edit_item_price)
        val editStock    = dialogView.findViewById<EditText>(R.id.edit_item_stock)
        val imagePreview = dialogView.findViewById<ImageView>(R.id.image_item_preview)
        val btnTakePhoto = dialogView.findViewById<Button>(R.id.btn_take_photo)
        val btnPickGallery = dialogView.findViewById<Button>(R.id.btn_pick_gallery)

        // Set up categories adapter
        val categories = fullItemList.map { it.category }.distinct().filter { it.isNotEmpty() }
        val categoryAdapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, categories)
        editCategory.setAdapter(categoryAdapter)

        btnTakePhoto.setOnClickListener {
            val photoFile = File(requireContext().cacheDir, "item_photo_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", photoFile)
            currentPhotoUri = uri
            currentDialogImageView = imagePreview
            takePictureLauncher.launch(uri)
        }

        btnPickGallery.setOnClickListener {
            currentDialogImageView = imagePreview
            pickImageLauncher.launch("image/*")
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Create New Item")
            .setView(dialogView)
            .setPositiveButton("Create") { _, _ ->
                val name = editName.text.toString().trim()
                val category = editCategory.text.toString().trim()
                val color = editColor.text.toString().trim()
                val priceStr = editPrice.text.toString().trim()
                val stockStr = editStock.text.toString().trim()

                val price = priceStr.toDoubleOrNull()
                val stock = stockStr.toIntOrNull()

                if (name.isNotEmpty() && color.isNotEmpty() && category.isNotEmpty()) {
                    if (price != null && stock != null && price >= 0 && stock >= 0) {
                        val newItem = Item(
                            name = name, 
                            category = category,
                            color = color, 
                            price = price, 
                            initialStock = stock, 
                            currentStock = stock, 
                            imageUri = currentPhotoUri?.toString()
                        )
                        viewModel.insertItem(newItem)
                        Toast.makeText(requireContext(), "Item created!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "Price and Stock must be 0 or more", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "Name and Color required", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddStockDialog(item: Item) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_manage_stock, null)
        val editQty = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edit_stock_quantity)
        val btnReduce = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_reduce_stock)
        val btnAdd = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_add_stock)

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Manage Stock: ${item.name}")
            .setView(dialogView)
            .create()

        btnReduce.setOnClickListener {
            val qty = editQty.text.toString().toIntOrNull()
            if (qty != null && qty > 0) {
                if (item.currentStock >= qty) {
                    viewModel.addStock(item.id, -qty)
                    Toast.makeText(requireContext(), "Stock reduced!", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                } else {
                    Toast.makeText(requireContext(), "Not enough stock to reduce!", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), "Please enter a valid quantity", Toast.LENGTH_SHORT).show()
            }
        }

        btnAdd.setOnClickListener {
            val qty = editQty.text.toString().toIntOrNull()
            if (qty != null && qty > 0) {
                viewModel.addStock(item.id, qty)
                Toast.makeText(requireContext(), "Stock added!", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            } else {
                Toast.makeText(requireContext(), "Please enter a valid quantity", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun showDeleteDialog(item: Item) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete Product?")
            .setMessage("Are you sure you want to delete '${item.name}'? This will also remove its entire sales history and cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteItem(item)
                Toast.makeText(requireContext(), "${item.name} Deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    private fun copyUriToCache(uri: Uri): Uri? {
        return try {
            val inputStream = requireContext().contentResolver.openInputStream(uri) ?: return null
            val photoFile = File(requireContext().cacheDir, "cached_upload_${System.currentTimeMillis()}.jpg")
            val outputStream = FileOutputStream(photoFile)
            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.close()
            FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", photoFile)
        } catch (e: Exception) {
            Log.e("InventoryFragment", "Failed to cache selected image: ${e.message}")
            null
        }
    }
}

// Fix #11: DiffUtil-backed ListAdapter — only changed items are rebound
class InventoryDiffCallback : DiffUtil.ItemCallback<Item>() {
    override fun areItemsTheSame(oldItem: Item, newItem: Item) = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: Item, newItem: Item) = oldItem == newItem
}

class InventoryAdapter(
    private val onAddStockClick: (Item) -> Unit,
    private val onDeleteClick: (Item) -> Unit
) : ListAdapter<Item, InventoryAdapter.ViewHolder>(InventoryDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_inventory, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onAddStockClick, onDeleteClick)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val textName:      TextView   = view.findViewById(R.id.text_item_name)
        private val textDetails:   TextView   = view.findViewById(R.id.text_item_details)
        private val btnAddStock:   Button     = view.findViewById(R.id.button_add_stock)
        private val imageThumbnail:ImageView  = view.findViewById(R.id.image_item_thumbnail)
        private val btnDelete:     ImageButton = view.findViewById(R.id.button_delete_item)

        fun bind(item: Item, onAddStockClick: (Item) -> Unit, onDeleteClick: (Item) -> Unit) {
            textName.text    = item.name
            textDetails.text = "ID: ${item.id} | Color: ${item.color} | Price: ₹${item.price} | Stock: ${item.currentStock}"
            if (item.imageUri != null) {
                Glide.with(itemView.context)
                    .load(Uri.parse(item.imageUri))
                    .placeholder(R.drawable.ic_launcher_foreground)
                    .into(imageThumbnail)
            } else {
                imageThumbnail.setImageResource(R.drawable.ic_launcher_foreground)
            }
            btnAddStock.setOnClickListener { onAddStockClick(item) }
            btnDelete.setOnClickListener   { onDeleteClick(item) }
        }
    }
}
