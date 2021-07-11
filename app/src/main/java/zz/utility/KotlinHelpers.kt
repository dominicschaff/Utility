@file:Suppress("NOTHING_TO_INLINE", "DefaultLocale", "unused")

package zz.utility

import android.content.Context
import androidx.exifinterface.media.ExifInterface
import com.google.gson.JsonObject
import zz.utility.helpers.asJsonObject
import java.io.File

fun String.extension(): String = substring(lastIndexOf(".")).lowercase()

fun StringBuilder.add(format: String, value: Int) {
    try {
        if (value > 0) {
            this.append(format.format(value))
            this.append("\n")
        }
    } catch (ignored: Exception) {
    }
}

fun StringBuilder.add(format: String, value: Double) {
    try {
        if (value > 0) {
            this.append(format.format(value))
            this.append("\n")
        }
    } catch (ignored: Exception) {
    }
}

fun StringBuilder.add(format: String, value: String?) {
    try {
        if (!value.isNullOrEmpty()) {
            this.append(format.format(value))
            this.append("\n")
        }
    } catch (ignored: Exception) {
    }
}

fun File.metaData(): String {

    try {
        val exifInterface = ExifInterface(this.absolutePath)

        val sb = StringBuilder()
        try {
            val w = exifInterface.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0)
            val h = exifInterface.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0)
            if (w != 0 && h != 0)
                sb.append("Size: %dx%d\n".format(w, h))
        } catch (e: Exception) {
        }
        try {
            val l = exifInterface.latLong
            if (l != null)
                sb.append("Location: %.4f, %.4f\n".format(l[0], l[1]))
        } catch (e: Exception) {
        }

        sb.add("Aperture: f/%.1f", exifInterface.getAttributeDouble(ExifInterface.TAG_APERTURE_VALUE, 0.0))
        sb.add("F Number: f/%.1f", exifInterface.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0))
        sb.add("ISO: %.1f", exifInterface.getAttributeDouble(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0.0))
        sb.add("Shutter Speed: %.5f", exifInterface.getAttributeDouble(ExifInterface.TAG_SHUTTER_SPEED_VALUE, 0.0))
        sb.add("Exposure Time: %.5f", exifInterface.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0))
        sb.add("Date Time: %s", exifInterface.getAttribute(ExifInterface.TAG_DATETIME))
        sb.add("Date Time Original: %s", exifInterface.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
        sb.add("Date Time Digitized: %s", exifInterface.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED))
        sb.add("Focal Length: %.1f", exifInterface.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0))
        sb.add("Focal Length 35MM: %.1f", exifInterface.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, 0.0))
        sb.add("Exposure Program: %s", when (exifInterface.getAttributeInt(ExifInterface.TAG_EXPOSURE_PROGRAM, 0).toShort()) {
            ExifInterface.EXPOSURE_PROGRAM_MANUAL -> "Manual"
            ExifInterface.EXPOSURE_PROGRAM_NORMAL -> "Program Normal"
            ExifInterface.EXPOSURE_PROGRAM_APERTURE_PRIORITY -> "Aperture Priority"
            ExifInterface.EXPOSURE_PROGRAM_SHUTTER_PRIORITY -> "Shutter Priority"
            ExifInterface.EXPOSURE_PROGRAM_CREATIVE -> "Creative"
            ExifInterface.EXPOSURE_PROGRAM_ACTION -> "Action"
            ExifInterface.EXPOSURE_PROGRAM_PORTRAIT_MODE -> "Portrait"
            ExifInterface.EXPOSURE_PROGRAM_LANDSCAPE_MODE -> "Landscape"
            else -> null
        })
        sb.add("Make: %s", exifInterface.getAttribute(ExifInterface.TAG_MAKE))
        sb.add("Model: %s", exifInterface.getAttribute(ExifInterface.TAG_MODEL))
        sb.add("Software: %s", exifInterface.getAttribute(ExifInterface.TAG_SOFTWARE))
        sb.add("Artist: %s", exifInterface.getAttribute(ExifInterface.TAG_ARTIST))
        sb.add("Copyright: %s", exifInterface.getAttribute(ExifInterface.TAG_COPYRIGHT))
        sb.add("Lens Max Aperture: f/%.1f", exifInterface.getAttributeDouble(ExifInterface.TAG_MAX_APERTURE_VALUE, 0.0))
        sb.add("Subject Distance: %.2f", exifInterface.getAttributeDouble(ExifInterface.TAG_SUBJECT_DISTANCE, 0.0))

        sb.add("Flash State: %s", when (exifInterface.getAttributeInt(ExifInterface.TAG_FLASH, 0).toShort()) {
            ExifInterface.FLAG_FLASH_FIRED -> "Fired"
            ExifInterface.FLAG_FLASH_RETURN_LIGHT_NOT_DETECTED -> "Return Light not Detected"
            ExifInterface.FLAG_FLASH_RETURN_LIGHT_DETECTED -> "Return Light Detected"
            ExifInterface.FLAG_FLASH_MODE_COMPULSORY_FIRING -> "Compulsory Firing"
            ExifInterface.FLAG_FLASH_MODE_COMPULSORY_SUPPRESSION -> "Compulsory Suppression"
            ExifInterface.FLAG_FLASH_MODE_AUTO -> "Auto"
            ExifInterface.FLAG_FLASH_NO_FLASH_FUNCTION -> "No Flash Function"
            ExifInterface.FLAG_FLASH_RED_EYE_SUPPORTED -> "Red Eye Supported"
            else -> null
        })
        sb.add("Flash Intensity: %.2f", exifInterface.getAttributeDouble(ExifInterface.TAG_FLASH_ENERGY, 0.0))
        sb.add("Exposure Index: %.2f", exifInterface.getAttributeDouble(ExifInterface.TAG_EXPOSURE_INDEX, 0.0))
        sb.add("Lens Specification: %s", exifInterface.getAttribute(ExifInterface.TAG_LENS_SPECIFICATION))

        return sb.toString()
    } catch (e: Exception) {
        return ""
    }
}

inline fun Context.externalFile(path: String) = File(homeDir(), path)

inline fun Context.homeDir() = getExternalFilesDir(null)!!
inline fun Context.logFile() = externalFile("log.json")
inline fun Context.configFile() = try {
    externalFile("utility.json").asJsonObject()
} catch (e: java.lang.Exception) {
    JsonObject()
}