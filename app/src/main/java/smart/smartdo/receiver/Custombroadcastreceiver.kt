package smart.smartdo.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class CustomBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Broadcast received: ${intent.action}")

        when (intent.action) {
            ACTION_COMMAND -> {
                val command = intent.getStringExtra(EXTRA_COMMAND)
                val data = intent.getStringExtra(EXTRA_DATA)
                handleCommand(context, command, data)
            }
            ACTION_STATUS -> {
                handleStatusRequest(context)
            }
        }
    }

    private fun handleCommand(context: Context, command: String?, data: String?) {
        Log.d(TAG, "Command: $command, Data: $data")

        when (command) {
            "LOCK" -> {
                // Trigger lock device
                sendLocalBroadcast(context, "LOCK_DEVICE")
            }
            "REBOOT" -> {
                // Trigger reboot
                sendLocalBroadcast(context, "REBOOT_DEVICE")
            }
            "HIDE_APP" -> {
                // Hide specific app
                sendLocalBroadcast(context, "HIDE_APP", data)
            }
            "SHOW_APP" -> {
                // Show specific app
                sendLocalBroadcast(context, "SHOW_APP", data)
            }
            "KIOSK_MODE" -> {
                // Enable/disable kiosk mode
                sendLocalBroadcast(context, "TOGGLE_KIOSK", data)
            }
        }
    }

    private fun handleStatusRequest(context: Context) {
        Log.d(TAG, "Status request received")
        // Send status response
        val responseIntent = Intent("smart.smartdo.ACTION_STATUS_RESPONSE").apply {
            putExtra("status", "active")
            putExtra("timestamp", System.currentTimeMillis())
        }
        context.sendBroadcast(responseIntent)
    }

    private fun sendLocalBroadcast(context: Context, action: String, data: String? = null) {
        val intent = Intent("smart.smartdo.INTERNAL_ACTION").apply {
            putExtra("internal_action", action)
            data?.let { putExtra("data", it) }
        }
        context.sendBroadcast(intent)
    }

    companion object {
        private const val TAG = "CustomBroadcastReceiver"
        const val ACTION_COMMAND = "smart.smartdo.ACTION_COMMAND"
        const val ACTION_STATUS = "smart.smartdo.ACTION_STATUS"
        const val EXTRA_COMMAND = "command"
        const val EXTRA_DATA = "data"
    }
}