package dev.adrian.showdown

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.min

class ShowdownDialog(context: Context) : Dialog(context) {
    companion object {
        const val BUTTON_POSITIVE = -1
        const val BUTTON_NEGATIVE = -2
        const val BUTTON_NEUTRAL = -3
    }

    private data class Action(
        val text: CharSequence,
        val listener: ((ShowdownDialog, Int) -> Unit)?,
        val kind: Int
    )

    private val density = context.resources.displayMetrics.density
    private val actions = linkedMapOf<Int, Action>()
    private var dialogTitle: CharSequence = ""
    private var message: CharSequence? = null
    private var customView: View? = null
    private var items: Array<out CharSequence>? = null
    private var itemListener: ((ShowdownDialog, Int) -> Unit)? = null
    private var checkedItem = -1
    private var titleView: TextView? = null
    private val buttonViews = mutableMapOf<Int, TextView>()
    private var shell: LinearLayout? = null

    override fun setTitle(title: CharSequence?) {
        dialogTitle = title ?: ""
        super.setTitle(title)
        titleView?.text = dialogTitle
    }

    fun setMessage(value: CharSequence?): ShowdownDialog {
        message = value
        return this
    }

    fun setView(view: View): ShowdownDialog {
        customView = view
        return this
    }

    fun setItems(values: Array<out CharSequence>, listener: (ShowdownDialog, Int) -> Unit): ShowdownDialog {
        items = values
        itemListener = listener
        return this
    }

    fun setSingleChoiceItems(values: Array<out CharSequence>, checked: Int, listener: (ShowdownDialog, Int) -> Unit): ShowdownDialog {
        items = values
        checkedItem = checked
        itemListener = listener
        return this
    }

    fun setPositiveButton(text: CharSequence, listener: ((ShowdownDialog, Int) -> Unit)?): ShowdownDialog = setAction(BUTTON_POSITIVE, text, listener, 2)

    fun setNegativeButton(text: CharSequence, listener: ((ShowdownDialog, Int) -> Unit)?): ShowdownDialog = setAction(BUTTON_NEGATIVE, text, listener, 0)

    fun setNeutralButton(text: CharSequence, listener: ((ShowdownDialog, Int) -> Unit)?): ShowdownDialog = setAction(BUTTON_NEUTRAL, text, listener, 1)

    fun getButton(which: Int): TextView? = buttonViews[which]

    override fun onCreate(state: android.os.Bundle?) {
        super.onCreate(state)
        window?.setBackgroundDrawableResource(android.R.color.transparent)
        window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        window?.attributes = window?.attributes?.apply { dimAmount = 0.72f }
        render()
    }

    override fun onStart() {
        super.onStart()
        val display = context.resources.displayMetrics
        window?.setLayout((display.widthPixels * 0.84f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        shell?.requestFocus()
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(shell?.windowToken, 0)
    }

    private fun setAction(which: Int, text: CharSequence, listener: ((ShowdownDialog, Int) -> Unit)?, kind: Int): ShowdownDialog {
        actions[which] = Action(text, listener, kind)
        return this
    }

    private fun render() {
        buttonViews.clear()
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isFocusable = true
            isFocusableInTouchMode = true
            descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            background = surface(Color.rgb(17, 35, 53), Color.rgb(47, 105, 119), 28f)
            setPadding(dp(12), dp(12), dp(12), dp(10))
        }
        shell = root
        titleView = TextView(context).apply {
            text = dialogTitle
            setTextColor(Color.rgb(235, 246, 249))
            setTextSize(24f)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        root.addView(titleView, LinearLayout.LayoutParams(-1, -2))
        val content = CappedFrameLayout(context, (context.resources.displayMetrics.heightPixels * 0.68f).toInt()).apply {
            background = surface(Color.rgb(10, 27, 42), Color.rgb(34, 74, 89), 20f)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        customView?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
            styleView(view)
            content.addView(view, FrameLayout.LayoutParams(-1, -2))
        } ?: message?.let { value ->
            content.addView(TextView(context).apply {
                text = value
                setTextColor(Color.rgb(207, 225, 232))
                setTextSize(17f)
                setPadding(dp(20), dp(16), dp(20), dp(16))
            }, FrameLayout.LayoutParams(-1, -2))
        } ?: items?.let { values ->
            val list = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }
            values.forEachIndexed { index, value ->
                val row = TextView(context).apply {
                    text = value
                    setTextColor(Color.rgb(223, 239, 243))
                    setTextSize(17f)
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(20), dp(14), dp(20), dp(14))
                    background = if (index == checkedItem) surface(Color.rgb(29, 115, 123), Color.rgb(133, 214, 209), 16f) else surface(Color.rgb(18, 48, 66), Color.rgb(54, 110, 122), 16f)
                    isClickable = true
                    setOnClickListener {
                        dismiss()
                        itemListener?.invoke(this@ShowdownDialog, index)
                    }
                }
                list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(8)) })
            }
            val scroll = ScrollView(context).apply {
                isFillViewport = true
                overScrollMode = View.OVER_SCROLL_NEVER
                addView(list, ViewGroup.LayoutParams(-1, -2))
            }
            content.addView(scroll, FrameLayout.LayoutParams(-1, -2))
        }
        root.addView(content, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        val actionBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(8), dp(4), 0)
        }
        listOf(BUTTON_NEGATIVE, BUTTON_NEUTRAL, BUTTON_POSITIVE).forEach { which ->
            actions[which]?.let { action ->
                val button = Button(context).apply {
                    text = action.text
                    isAllCaps = false
                    setTextColor(if (action.kind == 2) Color.rgb(229, 252, 248) else Color.rgb(137, 221, 215))
                    setTextSize(15f)
                    typeface = Typeface.create("sans-serif", Typeface.BOLD)
                    minHeight = dp(52)
                    minimumWidth = dp(92)
                    setPadding(dp(14), 0, dp(14), 0)
                    background = if (action.kind == 2) surface(Color.rgb(24, 124, 129), Color.rgb(121, 218, 211), 16f) else surface(Color.rgb(15, 50, 67), Color.rgb(53, 117, 127), 16f)
                    setOnClickListener {
                        action.listener?.invoke(this@ShowdownDialog, which)
                        if (action.listener == null || !isShowing) dismiss()
                        else dismiss()
                    }
                }
                buttonViews[which] = button
                actionBar.addView(button, LinearLayout.LayoutParams(0, dp(52), 1f).apply { setMargins(dp(4), 0, dp(4), 0) })
            }
        }
        root.addView(actionBar, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
    }

    private fun styleView(view: View) {
        when (view) {
            is EditText -> {
                view.imeOptions = view.imeOptions or EditorInfo.IME_FLAG_NO_EXTRACT_UI
                view.setTextColor(Color.rgb(234, 245, 247))
                view.setHintTextColor(Color.rgb(139, 171, 183))
                view.setTextSize(17f)
                view.background = surface(Color.rgb(14, 39, 56), Color.rgb(52, 113, 126), 14f)
                view.setPadding(dp(16), dp(10), dp(16), dp(10))
            }
            is Button -> {
                view.isAllCaps = false
                view.setTextColor(Color.rgb(137, 221, 215))
                view.setTextSize(15f)
                view.background = surface(Color.rgb(15, 50, 67), Color.rgb(53, 117, 127), 14f)
                view.minHeight = dp(50)
            }
            is CheckBox -> {
                view.setTextColor(Color.rgb(224, 239, 243))
                view.setTextSize(16f)
                view.buttonTintList = android.content.res.ColorStateList.valueOf(Color.rgb(118, 211, 204))
            }
            is TextView -> {
                if (view.text.isNotBlank()) view.setTextColor(Color.rgb(221, 237, 241))
            }
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) styleView(view.getChildAt(index))
        }
    }

    private fun surface(fill: Int, stroke: Int, radius: Float): GradientDrawable = GradientDrawable().apply {
        setColor(fill)
        setStroke(dp(1), stroke)
        cornerRadius = dp(radius).toFloat()
    }

    private fun dp(value: Int) = (value * density).toInt()

    private fun dp(value: Float) = (value * density).toInt()

    private class CappedFrameLayout(context: Context, private val maxHeight: Int) : FrameLayout(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val bounded = MeasureSpec.makeMeasureSpec(min(MeasureSpec.getSize(heightMeasureSpec), maxHeight), MeasureSpec.AT_MOST)
            super.onMeasure(widthMeasureSpec, bounded)
        }
    }
}

class ShowdownDialogBuilder(private val context: Context) {
    private val dialog = ShowdownDialog(context)

    fun setTitle(title: CharSequence): ShowdownDialogBuilder = apply { dialog.setTitle(title) }

    fun setMessage(message: CharSequence): ShowdownDialogBuilder = apply { dialog.setMessage(message) }

    fun setView(view: View): ShowdownDialogBuilder = apply { dialog.setView(view) }

    fun setItems(items: Array<out CharSequence>, listener: (ShowdownDialog, Int) -> Unit): ShowdownDialogBuilder = apply { dialog.setItems(items, listener) }

    fun setSingleChoiceItems(items: Array<out CharSequence>, checkedItem: Int, listener: (ShowdownDialog, Int) -> Unit): ShowdownDialogBuilder = apply { dialog.setSingleChoiceItems(items, checkedItem, listener) }

    fun setPositiveButton(text: CharSequence, listener: ((ShowdownDialog, Int) -> Unit)?): ShowdownDialogBuilder = apply { dialog.setPositiveButton(text, listener) }

    fun setNegativeButton(text: CharSequence, listener: ((ShowdownDialog, Int) -> Unit)?): ShowdownDialogBuilder = apply { dialog.setNegativeButton(text, listener) }

    fun setNeutralButton(text: CharSequence, listener: ((ShowdownDialog, Int) -> Unit)?): ShowdownDialogBuilder = apply { dialog.setNeutralButton(text, listener) }

    fun create(): ShowdownDialog = dialog

    fun show(): ShowdownDialog = dialog.also { it.show() }
}
