package app.getknit.knit.ui.radiohub

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.getknit.knit.R

/** Independent live group PTT via local Wi-Fi IP multicast. Not the Knit/LoRa Mesh transport. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanRadioScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val engine = remember(context) { LanRadioEngine(context) }
    val state by engine.state.collectAsStateWithLifecycle()
    var channel by remember { mutableStateOf("geral") }
    var password by remember { mutableStateOf("") } // Never save a radio password to SavedState.
    var micGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val requestMicrophone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { micGranted = it }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, engine) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) engine.leave()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            engine.close()
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.nos_live_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.radio_hub_back))
                }
            },
        )
    }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Wifi, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.nos_live_transport), fontWeight = FontWeight.SemiBold)
                    }
                    Text(stringResource(R.string.nos_live_explanation), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.nos_live_security), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (!state.joined) {
                OutlinedTextField(
                    value = channel, onValueChange = { channel = it },
                    label = { Text(stringResource(R.string.nos_live_channel)) },
                    singleLine = true, enabled = !state.busy, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text(stringResource(R.string.nos_live_password)) },
                    singleLine = true, enabled = !state.busy, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions.Default,
                )
                Button(
                    onClick = { engine.join(channel, password); password = "" },
                    enabled = !state.busy && LanRadioWire.validCredentials(LanRadioWire.normalizedChannel(channel), password),
                    modifier = Modifier.fillMaxWidth().semantics { testTag = "nos_join_live_channel" },
                ) { Text(if (state.busy) stringResource(R.string.nos_live_connecting) else stringResource(R.string.nos_live_join)) }
                Text(stringResource(R.string.nos_live_same_credentials), style = MaterialTheme.typography.bodySmall)
            } else {
                Text(stringResource(R.string.nos_live_connected, state.channel),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.nos_live_peers, state.peers))
                Text(
                    when {
                        state.transmitting -> stringResource(R.string.nos_live_speaking)
                        state.speaker != null -> stringResource(R.string.nos_live_listening, state.speaker!!)
                        state.peers == 0 -> stringResource(R.string.nos_live_alone)
                        else -> stringResource(R.string.nos_live_ready)
                    },
                    style = MaterialTheme.typography.titleSmall,
                )
                if (!micGranted) {
                    Button(onClick = { requestMicrophone.launch(Manifest.permission.RECORD_AUDIO) },
                        modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.nos_group_allow_mic))
                    }
                } else {
                    val canTalk = state.speaker == null
                    Box(
                        modifier = Modifier.align(Alignment.CenterHorizontally).size(180.dp)
                            .clip(CircleShape)
                            .background(if (state.transmitting) MaterialTheme.colorScheme.tertiaryContainer
                                else MaterialTheme.colorScheme.primaryContainer)
                            .pointerInput(engine, canTalk, micGranted) {
                                detectTapGestures(onPress = {
                                    if (canTalk) {
                                        engine.press()
                                        tryAwaitRelease()
                                        engine.release()
                                    }
                                })
                            }
                            .semantics { testTag = "nos_live_ptt" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(52.dp))
                            Text(
                                if (state.transmitting) stringResource(R.string.nos_live_release)
                                else stringResource(R.string.nos_live_hold),
                                textAlign = TextAlign.Center, fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                OutlinedButton(onClick = engine::leave, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.nos_live_leave))
                }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            Text(stringResource(R.string.nos_live_limit), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
