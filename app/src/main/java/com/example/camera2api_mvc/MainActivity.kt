package com.example.camera2api_mvc

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.graphics.*
import android.net.Uri
import android.os.*
import androidx.appcompat.app.AppCompatActivity
import android.provider.MediaStore
import android.util.Size
import android.view.*
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.core.widget.PopupWindowCompat
import com.example.camera2api_mvc.mediacodecDecoder.MediaCodecDecoder
import com.example.camera2api_mvc.model.CamData
import com.example.camera2api_mvc.util.*
import com.example.camera2api_mvc.view.IViewListener
import com.example.camera2api_mvc.view.IZoomWindowListener
import com.example.camera2api_mvc.view.ZoomPopupWindow
import com.example.trtcforkotlin.media_encode.IEncoderListener
import com.example.trtcforkotlin.media_encode.MediaCodecEncoder
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.zoom_popupwindow.*
import java.io.*
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

@Suppress("DEPRECATION")
class MainActivity : AppCompatActivity(), IEncoderListener, ICameraListener, IViewListener, IZoomWindowListener {

    companion object {
        private val TAG = MainActivity::class.java.simpleName
        private val PERMISSIONS_STORAGE = arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        private const val MSG_STOP_RECORD = 0
        private const val MSG_START_RECORD = 1
        private var BIT_RATE = 1*1024*1024
        private var FPS = 30
    }
    private lateinit var mCameraUtil: CameraUtil
    private lateinit var dateUpdateHandler : Handler
    private lateinit var zoomPopupWindow: ZoomPopupWindow
    private var mediaCodecEncoder: MediaCodecEncoder? = null
    private var mediaCodecDecoder: MediaCodecDecoder? = null
    private var isRecording = false
    private var isScreenShot = false
    private var mCamPreviewSize:Size? = null
    private var mZoomValue = 1.0f
    private var minZOOM = 1.0f
    private var maxZOOM = 3.0f

    //錄影時間參數
    private lateinit var mCountDownTimer: CountDownTimer
    private var second = 0
    private var minute = 0
    private var hour = 0

    private val recordHandler : Handler = @SuppressLint("HandlerLeak")
    object : Handler() {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            when(msg.what){
                MSG_STOP_RECORD->{
                    //停止錄影
                    isRecording = false
                    record_btn?.setBackgroundResource(R.drawable.button_rec)
                    //停止解碼器
                    mediaCodecDecoder?.stopDecoder()
                    mediaCodecDecoder?.releaseDecoder()
                    mediaCodecDecoder = null
                    //停止編碼器
                    mediaCodecEncoder?.stopEncoder()
                    mediaCodecEncoder?.releaseEncoder()
                    mediaCodecEncoder = null
                    mCountDownTimer.cancel()
                    txt_record_time?.text = getString(R.string.default_record_time)
                    txt_record_time?.setCompoundDrawablesWithIntrinsicBounds(R.drawable.stop_record_circle_12, 0, 0, 0)
                    second = 0
                    minute = 0
                    hour = 0
                }
                MSG_START_RECORD->{
                    //開始錄影
                    isRecording = true
                    record_btn?.setBackgroundResource(R.drawable.button_rec_stop)
                    txt_record_time?.setCompoundDrawablesWithIntrinsicBounds(R.drawable.start_record_circle_12, 0, 0, 0)
                    //開啟編碼器
                    mediaCodecEncoder = MediaCodecEncoder(mCamPreviewSize?.width!!, mCamPreviewSize?.height!!, BIT_RATE, FPS)
                    mediaCodecEncoder?.setEncodeDataCallback(this@MainActivity)
                    mediaCodecEncoder?.startEncoder()
                    mCountDownTimer.start()
                    //開啟解碼器
                    mediaCodecDecoder = MediaCodecDecoder(mCamPreviewSize?.width!!, mCamPreviewSize?.height!!, Surface(surface_view_decoder?.surfaceTexture), BIT_RATE, FPS)
                    mediaCodecDecoder?.startDecoder()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        init()
    }

    override fun onStart() {
        super.onStart()
        if(PermissionHelper.checkPermission(this, PERMISSIONS_STORAGE)) {
            LogUtil.d(TAG, "[onStart] All permission granted !!")
        }
    }

    override fun onResume() {
        super.onResume()
        mCameraUtil.startCameraPreView()
        startDateHandler(0)
    }

    override fun onStop() {
        super.onStop()
        if(isRecording){
            val msg = Message()
            msg.what = MSG_STOP_RECORD
            recordHandler.sendMessage(msg)
        }
        if(zoomPopupWindow.isShowing) zoomPopupWindow.dismiss()
        mCameraUtil.closeCamera()
        dateUpdateHandler.removeCallbacks(dateTaskRunnable)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        //處理手機全屏
        if(hasFocus) FullScreenUtil.hideNavigationBar(window)
    }

    //初始化物件、資料
    private fun init(){
        zoomPopupWindow = ZoomPopupWindow(this)
        mCameraUtil = CameraUtil(this, texture_view_camera2)
        mCameraUtil.setCameraListener(this)
        dateUpdateHandler = Handler(Looper.myLooper()!!)
        texture_view_camera2?.setViewListener(this)
        record_btn?.setOnTouchListener(btnRecordTouch)
        //建構mCountDownTimer(使用倒數計時器來實作錄影計時)
        mCountDownTimer = object : CountDownTimer(Long.MAX_VALUE, 1000){
            override fun onTick(millisUntilFinished: Long) {
                second++
                txt_record_time?.text = recorderTime()
            }
            //在倒數計時時間到才會觸發
            override fun onFinish() {
                LogUtil.d(TAG,"[startRecordTimer] onFinish !!")
            }
        }
    }

    //監聽錄影按鈕
    @SuppressLint("ClickableViewAccessibility")
    private val btnRecordTouch = View.OnTouchListener { _, event ->
        if (event?.action == MotionEvent.ACTION_DOWN) {
            val animScaleUp =
                AnimationUtils.loadAnimation(this@MainActivity, R.anim.scale_up)
            record_btn?.startAnimation(animScaleUp)
        } else if (event?.action == MotionEvent.ACTION_UP) {
            val animScaleDown =
                AnimationUtils.loadAnimation(this@MainActivity, R.anim.scale_down)
            record_btn?.startAnimation(animScaleDown)
            val msg = Message()
            if(!isRecording){
                msg.what = MSG_START_RECORD
            }else{
                Toast.makeText(this, "影片已暫停錄製", Toast.LENGTH_SHORT).show()
                msg.what = MSG_STOP_RECORD
            }
            recordHandler.sendMessage(msg)
        }
        true
    }

    //監聽截圖按鈕、縮放文字
    fun btnEventCLK(v: View) {
        when(v.id) {
            R.id.crop_btn->{
                isScreenShot = true
            }
            R.id.txt_zoom->{
                //點擊縮放文字 顯示縮放控制視窗
                if(!zoomPopupWindow.isShowing){
                    val contentView = zoomPopupWindow.contentView
                    contentView.measure(makeDropDownMeasureSpec(zoomPopupWindow.width),makeDropDownMeasureSpec(zoomPopupWindow.height))
                    val offsetX = -zoomPopupWindow.contentView.measuredWidth
                    val offsetY = -(zoomPopupWindow.contentView.measuredHeight + txt_zoom?.height!!) / 2
                    PopupWindowCompat.showAsDropDown(zoomPopupWindow, txt_zoom, offsetX, offsetY, Gravity.START)
                } else {
                    zoomPopupWindow.dismiss()
                }
            }
        }
    }

    //控制zoom button group控制項以及設定 camera 目前 zoom value
    private fun updateCameraZoomValue(zoomVal : Float) {
        mZoomValue = zoomVal
        val df = DecimalFormat("##0.0")
        val strZoom = df.format(mZoomValue)
        txt_zoom?.text = strZoom + "x"
        zoomPopupWindow.clearZoomWindow()
        if(zoomPopupWindow.isShowing){
            when (strZoom) {
                "1.0" -> {
                    zoomPopupWindow.updateZoomWindow("1.0")
                }
                "2.0" -> {
                    zoomPopupWindow.updateZoomWindow("2.0")
                }
                "4.0" -> {
                    zoomPopupWindow.updateZoomWindow("4.0")
                }
                (maxZOOM.toString()) -> {
                    zoomPopupWindow.updateZoomWindow(maxZOOM.toString())
                }
            }
        }
        mCameraUtil.setCameraZoom(zoomVal)
    }

    //處理日期更新
    private val dateTaskRunnable = Runnable {
        val calendar = Calendar.getInstance(Locale.TAIWAN)
        txt_date?.text = android.text.format.DateFormat.format("yyyy/MM/dd HH:mm", calendar).toString()
        startDateHandler(1000)
    }

    //開啟 dateUpdateHandler
    private fun startDateHandler(Milli_Sec:Long){
        dateUpdateHandler.postDelayed(dateTaskRunnable, Milli_Sec)
    }

    //將bitmap用jpg格式存在手機DCIM資料夾
    private fun saveImage(bitmap: Bitmap, name:String){
        val fos: OutputStream?
        try{
            fos = if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q){
                val contentValues = ContentValues()
                contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, "$name.jpg")
                contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpg")
                contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM)
                val imageUri: Uri? = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                contentResolver.openOutputStream(imageUri!!)
            }else{
                val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString()
                val image = File(imagesDir, "$name.jpg")
                FileOutputStream(image)
            }
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)
            fos?.close()
        }catch (e: IOException){
            e.printStackTrace()
        }
    }

    //顯示錄影時間
    private fun recorderTime():String{
        if(second==60){
            minute++
            second = 0
        }
        if(minute==60){
            hour++
            minute = 0
        }
        return String.format(Locale.TAIWAN, "%02d：%02d：%02d", hour, minute, second)
    }

    //請求權限回傳
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        PermissionHelper.onRequestPermissionsResult(requestCode, grantResults, object : IPermissionListener {
            override fun onPermissionGranted() {
                LogUtil.d(TAG, "[onRequestPermissionsResult] onPermissionGranted !!")
            }

            override fun onPermissionDenied() {
                LogUtil.d(TAG, "[onRequestPermissionsResult] onPermissionDenied !!")
                finish()
            }
        })
    }

    //從編碼器收到已經編碼好的資料
    override fun onEncodeFrameData(
        type: String,
        data: ByteArray,
        size: Int,
        presentationTime: Long
    ) {
        val camData = CamData()
        camData.data = data
        camData.dataSize = size
        camData.pts = presentationTime
        mediaCodecDecoder?.inputFrameToDecoder(camData)
    }

    //從相機收到影像資料，送到編碼器進行編碼
    override fun onPreviewFrame(data: ByteArray, width: Int, height: Int, pts: Long) {
        if(isRecording){
            if(null == mediaCodecEncoder) return
            val nv12 = ImageUtil.NV21ToNV12(data, width, height)
            mediaCodecEncoder?.inputFrameToEncoder(nv12, pts)
        }
        if(isScreenShot){
            val out = ByteArrayOutputStream()
            val yuvImage = YuvImage(data, ImageFormat.NV21, width, height, null)
            yuvImage.compressToJpeg(Rect(0, 0, width, height), 50, out)
            val imageBytes = out.toByteArray()
            val image = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            try{
                saveImage(image, SimpleDateFormat("yyyy-MM-dd-HHmmssSSS", Locale.TAIWAN).format( Date()))
                Toast.makeText(this, "已儲存至-相簿", Toast.LENGTH_SHORT).show()
            }catch (e:IOException){
                Toast.makeText(this, "圖片儲存失敗", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
            isScreenShot = false
        }
    }

    //獲得相機參數
    override fun onCameraParam(previewSize: Size, Max_Zoom: Float) {
        mCamPreviewSize = previewSize
        maxZOOM = Max_Zoom
        zoomPopupWindow.setMaxZoom(Max_Zoom)
    }

    //手勢操作zoom in、zoom out
    override fun onZoomLevelChanged(scaleFactor: Float) {
        mZoomValue *= scaleFactor
        mZoomValue = java.lang.Float.max(minZOOM, java.lang.Float.min(mZoomValue, maxZOOM))
        updateCameraZoomValue(mZoomValue)
    }
    //測量 popupWindow 大小
    private fun makeDropDownMeasureSpec(measureSpec:Int) : Int {
        val mode = if(measureSpec == ViewGroup.LayoutParams.WRAP_CONTENT) {
            View.MeasureSpec.UNSPECIFIED
        } else {
            View.MeasureSpec.EXACTLY
        }
        return View.MeasureSpec.makeMeasureSpec(View.MeasureSpec.getSize(measureSpec), mode)
    }
    //從popupWindow設定縮放數值
    override fun onZoomValueSelected(value: Float) {
        updateCameraZoomValue(value)
    }
}