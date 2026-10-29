package ar.pharmatrace.mobile

import android.app.Application
import ar.pharmatrace.mobile.ble.PharmaBoxBle
import ar.pharmatrace.mobile.data.ApiClient
import ar.pharmatrace.mobile.data.SessionStore

/** Manual dependency injection: the app is small enough not to need a framework. */
class PharmaTraceApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(app: Application) {
    val sessions = SessionStore(app, BuildConfig.DEFAULT_API_URL)
    val ble = PharmaBoxBle(app)

    /** Client for the URL of the current session (or the last one used). */
    fun api(url: String = sessions.session.value?.apiUrl ?: sessions.apiUrl): ApiClient =
        ApiClient(url, token = sessions::validToken)
}
