package com.wificall.app.utils

import android.content.Context
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar

/**
 * Extensions.kt
 * Kotlin extension functions that reduce boilerplate throughout the codebase.
 */

// ── View extensions ───────────────────────────────────────────────────────────

/** Makes a View visible (VISIBLE). */
fun View.show() { visibility = View.VISIBLE }

/** Hides a View but keeps its space in the layout (INVISIBLE). */
fun View.hide() { visibility = View.INVISIBLE }

/** Removes a View from the layout completely (GONE). */
fun View.gone() { visibility = View.GONE }

/** Shows or hides a View based on a boolean condition. */
fun View.visibleIf(condition: Boolean) {
    visibility = if (condition) View.VISIBLE else View.GONE
}

// ── Snackbar / Toast helpers ──────────────────────────────────────────────────

/** Shows a Snackbar anchored to [this] View. */
fun View.snack(message: String, duration: Int = Snackbar.LENGTH_SHORT) {
    Snackbar.make(this, message, duration).show()
}

/** Short Toast from any Context. */
fun Context.toast(message: String) =
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

/** Short Toast from a Fragment (delegates to requireContext()). */
fun Fragment.toast(message: String) =
    requireContext().toast(message)

// ── String extensions ─────────────────────────────────────────────────────────

/** Returns true if the string is a valid email address (basic pattern check). */
fun String.isValidEmail(): Boolean =
    android.util.Patterns.EMAIL_ADDRESS.matcher(this).matches()

/** Returns true if the string is a valid 4-digit ID (exactly 4 numeric digits). */
fun String.isValidFourDigitId(): Boolean =
    this.length == 4 && this.all { it.isDigit() }

// ── Duration formatting ───────────────────────────────────────────────────────

/**
 * Converts a duration in seconds to a "mm:ss" formatted string.
 * e.g. 75 → "01:15"
 */
fun Long.toCallDurationString(): String {
    val minutes = this / 60
    val seconds = this % 60
    return String.format("%02d:%02d", minutes, seconds)
}

/**
 * Converts a duration in seconds to a human-readable string.
 * e.g. 75 → "1 min 15 sec"
 */
fun Long.toReadableDuration(): String {
    if (this < 60) return "${this}s"
    val minutes = this / 60
    val seconds = this % 60
    return if (seconds == 0L) "${minutes}m" else "${minutes}m ${seconds}s"
}
