package com.lotus.lptablelook.network

enum class ServerCommand(val code: Int) {
    CMD_REGISTER_PHONE(56),
    CMD_GET_PLATFORMS(22),
    CMD_GET_ALL_TABLES(39),
    CMD_GET_FULL_TABLES(28),
    CMD_GET_TABLE_ORDERS(32),
    CMD_GET_TABLE_SUM(27),
    CMD_GET_PRODUCTS(23),
    CMD_GET_PRODUCT_GROUPS(19),
    CMD_TABLE_STATUS(40),
    CMD_PING(0);

    // Format code as 2-digit string (e.g., 0 -> "00", 56 -> "56")
    private fun getCodeString(): String = String.format("%02d", code)

    fun toMessage(vararg params: String): String {
        val codeStr = getCodeString()
        val paramString = params.joinToString(SocketService.DATA_SEPARATOR)
        return if (paramString.isNotEmpty()) {
            "$codeStr${SocketService.DATA_SEPARATOR}$paramString"
        } else {
            codeStr
        }
    }
}
