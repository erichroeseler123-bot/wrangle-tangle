package com.wrangletangle.text

import android.Manifest
import android.app.Activity
import android.content.ClipData
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
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

private enum class MessageStylePreset(
    val label: String,
    val headlineColor: Color,
    val bodyColor: Color,
    val cardColor: Color,
    val borderColor: Color
) {
    CLEAN(
        label = "Clean",
        headlineColor = Color(0xFFF8F7F2),
        bodyColor = Color(0xFFC7D1CC),
        cardColor = Color(0xFF10211D),
        borderColor = Color(0x332DD4BF)
    ),
    LOUD(
        label = "Loud",
        headlineColor = Color(0xFFFFF4D1),
        bodyColor = Color(0xFFFFDAB2),
        cardColor = Color(0xFF3A1500),
        borderColor = Color(0x66FF8C42)
    ),
    NIGHT(
        label = "Night",
        headlineColor = Color(0xFFF8F7F2),
        bodyColor = Color(0xFFB8BBFF),
        cardColor = Color(0xFF11142B),
        borderColor = Color(0x664D6BFF)
    ),
    FLYER(
        label = "Flyer",
        headlineColor = Color(0xFF101010),
        bodyColor = Color(0xFF252525),
        cardColor = Color(0xFFFFD54A),
        borderColor = Color(0x66FFF0A0)
    )
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
    var headline by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedStyle by remember { mutableStateOf(MessageStylePreset.CLEAN) }
    var handoffIndex by remember { mutableStateOf(0) }
    var selectedGroupId by remember { mutableStateOf<String?>(null) }
    var editorGroupId by remember { mutableStateOf<String?>(null) }
    var editorGroupName by remember { mutableStateOf("") }
    var showCanvaHelp by remember { mutableStateOf(false) }
    var testPhoneNumber by remember { mutableStateOf(loadTestPhoneNumber(context)) }
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

    fun resetComposeDraft() {
        headline = ""
        body = ""
        selectedImageUri = null
        selectedStyle = MessageStylePreset.CLEAN
        handoffIndex = 0
        handoffRecipients.clear()
    }

    fun goHome() {
        screen = WrangleScreen.HOME
        composeTitle = "Quick Send"
        quickRecipients.clear()
        composeRecipients.clear()
        selectedGroupId = null
        resetComposeDraft()
    }

    fun startQuickSend() {
        composeTitle = "Quick Send"
        resetComposeDraft()
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

    fun beginCompose(title: String, recipients: List<ContactRecipient>) {
        if (recipients.isEmpty()) {
            toast(context, "Pick at least one contact.")
            return
        }
        composeTitle = title
        resetComposeDraft()
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

    fun persistTestPhoneNumber() {
        saveTestPhoneNumber(context, testPhoneNumber)
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

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        selectedImageUri = uri
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
            headline.isBlank() && body.isBlank() -> toast(context, "Add a headline or body first.")
            composeRecipients.isEmpty() -> toast(context, "Pick at least one contact.")
            else -> {
                handoffRecipients.clear()
                handoffRecipients.addAll(composeRecipients)
                handoffIndex = 0
                screen = WrangleScreen.HANDOFF
            }
        }
    }

    fun testSendToMyself() {
        when {
            headline.isBlank() && body.isBlank() -> toast(context, "Add a headline or body first.")
            else -> {
                val selfNumber = testPhoneNumber.trim()
                if (selfNumber.isBlank()) {
                    toast(context, "Enter your test number first.")
                    return
                }

                val selfRecipient = ContactRecipient(name = "You", phoneNumber = selfNumber)
                val formattedMessage = buildFormattedMessage(
                    recipient = selfRecipient,
                    headline = headline,
                    body = body
                )
                val intent = buildMessagingIntent(
                    context = context,
                    recipient = selfRecipient,
                    formattedMessage = formattedMessage,
                    imageUri = selectedImageUri
                )

                if (intent.resolveActivity(context.packageManager) == null) {
                    toast(context, "No messaging app available on this device.")
                    return
                }

                runCatching {
                    context.startActivity(intent)
                    toast(context, "Test message opened. Tap Send in your messaging app.")
                }.onFailure {
                    toast(context, "Could not open your messaging app for the test send.")
                }
            }
        }
    }

    fun openNextThread() {
        val recipient = handoffRecipients.getOrNull(handoffIndex) ?: return
        val formattedMessage = buildFormattedMessage(
            recipient = recipient,
            headline = headline,
            body = body
        )
        val intent = buildMessagingIntent(
            context = context,
            recipient = recipient,
            formattedMessage = formattedMessage,
            imageUri = selectedImageUri
        )

        if (intent.resolveActivity(context.packageManager) == null) {
            toast(context, "No messaging app available on this device.")
            return
        }

        runCatching {
            context.startActivity(intent)
            toast(context, "Message opened for ${recipient.name}. Tap Send in your messaging app.")
        }.onFailure {
                toast(context, "Could not open your messaging app for this recipient.")
                return
            }
        handoffIndex += 1
        if (handoffIndex >= handoffRecipients.size) {
            screen = WrangleScreen.COMPLETE
        }
    }

    fun openCanvaOrExplain() {
        val canvaIntent = context.packageManager.getLaunchIntentForPackage("com.canva.editor")
        if (canvaIntent != null) {
            context.startActivity(canvaIntent)
        } else {
            toast(context, "Canva is not installed. Use the helper steps below.")
        }
    }

    val sampleRecipient = composeRecipients.firstOrNull()
        ?: handoffRecipients.firstOrNull()
        ?: ContactRecipient(name = "Sarah", phoneNumber = "(555) 555-5555")

    val screenTitle = when (screen) {
        WrangleScreen.HOME -> "WrangleTangle Text"
        WrangleScreen.GROUP_EDITOR -> if (editorGroupId == null) "New Group" else "Edit Group"
        WrangleScreen.GROUP_DETAIL -> selectedGroup()?.name ?: "Group"
        WrangleScreen.COMPOSE -> composeTitle
        WrangleScreen.HANDOFF -> "Private SMS Handoff"
        WrangleScreen.COMPLETE -> "Ready in Messages"
    }

    val screenSubtitle = when (screen) {
        WrangleScreen.HOME -> "Create named groups and send one message privately to everyone in them"
        WrangleScreen.GROUP_EDITOR -> "Saved groups are reusable recipient lists, not group chats"
        WrangleScreen.GROUP_DETAIL -> "Each person in this group gets their own private text"
        WrangleScreen.COMPOSE -> "Build a better-looking message without turning this into a design app"
        WrangleScreen.HANDOFF -> "Open one private compose screen at a time in your messaging app"
        WrangleScreen.COMPLETE -> "Each compose screen was opened separately in your messaging app"
    }

    if (showCanvaHelp) {
        ModalBottomSheet(
            onDismissRequest = { showCanvaHelp = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "From Canva",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Best lightweight flow right now:",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("1. Build your image in Canva.")
                Text("2. Save or export it to your phone.")
                Text("3. Come back here and tap Add Photo.")
                Text(
                    text = "Recipients do not need Canva. They just receive the image in a normal text or MMS.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            openCanvaOrExplain()
                            showCanvaHelp = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TealAccent,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Canva")
                    }
                    OutlinedButton(onClick = { showCanvaHelp = false }) {
                        Text("Close")
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
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
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = { launchPicker(PickerTarget.QUICK_SEND) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = TealAccent,
                                        contentColor = Color.Black
                                    )
                                ) {
                                    Icon(Icons.Outlined.People, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("New Message")
                                }
                                OutlinedButton(
                                    onClick = { openGroupEditor(null) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
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
                        GroupCard(group = group, onOpen = { openGroupDetail(group.id) })
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
                                        if (editorGroupId == null) goHome() else openGroupDetail(editorGroupId!!)
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
                                text = "${editorRecipients.size} contact${if (editorRecipients.size == 1) "" else "s"} in this group",
                                actionLabel = "Save group",
                                onContinue = ::saveEditorGroup
                            )
                        }
                    }

                    items(editorRecipients, key = { it.phoneNumber }) { recipient ->
                        RecipientEditorCard(
                            recipient = recipient,
                            onRemove = { editorRecipients.removeAll { it.phoneNumber == recipient.phoneNumber } }
                        )
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
                        PrimaryCard(title = "To") {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                composeRecipients.forEach { recipient ->
                                    FilterChip(selected = true, onClick = {}, label = { Text(recipient.name) })
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            OutlinedButton(
                                onClick = {
                                    if (selectedGroupId != null) screen = WrangleScreen.GROUP_DETAIL else screen = WrangleScreen.HOME
                                }
                            ) {
                                Text(if (selectedGroupId != null) "Back to group" else "Back home")
                            }
                        }
                    }

                    item {
                        PrimaryCard(title = "Headline") {
                            OutlinedTextField(
                                value = headline,
                                onValueChange = { headline = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Title or subject") },
                                supportingText = {
                                    Text("Examples: RED ROCKS TONIGHT, DINNER UPDATE, PICKUP PLAN")
                                }
                            )
                        }
                    }

                    item {
                        PrimaryCard(title = "Body") {
                            OutlinedTextField(
                                value = body,
                                onValueChange = { body = it },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 5,
                                maxLines = 9,
                                label = { Text("Body text") },
                                supportingText = {
                                    Text("Use ::firstname:: if you want the preview to personalize the greeting.")
                                }
                            )
                        }
                    }

                    item {
                        PrimaryCard(title = "My Test Number") {
                            OutlinedTextField(
                                value = testPhoneNumber,
                                onValueChange = {
                                    testPhoneNumber = it
                                    persistTestPhoneNumber()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Phone number for test sends") },
                                supportingText = {
                                    Text("Saved on this device and used by Test Send to Myself.")
                                }
                            )
                        }
                    }

                    item {
                        PrimaryCard(title = "Image") {
                            Text(
                                text = "Optional hero image. This tries to hand off as MMS when your messaging app supports it.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = { imagePicker.launch("image/*") },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = TealAccent,
                                        contentColor = Color.Black
                                    )
                                ) {
                                    Icon(Icons.Outlined.Image, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(if (selectedImageUri == null) "Add Photo" else "Change Photo")
                                }
                                OutlinedButton(onClick = { showCanvaHelp = true }) {
                                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("From Canva")
                                }
                                if (selectedImageUri != null) {
                                    OutlinedButton(onClick = { selectedImageUri = null }) {
                                        Icon(Icons.Outlined.Delete, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Remove")
                                    }
                                }
                            }
                            if (selectedImageUri != null) {
                                Spacer(modifier = Modifier.height(16.dp))
                                AsyncImage(
                                    model = selectedImageUri,
                                    contentDescription = "Selected hero image",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "Want to style the image first? Tap From Canva, export to your phone, then attach it here.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    item {
                        PrimaryCard(title = "Style") {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MessageStylePreset.entries.forEach { preset ->
                                    FilterChip(
                                        selected = selectedStyle == preset,
                                        onClick = { selectedStyle = preset },
                                        label = { Text(preset.label) }
                                    )
                                }
                            }
                        }
                    }

                    item {
                        PrimaryCard(title = "Live Preview") {
                            PreviewCard(
                                recipient = sampleRecipient,
                                headline = headline,
                                body = body,
                                imageUri = selectedImageUri,
                                style = selectedStyle
                            )
                        }
                    }

                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = ::beginNativeHandoff,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = TealAccent,
                                    contentColor = Color.Black
                                )
                            ) {
                                Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Open first message for ${composeRecipients.size} people")
                            }

                            OutlinedButton(
                                onClick = ::testSendToMyself,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Outlined.PhoneAndroid, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Open Test Message to Myself")
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
                                text = "WrangleTangle opens your messaging app one recipient at a time with the formatted message prefilled. You still need to tap Send in your messaging app.",
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

                    item {
                        PrimaryCard(title = "Current Layout") {
                            PreviewCard(
                                recipient = nextRecipient ?: sampleRecipient,
                                headline = headline,
                                body = body,
                                imageUri = selectedImageUri,
                                style = selectedStyle
                            )
                        }
                    }

                    items(handoffRecipients, key = { it.phoneNumber }) { recipient ->
                        RecipientStatusCard(
                            recipient = recipient,
                            status = when {
                                handoffRecipients.indexOf(recipient) < handoffIndex -> "Opened in Messages"
                                recipient.phoneNumber == nextRecipient?.phoneNumber -> "Next"
                                else -> "Queued"
                            }
                        )
                    }
                }

                WrangleScreen.COMPLETE -> {
                    item {
                        PrimaryCard(title = "Ready in Messages") {
                            Text(
                                text = "WrangleTangle opened ${handoffRecipients.size} private compose screens through your messaging app.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            PreviewCard(
                                recipient = handoffRecipients.firstOrNull() ?: sampleRecipient,
                                headline = headline,
                                body = body,
                                imageUri = selectedImageUri,
                                style = selectedStyle
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
                        RecipientStatusCard(recipient = recipient, status = "Opened")
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
    actionLabel: String,
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
                Text(actionLabel)
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
                    tint = if (status == "Queued") MaterialTheme.colorScheme.onSurfaceVariant else TealAccent
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
private fun PreviewCard(
    recipient: ContactRecipient,
    headline: String,
    body: String,
    imageUri: Uri?,
    style: MessageStylePreset
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = style.cardColor),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, style.borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (imageUri != null) {
                AsyncImage(
                    model = imageUri,
                    contentDescription = "Preview image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentScale = ContentScale.Crop
                )
            }
            Text(
                text = (headline.ifBlank { "TONIGHT'S PLAN" }).uppercase(),
                color = style.headlineColor,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black
            )
            Text(
                text = applyPersonalization(
                    body.ifBlank { "Hey ::firstname::, leaving at 6:15. Meet at the south lot bar." },
                    recipient
                ),
                color = style.bodyColor,
                style = MaterialTheme.typography.bodyLarge
            )
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

private fun buildMessagingIntent(
    context: Context,
    recipient: ContactRecipient,
    formattedMessage: String,
    imageUri: Uri?
): Intent {
    if (imageUri == null) {
        return Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(recipient.phoneNumber)}")).apply {
            putExtra("sms_body", formattedMessage)
        }
    }

    return Intent(Intent.ACTION_SEND).apply {
        type = "image/*"
        putExtra("address", recipient.phoneNumber)
        putExtra("sms_body", formattedMessage)
        putExtra(Intent.EXTRA_STREAM, imageUri)
        clipData = ClipData.newUri(context.contentResolver, "message-image", imageUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

private fun buildFormattedMessage(
    recipient: ContactRecipient,
    headline: String,
    body: String
): String {
    val safeHeadline = headline.trim()
    val safeBody = applyPersonalization(body.trim(), recipient)
    return buildString {
        if (safeHeadline.isNotBlank()) {
            append(safeHeadline.uppercase())
        }
        if (safeHeadline.isNotBlank() && safeBody.isNotBlank()) {
            append("\n\n")
        }
        if (safeBody.isNotBlank()) {
            append(safeBody)
        }
    }.ifBlank { "Hi ${recipient.name}," }
}

private fun applyPersonalization(
    text: String,
    recipient: ContactRecipient
): String {
    return text.replace("::firstname::", recipient.name.substringBefore(" ").trim())
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

private fun loadTestPhoneNumber(context: Context): String {
    return context
        .getSharedPreferences("wt_text_groups", Context.MODE_PRIVATE)
        .getString("test_phone_number", "")
        .orEmpty()
}

private fun saveTestPhoneNumber(context: Context, phoneNumber: String) {
    context
        .getSharedPreferences("wt_text_groups", Context.MODE_PRIVATE)
        .edit()
        .putString("test_phone_number", phoneNumber)
        .apply()
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
