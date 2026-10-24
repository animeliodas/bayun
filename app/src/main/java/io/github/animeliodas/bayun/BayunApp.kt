package io.github.animeliodas.bayun

import android.app.Application
import io.github.animeliodas.bayun.data.Importer
import io.github.animeliodas.bayun.data.Prefs
import io.github.animeliodas.bayun.engine.PolytomeEngine
import io.github.animeliodas.bayun.translate.NightTranslation
import io.github.animeliodas.bayun.translate.TranslationController
import io.github.animeliodas.bayun.tts.ReadAloud

class BayunApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        PolytomeEngine.init(this)
        ReadAloud.init(this)
        TranslationController.init(this)
        NightTranslation.init(this)
        // Python и движок стартуют заранее: первый запуск распаковывает библиотеки (несколько секунд)
        Thread {
            runCatching { Importer.cleanupLeftovers(this) }
            runCatching {
                PolytomeEngine.configure(Prefs.polytomeConfig())
                PolytomeEngine.warmUp()
            }
        }.start()
    }
}
