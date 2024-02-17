package com.websitebeaver.documentscanner.models

import android.graphics.Bitmap
import android.graphics.ColorFilter

/**
 * This class contains the original document photo, and a cropper. The user can drag the corners
 * to make adjustments to the detected corners.
 *
 * @param bitmap the photo bitmap before cropping
 * @param corners the document's 4 corner points
 * @constructor creates a document
 */
class Document(
    val originalPhotoPath: String,
    val preview: Bitmap,
    var corners: Quad,
    var colorFilter: ColorFilter?
)