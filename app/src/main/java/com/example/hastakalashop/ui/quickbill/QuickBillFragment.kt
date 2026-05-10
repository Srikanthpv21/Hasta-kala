package com.example.hastakalashop.ui.quickbill

import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.hastakalashop.data.Item
import com.example.hastakalashop.data.SaleItem
import com.example.hastakalashop.databinding.FragmentQuickBillBinding
import com.example.hastakalashop.databinding.ItemQuickBillBinding
import com.example.hastakalashop.viewmodel.MainViewModel
import android.content.Intent
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide

class QuickBillFragment : Fragment() {

    private var _binding: FragmentQuickBillBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: MainViewModel

    // Cart: itemId -> CartEntry
    private val cart = mutableMapOf<Int, CartEntry>()

    data class CartEntry(val item: Item, var quantity: Int, var price: Double)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentQuickBillBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]

        val adapter = ItemAdapter { item -> showAddToCartDialog(item) }

        binding.recyclerViewItems.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.recyclerViewItems.adapter = adapter

        viewModel.allItems.observe(viewLifecycleOwner) { items ->
            adapter.setOriginalList(items)
        }

        // Fix #5: Observe sale result and show feedback to the user
        viewModel.saleResult.observe(viewLifecycleOwner) { success ->
            if (success == null) return@observe
            if (success) {
                Toast.makeText(requireContext(), "All sales recorded successfully!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Sale failed — one or more items may be out of stock.", Toast.LENGTH_LONG).show()
            }
            viewModel.clearSaleResult()
        }

        // Fix: apply theme-aware text color to SearchView (XML android:textColor is ignored by SearchView)
        val typedValue = TypedValue()
        requireContext().theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
        val onSurfaceColor = typedValue.data
        val searchEditText = binding.searchView
            .findViewById<EditText>(androidx.appcompat.R.id.search_src_text)
        searchEditText?.setTextColor(onSurfaceColor)
        searchEditText?.setHintTextColor(onSurfaceColor and 0x80FFFFFF.toInt())

        binding.searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                adapter.filter(newText, getSelectedCategory())
                return true
            }
        })

        binding.chipGroupCategories.setOnCheckedStateChangeListener { _, _ ->
            adapter.filter(binding.searchView.query.toString(), getSelectedCategory())
        }

        binding.btnCheckout.setOnClickListener {
            showCheckoutSummary()
        }
    }

    private fun updateCartUI() {
        if (cart.isEmpty()) {
            binding.cardCheckout.visibility = View.GONE
        } else {
            binding.cardCheckout.visibility = View.VISIBLE
            val totalItems = cart.values.sumOf { it.quantity }
            val totalPrice = cart.values.sumOf { it.price }
            binding.textCartCount.text = "$totalItems Item(s) Selected"
            binding.textCartTotal.text = String.format("₹%.2f", totalPrice)
        }
    }

    private fun showAddToCartDialog(item: Item) {
        val currentInCart   = cart[item.id]?.quantity ?: 0
        val remainingStock  = item.currentStock - currentInCart

        if (remainingStock <= 0) {
            Toast.makeText(requireContext(), "No more stock available!", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView   = layoutInflater.inflate(com.example.hastakalashop.R.layout.dialog_custom_sale, null)
        val textName     = dialogView.findViewById<android.widget.TextView>(com.example.hastakalashop.R.id.text_sale_item_name)
        val textQty      = dialogView.findViewById<android.widget.TextView>(com.example.hastakalashop.R.id.text_quantity)
        val btnMinus     = dialogView.findViewById<android.widget.Button>(com.example.hastakalashop.R.id.button_minus)
        val btnPlus      = dialogView.findViewById<android.widget.Button>(com.example.hastakalashop.R.id.button_plus)
        val editTotalPrice = dialogView.findViewById<android.widget.EditText>(com.example.hastakalashop.R.id.edit_total_price)

        textName.text = "Add ${item.name} to Cart"

        var quantity     = 1
        val pricePerItem = item.price

        fun updatePrice() {
            textQty.text = quantity.toString()
            editTotalPrice.setText((quantity * pricePerItem).toString())
        }

        updatePrice()

        btnMinus.setOnClickListener {
            if (quantity > 1) { quantity--; updatePrice() }
        }

        btnPlus.setOnClickListener {
            if (quantity < remainingStock) {
                quantity++
                updatePrice()
            } else {
                Toast.makeText(requireContext(), "Only $remainingStock left in stock!", Toast.LENGTH_SHORT).show()
            }
        }

        AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Add to Cart") { _, _ ->
                val priceInput = editTotalPrice.text.toString().trim()
                val finalPrice = priceInput.toDoubleOrNull() ?: 0.0

                if (finalPrice <= 0) {
                    Toast.makeText(requireContext(), "Invalid price! Please enter a valid amount.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val existing = cart[item.id]
                if (existing != null) {
                    existing.quantity += quantity
                    existing.price    += finalPrice
                } else {
                    cart[item.id] = CartEntry(item, quantity, finalPrice)
                }

                updateCartUI()
                Toast.makeText(requireContext(), "Added to Cart!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCheckoutSummary() {
        // Fix #7: Re-validate stock freshness at checkout time
        val latestItems = viewModel.allItems.value ?: emptyList()
        val stockMap    = latestItems.associateBy { it.id }
        val staleItems  = cart.values.filter { entry ->
            val fresh = stockMap[entry.item.id]
            fresh == null || fresh.currentStock < entry.quantity
        }
        if (staleItems.isNotEmpty()) {
            val names = staleItems.joinToString(", ") { it.item.name }
            Toast.makeText(requireContext(), "Stock changed for: $names. Please review your cart.", Toast.LENGTH_LONG).show()
            return
        }

        val summary = StringBuilder()
        var total   = 0.0
        cart.values.forEach { entry ->
            summary.append("${entry.item.name} x${entry.quantity}: ₹${entry.price}\n")
            total += entry.price
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Confirm Transaction")
            .setMessage(summary.toString() + "\nTotal: ₹${String.format("%.2f", total)}")
            .setPositiveButton("Complete Sale") { _, _ ->
                // Fix #6: Convert cart to SaleItems and send as one atomic transaction
                val saleItems = cart.values.map { entry ->
                    SaleItem(entry.item.id, entry.quantity, entry.price)
                }
                viewModel.recordMultiSale(saleItems)
                generateAndShareMultiItemReceipt(cart.values.toList())
                cart.clear()
                updateCartUI()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun getSelectedCategory(): String {
        return when (binding.chipGroupCategories.checkedChipId) {
            com.example.hastakalashop.R.id.chip_bags      -> "Bags"
            com.example.hastakalashop.R.id.chip_keychains -> "Keychains"
            com.example.hastakalashop.R.id.chip_mats      -> "Mats"
            else -> "All"
        }
    }

    private fun generateAndShareMultiItemReceipt(items: List<CartEntry>) {
        val pdfFile = com.example.hastakalashop.utils.PdfHelper.generateMultiItemReceipt(requireContext(), items)
        if (pdfFile != null) {
            val uri    = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", pdfFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "Here is your professional digital receipt from Hasta-Kala Shop!")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share PDF Receipt"))
        } else {
            Toast.makeText(requireContext(), "Failed to generate PDF", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// Fix #11: DiffUtil — only rebinds items that actually changed
class ItemDiffCallback : DiffUtil.ItemCallback<Item>() {
    override fun areItemsTheSame(oldItem: Item, newItem: Item) = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: Item, newItem: Item) = oldItem == newItem
}

class ItemAdapter(private val onClick: (Item) -> Unit) : ListAdapter<Item, ItemAdapter.ViewHolder>(ItemDiffCallback()) {

    private var originalItems = listOf<Item>()

    fun setOriginalList(newItems: List<Item>) {
        originalItems = newItems
        filter("", "All")
    }

    fun filter(query: String?, category: String) {
        val filtered = originalItems.filter { item ->
            val matchesQuery    = query.isNullOrBlank() || item.name.contains(query, ignoreCase = true)
            // Fix #10: Filter by the data-backed category field, not name-heuristics
            val matchesCategory = category == "All" || item.category.equals(category, ignoreCase = true)
            matchesQuery && matchesCategory
        }
        submitList(filtered)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemQuickBillBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
        holder.itemView.setOnClickListener { onClick(item) }
    }

    class ViewHolder(private val binding: ItemQuickBillBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Item) {
            binding.textItemName.text  = item.name
            binding.textItemColor.text = item.color
            binding.textItemPrice.text = String.format("₹%.0f", item.price)

            if (item.currentStock <= 0) {
                binding.textItemStock.text = "OUT OF STOCK"
                binding.textItemStock.setBackgroundResource(com.example.hastakalashop.R.drawable.bg_stock_badge_out)
            } else {
                binding.textItemStock.text = "STOCK: ${item.currentStock}"
                binding.textItemStock.setBackgroundResource(com.example.hastakalashop.R.drawable.bg_stock_badge)
            }

            if (item.imageUri != null) {
                Glide.with(itemView.context)
                    .load(android.net.Uri.parse(item.imageUri))
                    .placeholder(com.example.hastakalashop.R.drawable.ic_launcher_foreground)
                    .into(binding.imageItemIcon)
            } else {
                binding.imageItemIcon.setImageResource(com.example.hastakalashop.R.drawable.ic_launcher_foreground)
            }
        }
    }
}
