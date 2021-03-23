package com.example.test

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.RequiresApi
import com.example.test.tflite.Classifier
import kotlin.math.max

class PredictionAdapter(context: Context) : BaseAdapter() {

    private var sList = arrayOf(
        Classifier.Recognition("0", "one", 0.8f, null),
        Classifier.Recognition("1", "two", 0.15f, null),
        Classifier.Recognition("2", "three", 0.05f, null)
    )

    private val mInflator: LayoutInflater
    private val context: Context

    fun setItems(predictions: List<Classifier.Recognition>) {
        sList = predictions.toTypedArray();
        (this.context as Activity).runOnUiThread(Runnable { notifyDataSetChanged() })

    }

    init {
        this.mInflator = LayoutInflater.from(context)
        this.context = context
    }

    override fun getCount(): Int {
        return sList.size
    }

    override fun getItem(position: Int): Any {
        return sList[position]
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

        if (position == 0) {
            progressBar!!.setProgress((item.confidence * 100).toInt(), true)
            progressBar.secondaryProgress = 10
        } else {
            progressBar!!.setProgress(0, true)
            progressBar.secondaryProgress = max(10, (item.confidence * 100).toInt())
        }

        return view
    }
}