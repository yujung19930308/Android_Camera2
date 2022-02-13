package com.example.trtcforkotlin.media_encode

interface IEncoderListener {
    fun onEncodeFrameData(type:String, data:ByteArray, size:Int, presentationTime:Long)
}