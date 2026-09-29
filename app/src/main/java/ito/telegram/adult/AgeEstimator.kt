package ito.telegram.adult

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * تخمین سن روی دستگاه.
 *
 * مدل: model_age_q.tflite از پروژه‌ی MIT زیر (فایل داخل assets است، چیزی آپلود
 * نمی‌شود و هیچ فریمی از دوربین ذخیره نمی‌شود):
 *   github.com/shubham0204/Age-Gender_Estimation_TF-Android  (MIT, 2021)
 *
 * خروجی مدل عددی در بازه‌ی (۰،۱] است که در ۱۱۶ ضرب می‌شود.
 *
 * صادقانه: این «تخمین» است، نه مدرک شناسایی. دقتش روی نور کم و زاویه‌ی بد
 * پایین می‌آید و دقیقاً به همین دلیل حالت سخت‌گیر، نتیجه‌ی نامطمئن را رد می‌کند.
 */
class AgeEstimator(context: Context) {

    private var interpreter: Interpreter? = null
    private var inputSize = 200
    private var loadError: String? = null

    val ready: Boolean get() = interpreter != null
    val error: String? get() = loadError

    init {
        try {
            val afd = context.assets.openFd(MODEL_ASSET)
            FileInputStream(afd.fileDescriptor).use { fis ->
                val buffer = fis.channel.map(
                    FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength
                )
                val options = Interpreter.Options().apply { numThreads = 2 }
                val itp = Interpreter(buffer, options)
                val shape = itp.getInputTensor(0).shape()
                if (shape.size == 4 && shape[1] > 0) inputSize = shape[1]
                interpreter = itp
            }
        } catch (t: Throwable) {
            loadError = t.message ?: t.javaClass.simpleName
            interpreter = null
        }
    }

    /** برش چهره با کمی حاشیه، مربع‌سازی و مقیاس به ورودی مدل. */
    fun cropFace(source: Bitmap, box: Rect, marginRatio: Float = 0.25f): Bitmap? {
        return try {
            val cx = box.centerX()
            val cy = box.centerY()
            val half = (maxOf(box.width(), box.height()) * (1f + marginRatio) / 2f).toInt()
            val left = (cx - half).coerceIn(0, source.width - 1)
            val top = (cy - half).coerceIn(0, source.height - 1)
            val right = (cx + half).coerceIn(left + 1, source.width)
            val bottom = (cy + half).coerceIn(top + 1, source.height)
            Bitmap.createBitmap(source, left, top, right - left, bottom - top)
        } catch (t: Throwable) {
            null
        }
    }

    fun estimate(face: Bitmap): Float? {
        val itp = interpreter ?: return null
        return try {
            val scaled = Bitmap.createScaledBitmap(face, inputSize, inputSize, true)
            val input = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3).apply {
                order(ByteOrder.nativeOrder())
            }
            val pixels = IntArray(inputSize * inputSize)
            scaled.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
            for (p in pixels) {
                input.putFloat(((p shr 16) and 0xFF) / 255f)
                input.putFloat(((p shr 8) and 0xFF) / 255f)
                input.putFloat((p and 0xFF) / 255f)
            }
            input.rewind()
            val out = Array(1) { FloatArray(1) }
            itp.run(input, out)
            val age = out[0][0] * AGE_SCALE
            if (age.isNaN() || age <= 0f || age > 120f) null else age
        } catch (t: Throwable) {
            loadError = t.message
            null
        }
    }

    fun rotate(bitmap: Bitmap, degrees: Int): Bitmap = if (degrees % 360 == 0) bitmap else try {
        Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height,
            Matrix().apply { postRotate(degrees.toFloat()) }, true
        )
    } catch (t: Throwable) {
        bitmap
    }

    fun close() {
        try {
            interpreter?.close()
        } catch (_: Throwable) {
        }
        interpreter = null
    }

    companion object {
        const val MODEL_ASSET = "age_model.tflite"
        const val AGE_SCALE = 116f
    }
}
