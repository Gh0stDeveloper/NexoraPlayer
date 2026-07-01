package com.nexora.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nexora.player.R
import com.nexora.player.data.model.MediaEntry
import com.nexora.player.data.online.OnlineSongDto
import com.nexora.player.data.online.OnlineUiState
import com.nexora.player.data.online.OnlineUploadProgress
import com.nexora.player.data.online.OnlineUserSession
import com.nexora.player.ui.components.MediaArtwork
import com.nexora.player.ui.components.formatDuration

private const val ONLINE_TAB_HOME = 0
private const val ONLINE_TAB_SEARCH = 1
private const val ONLINE_TAB_ACCOUNT = 2
private const val ONLINE_TAB_UPLOAD = 3

@Composable
fun OnlineScreen(
    modifier: Modifier = Modifier,
    state: OnlineUiState,
    localAudio: List<MediaEntry>,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String, String) -> Unit,
    onGoogleLogin: () -> Unit,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onLogout: () -> Unit,
    onRefresh: () -> Unit,
    onSearch: () -> Unit,
    onClearSearch: () -> Unit,
    onPlaySong: (OnlineSongDto) -> Unit,
    onUpdateProfile: (String) -> Unit,
    onChangePassword: (String) -> Unit,
    onToggleUploadSelection: (MediaEntry) -> Unit,
    onClearUploadSelection: () -> Unit,
    onUploadSelected: () -> Unit
) {
    if (state.restoringSession) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator()
                Text(stringResource(R.string.online_restoring_session))
            }
        }
        return
    }

    if (!state.loggedIn) {
        OnlineAuthContent(
            modifier = modifier,
            loading = state.authLoading,
            error = state.authError,
            onLogin = onLogin,
            onRegister = onRegister,
            onGoogleLogin = onGoogleLogin
        )
        return
    }

    val safeTab = selectedTab.coerceIn(ONLINE_TAB_HOME, ONLINE_TAB_UPLOAD)

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        when (safeTab) {
            ONLINE_TAB_HOME -> OnlineHomeContent(
                state = state,
                onRefresh = onRefresh,
                onPlaySong = onPlaySong,
                modifier = Modifier.fillMaxSize()
            )
            ONLINE_TAB_SEARCH -> OnlineSearchContent(
                state = state,
                onSearch = onSearch,
                onClearSearch = onClearSearch,
                onPlaySong = onPlaySong,
                modifier = Modifier.fillMaxSize()
            )
            ONLINE_TAB_ACCOUNT -> OnlineAccountContent(
                state = state,
                onLogout = onLogout,
                onUpdateProfile = onUpdateProfile,
                onChangePassword = onChangePassword,
                onOpenUpload = { onTabSelected(ONLINE_TAB_UPLOAD) },
                onOpenHome = { onTabSelected(ONLINE_TAB_HOME) },
                modifier = Modifier.fillMaxSize()
            )
            ONLINE_TAB_UPLOAD -> OnlineUploadContent(
                localAudio = localAudio,
                selectedIds = state.selectedUploadIds,
                progress = state.uploadProgress,
                onToggleSelection = onToggleUploadSelection,
                onClearSelection = onClearUploadSelection,
                onUploadSelected = onUploadSelected,
                onBackToProfile = { onTabSelected(ONLINE_TAB_ACCOUNT) },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun OnlineAuthContent(
    modifier: Modifier,
    loading: Boolean,
    error: String?,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String, String) -> Unit,
    onGoogleLogin: () -> Unit
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var registerMode by rememberSaveable { mutableStateOf(false) }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var confirmPasswordVisible by rememberSaveable { mutableStateOf(false) }

    val cleanEmail = email.trim()
    val cleanUsername = username.trim()
    val passwordsMatch = password == confirmPassword
    val emailLooksValid = cleanEmail.contains("@") && cleanEmail.substringAfter("@", "").contains(".")
    val canLogin = emailLooksValid && password.isNotBlank()
    val canRegister = cleanUsername.length >= 3 && emailLooksValid && password.length >= 6 && confirmPassword.isNotBlank() && passwordsMatch
    val canSubmit = !loading && if (registerMode) canRegister else canLogin

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.13f),
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { OnlineAuthHero() }
        item {
            ElevatedCard(shape = RoundedCornerShape(32.dp), modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(13.dp)
                ) {
                    Text(
                        text = if (registerMode) stringResource(R.string.online_create_account) else stringResource(R.string.online_login),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.online_auth_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (registerMode) {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text(stringResource(R.string.online_username)) },
                            leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text(stringResource(R.string.online_email)) },
                        leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth()
                    )

                    PasswordField(
                        value = password,
                        onValueChange = { password = it },
                        label = stringResource(R.string.online_password),
                        visible = passwordVisible,
                        onVisibleChange = { passwordVisible = !passwordVisible },
                        imeAction = if (registerMode) ImeAction.Next else ImeAction.Done,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (registerMode) {
                        PasswordField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = stringResource(R.string.online_confirm_password),
                            visible = confirmPasswordVisible,
                            onVisibleChange = { confirmPasswordVisible = !confirmPasswordVisible },
                            imeAction = ImeAction.Done,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (password.isNotBlank() && password.length < 6) {
                            InlineError(text = stringResource(R.string.online_password_minimum))
                        }
                        if (confirmPassword.isNotBlank() && !passwordsMatch) {
                            InlineError(text = stringResource(R.string.online_passwords_do_not_match))
                        }
                    }

                    if (!error.isNullOrBlank()) {
                        ErrorCard(message = error)
                    }

                    Button(
                        enabled = canSubmit,
                        onClick = {
                            if (registerMode) onRegister(cleanEmail, password, cleanUsername) else onLogin(cleanEmail, password)
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        if (loading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(if (registerMode) stringResource(R.string.online_register_action) else stringResource(R.string.online_login_action))
                        }
                    }

                    TextButton(onClick = { registerMode = !registerMode }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (registerMode) stringResource(R.string.online_have_account) else stringResource(R.string.online_need_account))
                    }

                    OnlineDividerLabel(text = stringResource(R.string.online_auth_google_divider))

                    OutlinedButton(
                        enabled = !loading,
                        onClick = onGoogleLogin,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_google_logo),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.size(10.dp))
                        Text(stringResource(R.string.online_google_login_action), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun OnlineAuthHero() {
    ElevatedCard(
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.CloudDone, contentDescription = null, modifier = Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(stringResource(R.string.online_auth_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.online_top_subtitle_guest), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun OnlineDividerLabel(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), thickness = 0.5.dp)
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(modifier = Modifier.weight(1f), thickness = 0.5.dp)
    }
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    visible: Boolean,
    onVisibleChange: () -> Unit,
    imeAction: ImeAction,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
        trailingIcon = {
            IconButton(onClick = onVisibleChange) {
                Icon(
                    imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = stringResource(if (visible) R.string.online_hide_password else R.string.online_show_password)
                )
            }
        },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        modifier = modifier
    )
}

@Composable
private fun InlineError(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}

@Composable
private fun OnlineHomeContent(
    state: OnlineUiState,
    onRefresh: () -> Unit,
    onPlaySong: (OnlineSongDto) -> Unit,
    modifier: Modifier
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            OnlineStatsRow(
                songsCount = state.songs.size,
                resultsCount = state.searchResults.size
            )
        }
        item {
            SectionTitleRow(
                title = stringResource(R.string.online_home_recent_title),
                action = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_refresh))
                    }
                }
            )
        }
        if (state.loadingSongs) {
            item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        }
        state.songsError?.let { item { ErrorCard(message = it, onRetry = onRefresh) } }
        if (!state.loadingSongs && state.songs.isEmpty() && state.songsError == null) {
            item { EmptyOnlineCard(text = stringResource(R.string.online_empty_home)) }
        }
        items(state.songs, key = { it.id }) { song ->
            OnlineSongRow(song = song, onClick = { onPlaySong(song) })
        }
    }
}

@Composable
private fun OnlineStatsRow(songsCount: Int, resultsCount: Int) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OnlineStatCard(label = stringResource(R.string.online_stats_songs), value = songsCount.toString(), modifier = Modifier.weight(1f))
        OnlineStatCard(label = stringResource(R.string.online_stats_results), value = resultsCount.toString(), modifier = Modifier.weight(1f))
    }
}

@Composable
private fun OnlineStatCard(label: String, value: String, modifier: Modifier = Modifier) {
    ElevatedCard(shape = RoundedCornerShape(22.dp), modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SectionTitleRow(title: String, action: @Composable (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        action?.invoke()
    }
}

@Composable
private fun OnlineSearchContent(
    state: OnlineUiState,
    onSearch: () -> Unit,
    onClearSearch: () -> Unit,
    onPlaySong: (OnlineSongDto) -> Unit,
    modifier: Modifier
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (state.onlineQuery.isNotBlank()) {
            item {
                SectionTitleRow(
                    title = state.onlineQuery,
                    action = {
                        IconButton(onClick = onClearSearch) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.online_clear_search))
                        }
                    }
                )
            }
        }
        if (state.searching) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        state.searchError?.let { item { ErrorCard(message = it, onRetry = onSearch) } }
        if (state.onlineQuery.isNotBlank() && !state.searching && state.searchResults.isEmpty() && state.searchError == null) {
            item { EmptyOnlineCard(text = stringResource(R.string.online_empty_search)) }
        }
        if (state.onlineQuery.isBlank() && state.searchResults.isEmpty()) {
            item { EmptyOnlineCard(text = stringResource(R.string.online_search_from_top)) }
        }
        items(state.searchResults, key = { it.id }) { song ->
            OnlineSongRow(song = song, onClick = { onPlaySong(song) })
        }
    }
}

@Composable
private fun OnlineAccountContent(
    state: OnlineUiState,
    onLogout: () -> Unit,
    onUpdateProfile: (String) -> Unit,
    onChangePassword: (String) -> Unit,
    onOpenUpload: () -> Unit,
    onOpenHome: () -> Unit,
    modifier: Modifier
) {
    val session = state.session
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            OnlineAccountHero(session = session, onLogout = onLogout)
        }
        item {
            ProfileActionsCard(onOpenUpload = onOpenUpload, onOpenHome = onOpenHome)
        }
        item {
            ProfileEditorCard(state = state, onUpdateProfile = onUpdateProfile)
        }
        item {
            PasswordSettingsCard(state = state, onChangePassword = onChangePassword)
        }
    }
}

@Composable
private fun OnlineAccountHero(session: OnlineUserSession?, onLogout: () -> Unit) {
    ElevatedCard(shape = RoundedCornerShape(30.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                OnlineProfileAvatar(session = session, size = 78)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = session?.profileName ?: stringResource(R.string.online_account_connected),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = session?.email ?: stringResource(R.string.online_account_connected),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    AssistChip(onClick = {}, label = { Text(session.providerLabel()) })
                }
            }
            FilledTonalButton(onClick = onLogout, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Icon(Icons.Filled.Logout, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.online_logout))
            }
        }
    }
}

@Composable
private fun ProfileActionsCard(onOpenUpload: () -> Unit, onOpenHome: () -> Unit) {
    ElevatedCard(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.online_upload_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.online_upload_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onOpenUpload,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(Icons.Filled.CloudUpload, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.online_tab_upload))
                }
                FilledTonalButton(
                    onClick = onOpenHome,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(stringResource(R.string.online_tab_home))
                }
            }
        }
    }
}

@Composable
private fun ProfileEditorCard(state: OnlineUiState, onUpdateProfile: (String) -> Unit) {
    val session = state.session
    var displayName by rememberSaveable(session?.userId, session?.displayName, session?.username) {
        mutableStateOf(session?.profileName.orEmpty())
    }
    ElevatedCard(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.online_profile_edit_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.online_account_profile_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text(stringResource(R.string.online_profile_name_label)) },
                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            state.profileMessage?.let { SuccessCard(message = it) }
            state.profileError?.let { ErrorCard(message = it) }
            Button(
                onClick = { onUpdateProfile(displayName) },
                enabled = !state.profileSaving && displayName.trim().length >= 2,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                if (state.profileSaving) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) else {
                    Icon(Icons.Filled.Save, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.online_profile_save))
                }
            }
        }
    }
}

@Composable
private fun PasswordSettingsCard(state: OnlineUiState, onChangePassword: (String) -> Unit) {
    val isGoogleAccount = state.session.providerLabel() == stringResource(R.string.online_provider_google)
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var confirmPasswordVisible by rememberSaveable { mutableStateOf(false) }
    val matches = password == confirmPassword
    val canSave = !state.passwordSaving && password.length >= 6 && confirmPassword.isNotBlank() && matches

    ElevatedCard(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.online_password_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (isGoogleAccount) stringResource(R.string.online_google_password_note) else stringResource(R.string.online_email_password_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            PasswordField(
                value = password,
                onValueChange = { password = it },
                label = stringResource(R.string.online_new_password),
                visible = passwordVisible,
                onVisibleChange = { passwordVisible = !passwordVisible },
                imeAction = ImeAction.Next,
                modifier = Modifier.fillMaxWidth()
            )
            PasswordField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = stringResource(R.string.online_confirm_new_password),
                visible = confirmPasswordVisible,
                onVisibleChange = { confirmPasswordVisible = !confirmPasswordVisible },
                imeAction = ImeAction.Done,
                modifier = Modifier.fillMaxWidth()
            )
            if (password.isNotBlank() && password.length < 6) InlineError(text = stringResource(R.string.online_password_minimum))
            if (confirmPassword.isNotBlank() && !matches) InlineError(text = stringResource(R.string.online_passwords_do_not_match))
            state.passwordMessage?.let { SuccessCard(message = it) }
            state.passwordError?.let { ErrorCard(message = it) }
            Button(
                onClick = { onChangePassword(password) },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                if (state.passwordSaving) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) else {
                    Icon(Icons.Filled.Key, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.online_change_password))
                }
            }
        }
    }
}

@Composable
private fun OnlineProfileAvatar(session: OnlineUserSession?, size: Int) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        val avatarUrl = session?.avatarUrl.orEmpty()
        if (avatarUrl.isNotBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = session?.profileName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(Icons.Filled.AccountCircle, contentDescription = null, modifier = Modifier.size((size * 0.7f).dp))
        }
    }
}

@Composable
private fun OnlineUserSession?.providerLabel(): String {
    val provider = this?.provider.orEmpty()
    return if (provider.contains("google", ignoreCase = true)) {
        stringResource(R.string.online_provider_google)
    } else {
        stringResource(R.string.online_provider_email)
    }
}

@Composable
private fun OnlineUploadContent(
    localAudio: List<MediaEntry>,
    selectedIds: Set<Long>,
    progress: OnlineUploadProgress,
    onToggleSelection: (MediaEntry) -> Unit,
    onClearSelection: () -> Unit,
    onUploadSelected: () -> Unit,
    onBackToProfile: () -> Unit,
    modifier: Modifier
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            ElevatedCard(shape = RoundedCornerShape(24.dp)) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.online_upload_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.online_upload_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = onBackToProfile) {
                            Text(stringResource(R.string.action_back))
                        }
                    }
                    Text(stringResource(R.string.online_upload_selected, selectedIds.size), fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onUploadSelected,
                            enabled = selectedIds.isNotEmpty() && !progress.running,
                            modifier = Modifier.weight(1f)
                        ) { Text(stringResource(R.string.online_upload_selected_action)) }
                        FilledTonalButton(
                            onClick = onClearSelection,
                            enabled = selectedIds.isNotEmpty() && !progress.running,
                            modifier = Modifier.weight(1f)
                        ) { Text(stringResource(R.string.online_clear_selection)) }
                    }
                    if (progress.running) {
                        LinearProgressIndicator(
                            progress = { if (progress.total > 0) progress.currentIndex / progress.total.toFloat() else 0f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    progress.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    progress.errors.take(3).forEach { ErrorCard(message = it) }
                }
            }
        }
        if (localAudio.isEmpty()) {
            item { EmptyOnlineCard(text = stringResource(R.string.online_upload_empty_local)) }
        }
        items(localAudio, key = { it.id }) { item ->
            UploadableLocalSongRow(
                item = item,
                selected = item.id in selectedIds,
                enabled = !progress.running,
                onToggle = { onToggleSelection(item) }
            )
        }
    }
}

@Composable
private fun OnlineSongRow(song: OnlineSongDto, onClick: () -> Unit) {
    ElevatedCard(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(58.dp).clip(RoundedCornerShape(18.dp)).background(
                    Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), MaterialTheme.colorScheme.surfaceVariant))
                ),
                contentAlignment = Alignment.Center
            ) {
                if (!song.coverUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = song.coverUrl,
                        contentDescription = song.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(Icons.Filled.LibraryMusic, contentDescription = null)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(song.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(song.artist, song.album).filter { it.isNotBlank() }.joinToString(" • ").ifBlank { stringResource(R.string.online_unknown_artist) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    formatDuration((song.durationSeconds ?: 0L) * 1000L),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onClick) {
                Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.action_play))
            }
        }
    }
}

@Composable
private fun UploadableLocalSongRow(
    item: MediaEntry,
    selected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onToggle).padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = selected, onCheckedChange = { onToggle() }, enabled = enabled)
            MediaArtwork(
                item = item,
                modifier = Modifier.size(54.dp),
                cornerRadius = 16.dp
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(
                    item.artist.ifBlank { item.album }.ifBlank { formatDuration(item.durationMs) },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        HorizontalDivider(thickness = 0.4.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: (() -> Unit)? = null) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.72f)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Text(message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            if (onRetry != null) {
                TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
            }
        }
    }
}

@Composable
private fun SuccessCard(message: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun EmptyOnlineCard(text: String) {
    ElevatedCard(shape = RoundedCornerShape(22.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Filled.CloudOff, contentDescription = null, modifier = Modifier.size(34.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
