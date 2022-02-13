package com.example.camera2api_mvc.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Outline
import android.graphics.Rect
import android.util.AttributeSet
import android.view.*
import com.example.camera2api_mvc.util.LogUtil

class AutoFitTextureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : TextureView(context, attrs, defStyle) {
    private val TAG = AutoFitTextureView::class.java.simpleName
    private var mRatioWidth = 0
    private var mRatioHeight = 0
    private val radius = 20f
    private lateinit var viewListener: IViewListener
    private var mScaleGestureDetector: ScaleGestureDetector? = null
    private val mScaleListener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            //縮放比例因子
            viewListener.onZoomLevelChanged(detector.scaleFactor)
            return true
        }
    }
    //初始化，讓畫面有圓角
    init {
        apply {
            outlineProvider = object : ViewOutlineProvider(){
                override fun getOutline(view: View, outline: Outline) {
                    val rect = Rect(0, 0, view.measuredWidth, view.measuredHeight)
                    outline.setRoundRect(rect, radius)
                }
            }
            this.clipToOutline = true
        }
        mScaleGestureDetector = ScaleGestureDetector(context, mScaleListener)
    }

    fun setViewListener(viewListener: IViewListener) {
        this.viewListener = viewListener
    }

    fun setAspectRatio(width:Int, height:Int){
        if(width < 0 || height < 0) throw IllegalArgumentException("Size cannot be negative.")
        mRatioWidth = width
        mRatioHeight = height
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        LogUtil.d(TAG,"w=$width, h=$height")
        if(0 == mRatioWidth || 0 == mRatioHeight){
            setMeasuredDimension(width, height)
        }else{
            if (width < ((height * mRatioWidth) / mRatioHeight)) {
                setMeasuredDimension(width, (width * mRatioHeight) / mRatioWidth)
            } else {
                setMeasuredDimension((height * mRatioWidth) / mRatioHeight, height)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        mScaleGestureDetector?.onTouchEvent(event)
        return true
    }
}