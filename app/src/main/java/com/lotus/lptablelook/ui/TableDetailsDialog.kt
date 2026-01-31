package com.lotus.lptablelook.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.lotus.lptablelook.R
import com.lotus.lptablelook.model.Table

class TableDetailsDialog(
    private val context: Context,
    private val table: Table,
    private val platformName: String = ""
) {

    private val dialog: Dialog = Dialog(context)
    private var onStatusChangeListener: ((Table) -> Unit)? = null
    private var onDetailsListener: ((Table) -> Unit)? = null

    init {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCancelable(true)

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_table_details, null)
        dialog.setContentView(view)

        val tvTableName = view.findViewById<TextView>(R.id.tvTableName)
        val tvStatus = view.findViewById<TextView>(R.id.tvStatus)
        val tvCapacity = view.findViewById<TextView>(R.id.tvCapacity)
        val tvPlatform = view.findViewById<TextView>(R.id.tvPlatform)
        val tvWaiter = view.findViewById<TextView>(R.id.tvWaiter)
        val waiterRow = view.findViewById<LinearLayout>(R.id.waiterRow)
        val btnClose = view.findViewById<ImageButton>(R.id.btnClose)
        val btnToggleStatus = view.findViewById<MaterialButton>(R.id.btnToggleStatus)
        val btnDetails = view.findViewById<MaterialButton>(R.id.btnDetails)
        val headerLayout = view.findViewById<LinearLayout>(R.id.headerLayout)

        // Set table name
        val displayName = if (table.name.isNotEmpty()) table.name else "Tisch ${table.number}"
        tvTableName.text = displayName

        // Set status and header color based on colorCode
        if (table.isOccupied) {
            tvStatus.text = "Besetzt"
            val statusColor = when (table.colorCode) {
                1 -> context.getColor(R.color.table_occupied_orange)
                2 -> context.getColor(R.color.table_occupied_blue)
                else -> context.getColor(R.color.table_occupied)
            }
            tvStatus.setTextColor(statusColor)
            headerLayout.setBackgroundColor(statusColor)
            btnToggleStatus.text = "Als frei markieren"

            // Show waiter name if available
            if (table.waiterName.isNotEmpty()) {
                waiterRow.visibility = View.VISIBLE
                tvWaiter.text = table.waiterName
            }
        } else {
            tvStatus.text = "Frei"
            tvStatus.setTextColor(context.getColor(R.color.table_available))
            headerLayout.setBackgroundColor(context.getColor(R.color.table_available))
            btnToggleStatus.text = "Als besetzt markieren"
            waiterRow.visibility = View.GONE
        }

        // Set capacity
        tvCapacity.text = "${table.capacity} Personen"

        // Set platform
        tvPlatform.text = platformName.ifEmpty { "Bereich ${table.platformId}" }

        // Close button
        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        // Toggle status button
        btnToggleStatus.setOnClickListener {
            onStatusChangeListener?.invoke(table)
            dialog.dismiss()
        }

        // Details button
        btnDetails.setOnClickListener {
            onDetailsListener?.invoke(table)
        }
    }

    fun setOnStatusChangeListener(listener: (Table) -> Unit): TableDetailsDialog {
        onStatusChangeListener = listener
        return this
    }

    fun setOnDetailsListener(listener: (Table) -> Unit): TableDetailsDialog {
        onDetailsListener = listener
        return this
    }

    fun show() {
        dialog.show()
    }

    fun dismiss() {
        if (dialog.isShowing) {
            dialog.dismiss()
        }
    }
}
