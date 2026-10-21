package com.eventroute.app.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eventroute.app.ui.components.*
import com.eventroute.app.ui.theme.*

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: AuthViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var isSignUp by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var agreedToTerms by rememberSaveable { mutableStateOf(true) }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = isSignUp) {
        isSignUp = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ClayBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 20.dp)
    ) {
        if (!isSignUp) LoginContent(
            email, { email = it }, password, { password = it },
            passwordVisible, { passwordVisible = !passwordVisible },
            errorMessage = uiState.errorMessage,
            onLogin = {
                viewModel.login(email.ifEmpty { "john.doe@nsut.ac.in" }, password.ifEmpty { "password123" }, onAuthSuccess)
            },
            onSwitch = { isSignUp = true }
        ) else SignUpContent(
            name, { name = it }, email, { email = it }, password, { password = it },
            agreedToTerms, { agreedToTerms = it },
            errorMessage = uiState.errorMessage,
            onCreate = {
                viewModel.register(name, email, password, null, onAuthSuccess)
            },
            onBack = { isSignUp = false }
        )
    }
}

@Composable
private fun ColumnScope.LoginContent(
    email: String, onEmail: (String) -> Unit,
    password: String, onPassword: (String) -> Unit,
    passwordVisible: Boolean, onToggleVisible: () -> Unit,
    errorMessage: String?,
    onLogin: () -> Unit, onSwitch: () -> Unit,
) {
    Spacer(Modifier.height(30.dp))
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        ClayIconBadge(Icons.Rounded.Route, background = ClayBluePrimary, tint = Color.White, size = 76.dp, shape = RoundedCornerShape(26.dp))
        Spacer(Modifier.height(14.dp))
        Text("EventRoute", style = ClayType.Title.copy(fontSize = 28.sp))
        Text("Welcome back!", style = ClayType.Body.copy(fontSize = 14.sp))
    }
    Spacer(Modifier.height(26.dp))

    ClayCard(Modifier.fillMaxWidth(), RoundedCornerShape(36.dp), depth = 14.dp) {
        Column(Modifier.padding(24.dp)) {
            Text("Login", style = ClayType.CardTitle.copy(fontSize = 20.sp))
            Spacer(Modifier.height(18.dp))

            ClayTextField(
                value = email, onValueChange = onEmail,
                label = "College Email", placeholder = "student@college.edu",
                leadingIcon = Icons.Rounded.Email, uppercaseLabel = false,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )
            Spacer(Modifier.height(16.dp))
            ClayTextField(
                value = password, onValueChange = onPassword,
                label = "Password", placeholder = "••••••••",
                leadingIcon = Icons.Rounded.Lock, uppercaseLabel = false,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    val src = remember { MutableInteractionSource() }
                    Icon(
                        if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password",
                        tint = ClayTextMuted,
                        modifier = Modifier.size(20.dp).clayPress(onToggleVisible, true, src)
                    )
                }
            )
            if (errorMessage != null) {
                Spacer(Modifier.height(8.dp))
                Text(errorMessage, style = ClayType.Caption.copy(color = ClayAlertPinkText))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Forgot Password?",
                style = ClayType.BodyStrong.copy(fontSize = 12.sp, color = ClayBlueDeep),
                modifier = Modifier.align(Alignment.End).clayPress({ }, true, remember { MutableInteractionSource() })
            )
            Spacer(Modifier.height(22.dp))
            ClayPillButton("Login", onLogin, Modifier.fillMaxWidth())
        }
    }

    Spacer(Modifier.height(24.dp))

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(Color(0xFFDDE6F2)))
        Text("  OR SIGN IN WITH  ", style = ClayType.Label.copy(fontSize = 9.sp))
        Box(Modifier.weight(1f).height(1.dp).background(Color(0xFFDDE6F2)))
    }
    Spacer(Modifier.height(16.dp))

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        SocialButton("Google", BrandIcons.Google, Color.Unspecified, Modifier.weight(1f))
        SocialButton("Apple", BrandIcons.Apple, ClayTextPrimary, Modifier.weight(1f))
    }

    Spacer(Modifier.height(28.dp))
    AuthFooter("Don't have an account? ", "Sign Up", ClayBlueDeep, onSwitch)
}

@Composable
private fun ColumnScope.SignUpContent(
    name: String, onName: (String) -> Unit,
    email: String, onEmail: (String) -> Unit,
    password: String, onPassword: (String) -> Unit,
    agreed: Boolean, onAgree: (Boolean) -> Unit,
    errorMessage: String?,
    onCreate: () -> Unit, onBack: () -> Unit,
) {
    ClayIconButton(Icons.AutoMirrored.Rounded.ArrowBack, onBack, contentDescription = "Back")
    Spacer(Modifier.height(22.dp))
    Text("Join the Hub", style = ClayType.Title)
    Spacer(Modifier.height(2.dp))
    Text("Connect with events and peers.", style = ClayType.Body.copy(fontSize = 14.sp))
    Spacer(Modifier.height(22.dp))

    ClayCard(Modifier.fillMaxWidth(), RoundedCornerShape(36.dp), depth = 14.dp) {
        Column(Modifier.padding(24.dp)) {
            ClayTextField(name, onName, "Full name", placeholder = "John Doe", leadingIcon = Icons.Rounded.Person)
            Spacer(Modifier.height(16.dp))
            ClayTextField(
                email, onEmail, "College email", placeholder = "john@iitd.ac.in",
                leadingIcon = Icons.Rounded.Email,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )
            Spacer(Modifier.height(16.dp))
            ClayTextField(
                password, onPassword, "Create password", placeholder = "••••••••",
                leadingIcon = Icons.Rounded.Lock,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
            if (errorMessage != null) {
                Spacer(Modifier.height(8.dp))
                Text(errorMessage, style = ClayType.Caption.copy(color = ClayAlertPinkText))
            }
            Spacer(Modifier.height(18.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                ClayCheckbox(agreed, onAgree)
                Spacer(Modifier.width(12.dp))
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = ClayTextSecondary)) { append("I agree to the ") }
                        withStyle(SpanStyle(color = ClayBlueDeep)) { append("Terms of Service") }
                        withStyle(SpanStyle(color = ClayTextSecondary)) { append(" and ") }
                        withStyle(SpanStyle(color = ClayBlueDeep)) { append("Privacy Policy") }
                        withStyle(SpanStyle(color = ClayTextSecondary)) { append(".") }
                    },
                    style = ClayType.Caption.copy(fontSize = 11.sp)
                )
            }
            Spacer(Modifier.height(22.dp))
            ClayPillButton(
                "Create Account", onCreate, Modifier.fillMaxWidth(),
                backgroundColor = ClayPinkPrimary, textColor = ClayPinkText, enabled = agreed
            )
        }
    }

    Spacer(Modifier.height(32.dp))
    AuthFooter("Already have an account? ", "Login", ClayPinkText, onBack)
}

@Composable
private fun AuthFooter(prompt: String, action: String, color: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Text(prompt, style = ClayType.Body.copy(fontSize = 14.sp))
        Text(
            action,
            style = ClayType.BodyStrong.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color, textDecoration = TextDecoration.Underline),
            modifier = Modifier.clayPress(onClick, true, remember { MutableInteractionSource() })
        )
    }
}

@Composable
private fun SocialButton(label: String, icon: ImageVector, tint: Color, modifier: Modifier) {
    val source = remember { MutableInteractionSource() }
    val pressed = rememberPressed(source)
    Row(
        modifier = modifier
            .clayPress({ }, true, source)
            .clay(RoundedCornerShape(50), Color.White, depth = 9.dp, pressed = pressed)
            .padding(vertical = 15.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = ClayType.BodyStrong)
    }
}

@Composable
private fun ClayCheckbox(checked: Boolean, onChange: (Boolean) -> Unit) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clayPress({ onChange(!checked) }, true, remember { MutableInteractionSource() })
            .then(
                if (checked) Modifier.clay(RoundedCornerShape(9.dp), ClayPinkPrimary, depth = 5.dp)
                else Modifier.clayInset(RoundedCornerShape(9.dp), depth = 4.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (checked) Icon(Icons.Rounded.Check, "Agreed", tint = ClayPinkText, modifier = Modifier.size(16.dp))
    }
}
