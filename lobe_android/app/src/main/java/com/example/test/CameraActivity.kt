package com.example.test

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.LayoutTransition
import android.animation.ObjectAnimator
import android.app.Activity
import android.app.Fragment
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.hardware.Camera
import android.media.ImageReader
import android.os.*
import android.util.DisplayMetrics
import android.util.Size
import android.util.TypedValue
import android.view.MotionEvent
import android.view.MotionEvent.*
import android.view.Surface
import android.view.View
import android.view.animation.*
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.example.test.env.ImageUtils
import com.example.test.env.Logger
import com.example.test.tflite.Classifier
import org.tensorflow.lite.examples.detection.LegacyCameraConnectionFragment
import java.io.*
import java.text.SimpleDateFormat
import java.util.*


@RequiresApi(Build.VERSION_CODES.KITKAT)
abstract class CameraActivity : Activity(), ImageReader.OnImageAvailableListener,
    Camera.PreviewCallback, CompoundButton.OnCheckedChangeListener, View.OnClickListener {
    private val debug = false

    private val LOGGER: Logger = Logger()
    private val PERMISSIONS_REQUEST = 1
    private var handler: Handler? = null
    private var handlerThread: HandlerThread? = null
    private val GALLARY_REQUEST_CODE = 123
    var screenHeight: Int? = null
    var screenWidth: Int? = null
    private val PERMISSION_CAMERA = Manifest.permission.CAMERA

    protected var previewWidth = 0
    protected var previewHeight = 0
    private var isProcessingFrame = false
    private val yuvBytes = arrayOfNulls<ByteArray>(3)
    private var rgbBytes: IntArray? = null
    private var yRowStride = 0
    private var postInferenceCallback: Runnable? = null
    private var imageConverter: Runnable? = null
    var rgbFrameBitmap: Bitmap? = null

    var useImage = false
    var useFront = false
    var imageView: ImageView? = null
    var outer: View? = null
    var inputData: ByteArray? = null
    var listView: ListView? = null
    var adapter: PredictionAdapter? = null
    var galleryImageView: ImageView? = null
    var flipImageView: ImageView? = null
    var closeImageView: ImageView? = null

    private val device: Classifier.Device = Classifier.Device.CPU

    val nThreads: Int = 4
    private val model: Classifier.Model = Classifier.Model.IMAGE_CLASSIFIER

    fun getNumThreads(): Int {
        return nThreads
    }

    fun setMode(useimg: Boolean) {
        useImage = useimg

        flipImageView!!.visibility = if (useImage) View.INVISIBLE else View.VISIBLE
        closeImageView!!.visibility = if (useImage) View.VISIBLE else View.INVISIBLE
    }

    protected open fun getModel(): Classifier.Model? {
        return model
    }

    protected open fun getDevice(): Classifier.Device? {
        return device
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        imageView = findViewById(R.id.myImageView)
        outer = findViewById(R.id.relativeLayout)

        galleryImageView = findViewById(R.id.galleryImageView)
        flipImageView = findViewById(R.id.flipImageView)
        closeImageView = findViewById(R.id.closeImageView)

        galleryImageView!!.setOnClickListener(object : View.OnClickListener {
            override fun onClick(p0: View?) {
                showImageSelect()
            }
        })

        flipImageView!!.setOnClickListener(object : View.OnClickListener {
            override fun onClick(p0: View?) {
                changeCam()
            }
        })

        closeImageView!!.setOnClickListener(object : View.OnClickListener {
            override fun onClick(p0: View?) {
                // dismiss

                //Create amd send the ACTION_DOWN MotionEvent
                var originalDownTime: Long = SystemClock.uptimeMillis()
                var eventTime: Long = SystemClock.uptimeMillis() + 100
                var x = 0f
                var y = 0f
                var metaState = 0
                var motionEvent = MotionEvent.obtain(
                    originalDownTime,
                    eventTime,
                    MotionEvent.ACTION_DOWN,
                    x,
                    y,
                    metaState
                )

                imageView!!.dispatchTouchEvent(motionEvent)

                //Create amd send the ACTION_DOWN MotionEvent
                eventTime = SystemClock.uptimeMillis() + 100
                motionEvent = MotionEvent.obtain(
                    originalDownTime,
                    eventTime,
                    MotionEvent.ACTION_DOWN,
                    x,
                    y + 10f,
                    metaState
                )
                imageView!!.dispatchTouchEvent(motionEvent)
            }
        })

        listView = findViewById(R.id.list_view)
        adapter = PredictionAdapter(this)
        listView!!.adapter = adapter
        var expanded = true
        listView!!.onItemClickListener = object : AdapterView.OnItemClickListener {
            override fun onItemClick(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {

                val displayMetrics: DisplayMetrics = resources.displayMetrics
                val dp =
                    Math.round(p0!!.height / (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT))

                val distance = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, dp - 88f,
                    resources.displayMetrics
                )
                if (expanded) {
                    val spring: SpringForce = SpringForce(distance)
                        .setDampingRatio(0.8f)
                        .setStiffness(300f)

                    val slideOutAnimation =
                        SpringAnimation(p0, DynamicAnimation.TRANSLATION_Y, distance).setSpring(
                            spring
                        )
                    slideOutAnimation.start()
                } else {

                    val distance = TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, 10f,
                        resources.displayMetrics
                    )
                    val spring: SpringForce = SpringForce(distance)
                        .setDampingRatio(0.8f)
                        .setStiffness(300f)

                    val slideInAnimation =
                        SpringAnimation(p0, DynamicAnimation.TRANSLATION_Y, distance).setSpring(spring)
                    slideInAnimation.start()
                }
                expanded = !expanded
            }

        }
        var transition = listView!!.layoutTransition
        transition.enableTransitionType(LayoutTransition.CHANGING)

        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        screenHeight = displayMetrics.heightPixels
        screenWidth = displayMetrics.widthPixels

        if (hasPermission()) {
            setFragment(false)
        } else {
            requestPermission()
        }

        outer!!.setOnTouchListener(object : OnSwipeTouchListener(this) {
            override fun doubleTap() {
                takeScreenshot()
            }
        })
    }

    private fun showImageSelect() {
        val intent = Intent()
        intent.type = "image/*"
        intent.action = Intent.ACTION_GET_CONTENT
        startActivityForResult(
            Intent.createChooser(intent, "Select Picture"),
            GALLARY_REQUEST_CODE
        )
        overridePendingTransition(R.anim.slide_animation, R.anim.slide_animation)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == GALLARY_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            setMode(true)
            val imageData = data.data
            var iStream: InputStream? = null
            try {
                iStream = contentResolver.openInputStream(imageData!!)
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
            }
            try {
                inputData = getBytes(iStream!!)
            } catch (e: IOException) {
                e.printStackTrace()
            }
            imageView!!.setImageURI(imageData)
            imageView!!.animate().scaleX(1.toFloat()).alpha(1.toFloat())
                .scaleY(1.toFloat()).alpha(1.toFloat()).x(0F).y(0F).setDuration(500).start()
            imageView!!.setOnTouchListener(OnDragTouchListener(imageView, this))
        }
    }

    @Throws(IOException::class)
    open fun getBytes(inputStream: InputStream): ByteArray? {
        val byteBuffer = ByteArrayOutputStream()
        val bufferSize = 1024
        val buffer = ByteArray(bufferSize)
        var len: Int
        while (inputStream.read(buffer).also { len = it } != -1) {
            byteBuffer.write(buffer, 0, len)
        }
        return byteBuffer.toByteArray()
    }

    override fun onImageAvailable(reader: ImageReader?) {
        System.out.println("not implemented (should not be needed)")
    }

    // for debugging purpose
    open fun onClickBtn(v: View?) {
        takeScreenshot()
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun changeCam() {
        var ctn: View = findViewById(R.id.container)
        val oa1: ObjectAnimator = ObjectAnimator.ofFloat(ctn, "scaleX", 1f, 0f)
        val oa2: ObjectAnimator = ObjectAnimator.ofFloat(ctn, "scaleX", 0f, 1f)
        oa1.interpolator = DecelerateInterpolator()
        oa2.interpolator = AccelerateDecelerateInterpolator()
        oa1.duration = 200
        oa2.duration = 200
        oa1.addListener(object : AnimatorListenerAdapter() {
            @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
            override fun onAnimationEnd(animation: Animator?) {
                super.onAnimationEnd(animation)
                useFront = !useFront
                setFragment(useFront)
                oa2.start()
            }
        })
        oa1.start()
    }

    private fun takeScreenshot() {
        if (!useImage) {
            val matrix = Matrix()
            matrix.postRotate(90f)
            var targetWidth =
                previewWidth.toFloat() / previewHeight.toFloat() * screenHeight!!.toFloat()
            val scaledBitmap =
                Bitmap.createScaledBitmap(
                    rgbFrameBitmap!!,
                    targetWidth.toInt(),
                    screenHeight!!,
                    true
                )
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
            var resizedbitmap1 =
                Bitmap.createBitmap(rotatedBitmap, 0, 0, w.toInt(), rotatedBitmap.height)
            imageView!!.setImageBitmap(resizedbitmap1)
        }

        var iv: ImageView = findViewById(R.id.allWhite)
        iv.visibility = View.VISIBLE
        val animation: Animation = AlphaAnimation(
            0.toFloat(),
            1.toFloat()
        ) //to change visibility from visible to invisible
        animation.duration = 100 //1 second duration for each animation cycle
        animation.interpolator = LinearInterpolator()
        animation.repeatCount = 1 //repeating indefinitely
        animation.repeatMode = Animation.REVERSE //animation will start from end point once ended.
        iv.startAnimation(animation)
        iv.visibility = View.INVISIBLE

        val pattern = "yyyy-MM-dd-hh-mm-ss"
        val simpleDateFormat = SimpleDateFormat(pattern)
        val date: String = simpleDateFormat.format(Date())
        try {
            val storageLoc =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)

            // create bitmap screen capture
            val v1 = outer!!
            v1.isDrawingCacheEnabled = true
            val bitmap = Bitmap.createBitmap(v1.drawingCache)
            v1.isDrawingCacheEnabled = false

            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                // Do the file write
                val imageFile = File(storageLoc, date + ".jpg")
                val outputStream = FileOutputStream(imageFile)
                val quality = 100
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
                outputStream.flush()
                outputStream.close()
            } else {
                // Request permission from the user
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    0
                )
            }
        } catch (e: Throwable) {
            // Several error may come out with file handling or DOM
            e.printStackTrace()
        }

        if (!useImage) {
            imageView!!.setImageBitmap(null)
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onPreviewFrame(bytes: ByteArray?, camera: Camera?) {
        if (isProcessingFrame) {
            System.out.println("Dropping frame!")
            return
        }
        try {
            // Initialize the storage bitmaps once when the resolution is known.
            if (rgbBytes == null) {
                val previewSize =
                    camera!!.parameters.previewSize
                previewHeight = previewSize.height
                previewWidth = previewSize.width
                rgbBytes = IntArray(previewWidth * previewHeight)
                onPreviewSizeChosen(Size(previewSize.width, previewSize.height), 90)
            }
        } catch (e: Exception) {
            throw java.lang.Exception(e)
        }

        isProcessingFrame = true
        yuvBytes[0] = bytes
        yRowStride = previewWidth

        imageConverter = Runnable {
            ImageUtils.convertYUV420SPToARGB8888(bytes, previewWidth, previewHeight, rgbBytes)
        }

        postInferenceCallback = Runnable {
            camera!!.addCallbackBuffer(bytes)
            isProcessingFrame = false
        }
        processImage()
    }

    override fun onCheckedChanged(p0: CompoundButton?, p1: Boolean) {
    }

    override fun onClick(p0: View?) {
    }

    private fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkSelfPermission(PERMISSION_CAMERA) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (shouldShowRequestPermissionRationale(PERMISSION_CAMERA)) {
                Toast.makeText(
                    this@CameraActivity,
                    "Camera permission is required for this demo",
                    Toast.LENGTH_LONG
                )
                    .show()
            }
            requestPermissions(
                arrayOf(PERMISSION_CAMERA),
                PERMISSIONS_REQUEST
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    protected open fun setFragment(useFront: Boolean) {
        val fragment: Fragment
        fragment = LegacyCameraConnectionFragment(
            this,
            getLayoutId(),
            getDesiredPreviewFrameSize(),
            useFront,
            screenHeight!!,
            screenWidth!!
        )
        fragmentManager.beginTransaction().replace(R.id.container, fragment).commit()
    }

    protected open fun getScreenOrientation(): Int {
        return when (windowManager.defaultDisplay.rotation) {
            Surface.ROTATION_270 -> 270
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_90 -> 90
            else -> 0
        }
    }

    open fun isDebug(): Boolean {
        return debug
    }

    protected open fun readyForNextImage() {
        if (postInferenceCallback != null) {
            postInferenceCallback!!.run()
        }
    }

    protected open fun getRgbBytes(): IntArray? {
        imageConverter!!.run()
        return rgbBytes
    }

    @Synchronized
    fun runInBackground(r: Runnable) {
        handler?.post(r)
    }

    @Synchronized
    override fun onStart() {
        LOGGER.d("onStart $this")
        super.onStart()
    }

    @Synchronized
    override fun onResume() {
        LOGGER.d("onResume $this")
        super.onResume()
        handlerThread = HandlerThread("inference")
        handlerThread!!.start()
        handler = Handler(handlerThread!!.looper)
    }

    @Synchronized
    override fun onPause() {
        LOGGER.d("onPause $this")
        handlerThread!!.quitSafely()
        try {
            handlerThread!!.join()
            handlerThread = null
            handler = null
        } catch (e: InterruptedException) {
            LOGGER.e(e, "Exception!")
        }
        super.onPause()
    }

    @Synchronized
    override fun onStop() {
        LOGGER.d("onStop $this")
        super.onStop()
    }

    @Synchronized
    override fun onDestroy() {
        LOGGER.d("onDestroy $this")
        super.onDestroy()
    }

    protected abstract fun onPreviewSizeChosen(size: Size?, rotation: Int)
    protected abstract fun processImage()
    protected abstract fun getLayoutId(): Int
    protected abstract fun getDesiredPreviewFrameSize(): Size
}