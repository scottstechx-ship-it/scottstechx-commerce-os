package com.scottstechx.commerceos.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.scottstechx.commerceos.R
import com.scottstechx.commerceos.data.auth.GoogleSignInHelper
import com.scottstechx.commerceos.data.auth.Role
import com.scottstechx.commerceos.ui.animation.AnimatedFadeInUp
import com.scottstechx.commerceos.ui.animation.rememberPulseAlpha
import com.scottstechx.commerceos.ui.brand.BrandLogo
import com.scottstechx.commerceos.ui.common.VoiceHelpButton
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface GoogleSignInEntryPoint {
    fun googleSignInHelper(): GoogleSignInHelper
}

@Composable
fun LoginScreen(
    onSignedIn: (Role) -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val logoPulse = rememberPulseAlpha(min = 0.92f, max = 1.0f, durationMs = 1800)

    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val googleHelper = remember(activity) {
        activity?.let {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                GoogleSignInEntryPoint::class.java
            ).googleSignInHelper()
        }
    }

    LaunchedEffect(state.signedInAs) {
        val role = state.signedInAs ?: return@LaunchedEffect
        viewModel.consumeSignedIn()
        onSignedIn(role)
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedFadeInUp(delayMs = 0) {
                BrandLogo(size = 96.dp, modifier = Modifier.scale(logoPulse))
            }
            Spacer(Modifier.height(16.dp))
            AnimatedFadeInUp(delayMs = 80) {
                Text(
                    text = stringResource(R.string.login_subtitle),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(8.dp))
            AnimatedFadeInUp(delayMs = 140) {
                Text(
                    text = stringResource(R.string.login_title),
                    style = MaterialTheme.typography.headlineMedium
                )
            }
            Spacer(Modifier.height(24.dp))

            // Tamper-detection warning, only shown if any indicator tripped.
            state.tamper?.let { report ->
                if (report.tags.isNotEmpty()) {
                    AnimatedFadeInUp(delayMs = 200) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    stringResource(R.string.tamper_warning_title),
                                    style = MaterialTheme.typography.titleSmall
                                )
                                if (report.isRooted) {
                                    Text(stringResource(R.string.tamper_warning_rooted),
                                        style = MaterialTheme.typography.bodySmall)
                                }
                                if (report.isDebuggable) {
                                    Text(stringResource(R.string.tamper_warning_debuggable),
                                        style = MaterialTheme.typography.bodySmall)
                                }
                                if (report.isEmulator) {
                                    Text(stringResource(R.string.tamper_warning_emulator),
                                        style = MaterialTheme.typography.bodySmall)
                                }
                                if (report.isInstallerSuspicious) {
                                    Text(stringResource(R.string.tamper_warning_installer),
                                        style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }

            Spacer(Modifier.height(24.dp))
            AnimatedFadeInUp(delayMs = 80) {
                val scope = rememberCoroutineScope()
                OutlinedButton(
                    onClick = {
                        val act = activity
                        val helper = googleHelper
                        if (act != null && helper != null) {
                            scope.launch {
                                val idToken = helper.signIn(act)
                                if (idToken != null) viewModel.signInWithGoogle(idToken)
                                else viewModel.cancelGoogleSignIn()
                            }
                        }
                    },
                    enabled = !state.isSubmitting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Continue with Google")
                }
            }
            Spacer(Modifier.height(16.dp))
            AnimatedFadeInUp(delayMs = 140) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Or sign in with phone",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    VoiceHelpButton(onResult = { viewModel.onPhoneChange(it) })
                }
            }
            Spacer(Modifier.height(8.dp))

            AnimatedFadeInUp(delayMs = 200) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.role == Role.BUYER,
                        onClick = { viewModel.onRoleChange(Role.BUYER) },
                        label = { Text(stringResource(R.string.login_role_buyer)) },
                        colors = FilterChipDefaults.filterChipColors()
                    )
                    FilterChip(
                        selected = state.role == Role.SELLER,
                        onClick = { viewModel.onRoleChange(Role.SELLER) },
                        label = { Text(stringResource(R.string.login_role_seller)) }
                    )
                    FilterChip(
                        selected = state.role == Role.DRIVER,
                        onClick = { viewModel.onRoleChange(Role.DRIVER) },
                        label = { Text(stringResource(R.string.login_role_driver)) }
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            AnimatedFadeInUp(delayMs = 260) {
                OutlinedTextField(
                    value = state.phone,
                    onValueChange = viewModel::onPhoneChange,
                    label = { Text(stringResource(R.string.login_phone_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(12.dp))
            AnimatedFadeInUp(delayMs = 320) {
                OutlinedTextField(
                    value = state.password,
                    onValueChange = viewModel::onPasswordChange,
                    label = { Text(stringResource(R.string.login_password_hint)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (state.error != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = state.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(Modifier.height(24.dp))
            AnimatedFadeInUp(delayMs = 380) {
                Button(
                    onClick = { viewModel.submit() },
                    enabled = !state.isSubmitting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.isSubmitting) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.height(20.dp)
                        )
                    } else {
                        Text(stringResource(R.string.login_button))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.login_demo_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
