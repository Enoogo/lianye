package org.lianye.ui.floating

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import org.lianye.platform.CaptureRegionStore
import org.lianye.platform.EdgeSnap
import org.lianye.platform.RegionSnapSettings
import org.lianye.platform.RegionSnapSettings.Edge
import kotlin.math.abs

/**
 * Full-screen editor for the capture rectangle.
 *
 * The red frame is one physical pixel wide with the stroke drawn entirely inside the
 * rectangle, so an edge at coordinate 0 or at the display limit is still visible. The
 * rectangle therefore describes the area enclosed by the outer edge of that line.
 *
 * The floating panel can be dragged anywhere, follows the finger without animation, and
 * carries per-edge snap settings behind the gear button.
 */
class RegionSelectionOverlay(
    private val context: Context,
    private val windowManager: WindowManager,
    private val windowType: Int,
    private val onClosed: () -> Unit
) {
    private val density = context.resources.displayMetrics.density
    private var root: FrameLayout? = null
    private val snapValues: MutableMap<Edge, EdgeSnap> = mutableMapOf()
    private val snapSwitches = mutableMapOf<Edge, Switch>()
    private val thresholdInputs = mutableMapOf<Edge, EditText>()
    private lateinit var panel: LinearLayout
    private var settingsSection: LinearLayout? = null
    private var settingsVisible = false
    private var stagedFullHeight = false
    private lateinit var leftInput: EditText
    private lateinit var topInput: EditText
    private lateinit var rightInput: EditText
    private lateinit var bottomInput: EditText
    private var syncing = false
    private var syncingThresholds = false

    val isVisible: Boolean get() = root != null

    private val params = WindowManager.LayoutParams().apply {
        type = windowType
        format = PixelFormat.TRANSLUCENT
        flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        gravity = Gravity.TOP or Gravity.START
        width = WindowManager.LayoutParams.MATCH_PARENT
        height = WindowManager.LayoutParams.MATCH_PARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Do not let the window be inset by the status/navigation bars: (0,0) must be
            // the physical top-left of the panel so region coordinates match the capture.
            setFitInsetsTypes(0)
            setFitInsetsSides(0)
        }
        softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
    }

    fun show() {
        if (root != null) return
        snapValues.clear()
        snapValues.putAll(RegionSnapSettings.load(context))
        stagedFullHeight = CaptureRegionStore.isFullHeightMode(context)
        val frame = CaptureRegionStore.fullFrame(context)
        val region = CaptureRegionStore.load(context)

        val container = FrameLayout(context)
        val border = RegionBorderView(context, Rect(region), frame) { snapValues[it] }
        border.onRegionChanged = { updated -> pushToInputs(updated) }
        container.addView(border, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        panel = buildPanel(frame, border)
        container.addView(panel, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ))

        container.isFocusableInTouchMode = true
        container.isFocusable = true

        root = container
        runCatching { windowManager.addView(container, params) }
        container.requestFocus()
    }

    fun hide() {
        val view = root ?: return
        root = null
        runCatching { windowManager.removeView(view) }
        onClosed()
    }

    private fun buildPanel(frame: Rect, border: RegionBorderView): LinearLayout {
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = 18 * density
                setColor(Color.parseColor("#F5FFFFFF"))
                setStroke(dp(1), Color.parseColor("#22000000"))
            }
        }

        // Header is the drag handle; the gear stays a working child button.
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(context).apply {
            text = "Capture area"
            textSize = 15f
            setTextColor(Color.parseColor("#1F1F1F"))
        }
        header.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val gear = TextView(context).apply {
            text = "⚙"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#1F1F1F"))
            setOnClickListener { toggleSettings() }
        }
        header.addView(gear, LinearLayout.LayoutParams(dp(40), dp(40)))
        makeDraggable(header)
        panel.addView(header)

        val topRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        topRow.addView(label("┌"))
        leftInput = numberInput(border.region.left)
        topInput = numberInput(border.region.top)
        topRow.addView(leftInput, inputParams())
        topRow.addView(spacer(6))
        topRow.addView(topInput, inputParams())
        panel.addView(topRow)

        val bottomRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, 0)
        }
        bottomRow.addView(label("┘"))
        rightInput = numberInput(border.region.right)
        bottomInput = numberInput(border.region.bottom)
        bottomRow.addView(rightInput, inputParams())
        bottomRow.addView(spacer(6))
        bottomRow.addView(bottomInput, inputParams())
        panel.addView(bottomRow)

        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(6), 0, 0)
        }
        actions.addView(actionButton("Reset") {
            border.region.set(CaptureRegionStore.defaultFrame(context))
            border.invalidate()
            pushToInputs(border.region)
        })
        actions.addView(spacer(6))
        actions.addView(actionButton("Cancel") { hide() })
        actions.addView(spacer(6))
        actions.addView(actionButton("✓ Done") {
            CaptureRegionStore.save(context, border.region)
            RegionSnapSettings.save(context, snapValues)
            CaptureRegionStore.setFullHeightMode(context, stagedFullHeight)
            Toast.makeText(context, "Capture area saved", Toast.LENGTH_SHORT).show()
            hide()
        })
        panel.addView(actions)

        listOf(leftInput, topInput, rightInput, bottomInput).forEach { input ->
            input.addTextChangedListener(simpleWatcher { if (!syncing) applyInputs(frame, border) })
        }
        return panel
    }

    private fun buildSettings(): LinearLayout {
        val section = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, 0)
        }

        val table = TableLayout(context)
        val captionRow = TableRow(context)
        captionRow.addView(tableLabel(""), cellParams(48, 24))
        captionRow.addView(columnCaption("Snap"), cellParams(100, 24))
        captionRow.addView(columnCaption("Threshold"), cellParams(100, 24))
        table.addView(captionRow)

        val linkRow = TableRow(context)
        linkRow.addView(tableLabel("Link"), cellParams(48, 40))
        val snapLink = linkCheckBox("Link all snap switches")
        val thresholdLink = linkCheckBox("Link all snap thresholds")
        snapLink.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                val enabled = snapSwitches.values.firstOrNull()?.isChecked ?: false
                snapSwitches.values.forEach { it.isChecked = enabled }
            }
        }
        thresholdLink.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                val value = thresholdInputs.values.firstOrNull()?.text?.toString() ?: ""
                syncingThresholds = true
                thresholdInputs.values.forEach { it.setText(value) }
                syncingThresholds = false
            }
        }
        linkRow.addView(centerInCell(snapLink), cellParams(100, 40))
        linkRow.addView(centerInCell(thresholdLink), cellParams(100, 40))
        table.addView(linkRow)

        Edge.entries.forEach { edge ->
            val row = TableRow(context)
            row.addView(tableLabel(edge.title()), cellParams(48, 44))
            val toggle = Switch(context).apply {
                text = ""
                gravity = Gravity.CENTER
                isChecked = snapValues[edge]?.enabled ?: false
                setOnCheckedChangeListener { _, checked ->
                    snapValues[edge] = (snapValues[edge] ?: EdgeSnap()).copy(enabled = checked)
                    if (snapLink.isChecked) {
                        Edge.entries.forEach { other ->
                            snapValues[other] = (snapValues[other] ?: EdgeSnap()).copy(enabled = checked)
                            snapSwitches[other]?.isChecked = checked
                        }
                    }
                }
            }
            snapSwitches[edge] = toggle
            row.addView(toggle, cellParams(100, 44))
            val threshold = EditText(context).apply {
                setText((snapValues[edge]?.threshold ?: RegionSnapSettings.DEFAULT_THRESHOLD).toString())
                inputType = InputType.TYPE_CLASS_NUMBER
                textSize = 14f
                gravity = Gravity.CENTER
                includeFontPadding = false
                setPadding(dp(4), 0, dp(4), 0)
                background = fieldBackground()
                addTextChangedListener(simpleWatcher {
                    if (syncingThresholds) return@simpleWatcher
                    val value = text.toString().toIntOrNull() ?: return@simpleWatcher
                    snapValues[edge] = (snapValues[edge] ?: EdgeSnap()).copy(threshold = value)
                    // Editing a threshold implies intent to use it: turn this edge's snap on
                    // so the next drag previews the value immediately.
                    if (snapValues[edge]?.enabled != true) {
                        snapValues[edge] = snapValues[edge]!!.copy(enabled = true)
                        snapSwitches[edge]?.isChecked = true
                    }
                    if (thresholdLink.isChecked) {
                        syncingThresholds = true
                        Edge.entries.forEach { other ->
                            snapValues[other] = (snapValues[other] ?: EdgeSnap()).copy(threshold = value)
                            val input = thresholdInputs[other]
                            if (input != null && input !== this) input.setText(value.toString())
                        }
                        syncingThresholds = false
                    }
                })
            }
            thresholdInputs[edge] = threshold
            row.addView(threshold, cellParams(100, 40))
            table.addView(row)
        }
        section.addView(table)

        val fullHeightRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, 0)
        }
        fullHeightRow.addView(TextView(context).apply {
            text = "Full height"
            textSize = 12f
            setTextColor(Color.parseColor("#3F3F3F"))
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val fullHeightSwitch = Switch(context).apply {
            text = ""
            isChecked = stagedFullHeight
            setOnCheckedChangeListener { _, checked -> stagedFullHeight = checked }
        }
        fullHeightRow.addView(fullHeightSwitch, LinearLayout.LayoutParams(dp(72), dp(40)))
        section.addView(fullHeightRow)
        return section
    }

    /**
     * Rebuilds the settings block instead of flipping visibility: hiding and re-showing a
     * TableLayout inside a WRAP_CONTENT window can leave it measured at zero height on the
     * second pass. A fresh view is cheap here and always lays out correctly.
     */
    private fun toggleSettings() {
        settingsVisible = !settingsVisible
        settingsSection?.let { panel.removeView(it) }
        settingsSection = null
        snapSwitches.clear()
        thresholdInputs.clear()
        if (settingsVisible) {
            val section = buildSettings()
            settingsSection = section
            panel.addView(section, (panel.childCount - 1).coerceAtLeast(0))
        }
        panel.requestLayout()
        panel.post { clampPanel() }
    }

    private fun makeDraggable(handle: View) {
        var startX = 0f
        var startY = 0f
        var baseX = 0f
        var baseY = 0f
        handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    baseX = panel.translationX
                    baseY = panel.translationY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    panel.translationX = baseX + (event.rawX - startX)
                    panel.translationY = baseY + (event.rawY - startY)
                    clampPanel()
                    true
                }
                else -> false
            }
        }
    }

    private fun clampPanel() {
        val parent = root ?: return
        val left = panel.left + panel.translationX
        val top = panel.top + panel.translationY
        val maxLeft = (parent.width - panel.width).coerceAtLeast(0).toFloat()
        val maxTop = (parent.height - panel.height).coerceAtLeast(0).toFloat()
        val clampedLeft = left.coerceIn(0f, maxLeft)
        val clampedTop = top.coerceIn(0f, maxTop)
        panel.translationX += clampedLeft - left
        panel.translationY += clampedTop - top
    }

    private fun applyInputs(frame: Rect, border: RegionBorderView) {
        val left = leftInput.text.toString().toIntOrNull() ?: return
        val top = topInput.text.toString().toIntOrNull() ?: return
        val right = rightInput.text.toString().toIntOrNull() ?: return
        val bottom = bottomInput.text.toString().toIntOrNull() ?: return
        val updated = CaptureRegionStore.clamp(Rect(left, top, right, bottom), frame)
        border.region.set(updated)
        border.invalidate()
    }

    private fun pushToInputs(region: Rect) {
        syncing = true
        leftInput.setText(region.left.toString())
        topInput.setText(region.top.toString())
        rightInput.setText(region.right.toString())
        bottomInput.setText(region.bottom.toString())
        syncing = false
    }

    private fun numberInput(value: Int): EditText = EditText(context).apply {
        setText(value.toString())
        inputType = InputType.TYPE_CLASS_NUMBER
        textSize = 13f
        gravity = Gravity.CENTER
        setSelectAllOnFocus(true)
        setPadding(dp(4), dp(4), dp(4), dp(4))
        background = fieldBackground()
    }

    private fun fieldBackground() = GradientDrawable().apply {
        cornerRadius = 8 * density
        setColor(Color.parseColor("#0F000000"))
        setStroke(dp(1), Color.parseColor("#33000000"))
    }

    private fun actionButton(text: String, onClick: () -> Unit) = Button(context).apply {
        this.text = text
        isAllCaps = false
        textSize = 12f
        minHeight = 0
        minimumHeight = 0
        setPadding(dp(10), dp(6), dp(10), dp(6))
        setOnClickListener { onClick() }
    }

    private fun label(text: String) = TextView(context).apply {
        this.text = text
        textSize = 12f
        setTextColor(Color.parseColor("#5F5F5F"))
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, 0, dp(6), 0)
    }

    private fun tableLabel(text: String) = TextView(context).apply {
        this.text = text
        textSize = 12f
        setTextColor(Color.parseColor("#3F3F3F"))
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun cellParams(width: Int, height: Int) =
        TableRow.LayoutParams(dp(width), dp(height))

    private fun columnCaption(text: String) = TextView(context).apply {
        this.text = text
        textSize = 11f
        setTextColor(Color.parseColor("#7F7F7F"))
        gravity = Gravity.CENTER
    }

    /** Text-free checkbox: centring the drawable keeps its midpoint on the Switch axis. */
    private fun linkCheckBox(description: String) = CheckBox(context).apply {
        text = ""
        contentDescription = description
        setPadding(0, 0, 0, 0)
    }

    /** CompoundButton ignores gravity for its box; centre the whole box inside the cell. */
    private fun centerInCell(view: View) = FrameLayout(context).apply {
        addView(view, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ))
    }

    private fun inputParams() = LinearLayout.LayoutParams(dp(64), LinearLayout.LayoutParams.WRAP_CONTENT)

    private fun spacer(widthDp: Int) = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(dp(widthDp), 1)
    }

    private fun simpleWatcher(onChanged: () -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) = onChanged()
    }

    private fun dp(value: Int) = (value * density).toInt()

    private fun Edge.title() = when (this) {
        Edge.TOP -> "Top"
        Edge.BOTTOM -> "Bottom"
        Edge.LEFT -> "Left"
        Edge.RIGHT -> "Right"
    }

    /**
     * Draws the 1px red frame and turns edge drags into rectangle edits. The stroke is inset
     * by half a pixel so it fully covers the first column/row of the region and stays visible
     * when an edge sits at 0 or at the display boundary.
     */
    private class RegionBorderView(
        context: Context,
        val region: Rect,
        private val frame: Rect,
        private val snapFor: (Edge) -> EdgeSnap?
    ) : View(context) {
        private val density = context.resources.displayMetrics.density
        private val edgeSlop = 40 * density
        private val paint = Paint().apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 1f
            isAntiAlias = false
        }
        private val rectF = RectF()
        private var dragMode = MODE_NONE
        private var lastX = 0f
        private var lastY = 0f
        var onRegionChanged: ((Rect) -> Unit)? = null

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            rectF.set(
                region.left + 0.5f,
                region.top + 0.5f,
                region.right - 0.5f,
                region.bottom - 0.5f
            )
            canvas.drawRect(rectF, paint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragMode = hitTest(event.x, event.y)
                    return dragMode != MODE_NONE
                }
                MotionEvent.ACTION_MOVE -> {
                    if (dragMode == MODE_NONE) return false
                    lastX = event.x
                    lastY = event.y
                    moveEdge(event.x, event.y)
                    invalidate()
                    onRegionChanged?.invoke(region)
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragMode != MODE_NONE) {
                        // Re-apply at the release point so the final position also snaps.
                        if (event.actionMasked == MotionEvent.ACTION_UP) moveEdge(event.x, event.y)
                        dragMode = MODE_NONE
                        onRegionChanged?.invoke(region)
                        return true
                    }
                }
            }
            return false
        }

        private fun hitTest(x: Float, y: Float): Int {
            val nearLeft = abs(x - region.left) <= edgeSlop
            val nearRight = abs(x - region.right) <= edgeSlop
            val nearTop = abs(y - region.top) <= edgeSlop
            val nearBottom = abs(y - region.bottom) <= edgeSlop
            val insideX = x >= region.left - edgeSlop && x <= region.right + edgeSlop
            val insideY = y >= region.top - edgeSlop && y <= region.bottom + edgeSlop
            return when {
                nearLeft && insideY -> MODE_LEFT
                nearRight && insideY -> MODE_RIGHT
                nearTop && insideX -> MODE_TOP
                nearBottom && insideX -> MODE_BOTTOM
                else -> MODE_NONE
            }
        }

        private fun moveEdge(x: Float, y: Float) {
            val minSize = CaptureRegionStore.MIN_SIZE_PX
            when (dragMode) {
                MODE_LEFT -> {
                    var value = x.toInt().coerceIn(frame.left, region.right - minSize)
                    val snap = snapFor(Edge.LEFT)
                    if (snap?.enabled == true && abs(value - frame.left) < snap.threshold) value = frame.left
                    region.left = value
                }
                MODE_RIGHT -> {
                    var value = x.toInt().coerceIn(region.left + minSize, frame.right)
                    val snap = snapFor(Edge.RIGHT)
                    if (snap?.enabled == true && abs(frame.right - value) < snap.threshold) value = frame.right
                    region.right = value
                }
                MODE_TOP -> {
                    var value = y.toInt().coerceIn(frame.top, region.bottom - minSize)
                    val snap = snapFor(Edge.TOP)
                    if (snap?.enabled == true && abs(value - frame.top) < snap.threshold) value = frame.top
                    region.top = value
                }
                MODE_BOTTOM -> {
                    var value = y.toInt().coerceIn(region.top + minSize, frame.bottom)
                    val snap = snapFor(Edge.BOTTOM)
                    if (snap?.enabled == true && abs(frame.bottom - value) < snap.threshold) value = frame.bottom
                    region.bottom = value
                }
            }
        }

        private companion object {
            const val MODE_NONE = 0
            const val MODE_LEFT = 1
            const val MODE_RIGHT = 2
            const val MODE_TOP = 3
            const val MODE_BOTTOM = 4
        }
    }
}
