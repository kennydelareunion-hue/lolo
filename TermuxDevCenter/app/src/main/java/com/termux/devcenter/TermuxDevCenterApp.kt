package com.termux.devcenter

import android.app.Application

class TermuxDevCenterApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: TermuxDevCenterApp
            private set
    }
}
