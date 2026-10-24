package io.github.animeliodas.bayun

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.animeliodas.bayun.data.Prefs
import io.github.animeliodas.bayun.translate.TranslationController
import io.github.animeliodas.bayun.ui.BayunTheme
import io.github.animeliodas.bayun.ui.LibraryScreen
import io.github.animeliodas.bayun.ui.Playback
import io.github.animeliodas.bayun.ui.ReaderScreen
import io.github.animeliodas.bayun.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    /** Файл, открытый через «Открыть с помощью Bayun», — импортируется на экране библиотеки. */
    private val pendingImport = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Playback.connect(this)
        takeImport(intent)
        setContent {
            val settings by Prefs.settings.collectAsState()
            BayunTheme(settings.theme, settings.accent) {
                AppNavigation(pendingImport.value) { pendingImport.value = null }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // приложение на экране — Android снова разрешает фоновую работу: продолжить прерванные переводы
        TranslationController.autoResume()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        takeImport(intent)
    }

    private fun takeImport(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) intent.data?.let { pendingImport.value = it }
    }

    override fun onDestroy() {
        if (isFinishing) Playback.release()
        super.onDestroy()
    }
}

@Composable
private fun AppNavigation(pendingImport: Uri?, consumeImport: () -> Unit) {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "library") {
        composable("library") {
            LibraryScreen(
                pendingImport = pendingImport,
                consumeImport = consumeImport,
                onOpen = { book -> nav.navigate("reader/" + Uri.encode(book.path)) },
                onSettings = { nav.navigate("settings") },
            )
        }
        composable("reader/{path}", arguments = listOf(navArgument("path") { type = NavType.StringType })) { entry ->
            ReaderScreen(path = Uri.decode(entry.arguments?.getString("path").orEmpty()), onBack = { nav.popBackStack() })
        }
        composable("settings") {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}
