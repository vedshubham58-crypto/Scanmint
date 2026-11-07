package com.vedshubham.scanmint.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.vedshubham.scanmint.data.Doc
import com.vedshubham.scanmint.pdf.PdfLock

/** What to do with the locked copy. */
enum class LockedTarget { SHARE, SAVE }

/** Ask for a password, then share or save a password-locked copy of [doc]. */
@Composable
fun LockDialog(doc: Doc, onDismiss: () -> Unit, onLock: (password: String, target: LockedTarget) -> Unit) {
    var password by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val supported = PdfLock.isSupported(password)
    val ok = password.length >= 4 && password == again && supported

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Lock, null) },
        title = { Text("Lock with a password") },
        text = {
            Column {
                Text("Anyone opening “${doc.name}” will need this password. Your copy in ScanMint stays unlocked.")
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it.take(32) },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, "Show password")
                        }
                    },
                )
                OutlinedTextField(
                    value = again,
                    onValueChange = { again = it.take(32) },
                    label = { Text("Type it again") },
                    singleLine = true,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                Spacer(Modifier.height(6.dp))
                val hint = when {
                    !supported -> "Use English letters, numbers and symbols only."
                    password.isNotEmpty() && password.length < 4 -> "At least 4 characters."
                    again.isNotEmpty() && again != password -> "The two passwords don't match."
                    else -> "Don't forget it: nobody can open the file without it."
                }
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (!supported || (again.isNotEmpty() && again != password)) Color(0xFFD03A3A) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = ok, onClick = { onLock(password, LockedTarget.SHARE); onDismiss() }) { Text("Share") }
        },
        dismissButton = {
            TextButton(enabled = ok, onClick = { onLock(password, LockedTarget.SAVE); onDismiss() }) { Text("Save to phone") }
        },
    )
}
