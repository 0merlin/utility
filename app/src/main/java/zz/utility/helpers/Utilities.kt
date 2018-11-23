package zz.utility.helpers

import android.content.Context
import android.os.StatFs
import androidx.core.content.ContextCompat
import java.io.File

object Utilities {
    fun getFreeInternalMemory(context: Context): Long = getFreeMemory(context.filesDir)

    fun getTotalInternalMemory(context: Context): Long = getTotalMemory(context.filesDir)

    fun getFreeExternalMemory(context: Context): Array<Long> =
            ContextCompat.getExternalFilesDirs(context, null)
                    .filter { it != null }
                    .map { getFreeMemory(it) }.toTypedArray()

    fun getTotalExternalMemory(context: Context): Array<Long> =
            ContextCompat.getExternalFilesDirs(context, null)
                    .filter { it != null }
                    .map { getTotalMemory(it) }.toTypedArray()

    private fun getFreeMemory(file: File): Long = StatFs(file.path).availableBytes

    private fun getTotalMemory(file: File): Long = StatFs(file.path).totalBytes
}


fun File.getFileSize(): Long = if (isFile) length() else listFiles().map { it.getFileSize() }.sum()
fun File.getFileCount(): Long = if (isFile) 1 else listFiles().map { it.getFileCount() }.sum()