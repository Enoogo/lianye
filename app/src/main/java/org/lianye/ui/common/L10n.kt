package org.lianye.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

/**
 * The product ships in English only. The two-argument call sites and the zh resources are
 * kept as historical scaffolding, but every visible string resolves to English.
 */
@Composable
fun uiText(zh: String, en: String): String = en

@Suppress("UNUSED_PARAMETER")
fun localized(zh: String, en: String, locale: Locale = Locale.getDefault()): String = en
