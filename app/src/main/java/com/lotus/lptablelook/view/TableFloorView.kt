package com.lotus.lptablelook.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.toColorInt
import com.lotus.lptablelook.R
import com.lotus.lptablelook.model.Table

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
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // When view size changes (e.g., orientation change), constrain tables within bounds
        if (w > 0 && h > 0 && tables.isNotEmpty()) {
            constrainTablePositions()
        }
    }

    private fun constrainTablePositions() {
        val chairMargin = 30f * tableScale // Extra margin for chairs
        for (table in tables) {
            val scaledWidth = table.width * tableScale
            val scaledHeight = table.height * tableScale
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

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), floorPaint)

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
    }

    private fun drawChairs(canvas: Canvas, table: Table, tableRect: RectF) {
        val chairSize = 20f * tableScale
        val chairOffset = 8f * tableScale

        when (table.capacity) {
            2 -> {
                canvas.drawRoundRect(
                    tableRect.left - chairSize - chairOffset,
                    tableRect.centerY() - chairSize / 2,
                    tableRect.left - chairOffset,
                    tableRect.centerY() + chairSize / 2,
                    4f, 4f, chairPaint
                )
                canvas.drawRoundRect(
                    tableRect.right + chairOffset,
                    tableRect.centerY() - chairSize / 2,
                    tableRect.right + chairSize + chairOffset,
                    tableRect.centerY() + chairSize / 2,
                    4f, 4f, chairPaint
                )
            }
            4 -> {
                val positions = listOf(
                    tableRect.centerX() - chairSize / 2 to tableRect.top - chairSize - chairOffset,
                    tableRect.centerX() - chairSize / 2 to tableRect.bottom + chairOffset,
                    tableRect.left - chairSize - chairOffset to tableRect.centerY() - chairSize / 2,
                    tableRect.right + chairOffset to tableRect.centerY() - chairSize / 2
                )

                for ((cx, cy) in positions) {
                    canvas.drawRoundRect(
                        cx, cy, cx + chairSize, cy + chairSize,
                        4f, 4f, chairPaint
                    )
                }
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
