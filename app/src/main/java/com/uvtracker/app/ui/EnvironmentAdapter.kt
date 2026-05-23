package com.uvtracker.app.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.uvtracker.app.databinding.ItemEnvironmentBinding
import com.uvtracker.app.model.EnvironmentUV

class EnvironmentAdapter : ListAdapter<EnvironmentUV, EnvironmentAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemEnvironmentBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val b: ItemEnvironmentBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun bind(env: EnvironmentUV) {
            b.tvIcon.text = env.icon
            b.tvName.text = env.name
            b.tvDescription.text = env.description

            // Effective UV index
            b.tvEffectiveUVI.text = "%.1f".format(env.effectiveUVIndex)

            // Percentage bar
            val pct = env.percentageOfBase.coerceIn(0, 200)
            b.progressUV.max = 200
            b.progressUV.progress = pct
            b.tvPercentage.text = "${pct}%"

            // Color coding from exposure type
            val tintColor = Color.parseColor(env.exposureType.colorHex)
            b.progressUV.setIndicatorColor(tintColor)
            val bgAlpha = ColorUtils.setAlphaComponent(tintColor, 30)
            b.cardRoot.setCardBackgroundColor(bgAlpha)
            b.viewAccent.setBackgroundColor(tintColor)

            // Breakdown
            b.tvDirect.text = "☀ Direct: %.2f".format(env.breakdown.directUV)
            b.tvDiffuse.text = "🌤 Diffuse: %.2f".format(env.breakdown.diffuseUV)
            b.tvReflected.text = "↩ Reflected: %.2f".format(env.breakdown.reflectedUV)
            if (env.breakdown.reflectionSource.isNotBlank()) {
                b.tvReflectionSource.text = env.breakdown.reflectionSource
                b.tvReflectionSource.visibility = android.view.View.VISIBLE
            } else {
                b.tvReflectionSource.visibility = android.view.View.GONE
            }

            // Burn time
            b.tvBurnTime.text = if (env.burnTimeMinutes == Int.MAX_VALUE) {
                "⏱ No burn risk"
            } else {
                "⏱ Burn in ~${env.burnTimeMinutes} min (Type II skin)"
            }

            // Protection tip
            b.tvProtectionTip.text = "💡 ${env.protectionTip}"
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<EnvironmentUV>() {
            override fun areItemsTheSame(a: EnvironmentUV, b: EnvironmentUV) =
                a.name == b.name
            override fun areContentsTheSame(a: EnvironmentUV, b: EnvironmentUV) =
                a == b
        }
    }
}
