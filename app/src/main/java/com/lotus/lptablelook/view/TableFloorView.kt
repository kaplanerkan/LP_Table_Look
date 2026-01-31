package com.lotus.lptablelook.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import com.lotus.lptablelook.R
import com.lotus.lptablelook.SettingsActivity
import com.lotus.lptablelook.model.Table
import java.io.File

class TableFloorView(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var tables: MutableList<Table> = mutableListOf()
    private var isEditMode: Boolean = false
    private var selectedTable: Table? = null
    private var dragOffsetX: Float = 0f
    private var dragOffsetY: Float = 0f

    var tableScale: Float = 1.0f
        set(value) {
            field = value
            if (width > 0 && height > 0) {
                constrainTablePositions()
            }
            invalidate()
        }

    private val tablePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val tableBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = context.getColor(R.color.table_border)
    }

    private val tableTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.table_text)
        textAlign = Paint.Align.CENTER
        textSize = 36f
        typeface = Typeface.DEFAULT_BOLD
    }

    private val capacityTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.table_text)
        textAlign = Paint.Align.CENTER
        textSize = 24f
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = context.getColor(R.color.grid_line)
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }

    private val selectedBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        color = context.getColor(R.color.edit_mode_highlight)
    }

    private val chairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = "#795548".toColorInt()
    }

    private val floorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = context.getColor(R.color.background_floor)
    }

    // Price badge paints
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = context.getColor(R.color.table_occupied)  // Red background
    }

    private val badgeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = Color.WHITE
    }

    private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 18f
        typeface = Typeface.DEFAULT_BOLD
    }

    // Background image
    private var backgroundBitmap: Bitmap? = null
    private var scaledBackgroundBitmap: Bitmap? = null
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
    }

    // Background style drawables
    private val backgroundDrawables: List<Drawable?> = listOf(
        ContextCompat.getDrawable(context, R.drawable.floor_gradient_blue),
        ContextCompat.getDrawable(context, R.drawable.floor_gradient_warm),
        ContextCompat.getDrawable(context, R.drawable.floor_gradient_dark),
        ContextCompat.getDrawable(context, R.drawable.floor_gradient_green),
        ContextCompat.getDrawable(context, R.drawable.floor_gradient_purple)
    )
    private var currentBackgroundStyle: Int = 0
    private var showChairs: Boolean = true
    private var showPrices: Boolean = true
    private var showCurrencySymbol: Boolean = true

    var onTablePositionChanged: ((Table) -> Unit)? = null
    var onTableClicked: ((Table) -> Unit)? = null
    var onTableLongClicked: ((Table) -> Unit)? = null

    // Long click detection
    private val longClickHandler = Handler(Looper.getMainLooper())
    private var longClickRunnable: Runnable? = null
    private var isLongClickTriggered = false
    private var touchStartX = 0f
    private var touchStartY = 0f
    private val longClickTimeout = 500L // 500ms for long click
    private val touchSlop = 20f // Movement threshold to cancel long click

    fun setTables(tableList: List<Table>) {
        tables.clear()
        tables.addAll(tableList)
        // Constrain table positions within view bounds
        if (width > 0 && height > 0) {
            constrainTablePositions()
        }
        // Debug logging for price badges
        val occupiedTables = tableList.filter { it.isOccupied }
        Log.d(TAG, "setTables: ${tableList.size} tables, ${occupiedTables.size} occupied, showPrices=$showPrices")
        occupiedTables.forEach {
            Log.d(TAG, "  Occupied table: ${it.name}, totalSum=${it.totalSum}")
        }
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // When view size changes (e.g., orientation change), constrain tables within bounds
        if (w > 0 && h > 0 && tables.isNotEmpty()) {
            constrainTablePositions()
        }
        // Scale background image to new size
        if (w > 0 && h > 0) {
            scaleBackgroundBitmap(w, h)
        }
    }

    /**
     * Load background image from app's internal storage
     */
    fun loadBackgroundImage() {
        val file = File(context.filesDir, SettingsActivity.FLOOR_PLAN_FILENAME)
        if (file.exists()) {
            try {
                backgroundBitmap = BitmapFactory.decodeFile(file.absolutePath)
                if (width > 0 && height > 0) {
                    scaleBackgroundBitmap(width, height)
                }
                invalidate()
                Log.d(TAG, "Background image loaded: ${file.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading background image", e)
                backgroundBitmap = null
                scaledBackgroundBitmap = null
            }
        } else {
            backgroundBitmap = null
            scaledBackgroundBitmap = null
            invalidate()
            Log.d(TAG, "No background image found")
        }
    }

    /**
     * Set background style (0=Blue, 1=Warm, 2=Dark, 3=Green, 4=Purple)
     */
    fun setBackgroundStyle(style: Int) {
        currentBackgroundStyle = style.coerceIn(0, backgroundDrawables.size - 1)
        invalidate()
        Log.d(TAG, "Background style set to: $currentBackgroundStyle")
    }

    /**
     * Set whether to show chairs around tables
     */
    fun setShowChairs(show: Boolean) {
        showChairs = show
        invalidate()
        Log.d(TAG, "Show chairs set to: $showChairs")
    }

    /**
     * Set whether to show price badges on occupied tables
     */
    fun setShowPrices(show: Boolean) {
        showPrices = show
        invalidate()
        Log.d(TAG, "Show prices set to: $showPrices")
    }

    /**
     * Set whether to show currency symbol (€) in price badges
     */
    fun setShowCurrencySymbol(show: Boolean) {
        showCurrencySymbol = show
        invalidate()
        Log.d(TAG, "Show currency symbol set to: $showCurrencySymbol")
    }

    private fun scaleBackgroundBitmap(w: Int, h: Int) {
        backgroundBitmap?.let { original ->
            try {
                scaledBackgroundBitmap = Bitmap.createScaledBitmap(original, w, h, true)
            } catch (e: Exception) {
                Log.e(TAG, "Error scaling background bitmap", e)
                scaledBackgroundBitmap = null
            }
        }
    }

    private fun constrainTablePositions() {
        for (table in tables) {
            val scaledWidth = table.width * tableScale
            val scaledHeight = table.height * tableScale
            // Chair margin proportional to table size
            val tableShortSide = minOf(scaledWidth, scaledHeight)
            val chairMargin = tableShortSide * 0.3f

            val maxX = (width - scaledWidth - chairMargin).coerceAtLeast(chairMargin)
            val maxY = (height - scaledHeight - chairMargin).coerceAtLeast(chairMargin)

            table.positionX = table.positionX.coerceIn(chairMargin, maxX)
            table.positionY = table.positionY.coerceIn(chairMargin, maxY)
        }
    }

    fun getTables(): List<Table> = tables.toList()

    fun setEditMode(enabled: Boolean) {
        isEditMode = enabled
        selectedTable = null
        invalidate()
    }

    fun isEditModeEnabled(): Boolean = isEditMode

    fun addTable(table: Table) {
        tables.add(table)
        invalidate()
    }

    fun removeTable(table: Table) {
        tables.remove(table)
        if (selectedTable == table) {
            selectedTable = null
        }
        invalidate()
    }

    fun getSelectedTable(): Table? = selectedTable

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw background - custom image if available, otherwise selected gradient style
        val bgBitmap = scaledBackgroundBitmap
        if (bgBitmap != null && !bgBitmap.isRecycled) {
            canvas.drawBitmap(bgBitmap, 0f, 0f, backgroundPaint)
        } else {
            // Draw selected gradient background
            val styleIndex = currentBackgroundStyle.coerceIn(0, backgroundDrawables.size - 1)
            backgroundDrawables[styleIndex]?.let { drawable ->
                drawable.setBounds(0, 0, width, height)
                drawable.draw(canvas)
            } ?: run {
                // Fallback to solid color if drawable fails
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), floorPaint)
            }
        }

        if (isEditMode) {
            drawGrid(canvas)
        }

        for (table in tables) {
            drawTable(canvas, table)
        }
    }

    private fun drawGrid(canvas: Canvas) {
        val gridSize = 50f * tableScale

        var x = 0f
        while (x < width) {
            canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint)
            x += gridSize
        }

        var y = 0f
        while (y < height) {
            canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
            y += gridSize
        }
    }

    private fun drawTable(canvas: Canvas, table: Table) {
        val scaledWidth = table.width * tableScale
        val scaledHeight = table.height * tableScale
        val x = table.positionX
        val y = table.positionY

        // Set colors based on table status and colorCode
        val isOccupied = table.isOccupied
        tablePaint.color = if (isOccupied) {
            // Use colorCode for occupied tables: 0=green(occupied), 1=orange, 2=blue
            when (table.colorCode) {
                1 -> context.getColor(R.color.table_occupied_orange)  // Orange
                2 -> context.getColor(R.color.table_occupied_blue)    // Blue
                else -> context.getColor(R.color.table_occupied)       // Red (default for occupied)
            }
        } else {
            context.getColor(R.color.table_available)  // Green for free tables
        }

        // Set text colors based on status
        val textColor = context.getColor(R.color.table_text)
        tableTextPaint.color = textColor
        capacityTextPaint.color = textColor

        // Set border color based on status
        tableBorderPaint.color = if (isOccupied) {
            context.getColor(R.color.table_border)
        } else {
            context.getColor(R.color.table_border_available)
        }

        val tableRect = RectF(x, y, x + scaledWidth, y + scaledHeight)

        drawChairs(canvas, table, tableRect)

        // Draw table shape based on isOval flag
        if (table.isOval) {
            // Draw oval/ellipse
            canvas.drawOval(tableRect, tablePaint)
            if (isEditMode && selectedTable == table) {
                canvas.drawOval(tableRect, selectedBorderPaint)
            } else {
                canvas.drawOval(tableRect, tableBorderPaint)
            }
        } else {
            // Draw rounded rectangle
            val cornerRadius = 12f * tableScale
            canvas.drawRoundRect(tableRect, cornerRadius, cornerRadius, tablePaint)
            if (isEditMode && selectedTable == table) {
                canvas.drawRoundRect(tableRect, cornerRadius, cornerRadius, selectedBorderPaint)
            } else {
                canvas.drawRoundRect(tableRect, cornerRadius, cornerRadius, tableBorderPaint)
            }
        }

        // Draw table name (use name if available, otherwise number)
        tableTextPaint.textSize = 36f * tableScale
        val displayText = if (table.name.isNotEmpty()) table.name else table.number.toString()
        val textY = y + (scaledHeight / 2) - ((tableTextPaint.descent() + tableTextPaint.ascent()) / 2)
        canvas.drawText(displayText, x + scaledWidth / 2, textY - 10f * tableScale, tableTextPaint)

        // Show waiter name only if occupied and has waiter
        if (isOccupied && table.waiterName.isNotEmpty()) {
            capacityTextPaint.textSize = 20f * tableScale
            canvas.drawText(table.waiterName, x + scaledWidth / 2, textY + 20f * tableScale, capacityTextPaint)
        }

        // Draw price badge if table is occupied and showPrices is enabled
        if (isOccupied && showPrices && table.totalSum > 0) {
            Log.d(TAG, "Drawing price badge for table ${table.name}: totalSum=${table.totalSum}")
            drawPriceBadge(canvas, tableRect, table.totalSum)
        } else if (isOccupied) {
            Log.d(TAG, "NOT drawing badge for table ${table.name}: showPrices=$showPrices, totalSum=${table.totalSum}")
        }
    }

    private fun drawPriceBadge(canvas: Canvas, tableRect: RectF, totalSum: Double) {
        // Format the price text (with or without currency symbol based on setting)
        val priceText = if (showCurrencySymbol) String.format("%.2f€", totalSum) else String.format("%.2f", totalSum)

        // Calculate badge dimensions - smaller text
        badgeTextPaint.textSize = 11f * tableScale
        val textWidth = badgeTextPaint.measureText(priceText)
        val paddingH = 6f * tableScale
        val paddingV = 3f * tableScale
        val badgeWidth = textWidth + paddingH * 2
        val badgeHeight = badgeTextPaint.textSize + paddingV * 2
        val cornerRadius = 4f * tableScale

        // Position badge at top-right corner, protruding outside the table
        val badgeLeft = tableRect.right - badgeWidth * 0.7f  // 70% inside, 30% outside
        val badgeTop = tableRect.top - badgeHeight * 0.5f    // 50% above the table
        val badgeRect = RectF(badgeLeft, badgeTop, badgeLeft + badgeWidth, badgeTop + badgeHeight)

        // Draw badge background
        canvas.drawRoundRect(badgeRect, cornerRadius, cornerRadius, badgePaint)

        // Draw badge border
        canvas.drawRoundRect(badgeRect, cornerRadius, cornerRadius, badgeBorderPaint)

        // Draw price text
        val textY = badgeTop + badgeHeight / 2 - (badgeTextPaint.descent() + badgeTextPaint.ascent()) / 2
        canvas.drawText(priceText, badgeLeft + badgeWidth / 2, textY, badgeTextPaint)
    }

    private fun drawChairs(canvas: Canvas, table: Table, tableRect: RectF) {
        // If showChairs is false or capacity is 0, don't draw any chairs
        if (!showChairs) return
        val capacity = table.capacity.coerceIn(0, 9)
        if (capacity == 0) return

        // Chair size proportional to table - approximately 20% of shorter dimension
        val tableShortSide = minOf(tableRect.width(), tableRect.height())
        val chairSize = tableShortSide * 0.22f
        val chairOffset = chairSize * 0.3f

        // Calculate chair positions around the table
        val positions = calculateChairPositions(tableRect, capacity, chairSize, chairOffset)

        // Draw each chair based on style
        for ((cx, cy) in positions) {
            drawChair(canvas, cx, cy, chairSize, table.chairStyle)
        }
    }

    private fun calculateChairPositions(
        tableRect: RectF,
        capacity: Int,
        chairSize: Float,
        chairOffset: Float
    ): List<Pair<Float, Float>> {
        val positions = mutableListOf<Pair<Float, Float>>()

        // Distribution strategy for 1-9 chairs
        // Top, Bottom, Left, Right sides
        val topCount: Int
        val bottomCount: Int
        val leftCount: Int
        val rightCount: Int

        when (capacity) {
            1 -> { topCount = 1; bottomCount = 0; leftCount = 0; rightCount = 0 }
            2 -> { topCount = 0; bottomCount = 0; leftCount = 1; rightCount = 1 }
            3 -> { topCount = 1; bottomCount = 1; leftCount = 0; rightCount = 1 }
            4 -> { topCount = 1; bottomCount = 1; leftCount = 1; rightCount = 1 }
            5 -> { topCount = 2; bottomCount = 2; leftCount = 0; rightCount = 1 }
            6 -> { topCount = 2; bottomCount = 2; leftCount = 1; rightCount = 1 }
            7 -> { topCount = 2; bottomCount = 2; leftCount = 1; rightCount = 2 }
            8 -> { topCount = 2; bottomCount = 2; leftCount = 2; rightCount = 2 }
            9 -> { topCount = 3; bottomCount = 3; leftCount = 1; rightCount = 2 }
            else -> { topCount = 1; bottomCount = 1; leftCount = 1; rightCount = 1 }
        }

        // Top chairs
        if (topCount > 0) {
            val spacing = tableRect.width() / (topCount + 1)
            for (i in 1..topCount) {
                val x = tableRect.left + spacing * i - chairSize / 2
                val y = tableRect.top - chairSize - chairOffset
                positions.add(Pair(x, y))
            }
        }

        // Bottom chairs
        if (bottomCount > 0) {
            val spacing = tableRect.width() / (bottomCount + 1)
            for (i in 1..bottomCount) {
                val x = tableRect.left + spacing * i - chairSize / 2
                val y = tableRect.bottom + chairOffset
                positions.add(Pair(x, y))
            }
        }

        // Left chairs
        if (leftCount > 0) {
            val spacing = tableRect.height() / (leftCount + 1)
            for (i in 1..leftCount) {
                val x = tableRect.left - chairSize - chairOffset
                val y = tableRect.top + spacing * i - chairSize / 2
                positions.add(Pair(x, y))
            }
        }

        // Right chairs
        if (rightCount > 0) {
            val spacing = tableRect.height() / (rightCount + 1)
            for (i in 1..rightCount) {
                val x = tableRect.right + chairOffset
                val y = tableRect.top + spacing * i - chairSize / 2
                positions.add(Pair(x, y))
            }
        }

        return positions
    }

    private fun drawChair(canvas: Canvas, x: Float, y: Float, size: Float, style: Int) {
        val rect = RectF(x, y, x + size, y + size)

        when (style) {
            0 -> { // Round - filled circle
                canvas.drawOval(rect, chairPaint)
            }
            1 -> { // Top view - square with back
                val cornerRadius = size * 0.15f
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, chairPaint)
                // Small back line
                val backPaint = Paint(chairPaint).apply { color = 0xFF5D4037.toInt() }
                canvas.drawRect(x, y, x + size, y + size * 0.25f, backPaint)
            }
            2 -> { // Simple - filled circle (lighter)
                val simplePaint = Paint(chairPaint).apply { color = 0xFF8D6E63.toInt() }
                canvas.drawOval(rect, simplePaint)
            }
            3 -> { // Person - circle with head indicator
                canvas.drawOval(rect, chairPaint)
                // Small head circle on top
                val headSize = size * 0.45f
                val headRect = RectF(
                    x + size / 2 - headSize / 2,
                    y - headSize * 0.2f,
                    x + size / 2 + headSize / 2,
                    y + headSize * 0.8f
                )
                canvas.drawOval(headRect, chairPaint)
            }
            4 -> { // Arc - half circle
                val arcRect = RectF(x, y - size / 2, x + size, y + size / 2)
                canvas.drawArc(arcRect, 0f, 180f, true, chairPaint)
            }
            else -> { // Default round
                canvas.drawOval(rect, chairPaint)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        Log.d(TAG, "onTouchEvent: action=${event.action}, x=$x, y=$y, isEditMode=$isEditMode, tableCount=${tables.size}")

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val touchedTable = findTableAt(x, y)
                Log.d(TAG, "ACTION_DOWN: touchedTable=${touchedTable?.name ?: "null"}")
                if (touchedTable != null) {
                    selectedTable = touchedTable
                    touchStartX = x
                    touchStartY = y
                    isLongClickTriggered = false

                    // Start long click detection only in edit mode
                    if (isEditMode) {
                        dragOffsetX = x - touchedTable.positionX
                        dragOffsetY = y - touchedTable.positionY

                        longClickRunnable = Runnable {
                            if (selectedTable != null && !isLongClickTriggered) {
                                isLongClickTriggered = true
                                Log.d(TAG, "Long click triggered for ${selectedTable?.name}")
                                onTableLongClicked?.invoke(selectedTable!!)
                            }
                        }
                        longClickHandler.postDelayed(longClickRunnable!!, longClickTimeout)
                        invalidate()
                    }
                    return true
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (isEditMode && selectedTable != null) {
                    // Check if moved beyond threshold - cancel long click
                    val dx = Math.abs(x - touchStartX)
                    val dy = Math.abs(y - touchStartY)
                    if (dx > touchSlop || dy > touchSlop) {
                        cancelLongClick()
                    }

                    // Only drag if long click wasn't triggered
                    if (!isLongClickTriggered) {
                        val table = selectedTable ?: return false
                        val newX = (x - dragOffsetX).coerceIn(0f, width - table.width * tableScale)
                        val newY = (y - dragOffsetY).coerceIn(0f, height - table.height * tableScale)
                        table.positionX = newX
                        table.positionY = newY
                        invalidate()
                    }
                    return true
                }
            }

            MotionEvent.ACTION_UP -> {
                cancelLongClick()
                Log.d(TAG, "ACTION_UP: selectedTable=${selectedTable?.name ?: "null"}, longClickTriggered=$isLongClickTriggered")

                // Don't process normal click/drag if long click was triggered
                if (!isLongClickTriggered) {
                    selectedTable?.let { table ->
                        if (isEditMode) {
                            val gridSize = 25f * tableScale
                            table.positionX = (table.positionX / gridSize).toInt() * gridSize
                            table.positionY = (table.positionY / gridSize).toInt() * gridSize
                            onTablePositionChanged?.invoke(table)
                            Log.d(TAG, "Edit mode: position changed for ${table.name}")
                        } else {
                            Log.d(TAG, "Normal mode: invoking onTableClicked for ${table.name}")
                            onTableClicked?.invoke(table)
                        }
                        invalidate()
                    }
                }
                selectedTable = null
                isLongClickTriggered = false
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                cancelLongClick()
                selectedTable = null
                isLongClickTriggered = false
            }
        }

        return super.onTouchEvent(event)
    }

    private fun cancelLongClick() {
        longClickRunnable?.let {
            longClickHandler.removeCallbacks(it)
        }
        longClickRunnable = null
    }

    companion object {
        private const val TAG = "TableFloorView"
    }

    private fun findTableAt(x: Float, y: Float): Table? {
        for (table in tables.reversed()) {
            val scaledWidth = table.width * tableScale
            val scaledHeight = table.height * tableScale
            val rect = RectF(
                table.positionX,
                table.positionY,
                table.positionX + scaledWidth,
                table.positionY + scaledHeight
            )
            if (rect.contains(x, y)) {
                return table
            }
        }
        return null
    }
}
