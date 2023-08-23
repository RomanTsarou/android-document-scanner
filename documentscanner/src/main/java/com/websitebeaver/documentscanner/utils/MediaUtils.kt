package com.websitebeaver.documentscanner.utils

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

suspend fun Context.insertMedia(file: File, environmentDirectory: String): Uri {
    return withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            applicationContext.saveMediaInQ(file, environmentDirectory)
        } else {
            saveMediaInLegacy(file, environmentDirectory)
        }
    }
}


/**
 * https://stackoverflow.com/questions/57726896/mediastore-images-media-insertimage-deprecated
 */
@RequiresApi(Build.VERSION_CODES.Q)
private fun Context.saveMediaInQ(file: File, environmentDirectory: String): Uri {
    val contentValues = file.baseMediaContentValues().apply {
        put(MediaStore.MediaColumns.RELATIVE_PATH, environmentDirectory)
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val uri =
        contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)!!
    file.inputStream().use { input ->
        contentResolver.openOutputStream(uri)!!.use { out ->
            input.copyTo(out)
        }
    }
    contentValues.clear()
    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
    contentResolver.update(uri, contentValues, null, null)
    return uri
}

private fun Context.saveMediaInLegacy(file: File, environmentDirectory: String): Uri {
    val dir = Environment.getExternalStoragePublicDirectory(environmentDirectory)
    val target = File(dir, file.name)
    file.copyTo(target)
    val contentValues = file.baseMediaContentValues().apply {
        put(MediaStore.MediaColumns.DATA, target.absolutePath)
    }
    val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)!!
    return uri
}

private fun File.baseMediaContentValues(): ContentValues {
    val file = this
    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension)!!
    return ContentValues().apply {
        put(MediaStore.MediaColumns.DATE_ADDED, file.lastModified() / 1000)
        put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
        put(MediaStore.MediaColumns.TITLE, file.name)
        put(MediaStore.MediaColumns.MIME_TYPE, mime)
    }
}

val Context.needStoragePermission
    get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(
        this, Manifest.permission.WRITE_EXTERNAL_STORAGE
    ) != PackageManager.PERMISSION_GRANTED