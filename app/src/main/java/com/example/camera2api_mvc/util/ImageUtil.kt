package com.example.camera2api_mvc.util

import android.graphics.ImageFormat
import android.graphics.Rect
import android.media.Image

class ImageUtil {
    companion object{
        fun NV21ToNV12(nv21:ByteArray, width:Int, height:Int):ByteArray{
            val nv12 = ByteArray(width*height*3/2)
            val frameSize = width * height
            System.arraycopy(nv21, 0, nv12, 0, frameSize)
            for(i in 0 until frameSize){
                nv12[i] = nv21[i]
            }
            for(j in 0 until (frameSize/2) step 2){
                nv12[frameSize + j - 1] = nv21[j + frameSize]
            }
            for(j in 0 until (frameSize/2) step 2){
                nv12[frameSize + j] = nv21[j + frameSize - 1]
            }
            return nv12
        }

        fun YUV_420_888toNV21(image: Image):ByteArray{
            val crop:Rect = image.cropRect
            val format = image.format
            val width = crop.width()
            val height = crop.height()
            val planes: Array<out Image.Plane>? = image.planes
            val data = ByteArray(width * height * ImageFormat.getBitsPerPixel(format) / 8)
            val rowData = ByteArray(planes?.get(0)!!.rowStride)
            var channelOffset = 0
            var outputStride = 1
            for(i in planes.indices){
                when(i){
                    0->{
                        channelOffset = 0
                        outputStride = 1
                    }
                    1->{
                        channelOffset = width * height + 1
                        outputStride = 2
                    }
                    2->{
                        channelOffset = width * height
                        outputStride = 2
                    }
                }

                val buffer = planes[i].buffer
                val rowStride = planes[i].rowStride
                val pixelStride = planes[i].pixelStride

                val shift = if(i==0) 0 else 1
                val w = width shr shift
                val h = height shr shift
                buffer.position(rowStride * (crop.top shr shift) + pixelStride * (crop.left shr shift))
                for(row in 0 until h step 1){
                    var length: Int
                    if(pixelStride == 1 && outputStride == 1){
                        length = w
                        buffer.get(data, channelOffset, length)
                        channelOffset += length
                    }else{
                        length = (w - 1) * pixelStride + 1
                        buffer.get(rowData, 0, length)
                        for(col in 0 until w step 1){
                            data[channelOffset] = rowData[col * pixelStride]
                            channelOffset += outputStride
                        }
                    }
                    if(row < h - 1){
                        buffer.position(buffer.position() + rowStride - length)
                    }
                }
            }
            return data
        }
    }
}