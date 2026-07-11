package com.verumomnis.forensic.ui

import androidx.compose.foundation.Canvas
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.verumomnis.forensic.ui.theme.VoAccentBlue
import com.verumomnis.forensic.ui.theme.VoGold
import kotlin.math.hypot
import kotlin.random.Random

/**
 * Subtle constellation / network backdrop matching verumglobal.foundation — faint
 * connected nodes on the deep-navy field. Deterministic (fixed seed) so it renders
 * identically every time.
 */
@Composable
fun ConstellationBackground(modifier: Modifier = Modifier, nodeCount: Int = 70) {
    Canvas(modifier = modifier) {
        val rnd = Random(4207)
        val points = List(nodeCount) {
            Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height)
        }
        val linkDistance = size.width * 0.16f
        // Faint connecting lines between nearby nodes.
        for (i in points.indices) {
            for (j in i + 1 until points.size) {
                val d = hypot(points[i].x - points[j].x, points[i].y - points[j].y)
                if (d < linkDistance) {
                    val alpha = 0.05f * (1f - d / linkDistance)
                    drawLine(
                        color = VoAccentBlue.copy(alpha = alpha),
                        start = points[i], end = points[j], strokeWidth = 1f
                    )
                }
            }
        }
        // Nodes — mostly cool white, a few gold accents.
        points.forEachIndexed { index, p ->
            val gold = index % 9 == 0
            drawCircle(
                color = if (gold) VoGold.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.10f),
                radius = if (gold) 2.4f else 1.6f,
                center = p
            )
        }
    }
}
