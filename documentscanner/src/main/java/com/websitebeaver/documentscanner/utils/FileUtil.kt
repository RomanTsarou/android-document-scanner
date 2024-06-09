package com.websitebeaver.documentscanner.utils

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * This class contains a helper function creating temporary files
 *
 * @constructor creates file util
 */
internal object FileUtil {
    /**
     * create a file in the app folder
     *
     * @param context the app context
     * @param pageNumber the current document page number
     */
    @Throws(IOException::class)
    fun createImageFile(context: Context, pageNumber: Int): File {
        // use current time to make file name more unique
        val dateTime: String = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(Date())

        // create file in pictures directory
        val storageDir = File(context.filesDir!!, Environment.DIRECTORY_PICTURES)
        storageDir.mkdirs()
        return File(storageDir, "SCAN-${dateTime}${pageNumber}.jpg")
    }

    fun getUriForFile(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.DocumentScannerFileProvider",
            file,
        )
    }
}