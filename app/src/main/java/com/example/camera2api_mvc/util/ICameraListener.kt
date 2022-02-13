package com.example.camera2api_mvc.util

import android.util.Size

interface ICameraListener {
    fun onPreviewFrame(data:ByteArray, width:Int, height:Int, pts:Long)
    fun onCameraParam(previewSize: Size, Max_Zoom:Float)
}