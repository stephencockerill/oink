package com.oink.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.emoji2.emojipicker.EmojiPickerView
import com.oink.app.ui.theme.OinkPink
import kotlinx.coroutines.launch

/**
 * Reusable habit-icon field: shows the current [emoji] as a tappable circular
 * chip and, on tap, opens a [ModalBottomSheet] hosting the AndroidX
 * [EmojiPickerView] - the full, searchable, categorized emoji grid with recents
 * and skin-tone variants.
 *
 * The emoji is treated as an opaque [String] end to end, so multi-codepoint
 * emoji (skin tones, ZWJ sequences, flags) round-trip intact: the picker hands
 * back the composed grapheme and we store it verbatim. Selecting an emoji
 * invokes [onEmojiSelected] and animates the sheet closed.
 *
 * State down, events up: this holds only the transient sheet-visibility flag;
 * the selected emoji is owned by the caller's ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmojiPickerField(
    emoji: String,
    onEmojiSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showSheet by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    // The picker listener is registered once in the AndroidView factory; read the
    // latest callback through this so a recomposed lambda is never stale.
    val currentOnEmojiSelected by rememberUpdatedState(onEmojiSelected)

    Box(
        modifier = modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(OinkPink.copy(alpha = 0.15f))
            .border(2.dp, OinkPink, CircleShape)
            .clickable(role = Role.Button) { showSheet = true }
            .semantics {
                contentDescription = "Habit icon: $emoji. Tap to change."
                role = Role.Button
            },
        contentAlignment = Alignment.Center
    ) {
        Text(text = emoji, fontSize = 28.sp)
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState
        ) {
            AndroidView(
                factory = { context ->
                    EmojiPickerView(context).apply {
                        setOnEmojiPickedListener { picked ->
                            currentOnEmojiSelected(picked.emoji)
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                if (!sheetState.isVisible) showSheet = false
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .padding(horizontal = 8.dp)
            )
        }
    }
}
