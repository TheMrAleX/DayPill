package app.pastillero

import android.app.Application

class PastilleroApp : Application() {
    val container: AppContainer by lazy { AppContainer() }
}

// Data and reminder dependencies are added in their respective milestones.
class AppContainer
