package dev.dcesares.bifolio.designsystem

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private val RingWidth = 2.dp
private val RingRadius = 12.dp
private const val PRESSED_ALPHA = 0.10f

/**
 * Indication for every clickable (A11Y-06): keyboard focus draws a 2dp ring in [ring], inside the bounds so a
 * parent clip never cuts it, and a press dims the control with [press]. Touch never focuses a clickable, so the
 * ring shows for keyboard and d-pad only. The accent holds 3:1 against the chrome surfaces (ContrastTest).
 */
internal data class FocusRingIndication(
    val ring: Color,
    val press: Color,
) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        FocusRingNode(interactionSource, ring, press)
}

private class FocusRingNode(
    private val source: InteractionSource,
    private val ring: Color,
    private val press: Color,
) : Modifier.Node(),
    DrawModifierNode {
    private var focused = false
    private var pressed = false

    override fun onAttach() {
        coroutineScope.launch {
            val focuses = mutableListOf<FocusInteraction.Focus>()
            val presses = mutableListOf<PressInteraction.Press>()
            source.interactions.collect { interaction ->
                when (interaction) {
                    is FocusInteraction.Focus -> focuses += interaction
                    is FocusInteraction.Unfocus -> focuses -= interaction.focus
                    is PressInteraction.Press -> presses += interaction
                    is PressInteraction.Release -> presses -= interaction.press
                    is PressInteraction.Cancel -> presses -= interaction.press
                }
                val nowFocused = focuses.isNotEmpty()
                val nowPressed = presses.isNotEmpty()
                if (nowFocused != focused || nowPressed != pressed) {
                    focused = nowFocused
                    pressed = nowPressed
                    invalidateDraw()
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (pressed) drawRect(press.copy(alpha = PRESSED_ALPHA))
        if (focused) {
            val width = RingWidth.toPx()
            val inset = width / 2
            drawRoundRect(
                color = ring,
                topLeft = Offset(inset, inset),
                size = Size(size.width - width, size.height - width),
                cornerRadius = CornerRadius(RingRadius.toPx()),
                style = Stroke(width),
            )
        }
    }
}
