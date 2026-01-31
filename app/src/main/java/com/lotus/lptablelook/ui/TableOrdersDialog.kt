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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lotus.lptablelook.R
import com.lotus.lptablelook.model.OrderItem
import com.lotus.lptablelook.model.Table

class TableOrdersDialog(
    private val context: Context,
    private val table: Table,
    private val orders: List<OrderItem>,
    private val totalSum: Double
) {

    private val dialog: Dialog = Dialog(context)

    init {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCancelable(true)

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_table_orders, null)
        dialog.setContentView(view)

        // Set minimum width for dialog
        dialog.window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.85).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val tvTableName = view.findViewById<TextView>(R.id.tvTableName)
        val rvOrders = view.findViewById<RecyclerView>(R.id.rvOrders)
        val tvEmpty = view.findViewById<TextView>(R.id.tvEmpty)
        val tvTotal = view.findViewById<TextView>(R.id.tvTotal)
        val btnClose = view.findViewById<ImageButton>(R.id.btnClose)
        val waiterLayout = view.findViewById<LinearLayout>(R.id.waiterLayout)
        val tvWaiterName = view.findViewById<TextView>(R.id.tvWaiterName)

        // Set table name
        val displayName = if (table.name.isNotEmpty()) table.name else context.getString(R.string.table_number, table.number)
        tvTableName.text = context.getString(R.string.table_orders, displayName)

        // Set waiter info (show only if waiter name exists)
        if (table.waiterName.isNotEmpty()) {
            waiterLayout.visibility = View.VISIBLE
            tvWaiterName.text = table.waiterName
        } else {
            waiterLayout.visibility = View.GONE
        }

        // Setup RecyclerView
        rvOrders.layoutManager = LinearLayoutManager(context)

        if (orders.isEmpty()) {
            rvOrders.visibility = View.GONE
            tvEmpty.visibility = View.VISIBLE
        } else {
            rvOrders.visibility = View.VISIBLE
            tvEmpty.visibility = View.GONE
            rvOrders.adapter = OrderAdapter(orders)
        }

        // Set total
        android.util.Log.d("TableOrdersDialog", "Setting total: $totalSum, orders count: ${orders.size}")
        tvTotal.text = String.format("%.2f EUR", totalSum)

        // Close button
        btnClose.setOnClickListener {
            dialog.dismiss()
        }
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
