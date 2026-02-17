package smart.smartdo.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class DeviceAdminManager(private val context: Context) {

    private val devicePolicyManager: DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    private val adminComponent: ComponentName =
        SmartDoDeviceAdminReceiver.getComponentName(context)

    /**
     * Check if app is Device Admin
     */
    fun isDeviceAdmin(): Boolean {
        return devicePolicyManager.isAdminActive(adminComponent)
    }

    /**
     * Check if app is Device Owner (highest privilege)
     */
    fun isDeviceOwner(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            devicePolicyManager.isDeviceOwnerApp(context.packageName)
        } else {
            false
        }
    }

    /**
     * Get intent to enable Device Admin
     */
    fun getEnableAdminIntent(): Intent {
        return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Enable SmartDo as Device Admin to unlock advanced features for Android TV control"
            )
        }
    }

    /**
     * Lock device immediately
     */
    fun lockDevice(): Boolean {
        return if (isDeviceAdmin()) {
            try {
                devicePolicyManager.lockNow()
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to lock device", e)
                false
            }
        } else {
            Log.w(TAG, "Cannot lock device - not a device admin")
            false
        }
    }

    /**
     * Wipe device data (CAREFUL!)
     */
    fun wipeDevice(wipeExternalStorage: Boolean = false) {
        if (isDeviceAdmin()) {
            val flags = if (wipeExternalStorage) {
                DevicePolicyManager.WIPE_EXTERNAL_STORAGE
            } else {
                0
            }
            devicePolicyManager.wipeData(flags)
        }
    }

    /**
     * Set lock task mode (Kiosk mode)
     */
    fun setLockTaskPackages(packages: Array<String>) {
        if (isDeviceOwner()) {
            devicePolicyManager.setLockTaskPackages(adminComponent, packages)
        }
    }

    /**
     * Enable/disable keyguard
     */
    fun setKeyguardDisabled(disabled: Boolean): Boolean {
        return if (isDeviceOwner()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                devicePolicyManager.setKeyguardDisabled(adminComponent, disabled)
            } else {
                false
            }
        } else {
            false
        }
    }

    /**
     * Enable/disable status bar
     */
    fun setStatusBarDisabled(disabled: Boolean): Boolean {
        return if (isDeviceOwner()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                devicePolicyManager.setStatusBarDisabled(adminComponent, disabled)
            } else {
                false
            }
        } else {
            false
        }
    }

    /**
     * Set user restrictions
     */
    fun addUserRestriction(restriction: String) {
        if (isDeviceOwner()) {
            devicePolicyManager.addUserRestriction(adminComponent, restriction)
        }
    }

    fun removeUserRestriction(restriction: String) {
        if (isDeviceOwner()) {
            devicePolicyManager.clearUserRestriction(adminComponent, restriction)
        }
    }

    /**
     * Reboot device
     */
    fun rebootDevice() {
        if (isDeviceOwner()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                devicePolicyManager.reboot(adminComponent)
            }
        }
    }

    /**
     * Set global settings (requires Device Owner)
     */
    fun setGlobalSetting(setting: String, value: String) {
        if (isDeviceOwner()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                devicePolicyManager.setGlobalSetting(adminComponent, setting, value)
            }
        }
    }

    /**
     * Hide/show apps
     */
    fun setApplicationHidden(packageName: String, hidden: Boolean): Boolean {
        return if (isDeviceOwner()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                devicePolicyManager.setApplicationHidden(adminComponent, packageName, hidden)
            } else {
                false
            }
        } else {
            false
        }
    }

    /**
     * Enable/disable system apps
     */
    fun enableSystemApp(packageName: String) {
        if (isDeviceOwner()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                devicePolicyManager.enableSystemApp(adminComponent, packageName)
            }
        }
    }

    /**
     * Set screen capture disabled
     */
    fun setScreenCaptureDisabled(disabled: Boolean) {
        if (isDeviceAdmin()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                devicePolicyManager.setScreenCaptureDisabled(adminComponent, disabled)
            }
        }
    }

    /**
     * Remove Device Admin
     */
    fun removeDeviceAdmin() {
        if (isDeviceAdmin()) {
            devicePolicyManager.removeActiveAdmin(adminComponent)
        }
    }

    /**
     * Clear application data (requires Device Owner)
     * @param packageName Package name of the app to clear data
     * @return true if operation started successfully
     */
    fun clearApplicationData(packageName: String): Boolean {
        return if (isDeviceOwner()) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    Log.d(TAG, "Attempting to clear data for: $packageName")
                    val executor = context.mainExecutor


                    val listener =
                        DevicePolicyManager.OnClearApplicationUserDataListener { packageName, succeeded ->
                            if (succeeded) {
                                Log.d(TAG, "Successfully cleared data for: $packageName")
                            } else {
                                Log.e(TAG, "Failed to clear data for: $packageName")
                            }
                        }

                    devicePolicyManager.clearApplicationUserData(
                        adminComponent,
                        packageName,
                        executor,
                        listener
                    )
                    Log.d(TAG, "Clear data operation initiated for: $packageName")
                    true
                } else {
                    Log.w(TAG, "clearApplicationUserData not supported on this API level")
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear application data for $packageName", e)
                false
            }
        } else {
            Log.w(TAG, "Cannot clear application data - not a device owner")
            false
        }
    }
    companion object {
        private const val TAG = "DeviceAdminManager"
    }
}