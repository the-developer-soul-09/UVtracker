package com.uvtracker.app.ui

import android.app.Application
import android.location.Geocoder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.uvtracker.app.data.UVRepository
import com.uvtracker.app.data.UVResult
import com.uvtracker.app.location.LocationHelper
import com.uvtracker.app.model.EnvironmentUV
import com.uvtracker.app.model.UVData
import com.uvtracker.app.utils.UVCalculator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val locationHelper = LocationHelper(application)
    private val repository = UVRepository(Geocoder(application, Locale.getDefault()))

    // ── Exposed state ─────────────────────────────────────────────────────────

    private val _uvData = MutableLiveData<UVData?>()
    val uvData: LiveData<UVData?> = _uvData

    private val _environments = MutableLiveData<List<EnvironmentUV>>()
    val environments: LiveData<List<EnvironmentUV>> = _environments

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _lastRefreshTime = MutableLiveData<Long>(0)
    val lastRefreshTime: LiveData<Long> = _lastRefreshTime

    // ── Internal state ────────────────────────────────────────────────────────

    private var pollingJob: Job? = null
    private var lastLat: Double = 0.0
    private var lastLon: Double = 0.0

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Start polling UV data every 5 minutes using the current GPS location.
     * Safe to call multiple times – cancels the previous job first.
     */
    fun startLiveUpdates() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                fetchUVData()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /** Force an immediate refresh (e.g., pull-to-refresh). */
    fun refresh() {
        viewModelScope.launch { fetchUVData() }
    }

    fun clearError() { _errorMessage.value = null }

    // ── Private helpers ───────────────────────────────────────────────────────

    private suspend fun fetchUVData() {
        _isLoading.value = true
        _errorMessage.value = null

        try {
            val location = locationHelper.getCurrentLocation()
            if (location == null) {
                _errorMessage.value = "Could not obtain GPS location.\nEnsure location permission is granted and GPS is enabled."
                _isLoading.value = false
                return
            }

            lastLat = location.latitude
            lastLon = location.longitude

            when (val result = repository.fetchUVData(location.latitude, location.longitude)) {
                is UVResult.Success -> {
                    _uvData.value = result.data
                    _environments.value = UVCalculator.computeAllEnvironments(result.data)
                    _lastRefreshTime.value = System.currentTimeMillis()
                    Log.d(TAG, "UV data updated: UVI=${result.data.uvIndex}")
                }
                is UVResult.Error -> {
                    _errorMessage.value = result.message
                }
                UVResult.Loading -> { /* handled by isLoading */ }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in fetchUVData", e)
            _errorMessage.value = "Unexpected error: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }

    companion object {
        private const val TAG = "MainViewModel"
        private const val POLL_INTERVAL_MS = 5 * 60 * 1000L  // 5 minutes
    }
}
