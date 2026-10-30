package com.example.stickiespost.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.stickiespost.data.remote.FirebaseNoteStore
import com.example.stickiespost.ui.theme.NoteAccentText
import com.example.stickiespost.ui.theme.NoteChipOutline
import com.example.stickiespost.ui.theme.NoteRowCream
import com.example.stickiespost.ui.theme.OpenStatusAccent
import com.example.stickiespost.ui.theme.StickiesPostTheme


@Composable
fun FirebaseAuthScreen(store: FirebaseNoteStore) {
    FirebaseAuthForm(
        onSignIn = store::signIn,
        onSignUp = store::signUp
    )
}

@Composable
private fun FirebaseAuthForm(
    onSignIn: (String, String, (Exception?) -> Unit) -> Unit,
    onSignUp: (String, String, (Exception?) -> Unit) -> Unit
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var creatingAccount by rememberSaveable { mutableStateOf(false) }
    var isLoading by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = NoteRowCream
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Card(
                modifier = Modifier.widthIn(max = 440.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = NoteRowCream)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = if (creatingAccount) {
                            "Create your account"
                        } else {
                            "Sign in to StickiesPost"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = NoteAccentText
                    )
                    Text(
                        "Sign in to securely sync your notes across your devices.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; error = null },
                        label = { Text("Email") },
                        singleLine = true,
                        enabled = !isLoading,
                        shape = RoundedCornerShape(18.dp),
                        colors = authFieldColors(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; error = null },
                        label = { Text("Password") },
                        singleLine = true,
                        enabled = !isLoading,
                        shape = RoundedCornerShape(18.dp),
                        colors = authFieldColors(),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                    Button(
                        onClick = {
                            if (email.isBlank() || password.isBlank()) {
                                error = "Enter your email and password."
                            } else if (creatingAccount && password.length < 6) {
                                error = "Use a password with at least 6 characters."
                            } else {
                                isLoading = true
                                error = null
                                val completion: (Exception?) -> Unit = { exception ->
                                    isLoading = false
                                    error = exception?.localizedMessage
                                }
                                if (creatingAccount) {
                                    onSignUp(email, password, completion)
                                } else {
                                    onSignIn(email, password, completion)
                                }
                            }
                        },
                        enabled = !isLoading,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OpenStatusAccent,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(2.dp),
                                color = Color.White
                            )
                        } else {
                            Text(if (creatingAccount) "Create account" else "Sign in")
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            creatingAccount = !creatingAccount
                            error = null
                        },
                        enabled = !isLoading,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (creatingAccount) {
                                "Already have an account? Sign in"
                            } else {
                                "New here? Create an account"
                            }
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FirebaseAuthScreenPreview() {
    StickiesPostTheme {
        FirebaseAuthForm(
            onSignIn = { _, _, onComplete -> onComplete(null) },
            onSignUp = { _, _, onComplete -> onComplete(null) }
        )
    }
}

@Composable
private fun authFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = OpenStatusAccent,
    unfocusedBorderColor = NoteChipOutline.copy(alpha = 0.55f)
)
