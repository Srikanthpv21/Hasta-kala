package com.example.hastakalashop.ui.auth

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.hastakalashop.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayoutMediator

class OnboardingActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        val viewPager = findViewById<ViewPager2>(R.id.viewPagerOnboarding)
        val btnNext = findViewById<MaterialButton>(R.id.btnNext)
        val btnSkip = findViewById<View>(R.id.btnSkip)
        val tabLayout = findViewById<com.google.android.material.tabs.TabLayout>(R.id.tabLayoutIndicator)

        val pages = listOf(
            OnboardingPage(
                "Welcome to Hasta-Kala",
                "Your premium artisan shop companion. Manage your inventory and sales with elegance.",
                R.drawable.ic_profile
            ),
            OnboardingPage(
                "Intelligent Inventory",
                "Keep track of every creation. Get instant alerts when your stock is running low.",
                android.R.drawable.ic_dialog_info
            ),
            OnboardingPage(
                "Business Insights",
                "Visualize your growth with professional revenue trends and sales analytics.",
                android.R.drawable.ic_menu_today
            )
        )

        viewPager.adapter = OnboardingAdapter(pages)
        TabLayoutMediator(tabLayout, viewPager) { _, _ -> }.attach()

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (position == pages.size - 1) {
                    btnNext.text = "Get Started"
                } else {
                    btnNext.text = "Next"
                }
            }
        })

        btnNext.setOnClickListener {
            if (viewPager.currentItem < pages.size - 1) {
                viewPager.currentItem += 1
            } else {
                completeOnboarding()
            }
        }

        btnSkip.setOnClickListener {
            completeOnboarding()
        }
    }

    private fun completeOnboarding() {
        val prefs = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("onboarding_completed", true).apply()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    data class OnboardingPage(val title: String, val description: String, val imageRes: Int)

    inner class OnboardingAdapter(private val pages: List<OnboardingPage>) : RecyclerView.Adapter<OnboardingAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_onboarding_page, parent, false)
            return ViewHolder(view)
        }
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val page = pages[position]
            holder.title.text = page.title
            holder.description.text = page.description
            holder.image.setImageResource(page.imageRes)
        }
        override fun getItemCount() = pages.size
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.textTitle)
            val description: TextView = view.findViewById(R.id.textDescription)
            val image: ImageView = view.findViewById(R.id.imageOnboarding)
        }
    }
}
