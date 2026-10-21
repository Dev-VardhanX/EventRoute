package com.eventroute.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Brand marks that Material Icons doesn't ship. Render with Icon(tint = Color.Unspecified) for Google. */
object BrandIcons {

    val Google: ImageVector by lazy {
        ImageVector.Builder("Google", 24.dp, 24.dp, 48f, 48f).apply {
            addPath(
                PathParser().parsePathString(
                    "M43.611,20.083H42V20H24v8h11.303c-1.649,4.657-6.08,8-11.303,8c-6.627,0-12-5.373-12-12c0-6.627,5.373-12,12-12c3.059,0,5.842,1.154,7.961,3.039l5.657-5.657C34.046,6.053,29.268,4,24,4C12.955,4,4,12.955,4,24c0,11.045,8.955,20,20,20c11.045,0,20-8.955,20-20C44,22.659,43.862,21.35,43.611,20.083z"
                ).toNodes(), fill = SolidColor(Color(0xFFFFC107))
            )
            addPath(
                PathParser().parsePathString(
                    "M6.306,14.691l6.571,4.819C14.655,15.108,18.961,12,24,12c3.059,0,5.842,1.154,7.961,3.039l5.657-5.657C34.046,6.053,29.268,4,24,4C16.318,4,9.656,8.337,6.306,14.691z"
                ).toNodes(), fill = SolidColor(Color(0xFFFF3D00))
            )
            addPath(
                PathParser().parsePathString(
                    "M24,44c5.166,0,9.86-1.977,13.409-5.192l-6.19-5.238C29.211,35.091,26.715,36,24,36c-5.202,0-9.619-3.317-11.283-7.946l-6.522,5.025C9.505,39.556,16.227,44,24,44z"
                ).toNodes(), fill = SolidColor(Color(0xFF4CAF50))
            )
            addPath(
                PathParser().parsePathString(
                    "M43.611,20.083H42V20H24v8h11.303c-0.792,2.237-2.231,4.166-4.087,5.571c0.001-0.001,0.002-0.001,0.003-0.002l6.19,5.238C36.971,39.205,44,34,44,24C44,22.659,43.862,21.35,43.611,20.083z"
                ).toNodes(), fill = SolidColor(Color(0xFF1976D2))
            )
        }.build()
    }

    val Apple: ImageVector by lazy {
        ImageVector.Builder("Apple", 24.dp, 24.dp, 24f, 24f).apply {
            addPath(
                PathParser().parsePathString(
                    "M18.71,19.5c-0.83,1.24-1.71,2.45-3.05,2.47c-1.34,0.03-1.77-0.79-3.29-0.79c-1.53,0-2,0.77-3.27,0.82c-1.31,0.05-2.3-1.32-3.14-2.53C4.25,17,2.94,12.45,4.7,9.39c0.87-1.52,2.43-2.48,4.12-2.51c1.28-0.02,2.5,0.87,3.29,0.87c0.78,0,2.26-1.07,3.81-0.91c0.65,0.03,2.47,0.26,3.64,1.98c-0.09,0.06-2.17,1.28-2.15,3.81c0.03,3.02,2.65,4.03,2.68,4.04C18.69,16.76,18.28,18.17,18.71,19.5z M13,3.5c0.73-0.83,1.94-1.46,2.94-1.5c0.13,1.17-0.34,2.35-1.04,3.19c-0.69,0.85-1.83,1.51-2.95,1.42C11.81,5.46,12.35,4.26,13,3.5z"
                ).toNodes(), fill = SolidColor(Color(0xFF1E293B))
            )
        }.build()
    }
}
