package com.example.test

import android.graphics.*
import android.media.ImageReader
import android.os.Build
import android.os.SystemClock
import android.util.Size
import android.util.TypedValue
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.example.test.customview.OverlayView
import com.example.test.env.ImageUtils
import com.example.test.env.Logger
import com.example.test.tflite.Classifier
import com.example.test.tracking.MultiBoxTracker
import java.io.IOException

@RequiresApi(Build.VERSION_CODES.KITKAT)
class DetectorActivity: CameraActivity(), ImageReader.OnImageAvailableListener {

    private val LOGGER: Logger = Logger()
    private val TF_OD_API_INPUT_SIZE = 448

    // Minimum detection confidence to track a detection.
    private val MAINTAIN_ASPECT = false
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private val DESIRED_PREVIEW_SIZE = Size(1280, 960)
    private val SAVE_PREVIEW_BITMAP = false
    private val TEXT_SIZE_DIP = 10f
    var trackingOverlay: OverlayView? = null
    private var sensorOrientation: Int? = null
    private var detector: Classifier? = null
    private var lastProcessingTimeMs: Long = 0
    private var croppedBitmap: Bitmap? = null
    private var cropCopyBitmap: Bitmap? = null
    private var computingDetection = false
    private var timestamp: Long = 0
    private var frameToCropTransform: Matrix? = null
    private var cropToFrameTransform: Matrix? = null
    private var tracker: MultiBoxTracker? = null

    private var imageSizeX = 0

    /** Input image size of the model along y axis.  */
    private var imageSizeY = 0


    private fun recreateClassifier(
        model: Classifier.Model,
        device: Classifier.Device,
        numThreads: Int
    ) {
        if (detector != null) {
            LOGGER.d("Closing classifier.")
            detector!!.close()
            detector = null
        }
        if (device === Classifier.Device.GPU
            && (model === Classifier.Model.QUANTIZED_MOBILENET || model === Classifier.Model.QUANTIZED_EFFICIENTNET)
        ) {
            LOGGER.d("Not creating classifier: GPU doesn't support quantized models.")
            return
        }
        try {
            LOGGER.d(
                "Creating classifier (model=%s, device=%s, numThreads=%d)",
                model,
                device,
                numThreads
            )
            detector = Classifier.create(this, model, device, numThreads)
        } catch (e: IOException) {
            LOGGER.e(
                e,
                "Failed to create classifier."
            )
        }

        // Updates the input image size.
        imageSizeX = detector!!.getImageSizeX()
        imageSizeY = detector!!.getImageSizeY()
    }


    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onPreviewSizeChosen(size: Size?, rotation: Int) {
        val textSizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            TEXT_SIZE_DIP,
            resources.displayMetrics
        )
        tracker = MultiBoxTracker(this)
        var cropSize: Int = TF_OD_API_INPUT_SIZE
        try {
            recreateClassifier(getModel()!!, getDevice()!!, getNumThreads())
            cropSize = TF_OD_API_INPUT_SIZE
        } catch (e: IOException) {
            e.printStackTrace()
            LOGGER.e(e, "Exception initializing classifier!")
            val toast = Toast.makeText(
                applicationContext, "Classifier could not be initialized", Toast.LENGTH_SHORT
            )
            toast.show()
            finish()
        }

        previewWidth = size!!.width
        previewHeight = size!!.height

        sensorOrientation = rotation - getScreenOrientation()
        LOGGER.i(
            "Camera orientation relative to screen canvas: %d",
            sensorOrientation
        )

        LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight)
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888)
        croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Bitmap.Config.ARGB_8888)

        frameToCropTransform = ImageUtils.getTransformationMatrix(
            previewWidth, previewHeight,
            cropSize, cropSize,
            sensorOrientation!!, MAINTAIN_ASPECT
        )

        cropToFrameTransform = Matrix()
        frameToCropTransform!!.invert(cropToFrameTransform)

        trackingOverlay = findViewById(R.id.tracking_overlay) as OverlayView
        trackingOverlay!!.addCallback(
            object : OverlayView.DrawCallback {
                override fun drawCallback(canvas: Canvas?) {
                    tracker!!.draw(canvas)
                    if (isDebug()) {
                        tracker!!.drawDebug(canvas)
                    }
                }
            })
        tracker!!.setFrameConfiguration(previewWidth, previewHeight, sensorOrientation!!)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun processImage() {
        ++timestamp
        val currTimestamp = timestamp
        trackingOverlay!!.postInvalidate()
        // No mutex needed as this method is not reentrant.
        if (computingDetection) {
            readyForNextImage()
            return
        }
        computingDetection = true
        if (useImage) {
            val cur = BitmapFactory.decodeByteArray(inputData, 0, inputData!!.size, null)
            rgbFrameBitmap = cur.copy(Bitmap.Config.ARGB_8888, true)
        } else {
            rgbFrameBitmap =
                Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888)
            rgbFrameBitmap!!.setPixels(
                getRgbBytes(),
                0,
                previewWidth,
                0,
                0,
                previewWidth,
                previewHeight
            )
        }
        readyForNextImage()
        // For examining the actual TF input.
        if (SAVE_PREVIEW_BITMAP) {
            ImageUtils.saveBitmap(croppedBitmap)
        }

        runInBackground(
            Runnable {
                var rawBitmap: Bitmap? = null
                if (!useImage) {
                    val matrix = Matrix()
                    matrix.postRotate(90f)
                    var targetWidth = previewWidth!!.toFloat() / previewHeight!!.toFloat() * screenHeight!!.toFloat()
                    val scaledBitmap =
                        Bitmap.createScaledBitmap(rgbFrameBitmap!!, targetWidth!!.toInt(), screenHeight!!, true)
                    val rotatedBitmap = Bitmap.createBitmap(
                        scaledBitmap,
                        0,
                        0,
                        scaledBitmap.width,
                        scaledBitmap.height,
                        matrix,
                        true
                    )

                    var w = screenWidth!!.toFloat() / screenHeight!!.toFloat() * rotatedBitmap.height
                    rawBitmap = Bitmap.createBitmap(rotatedBitmap, 0, 0, w.toInt(), rotatedBitmap.height)
                } else {
                    rawBitmap = rgbFrameBitmap
                }

                val matrix = Matrix()
                matrix.postRotate(90f)
                var curWidth = rawBitmap!!.width
                var curHeight = rawBitmap!!.height
                var squareBitmap: Bitmap? = null
                if (curHeight > curWidth) {
                    squareBitmap = Bitmap.createBitmap(rawBitmap, 0, ((curHeight.toFloat() - curWidth.toFloat()) / 2.toFloat()).toInt(),  curWidth, curWidth)
                } else {
                    squareBitmap = Bitmap.createBitmap(rawBitmap, ((curWidth.toFloat() - curHeight.toFloat()) / 2.toFloat()).toInt(), 0,  curHeight, curHeight)
                }

                val canvas1 = Canvas(croppedBitmap!!)
                val trans = ImageUtils.getTransformationMatrix(
                    squareBitmap!!.width, squareBitmap!!.height,
                    croppedBitmap!!.width, croppedBitmap!!.height,
                    0, MAINTAIN_ASPECT
                )

                canvas1.drawBitmap(squareBitmap!!, trans, null)
                val startTime = SystemClock.uptimeMillis()
                val results: List<Classifier.Recognition> =
                    detector!!.recognizeImage(croppedBitmap, sensorOrientation!!)
                label!!.text = "" + results[0].getTitle()
                progressBar!!.setProgress((results[0].getConfidence() * 100).toInt(), true)
                computingDetection = false
            })
    }

    override fun getLayoutId(): Int {
        return R.layout.tfe_od_camera_connection_fragment_tracking
    }

    override fun getDesiredPreviewFrameSize(): Size {
        return DESIRED_PREVIEW_SIZE
    }

    private enum class DetectorMode {
        TF_OD_API
    }
}