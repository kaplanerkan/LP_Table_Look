package com.lotus.lptablelook.model

data class
OrderItem(
    val id: Int = 0,
    val productCode: String = "",
    val productName: String = "",
    val quantity: Double = 0.0,
    val unitPrice: Double = 0.0,
    val total: Double = 0.0,
    val discount: Double = 0.0,
    val type: Int = 0  // 0=article, 1=note, etc.
) {
    val formattedQuantity: String
        get() = if (quantity == quantity.toInt().toDouble()) {
            quantity.toInt().toString()
        } else {
            String.format("%.2f", quantity)
        }

    val formattedTotal: String
        get() = String.format("%.2f €", total - discount)
}
