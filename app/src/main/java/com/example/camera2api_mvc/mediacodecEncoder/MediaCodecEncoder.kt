package com.example.trtcforkotlin.media_encode

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import com.example.camera2api_mvc.model.CamData
import com.example.camera2api_mvc.model.FrameType
import com.example.camera2api_mvc.util.LogUtil
import java.io.IOException
import java.util.*
import java.util.concurrent.ArrayBlockingQueue

@Suppress("DEPRECATION")
class MediaCodecEncoder(width:Int, height:Int, bit_rate:Int, fps:Int) {
    companion object{
        private val TAG = MediaCodecEncoder::class.java.simpleName
        const val CONFIGURE_FLAG_ENCODE = MediaCodec.CONFIGURE_FLAG_ENCODE
        const val VIDEO_MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC
        private const val CACHE_BUFFER_SIZE = 15
        val mInputDataQueue: Queue<CamData> = ArrayBlockingQueue(CACHE_BUFFER_SIZE)
    }
    private lateinit var mIEncoderListener : IEncoderListener
    private var mediaCodec : MediaCodec? = null
    private var mediaFormat :MediaFormat? = null
    //private lateinit var spsData : ByteArray

    init {
        try{
            mediaCodec = MediaCodec.createEncoderByType(VIDEO_MIME_TYPE)
        }catch (e:IOException){
            e.printStackTrace()
        }
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
        //mediaFormat!!.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)
        mediaFormat!!.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
        //Key frame interval time, in seconds
        mediaFormat!!.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
    }
    //開啟編碼器
    fun startEncoder(){
        LogUtil.d(TAG, "[startEncoder] startEncoder encoder")
        if(mediaCodec!=null){
            mediaCodec!!.setCallback(mediaCodecCallback)
            mediaCodec!!.configure(mediaFormat, null, null, CONFIGURE_FLAG_ENCODE)
            mediaCodec!!.start()
        }else{
            throw IllegalArgumentException("[startEncoder] startEncoder failed,is the MediaCodec has been init correct?")
        }
    }
    //停止編碼器
    fun stopEncoder(){
        LogUtil.d(TAG, "[stopEncoder] stop encoder")
        if(mediaCodec!=null){
            mediaCodec!!.stop()
            mediaCodec!!.setCallback(null)
        }
    }
    //釋放資源
    fun releaseEncoder(){
        LogUtil.d(TAG, "[releaseEncoder] release encoder")
        if(mediaCodec!=null){
            mediaCodec!!.release()
            mediaCodec = null
        }
        mInputDataQueue.clear()
    }
    //將相機影像資料放進 mInputDataQueue
    fun inputFrameToEncoder(nv12_Data:ByteArray, pts:Long){
        val camData = CamData()
        camData.data = nv12_Data
        camData.pts = pts
        mInputDataQueue.offer(camData)
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
            codec.queueInputBuffer(index, 0, length, pts, CONFIGURE_FLAG_ENCODE)
        }

        override fun onOutputBufferAvailable(
            codec: MediaCodec,
            index: Int,
            info: MediaCodec.BufferInfo
        ) {
            val outputBuffer = codec.getOutputBuffer(index)
            if(outputBuffer!=null && info.size > 0){
                val outData = ByteArray(outputBuffer.remaining())
                outputBuffer.get(outData)
                when {
                    info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0 -> {
                        // PPS, also known as SPS
                        /*spsData = ByteArray(outData.size)
                        System.arraycopy(outData, 0, spsData, 0, outData.size)*/
                        mIEncoderListener.onEncodeFrameData(
                            FrameType.type_sps,
                            outData, info.size, info.presentationTimeUs)
                    }
                    info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0 -> {
                        // Keyframes
                        //確保I frame之前帶有sps/pps frame
                        /*mIEncoderListener.onEncodeFrameData(
                            FrameType.type_sps,
                            spsData, spsData.size, info.presentationTimeUs)*/
                        mIEncoderListener.onEncodeFrameData(FrameType.type_I, outData, info.size, info.presentationTimeUs)
                    }
                    else -> {
                        // Non key frame and SPS, PPS, direct write file, may be B frame or P frame
                        mIEncoderListener.onEncodeFrameData(FrameType.type_P, outData, info.size, info.presentationTimeUs)
                    }
                }
            }
            codec.releaseOutputBuffer(index, false)
        }

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            LogUtil.e(TAG, "[mediaCodecCallback] onError")
        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            LogUtil.d(TAG, "[mediaCodecCallback] onOutputFormatChanged")
        }
    }

    fun setEncodeDataCallback(IEncoderListener: IEncoderListener){
        this.mIEncoderListener = IEncoderListener
    }
}