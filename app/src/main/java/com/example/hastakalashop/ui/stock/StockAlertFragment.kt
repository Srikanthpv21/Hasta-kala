package com.example.hastakalashop.ui.stock

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.hastakalashop.data.Item
import com.example.hastakalashop.databinding.FragmentStockAlertBinding
import com.example.hastakalashop.databinding.ItemQuickBillBinding
import com.example.hastakalashop.viewmodel.MainViewModel

class StockAlertFragment : Fragment() {

    private var _binding: FragmentStockAlertBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: MainViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStockAlertBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]

        // Fix #11: Use DiffUtil ListAdapter
        val adapter = StockAdapter()
        binding.recyclerViewStock.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewStock.adapter = adapter

        viewModel.lowStockItems.observe(viewLifecycleOwner) { items ->
            adapter.submitList(items)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// Fix #11: DiffUtil callback for Item
class StockItemDiffCallback : DiffUtil.ItemCallback<Item>() {
    override fun areItemsTheSame(oldItem: Item, newItem: Item) = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: Item, newItem: Item) = oldItem == newItem
}

class StockAdapter : ListAdapter<Item, StockAdapter.ViewHolder>(StockItemDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemQuickBillBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(private val binding: ItemQuickBillBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Item) {
            binding.textItemName.text  = item.name
            binding.textItemColor.text = item.color
            binding.textItemPrice.text = String.format("₹%.0f", item.price)

            binding.textItemStock.text = "LOW STOCK: ${item.currentStock}"
            binding.textItemStock.setBackgroundResource(com.example.hastakalashop.R.drawable.bg_stock_badge_out)
            binding.textItemStock.setTextColor(android.graphics.Color.WHITE)

            if (item.imageUri != null) {
                binding.imageItemIcon.setImageURI(android.net.Uri.parse(item.imageUri))
            } else {
                binding.imageItemIcon.setImageResource(com.example.hastakalashop.R.drawable.ic_launcher_foreground)
            }
        }
    }
}
