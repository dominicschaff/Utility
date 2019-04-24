package zz.utility.helpers

import android.content.Context
import android.os.StatFs
import androidx.core.content.ContextCompat
import java.io.File

fun Context.getFreeInternalMemory(): Long = filesDir.getFreeMemory()

fun Context.getTotalInternalMemory(): Long = filesDir.getTotalMemory()

fun Context.getFreeExternalMemory(): Array<Long> =
        ContextCompat.getExternalFilesDirs(this, null)
                .filter { it != null }
                .map { it.getFreeMemory() }.toTypedArray()

fun Context.getTotalExternalMemory(): Array<Long> =
        ContextCompat.getExternalFilesDirs(this, null)
                .filter { it != null }
                .map { it.getTotalMemory() }.toTypedArray()

private fun File.getFreeMemory(): Long = StatFs(path).availableBytes

private fun File.getTotalMemory(): Long = StatFs(path).totalBytes


fun File.getFileSize(): Long = if (isFile) length() else listFiles().map { it.getFileSize() }.sum()
fun File.getFileCount(): Long = if (isFile) 1 else listFiles().map { it.getFileCount() }.sum()