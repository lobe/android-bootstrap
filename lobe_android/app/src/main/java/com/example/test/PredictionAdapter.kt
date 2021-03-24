package com.example.test

import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.view.animation.PathInterpolatorCompat
import com.example.test.tflite.Classifier
import kotlin.math.min

class PredictionAdapter(context: Context) : BaseAdapter() {

    private val MAX_COUNT_SHOWN: Int = 3
    private val mInflator: LayoutInflater
    private val context: Context

    private var mList = arrayOf(
        Classifier.Recognition("0", "one", 0.8f, null),
        Classifier.Recognition("1", "two", 0.15f, null),
        Classifier.Recognition("2", "three", 0.05f, null)
    )
    private var mCountToShow: Int = MAX_COUNT_SHOWN


    fun setItems(predictions: List<Classifier.Recognition>) {
        var num = min(predictions.count(), mCountToShow) - 1
        mList = predictions.toTypedArray().sliceArray(0..num);
        (this.context as Activity).runOnUiThread(Runnable { notifyDataSetChanged() })
    }

    fun toggleCountShown() {
        this.mCountToShow = if (this.mCountToShow == 1) MAX_COUNT_SHOWN else 1
    }

    init {
        this.mInflator = LayoutInflater.from(context)
        this.context = context
    }

    override fun getCount(): Int {
        return mList.size
    }

    override fun getItem(position: Int): Any {
        return mList[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun getView(position: Int, convertView: View?, container: ViewGroup?): View? {

        var view: View? = convertView
        if (view == null) {
            view = this.mInflator.inflate(R.layout.customlistview, container, false)
        }

        var progressBar: ProgressBar? = view!!.findViewById(R.id.customProgressBar)
        var textView: TextView? = view.findViewById(R.id.customTextView)

        var item = (this.getItem(position) as Classifier.Recognition);
        textView!!.text = item.title


        val displayMetrics: DisplayMetrics = this.context.resources.displayMetrics
        val dpWidth =
            Math.round(container!!.width / (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT))

        val minProgress = 32.0f / dpWidth * 100.0f

        val confidence: Float =
            ((100.0f - minProgress) / 100.0f) * (item.confidence * 100.0f) + minProgress

        val anim = ProgressBarAnimation(
            progressBar!!,
            if (position == 0) progressBar!!.progress * 1.0f else progressBar!!.secondaryProgress * 1.0f,
            confidence,
            position == 0
        )
        anim.duration = 300
        anim.interpolator = PathInterpolatorCompat.create(0.420f, 0.000f, 0.580f, 1.000f)
        progressBar.startAnimation(anim)

        return view
    }
}