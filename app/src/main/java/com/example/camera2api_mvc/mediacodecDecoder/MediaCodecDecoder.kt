package com.example.camera2api_mvc.mediacodecDecoder

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.view.Surface
import com.example.camera2api_mvc.model.CamData
import com.example.camera2api_mvc.util.LogUtil
import java.io.IOException
import java.util.*
import java.util.concurrent.ArrayBlockingQueue

class MediaCodecDecoder(width:Int, height:Int, surface: Surface, bit_rate:Int, fps:Int) {
    companion object{
        private val TAG = MediaCodecDecoder::class.java.simpleName
        const val VIDEO_MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC
        private const val CACHE_BUFFER_SIZE = 15
        val mInputDataQueue: Queue<CamData> = ArrayBlockingQueue(CACHE_BUFFER_SIZE)
    }
    private var mediaCodec : MediaCodec? = null
    private var mediaFormat :MediaFormat? = null
    private var mSurface : Surface?= null

    init {
        try{
            mediaCodec = MediaCodec.createDecoderByType(VIDEO_MIME_TYPE)
        }catch (e: IOException){
            e.printStackTrace()
        }
        this.mSurface = surface
        //Height and width are generally the height and width of the camera.
        //Because the obtained video frame data is rotated 90 degrees counterclockwise, the width and height need to be reversed here
        //mediaFormat = MediaFormat.createVideoFormat(VIDEO_MIME_TYPE, height, width);
        mediaFormat = MediaFormat.createVideoFormat(VIDEO_MIME_TYPE, width, height)
        //A key describing the average bit rate (in bits per second). The associated value is an integer
        //參數越大視頻質量越好，但有上限，一般可以使用width*height*(1,3,5)等來控制質量
        mediaFormat!!.setInteger(MediaFormat.KEY_BIT_RATE, bit_rate)
        //Describe the key of the frame rate (in frames/second) of the video format. The frame rate is generally within 15 to 30, too small can easily cause video freezes.
        mediaFormat!!.setInteger(MediaFormat.KEY_FRAME_RATE, fps)
        //Color format, check the relevant API for details, the color format supported by different devices is not the same
        mediaFormat!!.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
        //Key frame interval time, in seconds
        mediaFormat!!.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
    }

    fun startDecoder() {
        LogUtil.d(TAG, "[startDecoder] startDecoder decoder")
        if (mediaCodec != null && mSurface != null) {
            mediaCodec!!.setCallback(mediaCodecCallback)
            mediaCodec!!.configure(mediaFormat, mSurface, null, 0)
            mediaCodec!!.start()
        } else {
            throw IllegalArgumentException("[startDecoder] startDecoder failed,is the MediaCodec has been init correct?")
        }
    }

    fun stopDecoder() {
        LogUtil.d(TAG, "[stopDecoder] stop decoder")
        if(mediaCodec!=null){
            mediaCodec!!.stop()
            mediaCodec!!.setCallback(null)
        }
    }

    fun releaseDecoder() {
        LogUtil.d(TAG, "[releaseDecoder] release decoder")
        if(mediaCodec!=null){
            mediaCodec!!.release()
            mediaCodec = null
        }
        mInputDataQueue.clear()
    }

    fun inputFrameToDecoder(decoderData : CamData) {
        LogUtil.d(TAG, "[inputFrameToDecoder] 解碼器接收")
        mInputDataQueue.offer(decoderData)
    }

    private val mediaCodecCallback = object : MediaCodec.Callback(){
        override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
            val inputBuffer = codec.getInputBuffer(index)
            inputBuffer?.clear()
            var length = 0
            var pts = 0L
            if(mInputDataQueue.size > 0){
                val camData = mInputDataQueue.poll()
                val dataSource = camData!!.data
                pts = camData.pts
                inputBuffer?.put(dataSource)
                length = dataSource!!.size
            }
            codec.queueInputBuffer(index, 0, length, pts, 0)
        }

        override fun onOutputBufferAvailable(
            codec: MediaCodec,
            index: Int,
            info: MediaCodec.BufferInfo
        ) {
            val outputBuffer = codec.getOutputBuffer(index)
            val outputFormat = codec.getOutputFormat(index)
            if(mediaFormat == outputFormat && outputBuffer != null && info.size > 0){
                val outData = ByteArray(outputBuffer.remaining())
                outputBuffer.get(outData)
            }
            codec.releaseOutputBuffer(index, true)
        }

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            LogUtil.e(TAG, "[mediaCodecCallback] onError")
        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            LogUtil.d(TAG, "[mediaCodecCallback] onOutputFormatChanged")
        }
    }
}