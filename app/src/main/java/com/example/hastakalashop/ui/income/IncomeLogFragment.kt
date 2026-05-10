package com.example.hastakalashop.ui.income

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.hastakalashop.data.SaleWithItem
import com.example.hastakalashop.databinding.FragmentIncomeLogBinding
import com.example.hastakalashop.databinding.ItemIncomeBinding
import com.example.hastakalashop.viewmodel.IncomeFilter
import com.example.hastakalashop.viewmodel.MainViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class IncomeLogFragment : Fragment() {

    private var _binding: FragmentIncomeLogBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: MainViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentIncomeLogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]

        // Fix #11: Use DiffUtil-backed ListAdapter
        val adapter = IncomeAdapter()
        binding.recyclerViewIncome.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewIncome.adapter = adapter

        binding.chipGroupFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                when (checkedIds.first()) {
                    com.example.hastakalashop.R.id.chip_this_week  -> viewModel.setIncomeFilter(IncomeFilter.THIS_WEEK)
                    com.example.hastakalashop.R.id.chip_this_month -> viewModel.setIncomeFilter(IncomeFilter.THIS_MONTH)
                    com.example.hastakalashop.R.id.chip_all_time   -> viewModel.setIncomeFilter(IncomeFilter.ALL_TIME)
                }
            }
        }

        viewModel.filteredSales.observe(viewLifecycleOwner) { sales ->
            adapter.submitList(sales)
            val total = sales.sumOf { it.totalPrice }
            val textTotalIncome = view.findViewById<android.widget.TextView>(com.example.hastakalashop.R.id.text_total_income)
            textTotalIncome.text = String.format("Total: ₹%.2f", total)
        }

        val btnExport = view.findViewById<Button>(com.example.hastakalashop.R.id.btn_export_csv)
        btnExport.setOnClickListener {
            exportToCsv(adapter.currentList)
        }
    }

    private fun exportToCsv(sales: List<SaleWithItem>) {
        if (sales.isEmpty()) {
            Toast.makeText(requireContext(), "No data to export", Toast.LENGTH_SHORT).show()
            return
        }

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val csvHeader  = "Date,Item Name,Color,Quantity,Total Price\n"

        // Fix #15: Escape double-quotes inside field values to prevent CSV injection
        val csvData = sales.joinToString("\n") { sale ->
            val safeName  = sale.itemName.replace("\"", "\"\"")
            val safeColor = sale.color.replace("\"", "\"\"")
            "${dateFormat.format(Date(sale.timestamp))},\"$safeName\",\"$safeColor\",${sale.quantitySold},${sale.totalPrice}"
        }

        // Fix #8: Overwrite instead of accumulating — always same filename
        val file = File(requireContext().cacheDir, "sales_export.csv")
        file.writeText(csvHeader + csvData)

        val uri    = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "Sales Export")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Export via..."))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// Fix #11: DiffUtil callback for SaleWithItem
class SaleDiffCallback : DiffUtil.ItemCallback<SaleWithItem>() {
    override fun areItemsTheSame(oldItem: SaleWithItem, newItem: SaleWithItem) = oldItem.saleId == newItem.saleId
    override fun areContentsTheSame(oldItem: SaleWithItem, newItem: SaleWithItem) = oldItem == newItem
}

class IncomeAdapter : ListAdapter<SaleWithItem, IncomeAdapter.ViewHolder>(SaleDiffCallback()) {

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemIncomeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), dateFormat)
    }

    class ViewHolder(private val binding: ItemIncomeBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(sale: SaleWithItem, dateFormat: SimpleDateFormat) {
            binding.textSaleItem.text  = "${sale.itemName} (${sale.color})"
            binding.textSaleDate.text  = dateFormat.format(Date(sale.timestamp))
            binding.textSalePrice.text = String.format("₹%.2f", sale.totalPrice)
        }
    }
}
