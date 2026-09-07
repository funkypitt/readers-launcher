package com.freedomfighter.readerslauncher.widgets

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.freedomfighter.readerslauncher.App
import com.freedomfighter.readerslauncher.R
import com.freedomfighter.readerslauncher.ui.LocalColors
import com.freedomfighter.readerslauncher.ui.LocalTypo
import com.freedomfighter.readerslauncher.ui.Nav
import com.freedomfighter.readerslauncher.ui.Page
import com.freedomfighter.readerslauncher.ui.Rule
import com.freedomfighter.readerslauncher.ui.Screen
import com.freedomfighter.readerslauncher.ui.ScreenTitle
import com.freedomfighter.readerslauncher.ui.Small
import com.freedomfighter.readerslauncher.ui.T
import com.freedomfighter.readerslauncher.ui.TextRow
import com.freedomfighter.readerslauncher.ui.rowPadH
import com.freedomfighter.readerslauncher.ui.rowPadV
import com.freedomfighter.readerslauncher.ui.widgetTwoLineHeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The Littré's word of the day, read from the dictionary app's provider. */
data class WordOfDay(val word: String, val nature: String?, val text: String)

object Littre {
    const val PACKAGE = "ch.littre.littre_app"
    private val URI: Uri = Uri.parse("content://ch.littre.littre_app.wotd/today")

    fun isInstalled(context: Context) = runCatching { context.packageManager.getPackageInfo(PACKAGE, 0) }.isSuccess

    fun today(context: Context): WordOfDay? = runCatching {
        context.contentResolver.query(URI, null, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return null
            WordOfDay(c.getString(c.getColumnIndex("word")), c.getString(c.getColumnIndex("nature")), c.getString(c.getColumnIndex("text")) ?: "")
        }
    }.getOrNull()

    fun open(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(PACKAGE)
            ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/funkypitt/littre-app"))
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

/** The word with a dim "mot du jour" under it; tap shows the definition, long press the tile menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WordTileView(nav: Nav, onLongPress: () -> Unit) {
    val context = LocalContext.current
    val now = rememberNow()
    val day = java.time.LocalDate.now().toString()
    val word by produceState<WordOfDay?>(null, day, now / (30 * 60_000)) {
        value = withContext(Dispatchers.IO) { Littre.today(context) }
    }
    val label = word?.word?.lowercase() ?: if (Littre.isInstalled(context)) "…" else "Le Littré"
    // Two lines, like the agenda tile: the word, then a dim "mot du jour" so it does not read
    // as an app or category name.
    Column(
        Modifier
            .fillMaxWidth()
            .height(widgetTwoLineHeight())
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { if (word != null) nav.push(Screen.Word) else Littre.open(context) },
                onLongClick = onLongPress
            )
            .padding(horizontal = rowPadH, vertical = rowPadV),
        verticalArrangement = Arrangement.Center
    ) {
        T(label, maxLines = 1)
        Small(stringResource(R.string.widget_word), maxLines = 1)
    }
}

/** The full definition, as text, in the launcher's own style. */
@Composable
fun WordScreen(nav: Nav, @Suppress("UNUSED_PARAMETER") app: App) {
    val context = LocalContext.current
    val typo = LocalTypo.current
    val colors = LocalColors.current
    val word by produceState<WordOfDay?>(null) { value = withContext(Dispatchers.IO) { Littre.today(context) } }
    BackHandler { nav.pop() }
    Page {
        Column(Modifier.fillMaxSize()) {
            ScreenTitle(stringResource(R.string.widget_word), onBack = { nav.pop() })
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = rowPadH, vertical = 20.dp)) {
                val w = word
                if (w == null) {
                    Small(stringResource(R.string.reader_loading))
                } else {
                    T(w.word, size = typo.big * 0.7f, lineHeightMul = 1.1f)
                    w.nature?.let { Small(it, Modifier.padding(top = 4.dp), maxLines = 2) }
                    Rule(Modifier.padding(vertical = 16.dp))
                    T(w.text, size = typo.title, lineHeightMul = 1.4f)
                }
            }
            Rule()
            TextRow(stringResource(R.string.open_in_littre), size = typo.title, onClick = { Littre.open(context) })
        }
    }
    @Suppress("UNUSED_VARIABLE") val unused = colors
}
