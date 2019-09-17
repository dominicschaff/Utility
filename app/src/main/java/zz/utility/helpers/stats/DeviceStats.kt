package zz.utility.helpers.stats

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Point
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import android.telephony.TelephonyManager
import android.util.DisplayMetrics
import android.view.Display



class DeviceStats(
        var deviceSerial: String = "",
        var battery: Float = 0.toFloat(),
        var battery_temperature: Double = 0.toDouble(),
        var manufacturer: String = "",
        var brand: String = "",
        var model: String = "",
        var device: String = "",
        var display: String = "",
        var product: String = "",
        var width: Int = 0,
        var height: Int = 0,
        var simSerialNumber: String = "",
        var deviceIdNumber: String = "",
        var uptime: Long = 0,
        val density:Float = 0F,
        val dpHeight:Float = 0.0F,
        val dpWidth:Float = 0.0F
)

@SuppressLint("HardwareIds", "MissingPermission")
fun Activity.getDeviceStats(): DeviceStats {

    val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = batteryIntent!!.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    val temp = batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
    val display = windowManager.defaultDisplay
    val size = Point()
    display.getSize(size)

    val outMetrics = DisplayMetrics()
    display.getMetrics(outMetrics)

    val density = resources.displayMetrics.density
    val dpHeight = outMetrics.heightPixels / density
    val dpWidth = outMetrics.widthPixels / density

    val telManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    @Suppress("DEPRECATION")
    return DeviceStats(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) android.os.Build.getSerial() else android.os.Build.SERIAL,
            if (level == -1 || scale == -1) 50.0f else level.toFloat() / scale.toFloat() * 100.0f,
            temp / 10.0,
            Build.MANUFACTURER,
            Build.BRAND,
            Build.MODEL,
            Build.DEVICE,
            Build.DISPLAY,
            Build.PRODUCT,
            size.x,
            size.y,
            telManager.simSerialNumber ?: "",
            telManager.imei,
            SystemClock.uptimeMillis(),
            density,
            dpHeight,
            dpWidth
    )
}