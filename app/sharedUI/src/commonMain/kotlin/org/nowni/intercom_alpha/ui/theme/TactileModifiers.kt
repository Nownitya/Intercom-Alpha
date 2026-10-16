package org.nowni.intercom_alpha.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Tactical Tactile Modifiers implementing ADR-014.
 * Fuses Claymorphism, Neobrutalism, Skeuomorphism, and Liquid Glass for high-speed outdoor cockpits.
 */

/**
 * 1. Neobrutalist High-Contrast Border Modifier.
 * Provides a crisp 2-3dp perimeter outline guaranteeing readability in blinding alpine sun glare.
 */
fun Modifier.neobrutalistBorder(
    color: Color = IntercomTokens.NeobrutalBorderIdle,
    thickness: Dp = IntercomTokens.NeobrutalBorderThickness,
    shape: Shape = RoundedCornerShape(IntercomTokens.CardCornerRadius)
): Modifier = this.border(
    border = BorderStroke(thickness, color),
    shape = shape
)

/**
 * 2. Claymorphic Tactile Depth Modifier.
 * Simulates a soft 3D pillow volume using dual inner and outer specular shadows.
 * Calibrated for tactile affordance when wearing heavy leather motorcycle gloves.
 */
fun Modifier.claymorphic(
    surfaceColor: Color = IntercomTokens.CockpitSurfaceElevated,
    isPressed: Boolean = false,
    shape: Shape = RoundedCornerShape(IntercomTokens.CardCornerRadius),
    cornerDip: Dp = IntercomTokens.CardCornerRadius
): Modifier = this
    .clip(shape)
    .background(
        brush = Brush.verticalGradient(
            colors = if (isPressed) {
                listOf(
                    surfaceColor.copy(alpha = 0.85f),
                    surfaceColor.copy(alpha = 0.98f)
                )
            } else {
                listOf(
                    surfaceColor.copy(alpha = 1.0f),
                    surfaceColor.copy(alpha = 0.82f)
                )
            }
        )
    )
    .drawWithContent {
        drawContent()

        val strokeWidth = 2.dp.toPx()

        if (!isPressed) {
            // Top-left specular highlight (gives convex clay cushion feeling)
            drawLine(
                brush = Brush.horizontalGradient(
                    0.0f to Color.White.copy(alpha = 0.22f),
                    0.8f to Color.White.copy(alpha = 0.04f),
                    1.0f to Color.Transparent
                ),
                start = Offset(strokeWidth, strokeWidth),
                end = Offset(size.width - strokeWidth, strokeWidth),
                strokeWidth = strokeWidth
            )
            // Left specular sheen
            drawLine(
                brush = Brush.verticalGradient(
                    0.0f to Color.White.copy(alpha = 0.22f),
                    0.8f to Color.White.copy(alpha = 0.04f),
                    1.0f to Color.Transparent
                ),
                start = Offset(strokeWidth, strokeWidth),
                end = Offset(strokeWidth, size.height - strokeWidth),
                strokeWidth = strokeWidth
            )
            // Bottom-right recessed ambient drop shadow
            drawLine(
                brush = Brush.horizontalGradient(
                    0.0f to Color.Transparent,
                    0.2f to Color.Black.copy(alpha = 0.35f),
                    1.0f to Color.Black.copy(alpha = 0.70f)
                ),
                start = Offset(strokeWidth, size.height - strokeWidth),
                end = Offset(size.width - strokeWidth, size.height - strokeWidth),
                strokeWidth = strokeWidth
            )
        } else {
            // Depressed cavity shadow (when pressed down)
            drawLine(
                brush = Brush.horizontalGradient(
                    0.0f to Color.Black.copy(alpha = 0.65f),
                    1.0f to Color.Black.copy(alpha = 0.25f)
                ),
                start = Offset(strokeWidth, strokeWidth),
                end = Offset(size.width - strokeWidth, strokeWidth),
                strokeWidth = strokeWidth * 1.5f
            )
        }
    }

/**
 * 3. Liquid Glass Backdrop Modifier.
 * Frosted translucent glass effect with micro-specular rim highlight.
 * Floats gracefully over real-time tactical trail maps and spatial radar.
 */
fun Modifier.liquidGlass(
    tint: Color = IntercomTokens.LiquidGlassBaseDark,
    specularColor: Color = IntercomTokens.LiquidGlassSpecularEdge,
    shape: Shape = RoundedCornerShape(IntercomTokens.CardCornerRadius)
): Modifier = this
    .clip(shape)
    .background(tint)
    .border(
        border = BorderStroke(1.dp, specularColor),
        shape = shape
    )

/**
 * 4. Concentric Skeuomorphic Bezel Modifier.
 * Simulates heavy-duty mil-spec knurling and machined aluminum bezels for rotary dials and PTT actuators.
 */
fun Modifier.skeuomorphicBezel(
    rimColor: Color = Color(0xFF2D333B),
    knurlColor: Color = Color(0x33FFFFFF),
    thickness: Dp = 4.dp
): Modifier = this.drawWithContent {
    drawContent()
    val pxThickness = thickness.toPx()
    // Outer machined rim
    drawCircle(
        color = rimColor,
        radius = (size.minDimension / 2f) - (pxThickness / 2f),
        style = Stroke(width = pxThickness)
    )
    // Micro-knurling specular tick
    drawCircle(
        color = knurlColor,
        radius = (size.minDimension / 2f) - pxThickness,
        style = Stroke(width = 1.dp.toPx())
    )
}
