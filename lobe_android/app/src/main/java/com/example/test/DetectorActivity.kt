package com.example.test

import android.graphics.*
import android.media.ImageReader
import android.os.Build
import android.os.SystemClock
import android.util.Size
import android.util.TypedValue
import android.widget.ImageView
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.example.test.customview.OverlayView
import com.example.test.env.ImageUtils
import com.example.test.env.Logger
import com.example.test.tflite.Classifier
import com.example.test.tflite.TFLiteObjectDetectionAPIModel
import com.example.test.tracking.MultiBoxTracker
import java.io.IOException
import java.util.*

@RequiresApi(Build.VERSION_CODES.KITKAT)
class DetectorActivity: CameraActivity(), ImageReader.OnImageAvailableListener {

    private val LOGGER: Logger = Logger()

    // Configuration values for the prepackaged SSD model.
    private val TF_OD_API_INPUT_SIZE = 300
    private val TF_OD_API_IS_QUANTIZED = true
    private val TF_OD_API_MODEL_FILE = "detect.tflite"
    private val TF_OD_API_LABELS_FILE = "file:///android_asset/labelmap.txt"
    private val MODE: DetectorMode = DetectorMode.TF_OD_API

    // Minimum detection confidence to track a detection.
    private val MINIMUM_CONFIDENCE_TF_OD_API = 0.5f
    private val MAINTAIN_ASPECT = false
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private val DESIRED_PREVIEW_SIZE = Size(1280, 720)
    private val SAVE_PREVIEW_BITMAP = false
    private val TEXT_SIZE_DIP = 10f
    var trackingOverlay: OverlayView? = null
    private var sensorOrientation: Int? = null

    private var detector: Classifier? = null

    private var lastProcessingTimeMs: Long = 0

    //  private Bitmap rgbFrameBitmap = null;
    private var croppedBitmap: Bitmap? = null
    private var cropCopyBitmap: Bitmap? = null

    private var computingDetection = false

    private var timestamp: Long = 0

    private var frameToCropTransform: Matrix? = null
    private var cropToFrameTransform: Matrix? = null

    private var tracker: MultiBoxTracker? = null

//    private var borderedText: BorderedText? = null




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
            detector = TFLiteObjectDetectionAPIModel.create(
                assets,
                TF_OD_API_MODEL_FILE,
                TF_OD_API_LABELS_FILE,
                TF_OD_API_INPUT_SIZE,
                TF_OD_API_IS_QUANTIZED
            )
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

        // No mutex needed as this method is not reentrant.
        if (computingDetection) {
            readyForNextImage()
            return
        }
        computingDetection = true
//    LOGGER.i("Preparing image " + currTimestamp + " for detection in bg thread.");

        //    LOGGER.i("Preparing image " + currTimestamp + " for detection in bg thread.");
        if (inputData != null) {
            val cur = BitmapFactory.decodeByteArray(inputData, 0, inputData!!.size, null)
            rgbFrameBitmap = cur.copy(Bitmap.Config.ARGB_8888, true)
//      final Canvas canvas = new Canvas(croppedBitmap);
//      canvas.drawBitmap(cur, frameToCropTransform, null);
//      croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Config.ARGB_8888);
        } else {
//      System.out.println("in else: ");
//      System.out.println(previewWidth);
//      System.out.println(previewHeight);
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

        val canvas = Canvas(croppedBitmap!!)
        val transForImages = ImageUtils.getTransformationMatrix(
            960, 1280,
            300, 300,
            0, MAINTAIN_ASPECT
        )
        if (inputData != null) {
            canvas.drawBitmap(rgbFrameBitmap!!, transForImages, null)
        } else {
            canvas.drawBitmap(rgbFrameBitmap!!, frameToCropTransform!!, null)
        }

        // For examining the actual TF input.

        // For examining the actual TF input.
        if (SAVE_PREVIEW_BITMAP) {
            ImageUtils.saveBitmap(croppedBitmap)
        }



        runInBackground(
            Runnable { //            LOGGER.i("Running detection on image " + currTimestamp);
                val startTime = SystemClock.uptimeMillis()
                val results: List<Classifier.Recognition> =
                    detector!!.recognizeImage(croppedBitmap)
                label!!.text = "" + results[0].getTitle()
                progressBar!!.setProgress((results[0].getConfidence() * 100).toInt(), true)
                lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime
                cropCopyBitmap = Bitmap.createBitmap(croppedBitmap!!)
                val canvas = Canvas(cropCopyBitmap!!)
                val paint = Paint()
                paint.color = Color.RED
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2.0f
                var minimumConfidence: Float =
                    MINIMUM_CONFIDENCE_TF_OD_API
                when (MODE) {
                    DetectorMode.TF_OD_API -> minimumConfidence =
                        MINIMUM_CONFIDENCE_TF_OD_API
                }
                val mappedRecognitions: MutableList<Classifier.Recognition> =
                    LinkedList<Classifier.Recognition>()
                for (result in results) {
                    val location: RectF = result.getLocation()
                    if (location != null && result.getConfidence() >= minimumConfidence) {
                        //                canvas.drawRect(location, paint);
                        cropToFrameTransform!!.mapRect(location)
                        result.setLocation(location)
                        mappedRecognitions.add(result)
                    }
                }
                tracker!!.trackResults(mappedRecognitions, currTimestamp)
                trackingOverlay!!.postInvalidate()
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