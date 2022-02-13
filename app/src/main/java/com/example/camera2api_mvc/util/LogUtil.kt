package com.example.camera2api_mvc.util

import android.util.Log

class LogUtil {
    companion object{
        private const val showLog : Boolean = true

        fun d(tag: String, msg: String) = log("d", tag, msg)
        fun i(tag: String, msg: String) = log("i", tag, msg)
        fun w(tag: String, msg: String) = log("w", tag, msg)
        fun e(tag: String, msg: String) = log("e", tag, msg)

        private fun log(type: String, tag: String, msg: String){
            if(!showLog) return
            when(type){
                "d"-> Log.d(tag, msg)
                "i"-> Log.i(tag, msg)
                "w"-> Log.w(tag, msg)
                "e"-> Log.e(tag, msg)
            }
        }
    }
}