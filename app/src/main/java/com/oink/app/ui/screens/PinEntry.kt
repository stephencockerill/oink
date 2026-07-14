package com.oink.app.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.oink.app.viewmodel.PrivateViewModel

/**
 * Numeric PIN input field, shared by the private area gate and the rewards
 * private-funds unlock so both prompts look and behave identically.
 */
@Composable
internal fun PinField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        isError = isError,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
    )
}

/**
 * Keep only digits and cap at the maximum PIN length, so a PIN field can never
 * hold non-numeric or over-long input.
 */
internal fun String.digitsOnly(): String =
    filter { it.isDigit() }.take(PrivateViewModel.PIN_LENGTH.last)
