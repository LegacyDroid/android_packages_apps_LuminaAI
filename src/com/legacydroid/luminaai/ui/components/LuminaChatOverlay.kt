/*
 * SPDX-FileCopyrightText: 2026 The LegacyDroid Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.legacydroid.luminaai.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.legacydroid.luminaai.model.ChatMessage
import com.legacydroid.luminaai.model.SuggestionItem
import com.legacydroid.luminaai.ui.theme.LuminaAmber
import com.legacydroid.luminaai.ui.theme.LuminaCoral
import com.legacydroid.luminaai.ui.theme.LuminaGold
import com.legacydroid.luminaai.ui.theme.LuminaTextPrimary
import com.legacydroid.luminaai.ui.theme.LuminaTextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * The awakened Lumina overlay: close/clear buttons, the orb at 32% height,
 * the LUMINA label, and the bottom chat stack (messages, suggestion chips,
 * pill input). Entry timings mirror the prototype: orb +500ms/1.1s, label
 * +1100ms, chat +900ms, buttons +1300ms.
 */
@Composable
fun LuminaChatOverlay(
    modifier: Modifier = Modifier,
    messages: List<ChatMessage>,
    isThinking: Boolean,
    onSendMessage: (String) -> Unit,
    onClose: () -> Unit,
    onClearChat: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val orbMaterialize = remember { Animatable(0f) }
    val labelAlpha = remember { Animatable(0f) }
    val closeBtnAlpha = remember { Animatable(0f) }
    val chatSlideProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch {
            delay(500)
            orbMaterialize.animateTo(
                targetValue = 1f,
                animationSpec = tween(1100, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f))
            )
        }
        launch {
            delay(1100)
            labelAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(800, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f))
            )
        }
        launch {
            delay(900)
            chatSlideProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(900, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f))
            )
        }
        launch {
            delay(1300)
            closeBtnAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(400, easing = FastOutSlowInEasing)
            )
        }
    }

    val suggestions = remember {
        listOf(
            SuggestionItem(emoji = "☀️", title = "Weather today", query = "What's the weather today?"),
            SuggestionItem(emoji = "📝", title = "Summarize", query = "Summarize my notifications"),
            SuggestionItem(emoji = "🎵", title = "Focus music", query = "Play some focus music"),
            SuggestionItem(emoji = "⏰", title = "Set reminder", query = "Set a reminder"),
            SuggestionItem(emoji = "💡", title = "Creative thought", query = "Give me an inspiring thought"),
            SuggestionItem(emoji = "✨", title = "Tell a joke", query = "Tell me a joke")
        )
    }

    LaunchedEffect(messages.size, isThinking) {
        if (messages.isNotEmpty()) {
            delay(40)
            listState.animateScrollToItem(messages.size - 1)
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

        // Close / clear chat buttons, top right
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 20.dp)
                .alpha(closeBtnAlpha.value),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (messages.size > 1) {
                IconButton(
                    onClick = onClearChat,
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(0x1AFFFFFF))
                        .border(0.5.dp, Color(0x26FFFFFF), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Clear Chat",
                        tint = LuminaTextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color(0x1AFFFFFF))
                    .border(0.5.dp, Color(0x26FFFFFF), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Lumina AI",
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        // Orb, centered at 32% height, materializing from blur+scale
        val orbProgress = orbMaterialize.value
        val orbScale = 0.5f + orbProgress * 0.5f
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = orbTopOffset)
                .alpha(orbProgress)
                .scale(orbScale)
                .blur(((1f - orbProgress) * 20f).dp),
            contentAlignment = Alignment.Center
        ) {
            LuminaOrb(
                size = 110.dp,
                isThinking = isThinking
            )
        }

        // LUMINA label below the orb
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = orbTopOffset + 75.dp)
                .alpha(labelAlpha.value),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "LUMINA",
                color = Color(0xFFFFF5E8).copy(alpha = 0.75f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 3.5.sp
            )
        }

        // Bottom chat stack, sliding up
        val slideProgress = chatSlideProgress.value
        val slideOffsetY = (1f - slideProgress) * 100.dp.value

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 14.dp, vertical = 20.dp)
                .offset(y = slideOffsetY.dp)
                .alpha(slideProgress)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Messages stream (max 190dp, bottom-aligned)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 190.dp)
                    .padding(bottom = 10.dp)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        MessageBubble(message = msg)
                    }
                    if (isThinking) {
                        item { TypingIndicatorBubble() }
                    }
                }
            }

            // Suggestion chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                items(suggestions, key = { it.id }) { chip ->
                    SuggestionChip(
                        title = chip.title,
                        onClick = { onSendMessage(chip.query) }
                    )
                }
            }

            // Pill input with gradient send button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xCC140E12))
                    .border(0.5.dp, Color(0x26FFFFFF), RoundedCornerShape(24.dp))
                    .padding(start = 16.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            text = "Ask Lumina anything...",
                            color = Color(0x66FFF5E8),
                            fontSize = 14.sp
                        )
                    },
                    colors = TextFieldDefaults.textFieldColors(
                        backgroundColor = Color.Transparent,
                        textColor = Color.White,
                        cursorColor = LuminaGold,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
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

                val canSend = inputText.isNotBlank()
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            if (canSend) Brush.linearGradient(
                                listOf(LuminaCoral, LuminaAmber)
                            ) else Brush.linearGradient(
                                listOf(Color(0x33FFFFFF), Color(0x22FFFFFF))
                            )
                        )
                        .clickable(enabled = canSend) {
                            if (canSend) {
                                val query = inputText.trim()
                                inputText = ""
                                onSendMessage(query)
                            }
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = "Send",
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    var displayedText by remember(message.id) {
        mutableStateOf(if (message.isUser) message.text else "")
    }
    val appearProgress = remember(message.id) { Animatable(0f) }

    // Typewriter effect: 22ms + random 12ms per char for AI messages
    LaunchedEffect(message.id) {
        appearProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(500, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f))
        )
        if (!message.isUser && message.text.isNotEmpty()) {
            val fullText = message.text
            displayedText = ""
            for (i in 1..fullText.length) {
                displayedText = fullText.substring(0, i)
                delay(22L + Random.nextInt(12))
            }
        } else {
            displayedText = message.text
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(appearProgress.value)
            .offset(y = ((1f - appearProgress.value) * 8f).dp),
        contentAlignment = if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        if (message.isUser) {
            // User bubble: coral→amber gradient, sharp bottom-right corner
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .clip(
                        RoundedCornerShape(
                            topStart = 18.dp,
                            topEnd = 18.dp,
                            bottomStart = 18.dp,
                            bottomEnd = 4.dp
                        )
                    )
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(0xD9FF6B5E),
                                Color(0xD9FFA552)
                            )
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = displayedText,
                    color = Color.White,
                    fontSize = 13.5.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        } else {
            // AI bubble: frosted white, sharp bottom-left corner
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .clip(
                        RoundedCornerShape(
                            topStart = 18.dp,
                            topEnd = 18.dp,
                            bottomStart = 4.dp,
                            bottomEnd = 18.dp
                        )
                    )
                    .background(Color(0x1AFFFFFF))
                    .border(
                        0.5.dp,
                        Color(0x26FFFFFF),
                        RoundedCornerShape(
                            topStart = 18.dp,
                            topEnd = 18.dp,
                            bottomStart = 4.dp,
                            bottomEnd = 18.dp
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = displayedText,
                    color = LuminaTextPrimary,
                    fontSize = 13.5.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun TypingIndicatorBubble() {
    val transition = rememberInfiniteTransition(label = "typing")
    val dot1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = -5f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1"
    )
    val dot2 by transition.animateFloat(
        initialValue = 0f,
        targetValue = -5f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, delayMillis = 200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2"
    )
    val dot3 by transition.animateFloat(
        initialValue = 0f,
        targetValue = -5f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, delayMillis = 400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3"
    )
    val dot1Alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1Alpha"
    )
    val dot2Alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, delayMillis = 200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2Alpha"
    )
    val dot3Alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
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
                .background(Color(0x1AFFFFFF))
                .border(0.5.dp, Color(0x26FFFFFF), RoundedCornerShape(16.dp))
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
                        .background(LuminaTextSecondary)
                )
            }
        }
    }
}

@Composable
private fun SuggestionChip(
    title: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x10FFFFFF))
            .border(0.5.dp, Color(0x26FFFFFF), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp)
    ) {
        Text(
            text = title,
            color = LuminaTextPrimary,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium
        )
    }
}