package com.example.hastakalashop.ui.dashboard

import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.hastakalashop.R
import com.example.hastakalashop.data.Item
import com.example.hastakalashop.data.ItemSalesAggregation
import com.example.hastakalashop.data.SaleWithItem
import com.example.hastakalashop.databinding.FragmentDashboardBinding
import com.example.hastakalashop.viewmodel.MainViewModel
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.utils.ColorTemplate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.bumptech.glide.Glide

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: MainViewModel
    
    private var isLineChartFirstLoad = true
    private var isPieChartFirstLoad = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]

        // Fix #11: All adapters use DiffUtil-backed ListAdapter
        val recyclerViewRecent = view.findViewById<RecyclerView>(R.id.recycler_view_recent_sales)
        val recentAdapter      = RecentSalesAdapter()
        recyclerViewRecent.layoutManager = LinearLayoutManager(requireContext())
        recyclerViewRecent.adapter = recentAdapter

        val recyclerViewAlerts = view.findViewById<RecyclerView>(R.id.recycler_view_alerts)
        val cardAlerts         = view.findViewById<androidx.cardview.widget.CardView>(R.id.card_alerts)
        val alertAdapter       = LowStockAdapter()
        recyclerViewAlerts.layoutManager = LinearLayoutManager(requireContext())
        recyclerViewAlerts.adapter = alertAdapter

        val pieChart  = view.findViewById<PieChart>(R.id.pie_chart)
        val lineChart = view.findViewById<LineChart>(R.id.line_chart)

        viewModel.aggregatedSales.observe(viewLifecycleOwner) { sales ->
            updatePieChart(pieChart, sales)
        }

        viewModel.filteredSales.observe(viewLifecycleOwner) { sales ->
            updateLineChart(lineChart, sales)
            recentAdapter.submitList(sales.take(5))

            binding.shimmerDashboard.stopShimmer()
            binding.shimmerDashboard.visibility = View.GONE
            binding.layoutDashboardContent.visibility = View.VISIBLE
        }

        viewModel.lowStockItems.observe(viewLifecycleOwner) { items ->
            alertAdapter.submitList(items)
            cardAlerts.visibility = if (items.isNotEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun updateLineChart(chart: LineChart, sales: List<SaleWithItem>) {
        // Resolve theme-aware text color
        val typedValue = TypedValue()
        requireContext().theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
        val textColor = typedValue.data

        val dailyTotals = mutableMapOf<Int, Float>()
        for (i in 0..6) dailyTotals[i] = 0f

        val now   = System.currentTimeMillis()
        val dayMs = 24 * 60 * 60 * 1000L

        for (sale in sales) {
            val diff = (now - sale.timestamp) / dayMs
            if (diff in 0..6) {
                val dayIndex = 6 - diff.toInt()
                dailyTotals[dayIndex] = (dailyTotals[dayIndex] ?: 0f) + sale.totalPrice.toFloat()
            }
        }

        val entries = (0..6).map { i -> Entry(i.toFloat(), dailyTotals[i] ?: 0f) }

        val dataSet = LineDataSet(entries, "Daily Revenue (₹)")
        dataSet.color = Color.parseColor("#4CAF50")
        dataSet.setCircleColor(Color.parseColor("#4CAF50"))
        dataSet.lineWidth = 3f
        dataSet.setDrawFilled(true)
        dataSet.fillColor  = Color.parseColor("#4CAF50")
        dataSet.fillAlpha  = 50
        dataSet.mode = LineDataSet.Mode.CUBIC_BEZIER
        dataSet.valueTextColor = textColor
        dataSet.valueTextSize  = 10f

        chart.data = LineData(dataSet)
        chart.description.isEnabled = false
        chart.xAxis.position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
        chart.axisRight.isEnabled = false

        // Apply theme-aware colors to chart text
        chart.xAxis.textColor           = textColor
        chart.axisLeft.textColor        = textColor
        chart.legend.textColor          = textColor
        chart.setNoDataTextColor(textColor)

        if (isLineChartFirstLoad) {
            chart.animateX(1000)
            isLineChartFirstLoad = false
        }

        chart.invalidate()
    }

    private fun updatePieChart(chart: PieChart, sales: List<ItemSalesAggregation>) {
        // Resolve theme-aware text color
        val typedValue = TypedValue()
        requireContext().theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
        val textColor = typedValue.data

        val entries = sales.map { agg -> PieEntry(agg.totalSold.toFloat(), agg.itemName) }

        val dataSet = PieDataSet(entries, "Best Sellers")
        dataSet.colors = ColorTemplate.MATERIAL_COLORS.toList()
        dataSet.valueTextSize  = 14f
        dataSet.valueTextColor = Color.WHITE

        chart.data = PieData(dataSet)
        chart.description.isEnabled = false
        chart.centerText = "Top Items"

        // Apply theme-aware colors to chart text
        chart.setCenterTextColor(textColor)
        chart.legend.textColor  = textColor
        chart.setEntryLabelColor(textColor)
        chart.setNoDataTextColor(textColor)
        chart.setCenterTextSize(14f)

        if (isPieChartFirstLoad) {
            chart.animateY(1400)
            isPieChartFirstLoad = false
        }

        chart.invalidate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// Fix #11: DiffUtil for Item
class LowStockDiffCallback : DiffUtil.ItemCallback<Item>() {
    override fun areItemsTheSame(oldItem: Item, newItem: Item) = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: Item, newItem: Item) = oldItem == newItem
}

class LowStockAdapter : ListAdapter<Item, LowStockAdapter.ViewHolder>(LowStockDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(com.example.hastakalashop.R.layout.item_low_stock, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val textName:       TextView  = view.findViewById(com.example.hastakalashop.R.id.text_alert_name)
        private val textDetails:    TextView  = view.findViewById(com.example.hastakalashop.R.id.text_alert_details)
        private val imageThumbnail: android.widget.ImageView = view.findViewById(com.example.hastakalashop.R.id.image_alert_thumbnail)
        private val stockBadge:     TextView  = view.findViewById(com.example.hastakalashop.R.id.text_alert_stock_badge)

        fun bind(item: Item) {
            textName.text    = item.name
            textDetails.text = "Color: ${item.color}"
            stockBadge.text  = "STOCK: ${item.currentStock}"
            
            if (item.imageUri != null) {
                Glide.with(itemView.context)
                    .load(android.net.Uri.parse(item.imageUri))
                    .placeholder(com.example.hastakalashop.R.drawable.ic_launcher_foreground)
                    .into(imageThumbnail)
            } else {
                imageThumbnail.setImageResource(com.example.hastakalashop.R.drawable.ic_launcher_foreground)
            }
        }
    }
}

// Fix #11: DiffUtil for SaleWithItem
class RecentSalesDiffCallback : DiffUtil.ItemCallback<SaleWithItem>() {
    override fun areItemsTheSame(old: SaleWithItem, new: SaleWithItem) = old.saleId == new.saleId
    override fun areContentsTheSame(old: SaleWithItem, new: SaleWithItem) = old == new
}

class RecentSalesAdapter : ListAdapter<SaleWithItem, RecentSalesAdapter.ViewHolder>(RecentSalesDiffCallback()) {

    private val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = com.example.hastakalashop.databinding.ItemIncomeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val sale = getItem(position)
        holder.binding.textSaleItem.text  = sale.itemName
        holder.binding.textSaleDate.text  = dateFormat.format(Date(sale.timestamp))
        holder.binding.textSalePrice.text = String.format("₹%.0f", sale.totalPrice)
    }

    class ViewHolder(val binding: com.example.hastakalashop.databinding.ItemIncomeBinding) : RecyclerView.ViewHolder(binding.root)
}
