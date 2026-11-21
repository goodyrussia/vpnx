package com.vpnx.dope.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentColors
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.DropdownArrowEndAction
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Window-anchored dropdown preference styled with Miuix [BasicComponent] and hosted in an
 * independent Android [Popup] window rather than Miuix's Scaffold-hosted `ListPopupLayout`.
 *
 * Bypasses both Scaffold overlay teardown and Miuix 0.9.4's `NavigationBackHandler` call
 * (which crashes when `androidx.activity:activity-compose` is below 1.12.0 and does not
 * provide `LocalNavigationEventDispatcherOwner`).
 */
@Composable
fun VpnxDropdownPreference(
    items: List<String>,
    selectedIndex: Int,
    title: String,
    modifier: Modifier = Modifier,
    titleColor: BasicComponentColors = BasicComponentDefaults.titleColor(),
    summary: String? = null,
    summaryColor: BasicComponentColors = BasicComponentDefaults.summaryColor(),
    startAction: @Composable (() -> Unit)? = null,
    bottomAction: (@Composable () -> Unit)? = null,
    insideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
    maxHeight: Dp? = null,
    enabled: Boolean = true,
    showValue: Boolean = true,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    onSelectedIndexChange: ((Int) -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    var isDropdownExpanded by remember { mutableStateOf(false) }

    val currentOnExpandedChange by rememberUpdatedState(onExpandedChange)
    val currentOnSelectedIndexChange by rememberUpdatedState(onSelectedIndexChange)

    val setExpanded: (Boolean) -> Unit = remember {
        { expanded ->
            if (isDropdownExpanded != expanded) {
                isDropdownExpanded = expanded
                currentOnExpandedChange?.invoke(expanded)
            }
        }
    }

    val itemsNotEmpty = items.isNotEmpty()
    val actualEnabled = enabled && itemsNotEmpty
    val safeSelectedIndex = if (itemsNotEmpty) selectedIndex.coerceIn(items.indices) else -1
    val selectedText = if (safeSelectedIndex >= 0) items[safeSelectedIndex] else null

    val actionColor = if (actualEnabled) {
        MiuixTheme.colorScheme.onSurfaceVariantActions
    } else {
        MiuixTheme.colorScheme.disabledOnSecondaryVariant
    }

    val density = LocalDensity.current
    val marginPx = remember(density) { with(density) { 12.dp.roundToPx() } }
    val verticalGapPx = remember(density) { with(density) { 4.dp.roundToPx() } }

    val popupPositionProvider = remember(marginPx, verticalGapPx) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                val preferredX = if (layoutDirection == LayoutDirection.Ltr) {
                    anchorBounds.right - popupContentSize.width
                } else {
                    anchorBounds.left
                }
                val minX = marginPx.coerceAtMost((windowSize.width - popupContentSize.width).coerceAtLeast(0))
                val maxX = (windowSize.width - popupContentSize.width - marginPx).coerceAtLeast(minX)
                val x = preferredX.coerceIn(minX, maxX)

                val spaceBelow = windowSize.height - anchorBounds.bottom - verticalGapPx - marginPx
                val spaceAbove = anchorBounds.top - verticalGapPx - marginPx
                val preferredY = if (spaceBelow >= popupContentSize.height || spaceBelow >= spaceAbove) {
                    anchorBounds.bottom + verticalGapPx
                } else {
                    anchorBounds.top - popupContentSize.height - verticalGapPx
                }
                val minY = marginPx.coerceAtMost((windowSize.height - popupContentSize.height).coerceAtLeast(0))
                val maxY = (windowSize.height - popupContentSize.height - marginPx).coerceAtLeast(minY)
                val y = preferredY.coerceIn(minY, maxY)

                return IntOffset(x, y)
            }
        }
    }

    val menuShape = RoundedCornerShape(16.dp)

    Box(modifier = modifier) {
        BasicComponent(
            modifier = Modifier.fillMaxWidth(),
            interactionSource = interactionSource,
            insideMargin = insideMargin,
            title = title,
            titleColor = titleColor,
            summary = summary,
            summaryColor = summaryColor,
            startAction = startAction,
            endActions = {
                if (showValue && !selectedText.isNullOrEmpty()) {
                    Text(
                        text = selectedText,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .align(Alignment.CenterVertically)
                            .weight(1f, fill = false),
                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                        color = actionColor,
                        textAlign = TextAlign.End
                    )
                }
                DropdownArrowEndAction(
                    actionColor = actionColor
                )
            },
            bottomAction = bottomAction,
            onClick = {
                if (actualEnabled) {
                    setExpanded(!isDropdownExpanded)
                }
            },
            role = Role.DropdownList,
            holdDownState = isDropdownExpanded && actualEnabled,
            enabled = actualEnabled
        )

        if (isDropdownExpanded && actualEnabled) {
            Popup(
                popupPositionProvider = popupPositionProvider,
                onDismissRequest = { setExpanded(false) },
                properties = PopupProperties(
                    focusable = true,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true,
                    clippingEnabled = true
                )
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(min = 180.dp, max = 280.dp)
                        .heightIn(max = maxHeight ?: 320.dp)
                        .shadow(elevation = 12.dp, shape = menuShape)
                        .clip(menuShape)
                        .background(MiuixTheme.colorScheme.surface)
                        .border(
                            width = 0.5.dp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.2f),
                            shape = menuShape
                        )
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 6.dp)
                ) {
                    items.forEachIndexed { index, label ->
                        val isSelected = index == safeSelectedIndex
                        val rowBackground = if (isSelected) {
                            MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
                        } else {
                            Color.Transparent
                        }
                        val rowTextColor = if (isSelected) {
                            MiuixTheme.colorScheme.primary
                        } else {
                            MiuixTheme.colorScheme.onSurface
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(rowBackground)
                                .clickable {
                                    setExpanded(false)
                                    currentOnSelectedIndexChange?.invoke(index)
                                }
                                .padding(horizontal = 16.dp, vertical = 13.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = label,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 12.dp),
                                fontSize = MiuixTheme.textStyles.body2.fontSize,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = rowTextColor
                            )
                            if (isSelected) {
                                Text(
                                    text = "✓",
                                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                                    fontWeight = FontWeight.Bold,
                                    color = MiuixTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}