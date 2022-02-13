package com.example.camera2api_mvc.view

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.RadioButton
import android.widget.RadioGroup
import com.example.camera2api_mvc.R
import kotlinx.android.synthetic.main.zoom_popupwindow.view.*

@SuppressLint("InflateParams")
class ZoomPopupWindow(activity: Activity) : PopupWindow() {

    private var radioButton1x: RadioButton?= null
    private var radioButton2x: RadioButton?= null
    private var radioButton4x: RadioButton?= null
    private var radioButtonMax: RadioButton?= null
    private var radioGroup: RadioGroup?= null
    private var iZoomWindowListener: IZoomWindowListener?= null
    private var maxZoom = 1.0f

    init {
        iZoomWindowListener = activity as IZoomWindowListener
        height = ViewGroup.LayoutParams.WRAP_CONTENT
        width = ViewGroup.LayoutParams.WRAP_CONTENT
        isOutsideTouchable = false
        isFocusable = false
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val contentView = LayoutInflater.from(activity).inflate(R.layout.zoom_popupwindow, null, false)
        radioButton1x = contentView.radio_1x
        radioButton2x = contentView.radio_2x
        radioButton4x = contentView.radio_4x
        radioButtonMax = contentView.radio_max
        contentView.radio_1x?.setOnClickListener {
            iZoomWindowListener?.onZoomValueSelected(1.0f)
            dismiss()
        }
        contentView.radio_2x?.setOnClickListener {
            iZoomWindowListener?.onZoomValueSelected(2.0f)
            dismiss()
        }
        contentView.radio_4x?.setOnClickListener {
            iZoomWindowListener?.onZoomValueSelected(4.0f)
            dismiss()
        }
        contentView.radio_max?.setOnClickListener {
            iZoomWindowListener?.onZoomValueSelected(maxZoom)
            dismiss()
        }
        radioGroup = contentView.zoom_radioGroup
        setContentView(contentView)
    }
    //更新縮放Window
    fun updateZoomWindow(msg: String) {
        when (msg) {
            "1.0" -> {
                radioButton1x?.isChecked = true
            }
            "2.0" -> {
                radioButton2x?.isChecked = true
            }
            "4.0" -> {
                radioButton4x?.isChecked = true
            }
            maxZoom.toString() -> {
                radioButtonMax?.isChecked = true
            }
        }
    }
    //清除radioGroup選取狀態
    fun clearZoomWindow() {
        radioGroup?.clearCheck()
    }
    //給予最大縮放數值
    fun setMaxZoom(max: Float) {
        this.maxZoom = max
    }
}