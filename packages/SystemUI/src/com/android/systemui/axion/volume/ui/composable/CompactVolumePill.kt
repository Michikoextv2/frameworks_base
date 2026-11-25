/*
 * Copyright (C) 2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.axion.volume.ui.composable

import android.media.AudioManager
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.systemui.axion.volume.ui.viewmodel.AxionVolumeDialogViewModel
import com.android.systemui.res.R
import java.text.NumberFormat

@Composable
internal fun CompactVolumePill(
    viewModel: AxionVolumeDialogViewModel,
    style: VolumeStyle,
    panel: @Composable () -> Unit,
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val state = ui.dialogState
    val stream = state.volumeStreams.find { it.streamType == state.activeStream }
        ?: state.volumeStreams.find { it.streamType == AudioManager.STREAM_MUSIC }
    val app = state.appVolumes.find { it.packageName == state.activeAppPackageName }
    val level = when {
        style == VolumeStyle.MOTO && app != null -> if (app.isMuted) 0f else app.volume
        stream == null || stream.isMuted -> 0f
        else -> stream.level
    }.coerceIn(0f, 1f)
    val fill by animateFloatAsState(level, tween(80), label = "Volume level")
    var revealed by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        if (revealed) 1f else 0f, tween(320, easing = FastOutSlowInEasing), label = "Panel reveal"
    )
    val visibility by animateFloatAsState(
        if (ui.isVisible) 1f else 0f, tween(150), label = "Pill visibility"
    )
    val showTapTarget by remember { derivedStateOf { progress < 1f } }
    val startSide = ui.isLeftSide

    Box {
        val mask = Modifier.drawWithCache {
            val p = progress
            if (p == 1f) return@drawWithCache onDrawWithContent { drawContent() }
            val width = lerp(7.dp.toPx().coerceAtMost(size.width), size.width, p)
            val leftEdge = startSide == (layoutDirection == LayoutDirection.Ltr)
            val left = if (leftEdge) 0f else size.width - width
            // Release the mask near the end so the style's own corners take over smoothly.
            val radius = width / 2 * ((1f - p) / 0.25f).coerceAtMost(1f)
            val outline = Path().apply {
                addRoundRect(
                    RoundRect(left, 0f, left + width, size.height, cornerRadius = CornerRadius(radius))
                )
            }
            onDrawWithContent {
                clipPath(outline) {
                    val opacity = (1f - p) * visibility
                    drawRect(Color.Gray.copy(alpha = 0.55f * opacity))
                    val filledHeight = size.height * fill
                    drawRect(
                        Color.White.copy(alpha = 0.94f * opacity),
                        Offset(left, size.height - filledHeight), Size(width, filledHeight)
                    )
                    drawPath(outline, Color.Gray.copy(alpha = 0.6f * opacity), style = Stroke(1.dp.toPx()))
                    if (p > 0f) this@onDrawWithContent.drawContent()
                }
            }
        }

        if (!revealed) {
            val view = LocalView.current
            val haptics by viewModel.isHapticEnabled.collectAsStateWithLifecycle()
            LaunchedEffect(viewModel, haptics) {
                viewModel.volumeKeyHapticTrigger.collect {
                    if (haptics) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
            }
            Box(Modifier.size(48.dp, 124.dp).then(mask))
        } else {
            Box(mask.layout { measurable, constraints ->
                val child = measurable.measure(constraints)
                val p = progress
                val width = lerp(constraints.constrainWidth(48.dp.roundToPx()), child.width, p)
                val height = lerp(constraints.constrainHeight(124.dp.roundToPx()), child.height, p)
                layout(width, height) {
                    if (p == 1f) {
                        child.placeRelative(0, 0)
                    } else if (p > 0f) {
                        val x = if (startSide) 0 else width - child.width
                        child.placeRelativeWithLayer(x, (height - child.height) / 2) { alpha = p }
                    }
                }
            }) { panel() }
        }

        if (showTapTarget) {
            val label = stringResource(R.string.volume_panel_expand_sliders)
            val percent = NumberFormat.getPercentInstance().format(level.toDouble())
            Box(
                Modifier.matchParentSize()
                    .semantics {
                        contentDescription = label
                        stateDescription = percent
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                    ) {
                        viewModel.rescheduleTimeout()
                        revealed = true
                    }
            )
        }
    }
}
