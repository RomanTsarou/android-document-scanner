package com.websitebeaver.documentscanner.utils

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.media.ExifInterface
import android.net.Uri
import com.websitebeaver.documentscanner.extensions.distance
import com.websitebeaver.documentscanner.extensions.toOpenCVPoint
import com.websitebeaver.documentscanner.models.Document
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.File
import kotlin.math.max
import kotlin.math.min


/**
 * This class contains helper functions for processing images
 *
 * @constructor creates image util
 */
class ImageUtil {

    private fun getExifRotation(filePath: String): Int {
        val orientation = ExifInterface(filePath).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )

        val rotation = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
        return rotation
    }

    private fun readBitmapFromFile(file: File, rotation: Int, maxWidth: Int): Bitmap {
        val opts = BitmapFactory.Options()
        opts.inJustDecodeBounds = true
        opts.inMutable = rotation != 0
        file.inputStream()
            .use { BitmapFactory.decodeStream(it, null, opts) }
        val bitmapRect = Rect(0, 0, opts.outWidth, opts.outHeight)
        opts.inJustDecodeBounds = false
        val width =
            if (rotation == 90 || rotation == 270) bitmapRect.height() else bitmapRect.width()
        opts.inSampleSize = max(1, width / maxWidth)
        val bitmap = file.inputStream().use {
            BitmapFactory.decodeStream(it, null, opts)!!
        }
        return if (rotation != 0) {
            val rotated = Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.width,
                bitmap.height,
                Matrix().apply { postRotate(1f * rotation) },
                true
            )
            if (bitmap !== rotated) {
                bitmap.recycle()
            }
            rotated
        } else bitmap
    }

    /**
     * get bitmap image from file path
     *
     * @param file image is saved here
     * @return image bitmap
     */
    fun getImageFromFile(file: File, maxWidth: Int): Bitmap {
        if (!file.exists()) {
            throw Exception("File doesn't exist - $file")
        }

        if (file.length() == 0L) {
            throw Exception("File is empty $file")
        }

        if (!file.canRead()) {
            throw Exception("You don't have permission to read $file")
        }
        val rotation = getExifRotation(file.absolutePath)
        return readBitmapFromFile(file, rotation, maxWidth = maxWidth)
    }

    /**
     * take a photo with a document, crop everything out but document, and force it to display
     * as a rectangle
     *
     * @param document with original image data
     * @param colorFilter for this image
     * @return bitmap with cropped and warped document
     */
    fun cropDocument(document: Document): Bitmap {
        val file = File(document.originalPhotoPath)
        val bitmap = getImageFromFile(file, 4000)

        // read image with OpenCV
        val image = Mat()
        Utils.bitmapToMat(bitmap, image)

        // convert corners from image preview coordinates to original photo coordinates
        // (original image is probably bigger than the preview image)
        val preview = document.preview
        val corners = document.corners
            .mapPreviewToOriginalImageCoordinates(
                RectF(0f, 0f, 1f * preview.width, 1f * preview.height),
                1f * preview.height / bitmap.height
            )

        // convert top left, top right, bottom right, and bottom left document corners from
        // Android points to OpenCV points
        val tLC = corners.topLeftCorner.toOpenCVPoint()
        val tRC = corners.topRightCorner.toOpenCVPoint()
        val bRC = corners.bottomRightCorner.toOpenCVPoint()
        val bLC = corners.bottomLeftCorner.toOpenCVPoint()

        // Calculate the document edge distances. The user might take a skewed photo of the
        // document, so the top left corner to top right corner distance might not be the same
        // as the bottom left to bottom right corner. We could take an average of the 2, but
        // this takes the smaller of the 2. It does the same for height.
        val width = min(tLC.distance(tRC), bLC.distance(bRC))
        val height = min(tLC.distance(bLC), tRC.distance(bRC))

        // create empty image matrix with cropped and warped document width and height
        val croppedImage = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(width, 0.0),
            Point(width, height),
            Point(0.0, height),
        )

        // This crops the document out of the rest of the photo. Since the user might take a
        // skewed photo instead of a straight on photo, the document might be rotated and
        // skewed. This corrects that problem. output is an image matrix that contains the
        // corrected image after this fix.
        val output = Mat()
        Imgproc.warpPerspective(
            image,
            output,
            Imgproc.getPerspectiveTransform(
                MatOfPoint2f(tLC, tRC, bRC, bLC),
                croppedImage
            ),
            Size(width, height)
        )

        // convert output image matrix to bitmap
        val croppedBitmap = Bitmap.createBitmap(
            output.cols(),
            output.rows(),
            Bitmap.Config.ARGB_8888
        )
        Utils.matToBitmap(output, croppedBitmap)

        val colorFilter = document.colorFilter
        if (colorFilter != null) {
            val canvas = Canvas(croppedBitmap)
            val paint = Paint()
            paint.colorFilter = colorFilter
            canvas.drawBitmap(croppedBitmap, 0f, 0f, paint)
        }

        return croppedBitmap
    }

    /**
     * get bitmap image from file uri
     *
     * @param fileUriString image is saved here and starts with file:///
     * @return bitmap image
     */
    fun readBitmapFromFileUriString(
        fileUriString: String,
        contentResolver: ContentResolver
    ): Bitmap {
        return BitmapFactory.decodeStream(
            contentResolver.openInputStream(Uri.parse(fileUriString))
        )
    }

    fun getColorMatrixFilter(
        contrast: Float = 1f,
        brightness: Float = 0f,
        saturation: Float = 1f,
    ): ColorMatrixColorFilter {
        val cm = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, brightness,
                0f, contrast, 0f, 0f, brightness,
                0f, 0f, contrast, 0f, brightness,
                0f, 0f, 0f, 1f, 0f
            )
        )
        if (saturation != 1f) {
            val cmSaturation = ColorMatrix().apply { setSaturation(saturation) }
            cm.postConcat(cmSaturation)
        }
        return ColorMatrixColorFilter(cm)
    }
}