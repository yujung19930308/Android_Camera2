package com.example.camera2api_mvc.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Range
import android.util.Size
import android.util.SparseIntArray
import android.view.Surface
import android.view.TextureView
import android.view.WindowManager
import androidx.core.app.ActivityCompat
import androidx.core.math.MathUtils
import com.example.camera2api_mvc.view.AutoFitTextureView
import java.lang.Exception
import java.lang.RuntimeException
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max

@Suppress("DEPRECATION")
class CameraUtil(private val mContext: Context, private val textureView: AutoFitTextureView) {
    companion object {
        private val TAG = CameraUtil::class.java.simpleName
        const val MAX_PREVIEW_WIDTH = 1920 //Max preview width that is guaranteed by Camera2 API
        const val MAX_PREVIEW_HEIGHT = 1080 //Max preview height that is guaranteed by Camera2 API
        const val DEFAULT_ZOOM_FACTOR = 1.0f
    }
    private lateinit var mCameraListener: ICameraListener
    private lateinit var mPreviewSize: Size
    private lateinit var mPreviewRequestBuilder: CaptureRequest.Builder
    private var mBackgroundThread: HandlerThread? = null
    private var mBackgroundHandler: Handler? = null
    private val mCameraOpenCloseLock = Semaphore(1)
    private var mCameraId: String? = null
    private var mCameraDevice: CameraDevice? = null
    private var mImageReader: ImageReader? = null
    private var mSensorOrientation: Int = 0
    private val defaultFPS = 30
    private var mImagePictureSize = Size(1280, 720)
    private var mPreviewSession: CameraCaptureSession? = null

    //Camera zoom 會使用到的參數
    private val mCropRegion = Rect()
    private var maxZoom: Float = 0.0f
    private var mSensorSize: Rect? = null
    private var hasSupport: Boolean = false

    private val mORIENTATIONS = SparseIntArray().apply {
        append(Surface.ROTATION_0, 90)
        append(Surface.ROTATION_90, 0)
        append(Surface.ROTATION_180, 270)
        append(Surface.ROTATION_270, 180)
    }

    private val mOnImageAvailableListener = object : ImageReader.OnImageAvailableListener {
        override fun onImageAvailable(reader: ImageReader?) {
            val image = reader?.acquireLatestImage() ?: return
            if(mCameraListener != null) mCameraListener.onPreviewFrame(ImageUtil.YUV_420_888toNV21(image), image.width, image.height, image.timestamp / 1000)
            image.close()
        }
    }

    private val mSurfaceTextureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            //在TextureView可用的時候打開相機
            LogUtil.d(TAG, "[onSurfaceTextureAvailable] w=$width, h=$height")
            openCamera(width, height)
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
            LogUtil.d(TAG, "[onSurfaceTextureSizeChanged] w=$width, h=$height")
            configureTransform(width, height)
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            return true
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {

        }
    }

    //監聽CameraDevice狀態回調
    private val mStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            //相機成功打開回調該方法
            mCameraOpenCloseLock.release()
            mCameraDevice = camera
            createCameraPreviewSession()//要想预览、拍照等操作都是需要通过会话来实现，所以创建会话用于预览
        }

        override fun onDisconnected(camera: CameraDevice) {
            //相機不再可用回調該方法，拋出CameraAccessException，可能是權限問題、相機ID不再可用
            mCameraOpenCloseLock.release()
            camera.close()
            mCameraDevice = null
        }

        override fun onError(camera: CameraDevice, error: Int) {
            //相機打開失敗回調該方法
            mCameraOpenCloseLock.release()
            camera.close()
            mCameraDevice = null
        }

        override fun onClosed(camera: CameraDevice) {
            //相機關閉回調該方法
            LogUtil.d(TAG, "[mStateCallback] Camera onClosed !!")
            super.onClosed(camera)
        }
    }

    //開啟相機預覽畫面
    fun startCameraPreView() {
        startBackgroundThread()
        //1、如果TextureView 可用则直接打开相机
        if (textureView != null) {
            if (textureView.isAvailable) {
                openCamera(textureView.width, textureView.height)
            } else {
                textureView.surfaceTextureListener = mSurfaceTextureListener //设置TextureView 的回调后，当满足之后自动回调到
            }
        }
    }

    //開啟相機功能
    private fun openCamera(width: Int, height: Int) {
        if (ActivityCompat.checkSelfPermission(
                mContext,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        //設置參數
        setUpCameraOutputs(width, height)
        configureTransform(width, height)
        val manager = mContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw RuntimeException("Time out waiting to lock camera opening.")
            }
            manager.openCamera(mCameraId!!, mStateCallback, mBackgroundHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera opening.", e)
        }
    }

    fun closeCamera() {
        if (mImageReader != null) {
            mImageReader!!.setOnImageAvailableListener(null, null)
        }
        try {
            mCameraOpenCloseLock.acquire()
            closePreviewSession()
            if (null != mCameraDevice) {
                mCameraDevice!!.close()
                mCameraDevice = null
            }
            if (null != mImageReader) {
                mImageReader!!.close()
                mImageReader = null
            }
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera closing.", e)
        } finally {
            mCameraOpenCloseLock.release()
        }
        stopBackgroundThread()
    }

    private fun closePreviewSession() {
        if (mPreviewSession != null) {
            mPreviewSession!!.close()
            mPreviewSession = null
        }
    }

    private fun setUpCameraOutputs(width: Int, height: Int) {
        val manager = mContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try{
            for(cameraId in manager.cameraIdList){
                val characteristics = manager.getCameraCharacteristics(cameraId)
                if(characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) continue
                val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                //獲取camera zoom數值
                mSensorSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
                if(mSensorSize==null){
                    maxZoom = DEFAULT_ZOOM_FACTOR
                    hasSupport = false
                }else{
                    val value = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)
                    maxZoom = if((value==null) || (value < DEFAULT_ZOOM_FACTOR)){
                        DEFAULT_ZOOM_FACTOR } else { value }
                    hasSupport = compareValues(this.maxZoom, DEFAULT_ZOOM_FACTOR) > 0
                }
                val displayRotation: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    mContext.display!!.rotation
                } else {
                    val wm = mContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                    val display = wm.defaultDisplay
                    display.rotation
                }
                mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)!!
                var swappedDimensions = false
                when(displayRotation){
                    Surface.ROTATION_0, Surface.ROTATION_180->{
                        if (mSensorOrientation == 90 || mSensorOrientation == 270) swappedDimensions =
                            true
                    }
                    Surface.ROTATION_90, Surface.ROTATION_270->{
                        if (mSensorOrientation == 0 || mSensorOrientation == 180) swappedDimensions =
                            true
                    }
                    else -> LogUtil.e(TAG, "[setUpCameraOutputs] Display rotation is invalid: $displayRotation")
                }

                var rotatedPreviewWidth = width
                var rotatedPreviewHeight = height
                if(swappedDimensions){
                    rotatedPreviewWidth = height
                    rotatedPreviewHeight = width
                }
                if(rotatedPreviewWidth > MAX_PREVIEW_WIDTH){
                    rotatedPreviewWidth = MAX_PREVIEW_WIDTH
                }
                if(rotatedPreviewHeight > MAX_PREVIEW_HEIGHT){
                    rotatedPreviewHeight = MAX_PREVIEW_HEIGHT
                }
                mPreviewSize = chooseOptimalSize(map!!.getOutputSizes(SurfaceTexture::class.java), MAX_PREVIEW_WIDTH, MAX_PREVIEW_HEIGHT, mImagePictureSize)
                //初始化好預覽大小，比較只有預覽大小確定後，我們才知道得使用一塊多大的內存來接收這塊數據
                mImageReader = ImageReader.newInstance(mPreviewSize.width, mPreviewSize.height, ImageFormat.YUV_420_888, 2)
                mImageReader!!.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler)
                mCameraId = cameraId
                mCameraListener.onCameraParam(mPreviewSize, maxZoom)
                return
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    //相機預覽
    private fun createCameraPreviewSession() {
        if(mCameraDevice == null || !textureView.isAvailable || mPreviewSize == null) return
        try{
            val texture = textureView.surfaceTexture
            //默認緩衝區的大小配置為我們想要的相機預覽的大小
            texture!!.setDefaultBufferSize(mPreviewSize.width, mPreviewSize.height)
            // set up a CaptureRequest.Builder with the output Surface.
            mPreviewRequestBuilder = mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
            val surfaces = ArrayList<Surface>()
            //自動對焦
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            //設置fps range
            val fps : Range<Int> = Range(defaultFPS, defaultFPS)
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fps)

            val rotation: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                mContext.display!!.rotation
            } else {
                val wm = mContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val display = wm.defaultDisplay
                display.rotation
            }
            mPreviewRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation))
            // This is the output Surface we need to start preview.
            val previewSurface = Surface(texture)
            surfaces.add(previewSurface)
            mPreviewRequestBuilder.addTarget(previewSurface)// 把显示预览界面的TextureView添加到到CaptureRequest.Builder中

            // Set up Surface for the MediaRecorder
            val recorderSurface = mImageReader!!.surface
            surfaces.add(recorderSurface)
            mPreviewRequestBuilder.addTarget(recorderSurface)

            // create a CameraCaptureSession for camera preview.
            mCameraDevice!!.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback(){
                override fun onConfigured(session: CameraCaptureSession) {
                    // When the session is ready, we start displaying the preview.
                    try{
                        mPreviewSession = session
                        mPreviewSession!!.setRepeatingRequest(mPreviewRequestBuilder.build(), null, mBackgroundHandler)
                    }catch (e: CameraAccessException){
                        e.printStackTrace()
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    LogUtil.d(TAG, "[startPreview] Unable to setup camera preview !!")
                }
            }, mBackgroundHandler)
        }catch (e: CameraAccessException){
            e.printStackTrace()
        }
    }

    //控制相機zoom in, zoom out
    fun setCameraZoom(zoomVal: Float) {
        if (!hasSupport) return
        val newZoom = MathUtils.clamp(
            zoomVal, DEFAULT_ZOOM_FACTOR,
            maxZoom
        )
        val centerX = this.mSensorSize?.width()!! / 2
        val centerY = this.mSensorSize?.height()!! / 2
        val deltaX = ((0.5f * this.mSensorSize?.width()!!) / newZoom).toInt()
        val deltaY = ((0.5f * this.mSensorSize?.height()!!) / newZoom).toInt()

        mCropRegion[centerX - deltaX, centerY - deltaY, centerX + deltaX] = centerY + deltaY

        mPreviewRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, mCropRegion)
        try {
            mPreviewSession!!.setRepeatingRequest(
                mPreviewRequestBuilder.build(),
                null,
                mBackgroundHandler
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun getOrientation(rotation: Int): Int {
        return (mORIENTATIONS.get(rotation) + mSensorOrientation + 270) % 360
    }

    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        if (null == textureView || null == mPreviewSize) {
            return
        }
        val rotation: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            mContext.display!!.rotation
        } else {
            val wm = mContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val display = wm.defaultDisplay
            display.rotation
        }
        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect =
            RectF(0f, 0f, mPreviewSize.height.toFloat(), mPreviewSize.width.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            val scale = max(
                viewHeight.toFloat() / mPreviewSize.height,
                viewWidth.toFloat() / mPreviewSize.width)
            with(matrix) {
                postScale(scale, scale, centerX, centerY)
                postRotate((90 * (rotation - 2)).toFloat(), centerX, centerY)
            }
        }
        textureView.setTransform(matrix)
    }

    //開啟HandlerThread
    private fun startBackgroundThread() {
        mBackgroundThread = HandlerThread("CameraBackground")
        mBackgroundThread!!.start()
        mBackgroundHandler = Handler(mBackgroundThread!!.looper)
    }

    //關閉HandlerThread
    private fun stopBackgroundThread() {
        if (mBackgroundThread == null) {
            return
        }
        mBackgroundThread?.quitSafely()
        try {
            mBackgroundThread?.join()
            mBackgroundThread = null
            mBackgroundHandler = null
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    //選擇最佳解析度
    private fun chooseOptimalSize(choices:Array<Size>, maxWidth:Int, maxHeight:Int, aspectRatio:Size):Size{
        var supportDefaultSize = false
        val w = aspectRatio.width
        val h = aspectRatio.height
        var wRatioDiff = Float.MAX_VALUE
        var hRatioDiff = Float.MAX_VALUE
        var approximateSize:Size? = null
        for(option in choices){
            if(option.width <=maxWidth && option.height <= maxHeight){
                //尋找與aspectRatio寬高、比例一樣的size
                if(aspectRatio.width == option.width && aspectRatio.height == option.height
                    && option.height == option.width * h / w){
                    supportDefaultSize = true
                    break
                }else{
                    //尋找與aspectRatio寬高接近的size
                    val curWidthRatio = (option.width / w).toFloat()
                    if(abs(curWidthRatio - 1) <= wRatioDiff){
                        wRatioDiff = abs(curWidthRatio - 1)
                        val curHeightRatio = (option.height / h).toFloat()
                        if(abs(curHeightRatio - 1) <= hRatioDiff){
                            hRatioDiff = abs(curHeightRatio - 1)
                            approximateSize = option
                        }
                    }
                }
            }
        }
        return if(supportDefaultSize){
            aspectRatio
        }else{
            approximateSize!!
        }
    }

    fun setCameraListener(cameraListener: ICameraListener) {
        this.mCameraListener = cameraListener
    }
}