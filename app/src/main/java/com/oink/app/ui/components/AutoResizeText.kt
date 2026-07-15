package com.oink.app.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * A single-line [Text] that shrinks its font to fit the available width instead of
 * ellipsizing, then renders the full string. The desired size is [maxFontSize];
 * the text steps down toward [minFontSize] only as far as it must to fit, so short
 * values stay large and bold while long ones shrink enough to read in full.
 *
 * Why not native auto-size: `TextAutoSize` / `BasicText(autoSize = …)` arrives in
 * Compose 1.8, and this app pins Compose 1.7.5 (see gradle/libs.versions.toml).
 * This fits via [rememberTextMeasurer] - a readonly measurement pass, no draw-time
 * feedback loop - so the size is known before layout and never flickers.
 *
 * Animation stability: the fit is computed from [sizingText], not [text], and is
 * remembered against it. Callers animating a rolling number (the hero balance
 * count-up) pass the stable target string as [sizingText] and the per-frame value
 * as [text], so the font is measured once for the target and held constant across
 * every frame - the number rolls up without the text resizing underneath it. When
 * [sizingText] is omitted it defaults to [text] and the fit tracks the text.
 *
 * @param text The string to render (may change every frame).
 * @param sizingText The string the fit is measured against; defaults to [text].
 *   Pass the widest value the row will show to keep the size stable while [text]
 *   animates.
 * @param style Base text style; its `fontSize` is replaced by the fitted size.
 * @param color Text color.
 * @param maxFontSize The desired (largest) size.
 * @param minFontSize The floor; the text is drawn at this size even if it still
 *   overflows, so the value is always shown in full rather than truncated.
 * @param stepSize How much to shrink per measurement step.
 */
@Composable
fun AutoResizeText(
    text: String,
    style: TextStyle,
    color: Color,
    maxFontSize: TextUnit,
    minFontSize: TextUnit,
    modifier: Modifier = Modifier,
    sizingText: String = text,
    stepSize: TextUnit = 1.sp
) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier = modifier) {
        val availableWidthPx = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE

        val fittedFontSize = remember(
            sizingText, availableWidthPx, style, maxFontSize, minFontSize, stepSize
        ) {
            fitFontSizeSp(
                maxSp = maxFontSize.value,
                minSp = minFontSize.value,
                stepSp = stepSize.value,
                maxWidthPx = availableWidthPx
            ) { candidateSp ->
                measurer.measure(
                    text = sizingText,
                    style = style.copy(fontSize = candidateSp.sp),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip
                ).size.width
            }.sp
        }

        Text(
            text = text,
            style = style,
            color = color,
            fontSize = fittedFontSize,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip
        )
    }
}

/**
 * The largest font size in sp that fits [maxWidthPx], stepping down from [maxSp]
 * toward [minSp] in [stepSp] increments. [measureWidthPx] returns the one-line
 * pixel width of the text at a candidate size.
 *
 * The floor is a hard stop: at or below [minSp] the size is returned regardless of
 * whether it fits, because the contract is to always render the full string - one
 * line, never ellipsized - accepting the floor size in the extreme case rather
 * than truncating. Pure and framework-free so the fit logic is unit-testable with
 * a fake measurer.
 */
internal fun fitFontSizeSp(
    maxSp: Float,
    minSp: Float,
    stepSp: Float,
    maxWidthPx: Int,
    measureWidthPx: (fontSizeSp: Float) -> Int
): Float {
    if (maxWidthPx == Int.MAX_VALUE || maxWidthPx <= 0) return maxSp

    var size = maxSp
    while (size > minSp) {
        if (measureWidthPx(size) <= maxWidthPx) return size
        size -= stepSp
    }
    return minSp
}
