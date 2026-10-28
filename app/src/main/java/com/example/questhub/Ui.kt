package com.example.questhub

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView

object Theme {
    const val BG = 0xFF05070F.toInt()
    const val SURFACE = 0xFF0F1626.toInt()
    const val SURFACE2 = 0xFF18213A.toInt()
    const val BLUE = 0xFF3B82F6.toInt()
    const val TEXT = Color.WHITE
    const val MUTED = 0xFF9AA6C2.toInt()
}

fun Context.dp(v: Int): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

fun Context.rounded(color: Int, radiusDp: Int = 16): GradientDrawable =
    GradientDrawable().apply { setColor(color); cornerRadius = dp(radiusDp).toFloat() }

fun Context.label(text: String, sizeSp: Float = 14f, color: Int = Theme.TEXT, bold: Boolean = false): TextView =
    TextView(this).apply {
        this.text = text; textSize = sizeSp; setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

fun Context.pill(text: String, filled: Boolean, onClick: () -> Unit): TextView =
    TextView(this).apply {
        this.text = text; textSize = 14f; gravity = Gravity.CENTER
        setTextColor(Theme.TEXT)
        background = rounded(if (filled) Theme.BLUE else Theme.SURFACE2, 22)
        setPadding(dp(18), dp(10), dp(18), dp(10))
        isClickable = true; isFocusable = true
        setOnClickListener { onClick() }
    }
