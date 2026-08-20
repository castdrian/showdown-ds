package dev.adrian.showdown

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
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
        private val controllerActionOrder = listOf(BUTTON_NEGATIVE, BUTTON_NEUTRAL, BUTTON_POSITIVE)
        private val openDialogs = linkedSetOf<ShowdownDialog>()

        fun dismissOpenDialogs(hostContext: Context) {
            openDialogs.toList()
                .filter { it.hostContext === hostContext && it.isShowing }
                .forEach { it.dismiss() }
        }

        fun dispatchControllerKey(hostContext: Context, keyCode: Int): Boolean {
            val dialog = openDialogs.lastOrNull { it.hostContext === hostContext && it.isShowing } ?: return false
            return dialog.handleControllerKey(keyCode)
        }

        fun dispatchControllerMotion(hostContext: Context, horizontal: Int, vertical: Int): Boolean {
            val dialog = openDialogs.lastOrNull { it.hostContext === hostContext && it.isShowing } ?: return false
            return dialog.handleControllerMotion(horizontal, vertical)
        }
    }

    private data class Action(
        val text: CharSequence,
        val listener: ((ShowdownDialog, Int) -> Unit)?,
        val kind: Int
    )

    private val density = context.resources.displayMetrics.density
    private val hostContext = context
    private val actions = linkedMapOf<Int, Action>()
    private var dialogTitle: CharSequence = ""
    private var message: CharSequence? = null
    private var customView: View? = null
    private var items: Array<out CharSequence>? = null
    private var itemListener: ((ShowdownDialog, Int) -> Unit)? = null
    private var checkedItem = -1
    private var searchableItems: SearchableItems? = null
    private var titleView: TextView? = null
    private val buttonViews = mutableMapOf<Int, TextView>()
    private var shell: LinearLayout? = null
    private var focusedControllerActionIndex = 0
    private var controllerHorizontal = 0
    private var controllerVertical = 0

    private data class SearchableItems(
        val hint: CharSequence,
        val values: List<CharSequence>,
        val searchValues: List<CharSequence>,
        val checkedItem: Int,
        val listener: (ShowdownDialog, Int) -> Unit
    )

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

    fun setSearchableSingleChoiceItems(
        hint: CharSequence,
        values: List<CharSequence>,
        checked: Int,
        searchValues: List<CharSequence> = values,
        listener: (ShowdownDialog, Int) -> Unit
    ): ShowdownDialog {
        searchableItems = SearchableItems(hint, values.toList(), searchValues.toList(), checked, listener)
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
        openDialogs += this
        val display = context.resources.displayMetrics
        window?.setLayout((display.widthPixels * 0.84f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        shell?.requestFocus()
        focusedControllerActionIndex = controllerActions().indexOf(BUTTON_POSITIVE).takeIf { it >= 0 } ?: 0
        updateControllerActionFocus()
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(shell?.windowToken, 0)
    }

    override fun onStop() {
        openDialogs -= this
        super.onStop()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (event.repeatCount > 0 && isConfirmKey(event.keyCode)) return true
            if (handleControllerKey(event.keyCode)) return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK && event.action == MotionEvent.ACTION_MOVE) {
            return handleControllerMotion(
                axisDirection(event, MotionEvent.AXIS_X, MotionEvent.AXIS_HAT_X),
                axisDirection(event, MotionEvent.AXIS_Y, MotionEvent.AXIS_HAT_Y)
            )
        }
        return super.dispatchGenericMotionEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.repeatCount > 0 && isConfirmKey(keyCode)) return true
        return if (handleControllerKey(keyCode)) true else super.onKeyDown(keyCode, event)
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
        } ?: searchableItems?.let { searchable ->
            val filter = EditText(context).apply {
                hint = searchable.hint
                isSingleLine = true
                imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI
                setTextColor(Color.rgb(234, 245, 247))
                setHintTextColor(Color.rgb(139, 171, 183))
                setTextSize(17f)
                background = surface(Color.rgb(14, 39, 56), Color.rgb(52, 113, 126), 14f)
                setPadding(dp(16), dp(10), dp(16), dp(10))
            }
            val list = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }
            fun renderRows(query: String) {
                list.removeAllViews()
                val terms = query.trim().split(Regex("\\s+")).filter(String::isNotBlank)
                searchable.values.forEachIndexed { index, value ->
                    val normalized = listOf(value, searchable.searchValues.getOrNull(index)?.toString().orEmpty()).joinToString(" ")
                    if (terms.isNotEmpty() && terms.any { !normalized.contains(it, ignoreCase = true) }) return@forEachIndexed
                    val row = TextView(context).apply {
                        text = value
                        setTextColor(Color.rgb(223, 239, 243))
                        setTextSize(17f)
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(20), dp(14), dp(20), dp(14))
                        background = if (index == searchable.checkedItem) surface(Color.rgb(29, 115, 123), Color.rgb(133, 214, 209), 16f) else surface(Color.rgb(18, 48, 66), Color.rgb(54, 110, 122), 16f)
                        isClickable = true
                        isFocusable = true
                        setOnClickListener {
                            dismiss()
                            searchable.listener(this@ShowdownDialog, index)
                        }
                    }
                    list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(8)) })
                }
                if (list.childCount == 0) {
                    list.addView(TextView(context).apply {
                        text = "No matching formats"
                        setTextColor(Color.rgb(175, 204, 212))
                        setTextSize(17f)
                        gravity = Gravity.CENTER
                        setPadding(dp(20), dp(32), dp(20), dp(32))
                    }, LinearLayout.LayoutParams(-1, -2))
                }
            }
            filter.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) = renderRows(text?.toString().orEmpty())
                override fun afterTextChanged(editable: Editable?) = Unit
            })
            renderRows("")
            val filteredContent = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(filter, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
                addView(ScrollView(context).apply {
                    isFillViewport = true
                    overScrollMode = View.OVER_SCROLL_NEVER
                    addView(list, ViewGroup.LayoutParams(-1, -2))
                }, LinearLayout.LayoutParams(-1, -2))
            }
            content.addView(filteredContent, FrameLayout.LayoutParams(-1, -2))
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

    private fun controllerActions(): List<Int> = controllerActionOrder.filter { buttonViews.containsKey(it) }

    private fun handleControllerKey(keyCode: Int): Boolean {
        val availableActions = controllerActions()
        when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                dismiss()
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_UP -> {
                if (availableActions.isNotEmpty()) {
                    focusedControllerActionIndex = (focusedControllerActionIndex - 1 + availableActions.size) % availableActions.size
                    updateControllerActionFocus()
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (availableActions.isNotEmpty()) {
                    focusedControllerActionIndex = (focusedControllerActionIndex + 1) % availableActions.size
                    updateControllerActionFocus()
                }
                return true
            }
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> {
                availableActions.getOrNull(focusedControllerActionIndex)?.let { buttonViews[it]?.performClick() }
                return true
            }
            KeyEvent.KEYCODE_BUTTON_L1,
            KeyEvent.KEYCODE_BUTTON_R1,
            KeyEvent.KEYCODE_BUTTON_X,
            KeyEvent.KEYCODE_BUTTON_Y,
            KeyEvent.KEYCODE_BUTTON_L2,
            KeyEvent.KEYCODE_BUTTON_R2,
            KeyEvent.KEYCODE_BUTTON_SELECT,
            KeyEvent.KEYCODE_BUTTON_START,
            KeyEvent.KEYCODE_BUTTON_THUMBL,
            KeyEvent.KEYCODE_BUTTON_THUMBR,
            KeyEvent.KEYCODE_MENU -> return true
            else -> return false
        }
    }

    private fun isConfirmKey(keyCode: Int) = keyCode == KeyEvent.KEYCODE_BUTTON_A ||
        keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER

    private fun handleControllerMotion(horizontal: Int, vertical: Int): Boolean {
        if (horizontal != controllerHorizontal || vertical != controllerVertical) {
            controllerHorizontal = horizontal
            controllerVertical = vertical
            when {
                horizontal < 0 || vertical < 0 -> moveControllerFocus(-1)
                horizontal > 0 || vertical > 0 -> moveControllerFocus(1)
            }
        }
        return true
    }

    private fun axisDirection(event: MotionEvent, primaryAxis: Int, fallbackAxis: Int): Int {
        var value = event.getAxisValue(primaryAxis)
        if (kotlin.math.abs(value) < 0.45f) value = event.getAxisValue(fallbackAxis)
        return when {
            value > 0.45f -> 1
            value < -0.45f -> -1
            else -> 0
        }
    }

    private fun moveControllerFocus(direction: Int) {
        val availableActions = controllerActions()
        if (availableActions.isEmpty()) return
        focusedControllerActionIndex = (focusedControllerActionIndex + direction + availableActions.size) % availableActions.size
        updateControllerActionFocus()
    }

    private fun updateControllerActionFocus() {
        val availableActions = controllerActions()
        availableActions.forEachIndexed { index, which ->
            val button = buttonViews[which] ?: return@forEachIndexed
            val focused = index == focusedControllerActionIndex
            button.setTextColor(if (focused) Color.rgb(229, 252, 248) else Color.rgb(137, 221, 215))
            button.background = if (focused) {
                surface(Color.rgb(24, 124, 129), Color.rgb(121, 218, 211), 16f)
            } else {
                surface(Color.rgb(15, 50, 67), Color.rgb(53, 117, 127), 16f)
            }
        }
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

    fun setSearchableSingleChoiceItems(
        hint: CharSequence,
        items: List<CharSequence>,
        checkedItem: Int,
        searchValues: List<CharSequence> = items,
        listener: (ShowdownDialog, Int) -> Unit
    ): ShowdownDialogBuilder = apply { dialog.setSearchableSingleChoiceItems(hint, items, checkedItem, searchValues, listener) }

    fun setPositiveButton(text: CharSequence, listener: ((ShowdownDialog, Int) -> Unit)?): ShowdownDialogBuilder = apply { dialog.setPositiveButton(text, listener) }

    fun setNegativeButton(text: CharSequence, listener: ((ShowdownDialog, Int) -> Unit)?): ShowdownDialogBuilder = apply { dialog.setNegativeButton(text, listener) }

    fun setNeutralButton(text: CharSequence, listener: ((ShowdownDialog, Int) -> Unit)?): ShowdownDialogBuilder = apply { dialog.setNeutralButton(text, listener) }

    fun create(): ShowdownDialog = dialog

    fun show(): ShowdownDialog = dialog.also { it.show() }
}
