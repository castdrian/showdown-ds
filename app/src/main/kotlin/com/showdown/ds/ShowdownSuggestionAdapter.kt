package com.showdown.ds

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView

class ShowdownSuggestionAdapter(context: Context, values: List<String>) : ArrayAdapter<String>(context, 0, values) {
    private val density = context.resources.displayMetrics.density

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = (convertView as? TextView) ?: TextView(context)
        view.text = getItem(position).orEmpty()
        view.setTextColor(Color.rgb(224, 243, 245))
        view.setTextSize(16f)
        view.setSingleLine(true)
        view.setPadding(dp(18), dp(13), dp(18), dp(13))
        view.background = GradientDrawable().apply {
            setColor(Color.rgb(14, 47, 64))
            setStroke(dp(1), Color.rgb(47, 126, 139))
            cornerRadius = dp(12).toFloat()
        }
        return view
    }

    private fun dp(value: Int) = (value * density).toInt()
}
