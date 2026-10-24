package io.github.animeliodas.bayun.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.animeliodas.bayun.R
import io.github.animeliodas.bayun.data.Prefs

/** Размер шрифта, тема и цвет интерфейса — одинаково в настройках и в панели «Оформление» читалки. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LookControls() {
    val s by Prefs.settings.collectAsState()
    Text(stringResource(R.string.font_size, s.fontSize.toInt()), style = MaterialTheme.typography.titleSmall)
    Slider(value = s.fontSize, onValueChange = { v -> Prefs.update { it.copy(fontSize = v) } }, valueRange = 13f..32f)
    Text(stringResource(R.string.theme), style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("system" to R.string.theme_system, "light" to R.string.theme_light,
            "sepia" to R.string.theme_sepia, "dark" to R.string.theme_dark).forEach { (id, label) ->
            FilterChip(selected = s.theme == id, onClick = { Prefs.update { it.copy(theme = id) } },
                label = { Text(stringResource(label)) })
        }
    }
    Text(stringResource(R.string.accent), style = MaterialTheme.typography.titleSmall)
    AccentPicker(s.accent, isDarkTheme(s.theme)) { id -> Prefs.update { it.copy(accent = id) } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccentPicker(selected: String, dark: Boolean, onPick: (String) -> Unit) {
    val context = LocalContext.current
    val options = ACCENTS.map { Triple(it.id, it.label, if (dark) it.dark.primary else it.light.primary) } +
        if (dynamicColorAvailable) listOf(Triple("dynamic", R.string.accent_dynamic,
            if (dark) dynamicDarkColorScheme(context).primary else dynamicLightColorScheme(context).primary))
        else emptyList()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (id, label, color) ->
            Column(Modifier.width(72.dp).clickable { onPick(id) }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(color)
                    .border(if (id == selected) 3.dp else 0.dp,
                        if (id == selected) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape),
                    contentAlignment = Alignment.Center) {
                    if (id == selected) Icon(Icons.Default.Check, null, tint = if (dark) Color.Black else Color.White)
                }
                Text(stringResource(label), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
            }
        }
    }
}
