package com.wrangletangle.text

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val TealAccent = Color(0xFF2DD4BF)
private val DarkSurface = Color(0xFF071613)
private val DarkBackground = Color(0xFF03110F)

private enum class WrangleScreen {
    HOME,
    GROUP_EDITOR,
    GROUP_DETAIL,
    COMPOSE,
    HANDOFF,
    COMPLETE
}

private enum class PickerTarget {
    QUICK_SEND,
    GROUP_EDITOR
}

data class ContactRecipient(
    val name: String,
    val phoneNumber: String
)

data class SavedGroup(
    val id: String,
    val name: String,
    val recipients: List<ContactRecipient>
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            WrangleTangleTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WrangleTangleScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WrangleTangleScreen() {
    val context = LocalContext.current
    var screen by remember { mutableStateOf(WrangleScreen.HOME) }
    var pickerTarget by remember { mutableStateOf(PickerTarget.QUICK_SEND) }
    var composeTitle by remember { mutableStateOf("Quick Send") }
    var message by remember { mutableStateOf("") }
    var handoffMessage by remember { mutableStateOf("") }
    var handoffIndex by remember { mutableStateOf(0) }
    var selectedGroupId by remember { mutableStateOf<String?>(null) }
    var editorGroupId by remember { mutableStateOf<String?>(null) }
    var editorGroupName by remember { mutableStateOf("") }
    val groups = remember { mutableStateListOf<SavedGroup>().apply { addAll(loadGroups(context)) } }
    val quickRecipients = remember { mutableStateListOf<ContactRecipient>() }
    val editorRecipients = remember { mutableStateListOf<ContactRecipient>() }
    val composeRecipients = remember { mutableStateListOf<ContactRecipient>() }
    val handoffRecipients = remember { mutableStateListOf<ContactRecipient>() }

    fun persistGroups() {
        saveGroups(context, groups)
    }

    fun selectedGroup(): SavedGroup? = groups.firstOrNull { it.id == selectedGroupId }

    fun addRecipients(target: PickerTarget, incoming: List<ContactRecipient>) {
        val destination = when (target) {
            PickerTarget.QUICK_SEND -> quickRecipients
            PickerTarget.GROUP_EDITOR -> editorRecipients
        }

        val newRecipients = incoming.filterNot { candidate ->
            destination.any { existing -> existing.phoneNumber == candidate.phoneNumber }
        }
        destination.addAll(newRecipients)
    }

    fun goHome() {
        screen = WrangleScreen.HOME
        message = ""
        handoffMessage = ""
        handoffIndex = 0
        composeTitle = "Quick Send"
        quickRecipients.clear()
        composeRecipients.clear()
        handoffRecipients.clear()
        selectedGroupId = null
    }

    fun startQuickSend() {
        composeTitle = "Quick Send"
        message = ""
        composeRecipients.clear()
        composeRecipients.addAll(quickRecipients)
        screen = if (quickRecipients.isEmpty()) WrangleScreen.HOME else WrangleScreen.COMPOSE
    }

    fun openGroupEditor(group: SavedGroup?) {
        editorGroupId = group?.id
        editorGroupName = group?.name.orEmpty()
        editorRecipients.clear()
        editorRecipients.addAll(group?.recipients.orEmpty())
        pickerTarget = PickerTarget.GROUP_EDITOR
        screen = WrangleScreen.GROUP_EDITOR
    }

    fun openGroupDetail(groupId: String) {
        selectedGroupId = groupId
        screen = WrangleScreen.GROUP_DETAIL
    }

    fun beginCompose(
        title: String,
        recipients: List<ContactRecipient>
    ) {
        if (recipients.isEmpty()) {
            toast(context, "Pick at least one contact.")
            return
        }
        composeTitle = title
        message = ""
        composeRecipients.clear()
        composeRecipients.addAll(recipients)
        screen = WrangleScreen.COMPOSE
    }

    fun saveEditorGroup() {
        val trimmedName = editorGroupName.trim()
        when {
            trimmedName.isBlank() -> toast(context, "Enter a group name.")
            editorRecipients.isEmpty() -> toast(context, "Add at least one contact.")
            else -> {
                val saved = SavedGroup(
                    id = editorGroupId ?: UUID.randomUUID().toString(),
                    name = trimmedName,
                    recipients = editorRecipients.toList()
                )
                val existingIndex = groups.indexOfFirst { it.id == saved.id }
                if (existingIndex >= 0) {
                    groups[existingIndex] = saved
                } else {
                    groups.add(0, saved)
                }
                persistGroups()
                selectedGroupId = saved.id
                screen = WrangleScreen.GROUP_DETAIL
            }
        }
    }

    fun deleteSelectedGroup() {
        val groupId = selectedGroupId ?: return
        groups.removeAll { it.id == groupId }
        persistGroups()
        goHome()
    }

    val contactPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val pickedRecipients = extractRecipientsFromIntent(context, result.data)
            addRecipients(pickerTarget, pickedRecipients)

            when (pickerTarget) {
                PickerTarget.QUICK_SEND -> {
                    if (quickRecipients.isNotEmpty()) {
                        startQuickSend()
                    }
                }
                PickerTarget.GROUP_EDITOR -> {
                    if (screen != WrangleScreen.GROUP_EDITOR) {
                        screen = WrangleScreen.GROUP_EDITOR
                    }
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            contactPicker.launch(buildContactPickerIntent())
        } else {
            toast(context, "Contacts permission is required to pick recipients.")
        }
    }

    fun launchPicker(target: PickerTarget) {
        pickerTarget = target
        if (hasContactsPermission(context)) {
            contactPicker.launch(buildContactPickerIntent())
        } else {
            permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    fun beginNativeHandoff() {
        when {
            message.isBlank() -> toast(context, "Enter a message first.")
            composeRecipients.isEmpty() -> toast(context, "Pick at least one contact.")
            else -> {
                handoffRecipients.clear()
                handoffRecipients.addAll(composeRecipients)
                handoffMessage = message.trim()
                handoffIndex = 0
                screen = WrangleScreen.HANDOFF
            }
        }
    }

    fun openNextThread() {
        val recipient = handoffRecipients.getOrNull(handoffIndex) ?: return
        val intent = buildSmsComposeIntent(recipient.phoneNumber, handoffMessage)

        if (intent.resolveActivity(context.packageManager) == null) {
            toast(context, "No SMS app available on this device.")
            return
        }

        context.startActivity(intent)
        handoffIndex += 1
        if (handoffIndex >= handoffRecipients.size) {
            screen = WrangleScreen.COMPLETE
        }
    }

    val screenTitle = when (screen) {
        WrangleScreen.HOME -> "WrangleTangle Text"
        WrangleScreen.GROUP_EDITOR -> if (editorGroupId == null) "New Group" else "Edit Group"
        WrangleScreen.GROUP_DETAIL -> selectedGroup()?.name ?: "Group"
        WrangleScreen.COMPOSE -> composeTitle
        WrangleScreen.HANDOFF -> "Private SMS Handoff"
        WrangleScreen.COMPLETE -> "Ready in SMS App"
    }

    val screenSubtitle = when (screen) {
        WrangleScreen.HOME -> "Create named groups and send one message privately to everyone in them"
        WrangleScreen.GROUP_EDITOR -> "Saved groups are reusable recipient lists, not group chats"
        WrangleScreen.GROUP_DETAIL -> "Each person in this group gets their own private text"
        WrangleScreen.COMPOSE -> "Write once. Open private SMS threads one by one."
        WrangleScreen.HANDOFF -> "Use your normal SMS app without turning this into a group thread"
        WrangleScreen.COMPLETE -> "Each compose screen was opened separately in your SMS app"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(screenTitle)
                        Text(
                            text = screenSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (screen) {
                WrangleScreen.HOME -> {
                    item {
                        PrimaryCard(title = "Start") {
                            Text(
                                text = "Saved groups are named recipient lists. They are not chat rooms or shared threads.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = { launchPicker(PickerTarget.QUICK_SEND) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = TealAccent,
                                        contentColor = Color.Black
                                    )
                                ) {
                                    Icon(Icons.Outlined.People, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("New Message")
                                }
                                OutlinedButton(onClick = { openGroupEditor(null) }) {
                                    Icon(Icons.AutoMirrored.Outlined.PlaylistAdd, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("New Group")
                                }
                            }
                        }
                    }

                    item {
                        PrimaryCard(title = "My Groups") {
                            if (groups.isEmpty()) {
                                Text(
                                    text = "No saved groups yet. Create one once, then reuse it whenever you need to send the same message privately.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Text(
                                    text = "Tap a group to review people, edit the list, or send a message.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    items(groups, key = { it.id }) { group ->
                        GroupCard(
                            group = group,
                            onOpen = { openGroupDetail(group.id) }
                        )
                    }
                }

                WrangleScreen.GROUP_EDITOR -> {
                    item {
                        PrimaryCard(title = if (editorGroupId == null) "Create Group" else "Edit Group") {
                            OutlinedTextField(
                                value = editorGroupName,
                                onValueChange = { editorGroupName = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Group name") },
                                supportingText = {
                                    Text("Examples: Red Rocks Crew, Family, Pickup A, Work Leads")
                                }
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = { launchPicker(PickerTarget.GROUP_EDITOR) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = TealAccent,
                                        contentColor = Color.Black
                                    )
                                ) {
                                    Icon(Icons.Outlined.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(if (editorRecipients.isEmpty()) "Add People" else "Add another")
                                }
                                OutlinedButton(
                                    onClick = {
                                        if (editorGroupId == null) {
                                            goHome()
                                        } else {
                                            openGroupDetail(editorGroupId!!)
                                        }
                                    }
                                ) {
                                    Text("Cancel")
                                }
                            }
                        }
                    }

                    if (editorRecipients.isNotEmpty()) {
                        item {
                            StageFooterCard(
                                text = "${editorRecipients.size} contact${if (editorRecipients.size == 1) "" else "s"} in this group"
                            ) {
                                saveEditorGroup()
                            }
                        }
                    }

                    items(editorRecipients, key = { it.phoneNumber }) { recipient ->
                        RecipientEditorCard(
                            recipient = recipient,
                            onRemove = {
                                editorRecipients.removeAll { it.phoneNumber == recipient.phoneNumber }
                            }
                        )
                    }

                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = ::saveEditorGroup,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = TealAccent,
                                    contentColor = Color.Black
                                )
                            ) {
                                Text("Save Group")
                            }
                        }
                    }
                }

                WrangleScreen.GROUP_DETAIL -> {
                    val group = selectedGroup()
                    if (group == null) {
                        item {
                            PrimaryCard(title = "Group not found") {
                                Text(
                                    text = "This group is no longer available.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                OutlinedButton(onClick = ::goHome) {
                                    Text("Back home")
                                }
                            }
                        }
                    } else {
                        item {
                            PrimaryCard(title = group.name) {
                                Text(
                                    text = "This is a saved recipient list. Each person gets their own private message and replies come back separately.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Button(
                                        onClick = { beginCompose(group.name, group.recipients) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = TealAccent,
                                            contentColor = Color.Black
                                        )
                                    ) {
                                        Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Send Message")
                                    }
                                    OutlinedButton(onClick = { openGroupEditor(group) }) {
                                        Icon(Icons.Outlined.Edit, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Edit Group")
                                    }
                                    OutlinedButton(onClick = ::deleteSelectedGroup) {
                                        Icon(Icons.Outlined.Delete, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Delete")
                                    }
                                }
                            }
                        }

                        items(group.recipients, key = { it.phoneNumber }) { recipient ->
                            RecipientCard(recipient = recipient)
                        }
                    }
                }

                WrangleScreen.COMPOSE -> {
                    item {
                        PrimaryCard(title = "Sending to") {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                composeRecipients.forEach { recipient ->
                                    FilterChip(
                                        selected = true,
                                        onClick = {},
                                        label = { Text(recipient.name) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            OutlinedButton(
                                onClick = {
                                    if (selectedGroupId != null) {
                                        screen = WrangleScreen.GROUP_DETAIL
                                    } else {
                                        screen = WrangleScreen.HOME
                                    }
                                }
                            ) {
                                Text(if (selectedGroupId != null) "Back to group" else "Back home")
                            }
                        }
                    }

                    item {
                        PrimaryCard(title = "Message") {
                            OutlinedTextField(
                                value = message,
                                onValueChange = { message = it },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 6,
                                maxLines = 10,
                                label = { Text("Write the text once") },
                                supportingText = {
                                    Text("WrangleTangle opens your SMS app one person at a time so you stay out of group chats.")
                                }
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = ::beginNativeHandoff,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = TealAccent,
                                    contentColor = Color.Black
                                )
                            ) {
                                Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Open in SMS app")
                            }
                        }
                    }
                }

                WrangleScreen.HANDOFF -> {
                    val nextRecipient = handoffRecipients.getOrNull(handoffIndex)
                    val openedCount = handoffIndex.coerceAtMost(handoffRecipients.size)

                    item {
                        PrimaryCard(title = "Private SMS Handoff") {
                            Text(
                                text = "WrangleTangle stays contacts-only. It opens your normal SMS app one recipient at a time with the message prefilled.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Opened $openedCount of ${handoffRecipients.size}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (nextRecipient != null) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Next up: ${nextRecipient.name}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = nextRecipient.phoneNumber,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Tap the button below, send the message in your SMS app, then come back here for the next person.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = ::openNextThread,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = TealAccent,
                                        contentColor = Color.Black
                                    )
                                ) {
                                    Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(if (nextRecipient == null) "Done" else "Open next thread")
                                }
                                OutlinedButton(onClick = { screen = WrangleScreen.COMPOSE }) {
                                    Text("Edit message")
                                }
                            }
                        }
                    }

                    items(handoffRecipients, key = { it.phoneNumber }) { recipient ->
                        RecipientStatusCard(
                            recipient = recipient,
                            status = when {
                                handoffRecipients.indexOf(recipient) < handoffIndex -> "Opened in SMS app"
                                recipient.phoneNumber == nextRecipient?.phoneNumber -> "Next"
                                else -> "Queued"
                            }
                        )
                    }
                }

                WrangleScreen.COMPLETE -> {
                    item {
                        PrimaryCard(title = "Sent to ${handoffRecipients.size} people") {
                            Text(
                                text = "Each person received their own private SMS compose screen. Replies come back separately in your normal messaging app.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = handoffMessage,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = ::goHome,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = TealAccent,
                                        contentColor = Color.Black
                                    )
                                ) {
                                    Text("Send another")
                                }
                                OutlinedButton(
                                    onClick = {
                                        handoffIndex = 0
                                        screen = WrangleScreen.HANDOFF
                                    }
                                ) {
                                    Text("Open again")
                                }
                            }
                        }
                    }

                    items(handoffRecipients, key = { it.phoneNumber }) { recipient ->
                        RecipientStatusCard(
                            recipient = recipient,
                            status = "Opened in SMS app"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PrimaryCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            content()
        }
    }
}

@Composable
private fun StageFooterCard(
    text: String,
    onContinue: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = text, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onContinue) {
                Text("Continue")
            }
        }
    }
}

@Composable
private fun GroupCard(
    group: SavedGroup,
    onOpen: () -> Unit
) {
    Card(
        modifier = Modifier.clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = group.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${group.recipients.size} contact${if (group.recipients.size == 1) "" else "s"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = group.recipients.joinToString(limit = 3, separator = " • ") { it.name },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RecipientCard(recipient: ContactRecipient) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = recipient.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = recipient.phoneNumber,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RecipientEditorCard(
    recipient: ContactRecipient,
    onRemove: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = recipient.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = recipient.phoneNumber,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(onClick = onRemove) {
                Icon(Icons.Outlined.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Remove")
            }
        }
    }
}

@Composable
private fun RecipientStatusCard(
    recipient: ContactRecipient,
    status: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = recipient.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = recipient.phoneNumber,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = TealAccent
                )
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun WrangleTangleTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = TealAccent,
        secondary = TealAccent,
        tertiary = TealAccent,
        background = DarkBackground,
        surface = DarkSurface,
        surfaceVariant = Color(0xFF10211D),
        onPrimary = Color.Black
    )

    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}

private fun buildContactPickerIntent(): Intent {
    return Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI).apply {
        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
    }
}

private fun buildSmsComposeIntent(phoneNumber: String, message: String): Intent {
    val destination = Uri.encode(phoneNumber)
    return Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$destination")).apply {
        putExtra("sms_body", message)
    }
}

private fun extractRecipientsFromIntent(context: Context, data: Intent?): List<ContactRecipient> {
    val uris = buildList {
        data?.data?.let(::add)
        data?.clipData?.let { clip ->
            for (index in 0 until clip.itemCount) {
                clip.getItemAt(index).uri?.let(::add)
            }
        }
    }

    return uris.mapNotNull { uri -> resolveRecipient(context, uri) }
}

private fun resolveRecipient(context: Context, uri: Uri): ContactRecipient? {
    val projection = arrayOf(
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        ContactsContract.CommonDataKinds.Phone.NUMBER
    )

    context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

        if (cursor.moveToFirst() && nameIndex >= 0 && numberIndex >= 0) {
            val name = cursor.getString(nameIndex).orEmpty().ifBlank { "Unnamed contact" }
            val number = cursor.getString(numberIndex)?.trim().orEmpty()
            if (number.isNotBlank()) {
                return ContactRecipient(name = name, phoneNumber = number)
            }
        }
    }

    return null
}

private fun hasContactsPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_CONTACTS
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
}

private fun loadGroups(context: Context): List<SavedGroup> {
    val raw = context
        .getSharedPreferences("wt_text_groups", Context.MODE_PRIVATE)
        .getString("saved_groups", null)
        ?: return emptyList()

    return runCatching {
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id")
                val name = item.optString("name")
                val recipientsArray = item.optJSONArray("recipients") ?: JSONArray()
                val recipients = buildList {
                    for (recipientIndex in 0 until recipientsArray.length()) {
                        val recipient = recipientsArray.optJSONObject(recipientIndex) ?: continue
                        val displayName = recipient.optString("name")
                        val phoneNumber = recipient.optString("phoneNumber")
                        if (phoneNumber.isNotBlank()) {
                            add(
                                ContactRecipient(
                                    name = displayName.ifBlank { "Unnamed contact" },
                                    phoneNumber = phoneNumber
                                )
                            )
                        }
                    }
                }
                if (id.isNotBlank() && name.isNotBlank() && recipients.isNotEmpty()) {
                    add(SavedGroup(id = id, name = name, recipients = recipients))
                }
            }
        }
    }.getOrDefault(emptyList())
}

private fun saveGroups(
    context: Context,
    groups: List<SavedGroup>
) {
    val array = JSONArray()
    groups.forEach { group ->
        val recipients = JSONArray()
        group.recipients.forEach { recipient ->
            recipients.put(
                JSONObject()
                    .put("name", recipient.name)
                    .put("phoneNumber", recipient.phoneNumber)
            )
        }
        array.put(
            JSONObject()
                .put("id", group.id)
                .put("name", group.name)
                .put("recipients", recipients)
        )
    }

    context
        .getSharedPreferences("wt_text_groups", Context.MODE_PRIVATE)
        .edit()
        .putString("saved_groups", array.toString())
        .apply()
}

private fun toast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
