package app.getknit.knit.ui.radiohub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.getknit.knit.R
import app.getknit.knit.data.message.Conversations
import app.getknit.knit.mesh.TransportHealth
import app.getknit.knit.ui.chatlist.ChatListUiState
import app.getknit.knit.ui.chatlist.ChatListViewModel
import app.getknit.knit.ui.chatlist.ConversationRow
import org.koin.androidx.compose.koinViewModel

/**
 * A voice-first doorway into Knit, not a second call engine. Direct calls route through CallManager.
 *
 * Walkie is currently a peer-to-peer mesh call, also available here for existing DMs.
 * Group voice clips use existing encrypted group messages (store-and-forward), not
 * a real-time multi-peer call. The separate LAN live radio uses local Wi-Fi multicast. Nearby remains text-only.
 */
@Composable
fun RadioHubScreen(
    onBack: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onPickContact: () -> Unit,
    onCreateGroup: () -> Unit,
    onOpenGroupPtt: (String) -> Unit,
    onOpenLiveRadio: () -> Unit,
    onOpenYourMesh: () -> Unit,
    viewModel: ChatListViewModel = koinViewModel(),
    dialer: RadioDialViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dialingPeer by dialer.dialingPeer.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(dialer, context) {
        dialer.events.collect { event -> snackbars.showSnackbar(context.getString(event.message)) }
    }
    RadioHubContent(
        state = state,
        onBack = onBack,
        onOpenConversation = onOpenConversation,
        onCallPeer = dialer::dial,
        dialingPeer = dialingPeer,
        snackbarHost = { SnackbarHost(snackbars) },
        onPickContact = onPickContact,
        onCreateGroup = onCreateGroup,
        onOpenGroupPtt = onOpenGroupPtt,
        onOpenLiveRadio = onOpenLiveRadio,
        onOpenYourMesh = onOpenYourMesh,
        onStartMesh = viewModel::startMesh,
        onResumeMesh = viewModel::resumeMesh,
    )
}

internal fun radioPeers(conversations: List<ConversationRow>): List<ConversationRow> =
    conversations.filterNot { it.isRoom || it.isGroup }

/** Group voice messages ride the existing encrypted group-chat attachment pipeline.
 * Nearby / commons / Meshtastic rooms are not voice channels and must never be offered here.
 */


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RadioHubContent(
    state: ChatListUiState,
    onBack: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onCallPeer: (String) -> Unit,
    dialingPeer: String?,
    snackbarHost: @Composable () -> Unit = {},
    onPickContact: () -> Unit,
    onCreateGroup: () -> Unit = {},
    onOpenGroupPtt: (String) -> Unit = {},
    onOpenLiveRadio: () -> Unit = {},
    onOpenYourMesh: () -> Unit,
    onStartMesh: () -> Unit,
    onResumeMesh: () -> Unit,
) {
    val peers = radioPeers(state.conversations)
    val groups = radioGroupThreads(state.conversations)

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = false, onClick = onBack,
                    icon = { Icon(Icons.AutoMirrored.Filled.Message, contentDescription = null) },
                    label = { Text(stringResource(R.string.nos_tab_messages)) },
                )
                NavigationBarItem(
                    selected = true, onClick = {},
                    icon = { Icon(Icons.Filled.Mic, contentDescription = null) },
                    label = { Text(stringResource(R.string.nos_tab_radio)) },
                )
            }
        },
        snackbarHost = snackbarHost,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.radio_hub_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.radio_hub_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).semantics { testTag = "radio_hub" },
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "nos_logo") {
                Image(
                    painter = painterResource(R.drawable.nos_wordmark),
                    contentDescription = stringResource(R.string.app_name),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().size(height = 122.dp, width = 335.dp),
                )
            }
            item(key = "mesh_status") {
                RadioMeshCard(state, onStartMesh, onResumeMesh, onOpenYourMesh)
            }
            item(key = "live_lan_radio") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.nos_live_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.nos_live_short_explanation))
                        Button(onClick = onOpenLiveRadio, modifier = Modifier.fillMaxWidth().semantics { testTag = "nos_open_live_radio" }) {
                            Icon(Icons.Filled.Mic, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.nos_live_open))
                        }
                    }
                }
            }
            item(key = "walkie_howto") {
                RadioWalkieCard(onPickContact)
            }
            item(key = "group_title") {
                RadioGroupIntro(onCreateGroup)
            }
            if (groups.isEmpty()) {
                item(key = "empty_groups") {
                    Text(
                        stringResource(R.string.radio_group_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else {
                items(groups, key = { "radio_group_${it.id}" }) { group ->
                    RadioGroupButton(group, onOpenConversation, onOpenGroupPtt)
                }
            }
            item(key = "nearby_room") {
                OutlinedButton(
                    onClick = { onOpenConversation(Conversations.NEARBY) },
                    modifier = Modifier.fillMaxWidth().semantics { testTag = "radio_hub_nearby" },
                ) {
                    Text(stringResource(R.string.radio_hub_nearby_messages))
                }
            }
            item(key = "recent_title") {
                Text(
                    stringResource(R.string.radio_hub_recent_people),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (peers.isEmpty()) {
                item(key = "empty_peers") {
                    Text(
                        stringResource(R.string.radio_hub_empty_people),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(peers, key = { it.id }) { peer ->
                    RadioPeerButton(peer, onOpenConversation, onCallPeer, dialingPeer)
                }
            }
        }
    }
}


/** A channel here is a persistent *group chat* with store-and-forward voice clips.
 * It is explicitly NOT a real-time multi-peer walkie session.
 */
@Composable
private fun RadioGroupIntro(onCreateGroup: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Groups, contentDescription = null)
                Spacer(Modifier.size(10.dp))
                Text(
                    stringResource(R.string.radio_group_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(stringResource(R.string.radio_group_instructions), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.radio_group_async_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = onCreateGroup,
                modifier = Modifier.fillMaxWidth().semantics { testTag = "radio_group_create" },
            ) {
                Icon(Icons.Filled.Groups, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.radio_group_create))
            }
        }
    }
}

@Composable
private fun RadioGroupButton(
    group: ConversationRow,
    onOpenConversation: (String) -> Unit,
    onOpenGroupPtt: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.GraphicEq, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(group.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (group.unreadCount > 0) {
                    Text(
                        stringResource(R.string.radio_group_unread, group.unreadCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Button(
                onClick = { onOpenGroupPtt(group.id) },
                modifier = Modifier.fillMaxWidth().semantics { testTag = "nos_group_fast_${group.id}" },
            ) { Text(stringResource(R.string.nos_group_fast_ptt)) }
            OutlinedButton(
                onClick = { onOpenConversation(group.id) },
                modifier = Modifier.fillMaxWidth().semantics { testTag = "radio_group_open_${group.id}" },
            ) { Text(stringResource(R.string.nos_group_recorded)) }
        }
    }
}

@Composable
private fun RadioMeshCard(
    state: ChatListUiState,
    onStartMesh: () -> Unit,
    onResumeMesh: () -> Unit,
    onOpenYourMesh: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Hub, contentDescription = null)
                Spacer(Modifier.size(10.dp))
                Text(
                    stringResource(R.string.radio_hub_mesh_status),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            RadioMeshStatus(state)
            Text(
                stringResource(R.string.radio_hub_offline_explanation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when {
                state.meshStopped ->
                    Button(onClick = onStartMesh) {
                        Text(stringResource(R.string.radio_hub_start_mesh))
                    }
                state.pausedUntil != null ->
                    Button(onClick = onResumeMesh) {
                        Text(stringResource(R.string.radio_hub_resume_mesh))
                    }
                else ->
                    TextButton(onClick = onOpenYourMesh) {
                        Text(stringResource(R.string.radio_hub_network_details))
                    }
            }
        }
    }
}

@Composable
private fun RadioMeshStatus(state: ChatListUiState) {
    val radiosUnavailable =
        state.transportHealth == TransportHealth.Unavailable ||
            state.radioWarning == app.getknit.knit.ui.chatlist.RadioWarning.AllRadiosOff
    val status =
        when {
            state.meshStopped -> stringResource(R.string.radio_hub_stopped)
            state.pausedUntil != null -> stringResource(R.string.radio_hub_paused)
            radiosUnavailable -> stringResource(R.string.radio_hub_radios_off)
            state.isLoading -> stringResource(R.string.radio_hub_loading)
            state.neighborCount == 0 -> stringResource(R.string.radio_hub_searching)
            else ->
                pluralStringResource(
                    R.plurals.radio_hub_neighbors,
                    state.neighborCount,
                    state.neighborCount,
                )
        }
    Text(status, style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun RadioWalkieCard(onPickContact: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Mic, contentDescription = null)
                Spacer(Modifier.size(10.dp))
                Text(
                    stringResource(R.string.radio_hub_walkie_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                stringResource(R.string.radio_hub_walkie_instructions),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.radio_hub_walkie_limits),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                onClick = onPickContact,
                modifier = Modifier.fillMaxWidth().semantics { testTag = "radio_hub_pick_contact" },
            ) {
                Icon(Icons.Filled.PersonAdd, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.radio_hub_pick_contact))
            }
        }
    }
}

@Composable
private fun RadioPeerButton(
    peer: ConversationRow,
    onOpenConversation: (String) -> Unit,
    onCallPeer: (String) -> Unit,
    dialingPeer: String?,
) {
    Card(modifier = Modifier.fillMaxWidth().semantics { testTag = "radio_hub_peer" }) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(peer.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onOpenConversation(peer.id) },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.radio_hub_open_chat)) }
                Button(
                    onClick = { onCallPeer(peer.id) },
                    enabled = dialingPeer == null,
                    modifier = Modifier.weight(1f).semantics { testTag = "radio_hub_call_${peer.id}" },
                ) {
                    Icon(Icons.Filled.Mic, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.radio_hub_call_peer))
                }
            }
        }
    }
}
