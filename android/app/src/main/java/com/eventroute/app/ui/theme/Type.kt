package com.eventroute.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.eventroute.app.R

/**
 * Add these files to res/font (lowercase, underscores):
 *   quicksand_semibold.ttf, quicksand_bold.ttf
 *   nunito_regular.ttf, nunito_semibold.ttf, nunito_bold.ttf
 * Both families are free on Google Fonts.
 */
val Quicksand = FontFamily(
    Font(R.font.quicksand_semibold, FontWeight.SemiBold),
    Font(R.font.quicksand_bold, FontWeight.Bold),
    Font(R.font.quicksand_bold, FontWeight.ExtraBold),
)

val Nunito = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_regular, FontWeight.Medium),
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_bold, FontWeight.Bold),
    Font(R.font.nunito_bold, FontWeight.ExtraBold),
)

/** Ready-made text styles so screens stay consistent. */
object ClayType {
    val Display = TextStyle(fontFamily = Quicksand, fontWeight = FontWeight.Bold, fontSize = 40.sp, color = ClayTextPrimary)
    val Title = TextStyle(fontFamily = Quicksand, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = ClayTextPrimary)
    val Heading = TextStyle(fontFamily = Quicksand, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = ClayTextPrimary)
    val Section = TextStyle(fontFamily = Quicksand, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = ClayTextPrimary)
    val CardTitle = TextStyle(fontFamily = Quicksand, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = ClayTextPrimary)
    val Body = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 20.sp, color = ClayTextSecondary)
    val BodyStrong = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = ClayTextPrimary)
    val Caption = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Normal, fontSize = 11.sp, color = ClayTextSecondary)
    val Label = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.8.sp, color = ClayTextMuted)
    val Button = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = ClayBlueText)
    val Mono = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 1.sp, color = ClayTextPrimary)
}

val Typography = Typography(
    headlineLarge = ClayType.Title.copy(fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = ClayType.Title,
    titleLarge = ClayType.Heading,
    titleMedium = ClayType.Section.copy(fontSize = 16.sp),
    bodyLarge = ClayType.Body.copy(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = ClayType.Body,
    labelLarge = ClayType.Button,
    labelSmall = ClayType.Label,
)
