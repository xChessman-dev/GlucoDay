package dev.chessman.glucoday

import android.app.Application
import dev.chessman.glucoday.data.AppRepository

class GlucoApplication : Application() {
    val repository by lazy { AppRepository(this) }
}
