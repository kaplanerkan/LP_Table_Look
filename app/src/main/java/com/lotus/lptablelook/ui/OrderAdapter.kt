package com.lotus.lptablelook.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.lotus.lptablelook.R
import com.lotus.lptablelook.model.OrderItem

class OrderAdapter(
    private val orders: List<OrderItem>,
    private val onItemClick: ((OrderItem, Int) -> Unit)? = null
) : RecyclerView.Adapter<OrderAdapter.OrderViewHolder>() {

    private var selectedPosition: Int = RecyclerView.NO_POSITION

    class OrderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvQuantity: TextView = view.findViewById(R.id.tvQuantity)
        val tvProductName: TextView = view.findViewById(R.id.tvProductName)
        val tvPrice: TextView = view.findViewById(R.id.tvPrice)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_order, parent, false)
        return OrderViewHolder(view)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        val order = orders[position]

        holder.tvQuantity.text = "${order.formattedQuantity}x"
        holder.tvProductName.text = order.productName
        holder.tvPrice.text = order.formattedTotal

        // Set selected state
        holder.itemView.isSelected = (position == selectedPosition)
        holder.itemView.isActivated = (position == selectedPosition)

        // Handle click
        holder.itemView.setOnClickListener {
            val previousSelected = selectedPosition
            selectedPosition = holder.adapterPosition

            // Update previous and current items
            if (previousSelected != RecyclerView.NO_POSITION) {
                notifyItemChanged(previousSelected)
            }
            notifyItemChanged(selectedPosition)

            // Invoke callback
            onItemClick?.invoke(order, holder.adapterPosition)
        }
    }

    override fun getItemCount(): Int = orders.size

    fun clearSelection() {
        val previousSelected = selectedPosition
        selectedPosition = RecyclerView.NO_POSITION
        if (previousSelected != RecyclerView.NO_POSITION) {
            notifyItemChanged(previousSelected)
        }
    }
}
