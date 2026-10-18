package com.tradingmaster.pro

import android.app.Application

class ProApp : Application() {
    lateinit var container: ProContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = ProContainer(this)
    }
}
