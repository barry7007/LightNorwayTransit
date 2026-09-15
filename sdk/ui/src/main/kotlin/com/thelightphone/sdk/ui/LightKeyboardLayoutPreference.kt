package com.thelightphone.sdk.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow

/**
 * Which built-in embedded keyboard [LightTextInputEditor] shows.
 *
 * [NORWEGIAN] maps to the fork's NoQwerty layout (adds æ ø å), [ENGLISH] to the
 * standard EnQwerty layout. Norwegian is the default; English is the backup.
 */
enum class LightKeyboardLayout {
    NORWEGIAN,
    ENGLISH,
}

/**
 * The keyboard-layout choice, persisted with [android.content.SharedPreferences].
 *
 * One choice per tool, not one per phone: `getSharedPreferences` is app-private storage, so
 * every tool keeps its own copy of this file and flipping the layout in Messages leaves Diary
 * and Ask on whatever they were set to. Each tool exposes it the same way, which is what makes
 * it feel global.
 *
 * Lives in `sdk:ui` because that is the only module that both writes it (via the
 * tools / `sdk:client`, which depend on `sdk:ui`) and reads it ([LightTextInputEditor]
 * picks the layout when a text field opens). Reading at editor-open time is what makes
 * a changed choice take effect the next time a field opens without a rebuild.
 */
object LightKeyboardLayoutPreference {
    private const val PREFS_NAME = "light_sdk_keyboard"
    private const val KEY_LAYOUT = "keyboard_layout"

    /** Norwegian by default, English as backup. */
    val DEFAULT = LightKeyboardLayout.NORWEGIAN

    /**
     * Bumped by every [set] / [toggle]. Composables read it through [currentKeyboardLayout],
     * which is what makes a change apply to an editor that is **already open** — without it,
     * SharedPreferences is invisible to Compose and a keyboard could only change on the next
     * editor open. Snapshot state, so the write (always from a click handler, never from
     * composition) safely recomposes every reader in the process.
     */
    internal var revision by mutableIntStateOf(0)
        private set

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(context: Context): LightKeyboardLayout {
        val name = prefs(context).getString(KEY_LAYOUT, DEFAULT.name)
        return runCatching { LightKeyboardLayout.valueOf(name!!) }.getOrDefault(DEFAULT)
    }

    fun set(context: Context, layout: LightKeyboardLayout) {
        prefs(context).edit().putString(KEY_LAYOUT, layout.name).apply()
        revision++
    }

    /** Flips between the two layouts and persists the result. */
    fun toggle(context: Context): LightKeyboardLayout {
        val next = when (get(context)) {
            LightKeyboardLayout.NORWEGIAN -> LightKeyboardLayout.ENGLISH
            LightKeyboardLayout.ENGLISH -> LightKeyboardLayout.NORWEGIAN
        }
        set(context, next)
        return next
    }
}

/** The current layout plus a persisting toggle, for a Settings row. */
class KeyboardLayoutToggle internal constructor(
    val layout: LightKeyboardLayout,
    val toggle: () -> Unit,
) {
    /** Two-letter label for a compact control — a bottom-bar button has room for the current
     *  language but not for "Norwegian". The Settings row uses the full names instead. */
    val label: String
        get() = when (layout) {
            LightKeyboardLayout.NORWEGIAN -> "NO"
            LightKeyboardLayout.ENGLISH -> "EN"
        }
}

/**
 * The keyboard layout in force right now, as observable Compose state: any composition that
 * reads this re-runs when the choice changes **anywhere in the process**, including while it
 * is on screen. [LightTextInputEditor] uses it so a mid-typing switch swaps the keyboard
 * under the caret instead of waiting for the field to be reopened.
 */
@Composable
fun currentKeyboardLayout(): LightKeyboardLayout {
    val context = LocalContext.current
    // Keyed on the revision: that subscribes this composition to changes, and caches the
    // SharedPreferences read in between them.
    return remember(context, LightKeyboardLayoutPreference.revision) {
        LightKeyboardLayoutPreference.get(context)
    }
}

/**
 * Composable accessor for [LightKeyboardLayoutPreference] so tools can show and flip
 * the keyboard choice without touching `LocalContext` themselves (the SDK owns that).
 * The returned [KeyboardLayoutToggle] recomposes the caller when the layout changes.
 *
 * Reads through [currentKeyboardLayout] rather than holding its own copy, so a Settings row
 * and an editor's in-place switch stay in agreement no matter which one did the flipping.
 */
@Composable
fun rememberKeyboardLayoutToggle(): KeyboardLayoutToggle {
    val context = LocalContext.current
    val layout = currentKeyboardLayout()
    return KeyboardLayoutToggle(layout) {
        LightKeyboardLayoutPreference.toggle(context)
    }
}

/**
 * Drop-in Settings row for the global keyboard-layout choice, styled to match the
 * other SDK Settings rows (a [LightTextVariant.Detail] label over a
 * [LightTextVariant.Heading] value). Self-contained — no arguments required — so any
 * tool can add it to its Settings column with a single line.
 *
 * Tapping toggles Norwegian ⇄ English and persists; the next text field that opens
 * picks up the new layout (NoQwerty / EnQwerty) without a rebuild.
 */
@Composable
fun LightKeyboardLayoutSetting(modifier: Modifier = Modifier) {
    val setting = rememberKeyboardLayoutToggle()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .lightClickable(onClick = setting.toggle)
            .padding(vertical = 0.75f.gridUnitsAsDp()),
    ) {
        LightText(
            text = "Keyboard",
            variant = LightTextVariant.Detail,
            lighten = true,
        )
        LightText(
            text = when (setting.layout) {
                LightKeyboardLayout.NORWEGIAN -> "Norwegian"
                LightKeyboardLayout.ENGLISH -> "English"
            },
            variant = LightTextVariant.Heading,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
