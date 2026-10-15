package app.magen.lock

import android.app.Application

class MagenApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
    }
}
