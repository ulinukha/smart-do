package smart.smartdo

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.UserManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import smart.smartdo.admin.DeviceAdminManager
import smart.smartdo.admin.SmartDoDeviceAdminReceiver
import smart.smartdo.ui.theme.SmartDoTheme

class MainActivity : ComponentActivity() {

    private lateinit var deviceAdminManager: DeviceAdminManager
    private val refreshTrigger = mutableIntStateOf(0)

    /**
     * Receiver Internal untuk menangani aksi dari CustomBroadcastReceiver (App Hospitality)
     */
    private val internalReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.getStringExtra("internal_action")
            val data = intent.getStringExtra("data")

            Log.d(TAG, "Internal action received: $action with data: $data")

            when (action) {
                "LOCK_DEVICE" -> deviceAdminManager.lockDevice()
                "REBOOT_DEVICE" -> deviceAdminManager.rebootDevice()
                "HIDE_APP" -> data?.let { deviceAdminManager.setApplicationHidden(it, true) }
                "SHOW_APP" -> data?.let { deviceAdminManager.setApplicationHidden(it, false) }
                "TOGGLE_KIOSK" -> toggleKioskMode(data == "true")
            }
        }
    }

    private val enableAdminLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Log.d(TAG, "Device Admin enabled successfully")
            refreshAdminStatusWithRetry()
        } else {
            Log.d(TAG, "Device Admin not enabled - user cancelled")
            refreshAdminStatus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        deviceAdminManager = DeviceAdminManager(this)

        val filter = IntentFilter("smart.smartdo.INTERNAL_ACTION")
        val receiverFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Context.RECEIVER_EXPORTED
        } else {
            0
        }
        registerReceiver(internalReceiver, filter, receiverFlags)

        applyDeviceOwnerRestrictions()

        setContent {
            SmartDoTheme {
                TVAdminScreen(
                    deviceAdminManager = deviceAdminManager,
                    refreshTrigger = refreshTrigger.intValue,
                    onEnableAdmin = { enableDeviceAdmin() },
                    onLockDevice = { deviceAdminManager.lockDevice() },
                    onReboot = { deviceAdminManager.rebootDevice() },
                    onToggleKiosk = { enabled -> toggleKioskMode(enabled) },
                    onDisableStatusBar = { deviceAdminManager.setStatusBarDisabled(it) },
                    onDisableKeyguard = { deviceAdminManager.setKeyguardDisabled(it) }
                )
            }
        }
    }

    /**
     * Menerapkan pembatasan sistem agar TV tidak bisa di-reset manual oleh tamu
     */
    private fun applyDeviceOwnerRestrictions() {
        if (deviceAdminManager.isDeviceOwner()) {
            val adminComponent = SmartDoDeviceAdminReceiver.getComponentName(this)

            deviceAdminManager.addUserRestriction(UserManager.DISALLOW_FACTORY_RESET)
            deviceAdminManager.removeUserRestriction(UserManager.DISALLOW_MODIFY_ACCOUNTS)

            Log.d(TAG, "Device Owner security restrictions applied.")
        }
    }

    private fun enableDeviceAdmin() {
        if (!deviceAdminManager.isDeviceAdmin()) {
            enableAdminLauncher.launch(deviceAdminManager.getEnableAdminIntent())
        }
    }

    private fun refreshAdminStatus() {
        lifecycleScope.launch {
            delay(300)
            refreshTrigger.intValue++
        }
    }

    private fun refreshAdminStatusWithRetry() {
        lifecycleScope.launch {
            repeat(5) {
                delay(500)
                refreshTrigger.intValue++
                if (deviceAdminManager.isDeviceAdmin()) return@launch
            }
        }
    }

    private fun toggleKioskMode(enable: Boolean) {
        if (enable) {
            // Daftarkan package ini dan Hospitality agar bisa masuk mode Kiosk
            val packages = arrayOf(packageName, "com.smartiv.hospitality")
            deviceAdminManager.setLockTaskPackages(packages)
            startLockTask()
        } else {
            stopLockTask()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshAdminStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(internalReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Receiver already unregistered")
        }
    }

    companion object {
        private const val TAG = "SmartDo_Main"
    }
}

/**
 * UI Komponen menggunakan Jetpack Compose untuk Android TV
 */
@Composable
fun TVAdminScreen(
    deviceAdminManager: DeviceAdminManager,
    refreshTrigger: Int,
    onEnableAdmin: () -> Unit,
    onLockDevice: () -> Unit,
    onReboot: () -> Unit,
    onToggleKiosk: (Boolean) -> Unit,
    onDisableStatusBar: (Boolean) -> Unit,
    onDisableKeyguard: (Boolean) -> Unit
) {
    var isAdminActive by remember { mutableStateOf(false) }
    var isDeviceOwner by remember { mutableStateOf(false) }

    // UI State Switches
    var kioskEnabled by remember { mutableStateOf(false) }
    var statusBarDisabled by remember { mutableStateOf(false) }
    var keyguardDisabled by remember { mutableStateOf(false) }

    LaunchedEffect(refreshTrigger) {
        isAdminActive = deviceAdminManager.isDeviceAdmin()
        isDeviceOwner = deviceAdminManager.isDeviceOwner()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Smart DO (Device Owner) Controller", style = MaterialTheme.typography.headlineMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatusCard("Admin Status", isAdminActive)
                StatusCard("Owner Status", isDeviceOwner)
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            if (!isAdminActive) {
                Button(onClick = onEnableAdmin, modifier = Modifier.fillMaxWidth(0.6f)) {
                    Text("Enable Device Admin")
                }
            } else {
                // Controls Group
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onLockDevice) { Text("Lock TV") }
                    Button(onClick = onReboot, enabled = isDeviceOwner) { Text("Reboot TV") }
                }

                if (isDeviceOwner) {
                    Card(modifier = Modifier.fillMaxWidth(0.8f)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Advanced Policy (Owner Only)", fontWeight = FontWeight.Bold)

                            ControlRow("Kiosk Mode", kioskEnabled) {
                                kioskEnabled = it
                                onToggleKiosk(it)
                            }
                            ControlRow("Disable Status Bar", statusBarDisabled) {
                                statusBarDisabled = it
                                onDisableStatusBar(it)
                            }
                            ControlRow("Disable Keyguard", keyguardDisabled) {
                                keyguardDisabled = it
                                onDisableKeyguard(it)
                            }
                        }
                    }
                } else {
                    Text(
                        "Please run ADB command to promote to Device Owner",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
fun ControlRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun StatusCard(label: String, isActive: Boolean) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(if (isActive) "ACTIVE" else "INACTIVE", fontWeight = FontWeight.ExtraBold)
        }
    }
}