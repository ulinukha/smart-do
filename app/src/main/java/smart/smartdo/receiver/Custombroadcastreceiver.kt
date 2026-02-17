package smart.smartdo.receiver

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import smart.smartdo.admin.SmartDoDeviceAdminReceiver

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
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, SmartDoDeviceAdminReceiver::class.java)

        Log.d(TAG, "Command: $command, Data: $data")

        when (command) {
            "CLEAR_DATA" -> handleClearData(context, dpm, adminComponent, data)
            "LOCK" -> sendInternalBroadcast(context, "LOCK_DEVICE")
            "REBOOT" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                    dpm.isDeviceOwnerApp(context.packageName)
                ) {
                    dpm.reboot(adminComponent)
                }
            }
            "HIDE_APP" -> sendInternalBroadcast(context, "HIDE_APP", data)
            "SHOW_APP" -> sendInternalBroadcast(context, "SHOW_APP", data)
            "KIOSK_MODE" -> sendInternalBroadcast(context, "TOGGLE_KIOSK", data)
        }
    }

    /**
     * Handle CLEAR_DATA command.
     * Support single: "com.netflix.ninja"
     * Support multi:  "com.netflix.ninja,com.google.android.youtube.tv"
     */
    private fun handleClearData(
        context: Context,
        dpm: DevicePolicyManager,
        adminComponent: ComponentName,
        data: String?
    ) {
        if (data.isNullOrBlank()) {
            Log.w(TAG, "CLEAR_DATA: data kosong, tidak ada package yang dihapus")
            return
        }

        if (!dpm.isDeviceOwnerApp(context.packageName)) {
            Log.e(TAG, "CLEAR_DATA: App bukan Device Owner, operasi dibatalkan")
            sendStatusBroadcast(context, "CLEAR_DATA_FAILED", data, "NOT_DEVICE_OWNER")
            return
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            Log.e(TAG, "CLEAR_DATA: API level tidak support (butuh >= 22)")
            sendStatusBroadcast(context, "CLEAR_DATA_FAILED", data, "API_NOT_SUPPORTED")
            return
        }

        val packages = data.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        Log.d(TAG, "CLEAR_DATA: Memproses ${packages.size} package(s): $packages")

        packages.forEach { packageName ->
            clearSinglePackage(context, dpm, adminComponent, packageName)
        }
    }

    private fun clearSinglePackage(
        context: Context,
        dpm: DevicePolicyManager,
        adminComponent: ComponentName,
        packageName: String
    ) {
        try {
            Log.d(TAG, "CLEAR_DATA: Menghapus data untuk package: $packageName")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                dpm.clearApplicationUserData(
                    adminComponent,
                    packageName,
                    context.mainExecutor,
                    { pkg, succeeded ->
                        if (succeeded) {
                            Log.d(TAG, "CLEAR_DATA: Berhasil menghapus data $pkg")
                            sendStatusBroadcast(context, "CLEAR_DATA_SUCCESS", pkg, "OK")
                        } else {
                            Log.e(TAG, "CLEAR_DATA: Gagal menghapus data $pkg")
                            sendStatusBroadcast(context, "CLEAR_DATA_FAILED", pkg, "FAILED")
                        }
                    }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "CLEAR_DATA: Exception saat menghapus data $packageName", e)
            sendStatusBroadcast(context, "CLEAR_DATA_FAILED", packageName, e.message ?: "EXCEPTION")
        }
    }

    private fun handleStatusRequest(context: Context) {
        Log.d(TAG, "Status request received")
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val responseIntent = Intent(ACTION_STATUS_RESPONSE).apply {
            putExtra("status", "active")
            putExtra("is_device_owner", dpm.isDeviceOwnerApp(context.packageName))
            putExtra("timestamp", System.currentTimeMillis())
        }
        context.sendBroadcast(responseIntent)
    }

    /**
     * Kirim hasil operasi kembali ke com.smartiv.hospitality
     */
    private fun sendStatusBroadcast(
        context: Context,
        event: String,
        packageName: String,
        result: String
    ) {
        val intent = Intent(ACTION_STATUS_RESPONSE).apply {
            putExtra("event", event)
            putExtra("package", packageName)
            putExtra("result", result)
            putExtra("timestamp", System.currentTimeMillis())
        }
        context.sendBroadcast(intent)
        Log.d(TAG, "Status dikirim ke hospitality: event=$event, pkg=$packageName, result=$result")
    }

    /**
     * Kirim ke internal broadcast (ditangkap oleh internalReceiver di MainActivity)
     */
    private fun sendInternalBroadcast(context: Context, action: String, data: String? = null) {
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
        const val ACTION_STATUS_RESPONSE = "smart.smartdo.ACTION_STATUS_RESPONSE"
        const val EXTRA_COMMAND = "command"
        const val EXTRA_DATA = "data"
    }
}