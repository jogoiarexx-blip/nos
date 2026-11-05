package app.getknit.knit.ui.radiohub

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.getknit.knit.R
import org.koin.androidx.compose.koinViewModel

/** Fast press-and-send voice bursts (queued group attachments), NOT simultaneous live group audio. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupPttScreen(
    groupId: String,
    onBack: () -> Unit,
    onOpenRecorded: (String) -> Unit,
    viewModel: GroupPttViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        micGranted = it
    }
    val snackbars = remember { SnackbarHostState() }
    LaunchedEffect(groupId) { viewModel.open(groupId) }
    LaunchedEffect(viewModel, context) {
        viewModel.events.collect { id -> snackbars.showSnackbar(context.getString(id)) }
    }
    DisposableEffect(viewModel) { onDispose { viewModel.cancel() } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbars) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nos_group_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.radio_hub_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = state.title.ifBlank { stringResource(R.string.nos_group_title) },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(R.string.nos_group_members, state.members),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.nos_group_disclaimer),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(R.string.nos_group_floor_local),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.size(8.dp))
            if (!micGranted) {
                Button(onClick = { askMic.launch(Manifest.permission.RECORD_AUDIO) }) {
                    Text(stringResource(R.string.nos_group_allow_mic))
                }
            } else {
                val holding = state.phase == GroupPttViewModel.Phase.RECORDING
                val enabled = state.phase == GroupPttViewModel.Phase.READY || holding
                val actionLabel = if (holding) stringResource(R.string.nos_group_release) else stringResource(R.string.nos_group_hold)
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .clip(CircleShape)
                        .background(if (holding) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer)
                        .pointerInput(enabled) {
                            detectTapGestures(onPress = {
                                if (enabled) {
                                    if (holding) {
                                        viewModel.release()
                                    } else {
                                        viewModel.press()
                                        if (tryAwaitRelease()) viewModel.release() else viewModel.cancel()
                                    }
                                }
                            })
                        }
                        .semantics {
                            testTag = "nos_group_ptt"
                            contentDescription = actionLabel
                            onClick(label = actionLabel) {
                                if (enabled) {
                                    if (holding) viewModel.release() else viewModel.press()
                                }
                                true
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(40.dp))
                        Text(actionLabel, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Text(
                when (state.phase) {
                    GroupPttViewModel.Phase.LOADING -> stringResource(R.string.radio_hub_loading)
                    GroupPttViewModel.Phase.READY -> stringResource(R.string.nos_group_ready)
                    GroupPttViewModel.Phase.RECORDING -> stringResource(R.string.nos_group_recording, state.elapsedMs / 1000)
                    GroupPttViewModel.Phase.SENDING -> stringResource(R.string.nos_group_sending)
                    GroupPttViewModel.Phase.ERROR -> state.error?.let { stringResource(it) } ?: stringResource(R.string.nos_group_unavailable)
                },
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            OutlinedButton(
                onClick = { onOpenRecorded(groupId) },
                modifier = Modifier.fillMaxWidth().semantics { testTag = "nos_group_recorded" },
            ) {
                Icon(Icons.Filled.PlayCircle, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.nos_group_recorded))
            }
            Text(
                stringResource(R.string.nos_group_recorded_explanation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
