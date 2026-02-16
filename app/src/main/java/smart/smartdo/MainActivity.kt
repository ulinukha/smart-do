package smart.smartdo

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
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

    // State untuk trigger refresh UI
    private var refreshTrigger = mutableIntStateOf(0)

    private val internalReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.getStringExtra("internal_action")
            val data = intent.getStringExtra("data")

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

        // DEBUG: Test component name immediately
        debugComponentName()

        // Register internal broadcast receiver
        val filter = IntentFilter("smart.smartdo.INTERNAL_ACTION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(internalReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(internalReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(internalReceiver, filter)
        }

        setContent {
            SmartDoTheme {
                TVAdminScreen(
                    deviceAdminManager = deviceAdminManager,
                    refreshTrigger = refreshTrigger.value,
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

    private fun debugComponentName() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val component = ComponentName(this, SmartDoDeviceAdminReceiver::class.java)

        Log.e("TEST", "=================================")
        Log.e("TEST", "Package: ${packageName}")
        Log.e("TEST", "Component: ${component.flattenToString()}")
        Log.e("TEST", "Component pkg: ${component.packageName}")
        Log.e("TEST", "Component class: ${component.className}")
        Log.e("TEST", "Is Active: ${dpm.isAdminActive(component)}")

        // Check all active admins
        val admins = dpm.activeAdmins
        Log.e("TEST", "Active admins count: ${admins?.size ?: 0}")
        admins?.forEach {
            Log.e("TEST", "  Admin: ${it.flattenToString()}")
            Log.e("TEST", "    Package: ${it.packageName}")
            Log.e("TEST", "    Class: ${it.className}")
            Log.e("TEST", "    Match: ${it == component}")
        }
        Log.e("TEST", "=================================")
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume called")
        refreshAdminStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(internalReceiver)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Receiver already unregistered", e)
        }
    }

    private fun enableDeviceAdmin() {
        if (!deviceAdminManager.isDeviceAdmin()) {
            Log.d(TAG, "Launching enable admin intent")
            enableAdminLauncher.launch(deviceAdminManager.getEnableAdminIntent())
        } else {
            Log.d(TAG, "Device admin already enabled")
        }
    }

    private fun refreshAdminStatus() {
        lifecycleScope.launch {
            delay(300)
            refreshTrigger.value++
            val isAdmin = deviceAdminManager.isDeviceAdmin()
            val isOwner = deviceAdminManager.isDeviceOwner()
            Log.d(TAG, "Admin status refreshed: Admin=$isAdmin, Owner=$isOwner")
        }
    }

    private fun refreshAdminStatusWithRetry() {
        lifecycleScope.launch {
            var attempts = 0
            val maxAttempts = 5

            while (attempts < maxAttempts) {
                delay(500)
                attempts++

                val isAdmin = deviceAdminManager.isDeviceAdmin()
                val isOwner = deviceAdminManager.isDeviceOwner()

                Log.d(TAG, "Retry $attempts/$maxAttempts - Admin=$isAdmin, Owner=$isOwner")

                if (isAdmin) {
                    refreshTrigger.value++
                    Log.d(TAG, "Admin status confirmed active after $attempts attempts")
                    break
                }

                if (attempts == maxAttempts) {
                    refreshTrigger.value++
                    Log.w(TAG, "Admin status still inactive after $maxAttempts attempts")
                }
            }
        }
    }

    private fun toggleKioskMode(enable: Boolean) {
        if (enable) {
            deviceAdminManager.setLockTaskPackages(arrayOf(packageName))
            startLockTask()
        } else {
            stopLockTask()
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}

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
    var isAdminActive by remember { mutableStateOf(deviceAdminManager.isDeviceAdmin()) }
    var isDeviceOwner by remember { mutableStateOf(deviceAdminManager.isDeviceOwner()) }
    var kioskModeEnabled by remember { mutableStateOf(false) }
    var statusBarDisabled by remember { mutableStateOf(false) }
    var keyguardDisabled by remember { mutableStateOf(false) }

    LaunchedEffect(refreshTrigger) {
        Log.d("TVAdminScreen", "LaunchedEffect triggered with refreshTrigger=$refreshTrigger")
        isAdminActive = deviceAdminManager.isDeviceAdmin()
        isDeviceOwner = deviceAdminManager.isDeviceOwner()
        Log.d("TVAdminScreen", "Status updated - Admin: $isAdminActive, Owner: $isDeviceOwner")
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = "SmartDo Admin Control",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatusCard(
                    title = "Device Admin",
                    status = isAdminActive,
                    modifier = Modifier.weight(1f)
                )
                StatusCard(
                    title = "Device Owner",
                    status = isDeviceOwner,
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider()

            if (!isAdminActive) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Enable Device Admin to unlock features",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onEnableAdmin,
                            modifier = Modifier.fillMaxWidth(0.5f)
                        ) {
                            Text("Enable Device Admin")
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Debug: refreshTrigger=$refreshTrigger",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (isAdminActive) {
                Text(
                    text = "Admin Controls",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = onLockDevice,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Lock Device")
                    }
                    Button(
                        onClick = onReboot,
                        enabled = isDeviceOwner,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Reboot")
                    }
                }

                if (isDeviceOwner) {
                    Text(
                        text = "Device Owner Controls",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Kiosk Mode", style = MaterialTheme.typography.bodyLarge)
                        Switch(
                            checked = kioskModeEnabled,
                            onCheckedChange = {
                                kioskModeEnabled = it
                                onToggleKiosk(it)
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Disable Status Bar", style = MaterialTheme.typography.bodyLarge)
                        Switch(
                            checked = statusBarDisabled,
                            onCheckedChange = {
                                statusBarDisabled = it
                                onDisableStatusBar(it)
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Disable Lock Screen", style = MaterialTheme.typography.bodyLarge)
                        Switch(
                            checked = keyguardDisabled,
                            onCheckedChange = {
                                keyguardDisabled = it
                                onDisableKeyguard(it)
                            }
                        )
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "💡 To unlock Device Owner features:",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Run via ADB:\nadb shell dpm set-device-owner smart.smartdo/.admin.SmartDoDeviceAdminReceiver",
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusCard(
    title: String,
    status: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (status) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (status) "✓ Active" else "✗ Inactive",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}