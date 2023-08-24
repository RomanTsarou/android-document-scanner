package com.websitebeaver.documentscanner.utils

import android.content.Context
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
class FileUtil {
    /**
     * create a temporary file
     *
     * @param context the app context
     * @param pageNumber the current document page number
     */
    @Throws(IOException::class)
    fun createImageFile(context: Context, pageNumber: Int): File {
        // use current time to make file name more unique
        val dateTime: String = SimpleDateFormat(
            "yyyyMMdd_HHmmss",
            Locale.US
        ).format(Date())

        // create file in pictures directory
        val storageDir: File = context.cacheDir!!
        return File.createTempFile(
            "SCAN_${pageNumber}_${dateTime}",
            ".jpg",
            storageDir
        )
    }
}