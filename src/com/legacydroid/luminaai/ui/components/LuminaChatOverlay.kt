/*
 * SPDX-FileCopyrightText: 2026 The LegacyDroid Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.legacydroid.luminaai.ui.components

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.legacydroid.luminaai.data.HistorySession
import com.legacydroid.luminaai.data.MemoryEntry
import com.legacydroid.luminaai.model.Block
import com.legacydroid.luminaai.model.ChatMessage
import com.legacydroid.luminaai.model.Role
import com.legacydroid.luminaai.model.SuggestionItem
import com.legacydroid.luminaai.model.ToolStatus
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/**
 * The awakened Lumina overlay: orb + label, chat stack (message blocks,
 * tool cards, memory chips), suggestion chips, pill input, and the memories
 * bottom sheet.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LuminaChatOverlay(
    modifier: Modifier = Modifier,
    messages: List<ChatMessage>,
    isThinking: Boolean,
    pendingApproval: Block.ToolCall?,
    memories: List<MemoryEntry>,
    engineLabel: String,
    onSendMessage: (String) -> Unit,
    onCancelGeneration: () -> Unit,
    onRerunLast: () -> Unit,
    onClose: () -> Unit,
    onClearChat: () -> Unit,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    onSuggestionToolAction: (String, Boolean) -> Unit,
    onAddMemory: (String, String) -> Unit,
    onEditMemory: (String, String, String) -> Unit,
    onDeleteMemory: (String) -> Unit,
    onClearMemories: () -> Unit,
    onLoadHistorySessions: () -> List<HistorySession>,
    onResumeHistory: (Long) -> Boolean,
    onDeleteHistorySession: (Long) -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    var showMemories by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scheme = MaterialTheme.colorScheme
    val typedKeys = remember { mutableStateMapOf<String, Boolean>() }

    val introProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        introProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(1000, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f))
        )
    }

    val suggestions = remember {
        listOf(
            SuggestionItem(emoji = "", title = "Toggle Wi-Fi", query = "Turn off my Wi-Fi"),
            SuggestionItem(emoji = "", title = "Battery status", query = "How is my battery doing?"),
            SuggestionItem(emoji = "", title = "Summarize notifications", query = "Summarize my recent notifications"),
            SuggestionItem(emoji = "", title = "System status", query = "Check my system status"),
            SuggestionItem(emoji = "", title = "What do you remember?", query = "What do you remember about me?"),
            SuggestionItem(emoji = "", title = "Privacy audit", query = "Run a privacy audit"),
            SuggestionItem(emoji = "", title = "Creative thought", query = "Give me an inspiring thought"),
            SuggestionItem(emoji = "", title = "Tell a joke", query = "Tell me a joke")
        )
    }

    LaunchedEffect(messages.size, isThinking) {
        if (messages.isNotEmpty()) {
            delay(40)
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    LaunchedEffect(isThinking) {
        while (isThinking) {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            val nearBottom = last == null ||
                (last.index >= info.totalItemsCount - 1 &&
                    last.offset + last.size >= info.viewportEndOffset)
            if (nearBottom && info.totalItemsCount > 0) {
                listState.scrollToItem(info.totalItemsCount - 1)
            }
            delay(250)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        val totalHeight = maxHeight
        val orbTopOffset = totalHeight * 0.32f

        val controlBg = scheme.surfaceVariant.copy(alpha = 0.45f)
        val controlBorder = scheme.outlineVariant.copy(alpha = 0.40f)

        if (engineLabel.isNotBlank()) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 20.dp, start = 20.dp)
                    .graphicsLayer { alpha = introStage(introProgress.value, 0.65f, 1f) }
                    .clip(RoundedCornerShape(12.dp))
                    .background(controlBg)
                    .border(0.5.dp, controlBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(scheme.primary)
                )
                Text(
                    text = engineLabel,
                    color = scheme.onSurfaceVariant,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 20.dp)
                .graphicsLayer { alpha = introStage(introProgress.value, 0.65f, 1f) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircleIconButton(
                onClick = { showHistory = true },
                contentDescription = "History",
                icon = Icons.Default.StickyNote2,
                tint = scheme.onSurfaceVariant,
                background = controlBg,
                border = controlBorder
            )
            CircleIconButton(
                onClick = { showMemories = true },
                contentDescription = "Memories",
                icon = Icons.Default.Memory,
                tint = if (memories.isNotEmpty()) scheme.primary else scheme.onSurfaceVariant,
                background = controlBg,
                border = controlBorder
            ) {
                if (memories.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-4).dp)
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(scheme.primary)
                    )
                }
            }
            if (messages.size > 1) {
                CircleIconButton(
                    onClick = onClearChat,
                    contentDescription = "Clear Chat",
                    icon = Icons.Default.Refresh,
                    tint = scheme.onSurfaceVariant,
                    background = controlBg,
                    border = controlBorder
                )
            }
            CircleIconButton(
                onClick = onClose,
                contentDescription = "Close Lumina AI",
                icon = Icons.Default.Close,
                tint = scheme.onSurface,
                background = controlBg,
                border = controlBorder
            )
        }

        val conversationActive = messages.isNotEmpty()
        val heroCollapse by animateFloatAsState(
            targetValue = if (conversationActive) 0f else 1f,
            animationSpec = tween(500, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)),
            label = "heroCollapse"
        )
        val messagesMaxHeight by animateDpAsState(
            targetValue = if (conversationActive) totalHeight * 0.86f else 190.dp,
            animationSpec = tween(500, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)),
            label = "messagesHeight"
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = orbTopOffset)
                .graphicsLayer {
                    val t = introStage(introProgress.value, 0f, 0.55f) * heroCollapse
                    alpha = t
                    scaleX = 0.5f + t * 0.5f
                    scaleY = 0.5f + t * 0.5f
                    translationY -= (1f - heroCollapse) * 24.dp.toPx()
                    renderEffect = if (t < 1f && Build.VERSION.SDK_INT >= 31 && introProgress.value < 1f) {
                        val radius = (1f - t) * 20f * density
                        android.graphics.RenderEffect.createBlurEffect(
                            radius, radius, android.graphics.Shader.TileMode.CLAMP
                        ).asComposeRenderEffect()
                    } else null
                },
            contentAlignment = Alignment.Center
        ) {
            LuminaOrb(
                size = 110.dp,
                isThinking = isThinking
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = orbTopOffset + 186.dp)
                .graphicsLayer {
                    alpha = introStage(introProgress.value, 0.35f, 0.7f) * heroCollapse
                    translationY -= (1f - heroCollapse) * 24.dp.toPx()
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "LUMINA",
                color = scheme.onSurface.copy(alpha = 0.75f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 3.5.sp
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 14.dp, vertical = 20.dp)
                .graphicsLayer {
                    val p = introStage(introProgress.value, 0.15f, 0.6f)
                    alpha = p
                    translationY = (1f - p) * 100.dp.toPx()
                }
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = messagesMaxHeight)
                    .padding(bottom = 10.dp)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    val latestUserIndex = messages.indexOfLast { msg ->
                        msg.role == Role.USER &&
                            msg.blocks.any { it is Block.Text && it.content.isNotBlank() }
                    }
                    itemsIndexed(messages, key = { _, msg -> msg.id }) { index, msg ->
                        MessageBlocks(
                            message = msg,
                            isLatestUser = index == latestUserIndex,
                            pendingApproval = pendingApproval,
                            typedKeys = typedKeys,
                            onApprove = onApprove,
                            onDeny = onDeny,
                            onSuggestionToolAction = onSuggestionToolAction,
                            onRerunLast = onRerunLast
                        )
                    }
                    if (isThinking) {
                        item(key = "thinking") { TypingIndicatorBubble() }
                    }
                }
            }

            AnimatedVisibility(visible = !conversationActive) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(suggestions, key = { it.id }) { chip ->
                        SuggestionChip(
                            emoji = chip.emoji,
                            title = chip.title,
                            onClick = { onSendMessage(chip.query) }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(scheme.surfaceVariant.copy(alpha = 0.60f))
                    .border(0.5.dp, controlBorder, RoundedCornerShape(24.dp))
                    .padding(start = 16.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            text = "Ask Lumina anything...",
                            color = scheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        cursorColor = scheme.primary,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        focusedPlaceholderColor = scheme.onSurfaceVariant,
                        unfocusedPlaceholderColor = scheme.onSurfaceVariant,
                        disabledPlaceholderColor = scheme.onSurfaceVariant
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (inputText.isNotBlank()) {
                                val query = inputText.trim()
                                inputText = ""
                                onSendMessage(query)
                            }
                        }
                    ),
                    modifier = Modifier.weight(1f)
                )

                val canSend = inputText.isNotBlank() && !isThinking
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isThinking -> Brush.linearGradient(
                                    listOf(scheme.error.copy(alpha = 0.85f),
                                        scheme.error.copy(alpha = 0.65f))
                                )
                                canSend -> Brush.linearGradient(
                                    listOf(scheme.primary, scheme.secondary)
                                )
                                else -> Brush.linearGradient(
                                    listOf(scheme.onSurface.copy(alpha = 0.15f),
                                        scheme.onSurface.copy(alpha = 0.10f))
                                )
                            }
                        )
                        .clickable(enabled = canSend || isThinking) {
                            if (isThinking) {
                                onCancelGeneration()
                            } else if (canSend) {
                                val query = inputText.trim()
                                inputText = ""
                                onSendMessage(query)
                            }
                        }
                ) {
                    Icon(
                        imageVector = if (isThinking) Icons.Default.Close else Icons.Default.ArrowUpward,
                        contentDescription = if (isThinking) "Stop generating" else "Send",
                        tint = if (isThinking) scheme.onError else scheme.onPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }

    if (showMemories) {
        MemoriesSheet(
            memories = memories,
            onDismiss = { showMemories = false },
            onAdd = onAddMemory,
            onEdit = onEditMemory,
            onDelete = onDeleteMemory,
            onClear = onClearMemories
        )
    }

    if (showHistory) {
        HistorySheet(
            onDismiss = { showHistory = false },
            onLoadSessions = onLoadHistorySessions,
            onJump = { sessionId ->
                if (onResumeHistory(sessionId)) showHistory = false
            },
            onDeleteSession = onDeleteHistorySession
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBlocks(
    message: ChatMessage,
    isLatestUser: Boolean,
    pendingApproval: Block.ToolCall?,
    typedKeys: SnapshotStateMap<String, Boolean>,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    onSuggestionToolAction: (String, Boolean) -> Unit,
    onRerunLast: () -> Unit
) {
    if (message.role == Role.USER) {
        val text = message.blocks.filterIsInstance<Block.Text>().joinToString("\n") { it.content }
        if (text.isNotBlank()) {
            TypewriterBubble(
                text = text,
                isUser = true,
                rerunnable = isLatestUser,
                onRerun = onRerunLast
            )
        }
        message.blocks.filterIsInstance<Block.Note>().forEach { NoteChip(it) }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            message.blocks.forEachIndexed { index, block ->
                when (block) {
                    is Block.Text -> {
                        val effectKey = message.id + index
                        val isFresh = typedKeys[effectKey] != true
                        if (isFresh) typedKeys[effectKey] = true
                        TypewriterBubble(
                            text = block.content,
                            isUser = false,
                            key = effectKey,
                            animate = isFresh
                        )
                    }
                    is Block.ToolCall -> ToolCallCard(
                        block = block,
                        isPendingApproval = pendingApproval?.id == block.id,
                        onApprove = onApprove,
                        onDeny = onDeny,
                        onSuggestionAction = { approved -> onSuggestionToolAction(block.id, approved) }
                    )
                    is Block.MemoryEvent -> MemoryEventChip(block)
                    is Block.Note -> NoteChip(block)
                    is Block.Error -> ErrorCard(block)
                }
            }
        }
    }
}

@Composable
private fun CircleIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    icon: ImageVector,
    tint: Color,
    background: Color,
    border: Color,
    extraContent: @Composable BoxScope.() -> Unit = {}
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(background)
            .border(0.5.dp, border, CircleShape)
            .clickable(onClick = onClick)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(15.dp)
        )
        extraContent()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TypewriterBubble(
    text: String,
    isUser: Boolean,
    key: Any = text,
    animate: Boolean = true,
    rerunnable: Boolean = false,
    onRerun: () -> Unit = {}
) {
    val scheme = MaterialTheme.colorScheme
    var displayedText by remember(key) {
        mutableStateOf(if (isUser || !animate) text else "")
    }
    val appearProgress = remember(key) { Animatable(0f) }

    LaunchedEffect(key) {
        appearProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(500, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f))
        )
        if (animate && !isUser && text.isNotEmpty()) {
            displayedText = ""
            val step = if (text.length > 400) 3 else 1
            var i = 0
            while (i < text.length) {
                displayedText = text.substring(0, i)
                i += step
                delay(if (step > 1) 12L else 22L + Random.nextInt(12))
            }
            displayedText = text
        } else {
            displayedText = text
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(appearProgress.value)
            .offset(y = ((1f - appearProgress.value) * 8f).dp),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        if (isUser) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.End)
            ) {
                if (rerunnable) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Hold to rerun",
                        tint = scheme.onSurfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.size(12.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.82f)
                        .clip(
                            RoundedCornerShape(
                                topStart = 18.dp, topEnd = 18.dp,
                                bottomStart = 18.dp, bottomEnd = 4.dp
                            )
                        )
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    scheme.primary.copy(alpha = 0.85f),
                                    scheme.secondary.copy(alpha = 0.85f)
                                )
                            )
                        )
                        .combinedClickable(onClick = {}, onLongClick = onRerun, enabled = rerunnable)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    MarkdownText(
                        text = displayedText,
                        style = TextStyle(
                            fontSize = 13.5.sp,
                            lineHeight = 19.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        isUser = true
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .clip(
                        RoundedCornerShape(
                            topStart = 18.dp, topEnd = 18.dp,
                            bottomStart = 4.dp, bottomEnd = 18.dp
                        )
                    )
                    .background(scheme.surfaceVariant.copy(alpha = 0.55f))
                    .border(
                        0.5.dp,
                        scheme.outlineVariant.copy(alpha = 0.40f),
                        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp,
                            bottomStart = 4.dp, bottomEnd = 18.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                MarkdownText(
                    text = displayedText,
                    style = TextStyle(
                        fontSize = 13.5.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    isUser = false
                )
            }
        }
    }
}

@Composable
private fun ToolCallCard(
    block: Block.ToolCall,
    isPendingApproval: Boolean,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    onSuggestionAction: (Boolean) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val pulse = rememberInfiniteTransition(label = "toolPulse")
    val runningAlpha by pulse.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "toolAlpha"
    )

    val statusColor = when (block.status) {
        ToolStatus.RUNNING -> scheme.primary
        ToolStatus.DONE -> scheme.primary
        ToolStatus.FAILED -> scheme.error
        ToolStatus.PENDING -> scheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surfaceVariant.copy(alpha = 0.55f))
            .border(0.5.dp, scheme.outlineVariant.copy(alpha = 0.40f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(statusColor.copy(alpha = 0.18f))
                ) {
                    when (block.status) {
                        ToolStatus.DONE -> Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Done",
                            tint = statusColor,
                            modifier = Modifier.size(15.dp)
                        )
                        ToolStatus.FAILED -> Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Failed",
                            tint = statusColor,
                            modifier = Modifier.size(15.dp)
                        )
                        else -> Box(
                            modifier = Modifier
                                .size(8.dp)
                                .alpha(runningAlpha)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = block.label,
                        color = scheme.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = prettyParams(block.params),
                        color = scheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
                Text(
                    text = when (block.status) {
                        ToolStatus.PENDING -> if (isPendingApproval) "Needs approval" else "Waiting"
                        ToolStatus.RUNNING -> "Working..."
                        ToolStatus.DONE -> "Done"
                        ToolStatus.FAILED -> "Failed"
                    },
                    color = statusColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            block.result?.let { result ->
                Text(
                    text = prettyResult(result),
                    color = if (block.status == ToolStatus.FAILED) scheme.error else scheme.onSurfaceVariant,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp
                )
            }

            if (block.status == ToolStatus.PENDING && isPendingApproval) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = onApprove,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = scheme.primary)
                    ) {
                        Text("Approve", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = onDeny,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel", fontSize = 12.sp)
                    }
                }
            } else if (block.status == ToolStatus.PENDING && !isPendingApproval) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = { onSuggestionAction(true) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = scheme.primary)
                    ) {
                        Text("Approve", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = { onSuggestionAction(false) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryEventChip(block: Block.MemoryEvent) {
    val scheme = MaterialTheme.colorScheme
    val color = when (block.action) {
        "saved" -> scheme.tertiary
        "forgotten" -> scheme.error
        else -> scheme.primary
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.15f))
            .border(0.5.dp, color.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("", fontSize = 11.sp)
        Text(
            text = when (block.action) {
                "saved" -> "Saved to memory: ${block.content}"
                "forgotten" -> "Forgotten: ${block.content}"
                else -> "Memory recalled: ${block.content}"
            },
            color = scheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun NoteChip(block: Block.Note) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.surfaceVariant.copy(alpha = 0.30f))
            .border(0.5.dp, scheme.outlineVariant.copy(alpha = 0.30f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("•", fontSize = 11.sp, color = scheme.onSurfaceVariant)
        Text(
            text = block.content.take(160),
            color = scheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun ErrorCard(block: Block.Error) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.errorContainer.copy(alpha = 0.55f))
            .border(0.5.dp, scheme.error.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = "Error",
            tint = scheme.error,
            modifier = Modifier.size(16.dp)
        )
        Column {
            Text(
                text = block.title,
                color = scheme.onErrorContainer,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = block.message,
                color = scheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemoriesSheet(
    memories: List<MemoryEntry>,
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit,
    onEdit: (String, String, String) -> Unit,
    onDelete: (String) -> Unit,
    onClear: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var editing by remember { mutableStateOf<MemoryEntry?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = scheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Memories (${memories.size})",
                color = scheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { showAdd = true }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add memory",
                    tint = scheme.primary
                )
            }
            if (memories.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear all memories",
                        tint = scheme.error
                    )
                }
            }
        }
        Text(
            text = "Importance: high = always in context, normal = standard, low = rarely recalled",
            color = scheme.onSurfaceVariant,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
        )

        if (memories.isEmpty()) {
            Text(
                text = "No memories yet. Ask Lumina to remember something, or add one manually.",
                color = scheme.onSurfaceVariant,
                fontSize = 13.sp,
                modifier = Modifier.padding(20.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(memories, key = { it.id }) { mem ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(scheme.surfaceVariant.copy(alpha = 0.45f))
                            .clickable { editing = mem }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = mem.content,
                                color = scheme.onSurface,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Badge(
                                    text = mem.importance,
                                    color = when (mem.importance) {
                                        "high" -> scheme.error
                                        "low" -> scheme.onSurfaceVariant
                                        else -> scheme.primary
                                    }
                                )
                                Badge(
                                    text = if (mem.source == "ai") "Lumina" else "You",
                                    color = scheme.tertiary
                                )
                            }
                        }
                        IconButton(onClick = { onDelete(mem.id) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = scheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }

    if (editing != null) {
        MemoryDialog(
            title = "Edit memory",
            initialContent = editing!!.content,
            initialImportance = editing!!.importance,
            onDismiss = { editing = null },
            onConfirm = { content, importance ->
                onEdit(editing!!.id, content, importance)
                editing = null
            }
        )
    }

    if (showAdd) {
        MemoryDialog(
            title = "New memory",
            initialContent = "",
            initialImportance = "normal",
            onDismiss = { showAdd = false },
            onConfirm = { content, importance ->
                onAdd(content, importance)
                showAdd = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistorySheet(
    onDismiss: () -> Unit,
    onLoadSessions: () -> List<HistorySession>,
    onJump: (Long) -> Unit,
    onDeleteSession: (Long) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var sessions by remember { mutableStateOf<List<HistorySession>>(emptyList()) }

    LaunchedEffect(Unit) {
        sessions = onLoadSessions()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = scheme.surfaceContainerHigh
    ) {
        Text(
            text = "History",
            color = scheme.onSurface,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )
        Text(
            text = "Tap a session to jump back into the conversation",
            color = scheme.onSurfaceVariant,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
        )

        if (sessions.isEmpty()) {
            Text(
                text = "No history yet. Your conversations will appear here after you chat.",
                color = scheme.onSurfaceVariant,
                fontSize = 13.sp,
                modifier = Modifier.padding(20.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sessions, key = { it.id }) { session ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(scheme.surfaceVariant.copy(alpha = 0.45f))
                            .clickable { onJump(session.id) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = session.title.ifBlank { "Untitled session" },
                                color = scheme.onSurface,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Text(
                                text = formatHistoryTimestamp(session.updatedAt),
                                color = scheme.onSurfaceVariant,
                                fontSize = 10.5.sp,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                        }
                        IconButton(onClick = {
                            onDeleteSession(session.id)
                            sessions = onLoadSessions()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete session",
                                tint = scheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

private fun formatHistoryTimestamp(timestamp: Long, pattern: String = "EEE, MMM d • HH:mm"): String =
    runCatching {
        SimpleDateFormat(pattern, Locale.getDefault()).format(Date(timestamp))
    }.getOrDefault("")

@Composable
private fun Badge(text: String, color: Color) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text = text,
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    )
}

@Composable
private fun MemoryDialog(
    title: String,
    initialContent: String,
    initialImportance: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    var content by remember { mutableStateOf(initialContent) }
    var importance by remember { mutableStateOf(initialImportance) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = scheme.surfaceContainerHigh,
        title = { Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(
                    value = content,
                    onValueChange = { content = it },
                    placeholder = { Text("What should Lumina remember?") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (level in listOf("high", "normal", "low")) {
                        FilterChip(
                            selected = importance == level,
                            onClick = { importance = level },
                            label = { Text(level, fontSize = 11.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(content.trim(), importance) },
                enabled = content.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun TypingIndicatorBubble() {
    val scheme = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "typing")
    val dot1 by transition.animateFloat(
        initialValue = 0f, targetValue = -5f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1"
    )
    val dot2 by transition.animateFloat(
        initialValue = 0f, targetValue = -5f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, delayMillis = 200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2"
    )
    val dot3 by transition.animateFloat(
        initialValue = 0f, targetValue = -5f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, delayMillis = 400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3"
    )
    val dot1Alpha by transition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1Alpha"
    )
    val dot2Alpha by transition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, delayMillis = 200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2Alpha"
    )
    val dot3Alpha by transition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, delayMillis = 400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3Alpha"
    )

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(scheme.surfaceVariant.copy(alpha = 0.55f))
                .border(0.5.dp, scheme.outlineVariant.copy(alpha = 0.40f),
                    RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val dots = listOf(dot1 to dot1Alpha, dot2 to dot2Alpha, dot3 to dot3Alpha)
            for ((offset, alpha) in dots) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .offset(y = offset.dp)
                        .alpha(alpha)
                        .clip(CircleShape)
                        .background(scheme.onSurfaceVariant)
                )
            }
        }
    }
}

@Composable
private fun SuggestionChip(
    emoji: String,
    title: String,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surfaceVariant.copy(alpha = 0.45f))
            .border(0.5.dp, scheme.outlineVariant.copy(alpha = 0.40f),
                RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 11.sp)
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = title,
                color = scheme.onSurface,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun introStage(progress: Float, start: Float, end: Float): Float =
    ((progress - start) / (end - start)).coerceIn(0f, 1f)

private fun prettyParams(params: String): String {
    val json = runCatching { JSONObject(params) }.getOrNull() ?: return params
    val parts = json.keys().asSequence().map { key ->
        val v = json.opt(key)
        val vs = when (v) {
            is String -> v
            is Boolean, is Int, is Long, is Double -> v.toString()
            else -> v.toString()
        }
        "$key: $vs"
    }
    return parts.joinToString(" • ")
}

private fun prettyResult(result: String): String {
    val json = runCatching { JSONObject(result) }.getOrNull() ?: return result
    if (!json.optBoolean("success", true)) {
        return json.optString("error", "Failed")
    }
    val data = json.optJSONObject("data") ?: return json.optString("error", "Done")
    val parts = data.keys().asSequence().map { key ->
        val v = data.opt(key)
        val vs = when (v) {
            is String -> v
            is Boolean, is Int, is Long, is Double -> v.toString()
            is JSONObject -> "( )"
            else -> v.toString()
        }
        "$key: $vs"
    }
    return parts.joinToString(" • ").take(120)
}