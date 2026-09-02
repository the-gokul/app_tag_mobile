package com.nordic.tagmobile

import android.app.Application
import com.nordic.tagmobile.ble.TagBleManager

class TagApp : Application() {
    lateinit var bleManager: TagBleManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        bleManager = TagBleManager(this)
    }

    companion object {
        lateinit var instance: TagApp
            private set
    }
}
