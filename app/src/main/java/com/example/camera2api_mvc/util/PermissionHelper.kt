package com.example.camera2api_mvc.util

import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

class PermissionHelper {

    companion object{
        private const val REQUEST_CODE = 10

        //請求權限，只要有一個權限沒被授權，返回false
        fun checkPermission(activity: Activity, permissionArray: Array<String>): Boolean {
            for(permission in permissionArray) {
                if(ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
                    activity.requestPermissions(permissionArray, REQUEST_CODE)
                    return false
                }
            }
            return true
        }
        //請求權限回傳
        fun onRequestPermissionsResult(requestCode:Int, grantResults:IntArray, permissionListener: IPermissionListener) {
            when(requestCode) {
                REQUEST_CODE -> {
                    if(grantResults.isNotEmpty()){
                        for(element in grantResults){
                            if(element == PackageManager.PERMISSION_GRANTED){
                                permissionListener.onPermissionGranted()
                            }else{
                                permissionListener.onPermissionDenied()
                            }
                        }
                    }
                }
            }
        }
    }
}