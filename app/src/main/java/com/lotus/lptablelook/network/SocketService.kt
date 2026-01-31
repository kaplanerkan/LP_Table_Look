package com.lotus.lptablelook.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket

class SocketService(
    private val host: String,
    private val port: Int
) {
    companion object {
        private const val TAG = "SocketService"

        const val DATA_FINISH = ";SON;"
        const val DATA_SEPARATOR = ";!;"
        const val EMPTY = ";BOS;"
        const val ERROR = ";ERROR;"
        const val RETURN_FAULT = "False"
        const val RETURN_OK = "True"

        private const val CONNECTION_TIMEOUT = 30000
        private const val READ_TIMEOUT = 10000
    }

    init {
        Log.d(TAG, "SocketService created - host: $host, port: $port")
    }

    sealed class SocketResult {
        data class Success(val data: String) : SocketResult()
        data class Error(val message: String) : SocketResult()
    }

    suspend fun sendMessage(message: String): SocketResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "sendMessage - host: $host, port: $port")
        Log.d(TAG, "sendMessage - message: $message")

        var socket: Socket? = null
        var writer: PrintWriter? = null
        var reader: BufferedReader? = null

        try {
            // Create new socket for each request (like original Java code)
            socket = Socket()
            socket.soTimeout = READ_TIMEOUT

            Log.d(TAG, "Connecting to $host:$port with timeout $CONNECTION_TIMEOUT ms...")
            socket.connect(InetSocketAddress(host, port), CONNECTION_TIMEOUT)

            if (!socket.isConnected) {
                Log.e(TAG, "Socket not connected after connect() call")
                return@withContext SocketResult.Error("Verbindung fehlgeschlagen")
            }

            Log.d(TAG, "Socket connected successfully")

            // Create streams (UTF-8 encoding like original)
            reader = BufferedReader(InputStreamReader(socket.getInputStream(), "UTF-8"))
            writer = PrintWriter(BufferedWriter(OutputStreamWriter(socket.getOutputStream(), "UTF-8")), true)

            // Send message with newline (println adds \n)
            Log.d(TAG, "Sending: $message")
            writer.println(message)
            writer.flush()

            // Read response
            Log.d(TAG, "Reading response...")
            val response = readResponse(reader)
            Log.d(TAG, "Response: $response")

            SocketResult.Success(response)

        } catch (e: Exception) {
            val errorMsg = "${e.javaClass.simpleName}: ${e.message}"
            Log.e(TAG, "Socket error: $errorMsg", e)
            SocketResult.Error(errorMsg)
        } finally {
            // Clean up resources
            try { writer?.close() } catch (_: Exception) {}
            try { reader?.close() } catch (_: Exception) {}
            try { if (socket != null && !socket.isClosed) socket.close() } catch (_: Exception) {}
        }
    }

    private fun readResponse(reader: BufferedReader): String {
        val response = StringBuilder()
        var line: String?

        while (reader.readLine().also { line = it } != null) {
            response.append(line)
            // Stop reading when we find DATA_FINISH marker
            if (line?.contains(DATA_FINISH) == true) {
                break
            }
        }

        return response.toString()
    }
}
