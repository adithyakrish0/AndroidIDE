package com.example.foldermind

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AgentLogger(private val context: Context) {
    private val logFile = File(context.filesDir, "agent_debug.log")
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    suspend fun log(message: String) {
        withContext(Dispatchers.IO) {
            try {
                val timestamp = dateFormat.format(Date())
                val formattedMessage = "[$timestamp] $message\n"
                logFile.appendText(formattedMessage)
            } catch (e: Exception) {
                // Ignore logging errors to prevent crashing the agent
            }
        }
    }

    suspend fun getLogs(): String {
        return withContext(Dispatchers.IO) {
            try {
                if (logFile.exists()) {
                    logFile.readText()
                } else {
                    "No logs available."
                }
            } catch (e: Exception) {
                "Error reading logs: ${e.message}"
            }
        }
    }

    suspend fun clearLogs() {
        withContext(Dispatchers.IO) {
            try {
                if (logFile.exists()) {
                    logFile.writeText("")
                }
            } catch (e: Exception) {
                // Ignore logging errors
            }
        }
    }
}
