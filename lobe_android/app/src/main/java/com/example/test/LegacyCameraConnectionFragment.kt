package org.tensorflow.lite.examples.detection
/*
 * Copyright 2021 Microsoft. All Rights Reserved.
 *
 * Copyright 2019 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * This file has been modified by Microsoft to add support for using Lobe exported models.
 */
import android.annotation.SuppressLint
import android.app.Fragment
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.hardware.Camera.CameraInfo
import android.hardware.Camera.PreviewCallback
import android.os.Build
import android.os.Bundle
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.util.Size
import android.util.SparseIntArray
import android.view.LayoutInflater
import android.view.Surface
import android.view.TextureView.SurfaceTextureListener
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import com.example.test.CameraConnectionFragment.Companion.chooseOptimalSize
import com.example.test.R
import com.example.test.customview.AutoFitTextureView
import com.example.test.env.ImageUtils
import com.example.test.env.Logger
import java.io.IOException

class LegacyCameraConnectionFragment @SuppressLint("ValidFragment") constructor(
    private val imageListener: PreviewCallback,
    /** The layout identifier to inflate for this Fragment.  */
    private val layout: Int,
    private val desiredSize: Size,
    useFront: Boolean,
    screenHeight: Int,
    screenWidth: Int
) :
    Fragment() {
    companion object {
        private val LOGGER: Logger = Logger()

        /** Conversion from screen rotation to JPEG orientation.  */
        private val ORIENTATIONS = SparseIntArray()

        init {
            ORIENTATIONS.append(
                Surface.ROTATION_0,
                90
            )
            ORIENTATIONS.append(
                Surface.ROTATION_90,
                0
            )
            ORIENTATIONS.append(
                Surface.ROTATION_180,
                270
            )
            ORIENTATIONS.append(
                Surface.ROTATION_270,
                180
            )
        }
    }

    private var camera: Camera? = null

    /** An [AutoFitTextureView] for camera preview.  */
    private var textureView: AutoFitTextureView? = null
    private var availableSurfaceTexture: SurfaceTexture? = null
    private var useFront = false
    var screenHeight = 1000
    var screenWidth = 1000

    /**
     * [TextureView.SurfaceTextureListener] handles several lifecycle events on a [ ].
     */
    private val surfaceTextureListener: SurfaceTextureListener = object : SurfaceTextureListener {
        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        override fun onSurfaceTextureAvailable(
            texture: SurfaceTexture, width: Int, height: Int
        ) {
            availableSurfaceTexture = texture
            startCamera()
        }

        override fun onSurfaceTextureSizeChanged(
            texture: SurfaceTexture, width: Int, height: Int
        ) {
        }

        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
            return true
        }

        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {}
    }

    /** An additional thread for running tasks that shouldn't block the UI.  */
    private var backgroundThread: HandlerThread? = null
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(layout, container, false)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        textureView = view.findViewById<View>(R.id.texture) as AutoFitTextureView
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onResume() {
        super.onResume()
        startBackgroundThread()
        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).
        if (textureView!!.isAvailable()) {
            startCamera()
        } else {
            textureView!!.setSurfaceTextureListener(surfaceTextureListener)
        }
    }

    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    override fun onPause() {
        stopCamera()
        stopBackgroundThread()
        super.onPause()
    }

    /** Starts a background thread and its [Handler].  */
    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground")
        backgroundThread!!.start()
    }

    /** Stops the background thread and its [Handler].  */
    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private fun stopBackgroundThread() {
        backgroundThread!!.quitSafely()
        try {
            backgroundThread!!.join()
            backgroundThread = null
        } catch (e: InterruptedException) {
            LOGGER.e(e, "Exception!")
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun startCamera() {
        val index = getCameraId()
        camera = Camera.open(index)
        try {
            val parameters = camera!!.getParameters()
            val focusModes =
                parameters?.supportedFocusModes
            if (focusModes != null
                && focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)
            ) {
                parameters.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
            }
            val cameraSizes =
                parameters?.supportedPreviewSizes
            var sizes = arrayOfNulls<Size>(cameraSizes!!.size)

            var i = 0
            for (size in cameraSizes) {
                sizes[i++] = Size(size.width, size.height)
            }

            val previewSize: Size? = chooseOptimalSize(
                sizes.copyOfRange(5,6), desiredSize.width, desiredSize.height
            )
            parameters!!.setPreviewSize(previewSize!!.width, previewSize!!.height)
            camera!!.setDisplayOrientation(90)
            camera!!.setParameters(parameters)
            camera!!.setPreviewTexture(availableSurfaceTexture)
        } catch (exception: IOException) {
            camera!!.release()
        }
        camera!!.setPreviewCallbackWithBuffer(imageListener)
        val s = camera!!.getParameters().previewSize
        camera!!.addCallbackBuffer(ByteArray(ImageUtils.getYUVByteSize(s.height, s.width)))
        textureView!!.setAspectRatio(s.height, s.width)

        textureView!!.setLayoutParams(FrameLayout.LayoutParams(10000, screenHeight))
//        textureView?.setAspectRatio(960, 1280);
        camera!!.startPreview()
    }

    protected fun stopCamera() {
        if (camera != null) {
            camera!!.stopPreview()
            camera!!.setPreviewCallback(null)
            camera!!.release()
            camera = null
        }
    }

    private fun getCameraId(): Int {
        val ci = CameraInfo()
        for (i in 0 until Camera.getNumberOfCameras()) {
            Camera.getCameraInfo(i, ci)
            if (useFront) {
                if (ci.facing == CameraInfo.CAMERA_FACING_FRONT) return i
            } else {
                if (ci.facing == CameraInfo.CAMERA_FACING_BACK) return i
            }
        }
        return -1 // No camera found
    }

    init {
        this.useFront = useFront
        this.screenHeight = screenHeight
        this.screenWidth = screenWidth
    }
}