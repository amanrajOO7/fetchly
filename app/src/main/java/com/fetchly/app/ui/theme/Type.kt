package com.fetchly.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * The typeface for the whole app: the device's system font family (Roboto on most phones, or
 * the manufacturer's own font where one is set). Every style in [FetchlyTypography] uses this.
 */
val AppFont: FontFamily = FontFamily.Default

private val base = Typography()

private fun TextStyle.withApp(weight: FontWeight = FontWeight.Normal): TextStyle =
    copy(fontFamily = AppFont, fontWeight = weight)

/** Material 3 type scale with [AppFont] applied. Headings and labels use medium weight for hierarchy. */
val FetchlyTypography = Typography(
    displayLarge = base.displayLarge.withApp(),
    displayMedium = base.displayMedium.withApp(),
    displaySmall = base.displaySmall.withApp(),
    headlineLarge = base.headlineLarge.withApp(FontWeight.Medium),
    headlineMedium = base.headlineMedium.withApp(FontWeight.Medium),
    headlineSmall = base.headlineSmall.withApp(FontWeight.Medium),
    titleLarge = base.titleLarge.withApp(FontWeight.Medium),
    titleMedium = base.titleMedium.withApp(FontWeight.Medium),
    titleSmall = base.titleSmall.withApp(FontWeight.Medium),
    bodyLarge = base.bodyLarge.withApp(),
    bodyMedium = base.bodyMedium.withApp(),
    bodySmall = base.bodySmall.withApp(),
    labelLarge = base.labelLarge.withApp(FontWeight.Medium),
    labelMedium = base.labelMedium.withApp(FontWeight.Medium),
    labelSmall = base.labelSmall.withApp(FontWeight.Medium),
)
