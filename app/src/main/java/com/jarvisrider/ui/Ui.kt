package com.jarvisrider.ui

import android.app.Activity
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

/** Tiny programmatic UI builder: keeps the APK free of AppCompat/Material and layout bloat. */
internal class Ui(private val activity: Activity) {
    val root = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        val p = dp(16)
        setPadding(p, p, p, p)
    }

    fun dp(v: Int): Int = (v * activity.resources.displayMetrics.density).toInt()

    fun attach(): ScrollView {
        val scroll = ScrollView(activity)
        scroll.addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        activity.setContentView(scroll)
        return scroll
    }

    fun header(text: CharSequence): TextView = TextView(activity).apply {
        this.text = text
        textSize = 20f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(16), 0, dp(6))
        root.addView(this)
    }

    fun text(text: CharSequence, sizeSp: Float = 15f): TextView = TextView(activity).apply {
        this.text = text
        textSize = sizeSp
        setPadding(0, dp(4), 0, dp(4))
        setTextIsSelectable(true)
        root.addView(this)
    }

    fun button(text: CharSequence, onClick: () -> Unit): Button = Button(activity).apply {
        this.text = text
        isAllCaps = false
        textSize = 16f
        minHeight = dp(56)
        setOnClickListener { onClick() }
        root.addView(this, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(6)
        })
    }

    @Suppress("UseSwitchCompatOrMaterialCode") // deliberately framework-only
    fun switch(text: CharSequence, checked: Boolean, onChange: (Boolean) -> Unit): Switch = Switch(activity).apply {
        this.text = text
        textSize = 16f
        isChecked = checked
        minHeight = dp(52)
        setOnCheckedChangeListener { _, value -> onChange(value) }
        root.addView(this, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    fun add(view: View) = root.addView(view)
}
