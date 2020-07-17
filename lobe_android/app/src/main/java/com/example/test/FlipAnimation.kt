package com.example.test

import android.graphics.Camera
import android.view.animation.Animation
import android.view.animation.Transformation

class FlipAnimation(fromDegre: Float, toDegre: Float, centerX : Float, centerY : Float) : Animation() {

    var fromDegree : Float = 0.0f
    var toDegree : Float = 0.0f
    var centerX : Float = 0.0f
    var centerY : Float = 0.0f
    lateinit var camera : Camera

    init {
        this.fromDegree = fromDegre
        this.toDegree   = toDegre
        this.centerX  = centerX
        this.centerY  = centerY
    }

    override
    fun initialize(width: Int, height: Int, parentWidth: Int, parentHeight: Int) {
        super.initialize(width, height, parentWidth, parentHeight)
        camera = Camera()
    }

    override fun applyTransformation(interpolatedTime: Float, t: Transformation) {

        val degrees = fromDegree + (toDegree - fromDegree) * interpolatedTime
        val camera = camera

        val matrix = t.matrix

        camera.save()

        camera.rotateY(degrees)

        camera.getMatrix(matrix)
        camera.restore()

        matrix.preTranslate(-centerX, -centerY)
        matrix.postTranslate(centerX, centerY)

    }
}