package com.uvtracker.app

import android.Manifest
import android.animation.ObjectAnimator
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.uvtracker.app.databinding.ActivityMainBinding
import com.uvtracker.app.model.UVData
import com.uvtracker.app.model.UVRiskLevel
import com.uvtracker.app.ui.EnvironmentAdapter
import com.uvtracker.app.ui.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private val envAdapter = EnvironmentAdapter()

    // ── Permission launcher ───────────────────────────────────────────────────

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
                || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            viewModel.startLiveUpdates()
        } else {
            showPermissionRationale()
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupSwipeRefresh()
        observeViewModel()
        checkAndRequestPermissions()
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        binding.rvEnvironments.adapter = envAdapter
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refresh()
        }
        binding.swipeRefresh.setColorSchemeColors(
            ContextCompat.getColor(this, R.color.uv_purple),
            ContextCompat.getColor(this, R.color.uv_orange),
            ContextCompat.getColor(this, R.color.uv_red)
        )
    }

    // ── Observation ───────────────────────────────────────────────────────────

    private fun observeViewModel() {
        viewModel.isLoading.observe(this) { loading ->
            binding.swipeRefresh.isRefreshing = loading
            if (loading) {
                binding.tvStatus.text = "📡 Fetching UV data…"
                binding.tvStatus.visibility = View.VISIBLE
            } else {
                binding.tvStatus.visibility = View.GONE
            }
        }

        viewModel.uvData.observe(this) { data ->
            if (data != null) {
                updateMainUI(data)
                binding.layoutContent.visibility = View.VISIBLE
                binding.layoutEmpty.visibility = View.GONE
            }
        }

        viewModel.environments.observe(this) { envs ->
            envAdapter.submitList(envs)
        }

        viewModel.errorMessage.observe(this) { msg ->
            if (!msg.isNullOrBlank()) {
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                if (viewModel.uvData.value == null) {
                    binding.tvEmptyMessage.text = msg
                    binding.layoutEmpty.visibility = View.VISIBLE
                    binding.layoutContent.visibility = View.GONE
                }
                viewModel.clearError()
            }
        }

        viewModel.lastRefreshTime.observe(this) { ts ->
            if (ts > 0) {
                val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                binding.tvLastRefresh.text = "Updated ${fmt.format(Date(ts))}"
            }
        }
    }

    // ── Main UI update ────────────────────────────────────────────────────────

    private fun updateMainUI(data: UVData) {
        val risk = data.riskLevel

        // UV Index number with animation
        animateNumber(binding.tvUVIndex.text.toString().toDoubleOrNull() ?: 0.0, data.uvIndex) {
            binding.tvUVIndex.text = "%.1f".format(it)
        }

        // Risk level badge
        binding.tvRiskLevel.text = risk.label.uppercase()
        val riskColor = Color.parseColor(risk.colorHex)
        binding.tvRiskLevel.setBackgroundColor(riskColor)
        binding.tvRiskLevel.setTextColor(if (risk == UVRiskLevel.MODERATE) Color.BLACK else Color.WHITE)

        // Header gradient tint
        binding.headerCard.setCardBackgroundColor(
            adjustAlpha(riskColor, 0.85f)
        )

        // Location
        binding.tvLocation.text = "📍 ${data.locationName}"
        binding.tvCoords.text = "${"%.4f".format(data.latitude)}°, ${"%.4f".format(data.longitude)}°"

        // UV breakdown
        binding.tvUVIClearSky.text = "Clear-sky max: %.1f".format(data.uvIndexClearSky)
        binding.tvCloudAttenuation.text = "Cloud cover: ${(data.cloudFraction * 100).roundToInt()}%"

        // Direct / Diffuse chips
        binding.chipDirectUV.text = "☀ Direct: %.2f".format(data.directUV)
        binding.chipDiffuseUV.text = "🌤 Diffuse: %.2f".format(data.diffuseUV)

        // Recommendation
        binding.tvRecommendation.text = risk.recommendation

        // Burn time
        binding.tvBurnTime.text = if (data.burnTimeMinutes == Int.MAX_VALUE) {
            "⏱  UV too low to cause sunburn"
        } else {
            "⏱  Unprotected Type II skin burns in ~${data.burnTimeMinutes} min"
        }

        // Progress arc (0–12+ UVI scale, capped at 12)
        val progressPct = ((data.uvIndex / 12.0) * 100).toInt().coerceIn(0, 100)
        ObjectAnimator.ofInt(binding.progressUVI, "progress", progressPct).apply {
            duration = 800
            start()
        }
        binding.progressUVI.setIndicatorColor(riskColor)
    }

    // ── Permissions ───────────────────────────────────────────────────────────

    private fun checkAndRequestPermissions() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)

        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            viewModel.startLiveUpdates()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun showPermissionRationale() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Location Required")
            .setMessage(
                "UV Tracker needs your GPS location to fetch accurate UV index data " +
                "for your exact position — UV levels can vary significantly over short distances " +
                "due to altitude, cloud patterns, and local atmospheric conditions."
            )
            .setPositiveButton("Grant Permission") { _, _ ->
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun animateNumber(from: Double, to: Double, onUpdate: (Double) -> Unit) {
        val animator = ObjectAnimator.ofFloat(from.toFloat(), to.toFloat())
        animator.duration = 600
        animator.addUpdateListener { onUpdate(it.animatedValue as Float) }
        animator.start()
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = (Color.alpha(color) * factor).roundToInt()
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }
}
