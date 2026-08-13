package com.chartmann.knightfall.ui.settings

import android.content.Context
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chartmann.knightfall.AppContainer
import com.chartmann.knightfall.data.feedback.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SupportCard(container: AppContainer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val reports by container.feedbackRepo.bugReports.collectAsState(initial = emptyList())

    var showReportDialog by remember { mutableStateOf(false) }
    var selectedReport by remember { mutableStateOf<BugReport?>(null) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Support & Feedback",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Report bugs or suggest improvements directly to the project repository.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { showReportDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Report a Problem")
            }

            if (reports.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Submitted Reports",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                reports.forEach { report ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedReport = report }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = report.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "#${report.number} • ${report.createdAt}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }

                        val isOpen = report.status.lowercase() == "open"
                        val badgeColor = if (isOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = badgeColor.copy(alpha = 0.15f),
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text(
                                text = report.status.uppercase(),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = badgeColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    if (showReportDialog) {
        ReportBugDialog(
            container = container,
            onDismiss = { showReportDialog = false }
        )
    }

    if (selectedReport != null) {
        IssueDetailsDialog(
            container = container,
            report = selectedReport!!,
            onDismiss = { selectedReport = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportBugDialog(
    container: AppContainer,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var includeDiagnostics by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    // Always true now — the relay is a fixed public Worker URL, not per-install config.
    val configValid = true

    val photoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> selectedImageUri = uri }
    )

    val imageBitmap = rememberUriImage(selectedImageUri, context)

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        content = {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Report a Problem") },
                        navigationIcon = {
                            IconButton(
                                onClick = onDismiss,
                                enabled = !isSubmitting
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 20.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Safety Warning Banner
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Warning: Your report will be submitted to this app’s GitHub issue tracker. Do not include passwords, private keys, medical information, financial information, or anything you do not want visible to the repository maintainers. If this repository is public, your report may be publicly visible.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    if (!configValid) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Configuration Error: GitHub project API details are missing. Submissions are disabled.",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title / Subject *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isSubmitting
                    )

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description / What happened *") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        enabled = !isSubmitting
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = includeDiagnostics,
                            onCheckedChange = { includeDiagnostics = it },
                            enabled = !isSubmitting
                        )
                        Text(
                            text = "Include phone/app diagnostics",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.clickable { if (!isSubmitting) includeDiagnostics = !includeDiagnostics }
                        )
                    }

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Your Name (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isSubmitting
                    )

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Your Email (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isSubmitting
                    )

                    // Screenshot Section
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                photoLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            enabled = !isSubmitting
                        ) {
                            Text("Attach Screenshot / Image")
                        }

                        if (selectedImageUri != null) {
                            TextButton(
                                onClick = { selectedImageUri = null },
                                enabled = !isSubmitting
                            ) {
                                Text("Clear Attachment", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    if (imageBitmap != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Image(
                                bitmap = imageBitmap,
                                contentDescription = "Attached screenshot preview",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            enabled = !isSubmitting
                        ) {
                            Text("Cancel")
                        }

                        Button(
                            onClick = {
                                if (title.isBlank() || description.isBlank()) {
                                    Toast.makeText(context, "Please fill in all required fields", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                scope.launch {
                                    isSubmitting = true
                                    try {
                                        var screenshotUrl: String? = null
                                        if (selectedImageUri != null) {
                                            val base64 = withContext(Dispatchers.IO) {
                                                ImageUploadHelper.uriToBase64(context, selectedImageUri!!)
                                            }
                                            val filename = ImageUploadHelper.generateUniqueFilename()
                                            val uploadRequest = UploadAssetRequest(
                                                filename = filename,
                                                contentBase64 = base64
                                            )
                                            val uploadResult = withContext(Dispatchers.IO) {
                                                container.githubApi.uploadAsset(uploadRequest)
                                            }
                                            screenshotUrl = uploadResult.content?.download_url
                                        }

                                        val diagInfo = if (includeDiagnostics) DiagnosticsHelper.getDiagnosticsMarkdown(context) else ""

                                        val bodyBuilder = StringBuilder()
                                        bodyBuilder.append("## Description\n\n$description\n\n")
                                        bodyBuilder.append("## Contact Info\n\n")
                                        bodyBuilder.append("- Name: ${name.ifBlank() { "Not provided" }}\n")
                                        bodyBuilder.append("- Email: ${email.ifBlank() { "Not provided" }}\n\n")

                                        if (screenshotUrl != null) {
                                            bodyBuilder.append("## Attachment\n\n![Screenshot]($screenshotUrl)\n\n")
                                        }

                                        if (diagInfo.isNotEmpty()) {
                                            bodyBuilder.append(diagInfo)
                                        }

                                        val issueRequest = CreateIssueRequest(
                                            title = "[Feedback] $title",
                                            body = bodyBuilder.toString()
                                        )

                                        val createdIssue = withContext(Dispatchers.IO) {
                                            container.githubApi.createIssue(issueRequest)
                                        }

                                        val bugReport = BugReport(
                                            number = createdIssue.number,
                                            title = createdIssue.title,
                                            status = createdIssue.state,
                                            createdAt = createdIssue.created_at,
                                            htmlUrl = createdIssue.html_url
                                        )
                                        container.feedbackRepo.saveBugReport(bugReport)

                                        Toast.makeText(context, "Feedback submitted successfully!", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                    } finally {
                                        isSubmitting = false
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isSubmitting && configValid
                        ) {
                            if (isSubmitting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Submit")
                            }
                        }
                    }
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssueDetailsDialog(
    container: AppContainer,
    report: BugReport,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var issueDetails by remember { mutableStateOf<GithubIssue?>(null) }
    var comments by remember { mutableStateOf<List<GithubComment>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    var replyText by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var isPostingReply by remember { mutableStateOf(false) }

    val photoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> selectedImageUri = uri }
    )

    val imageBitmap = rememberUriImage(selectedImageUri, context)

    val fetchLatestData = {
        scope.launch {
            isLoading = true
            try {
                val issue = withContext(Dispatchers.IO) {
                    container.githubApi.getIssue(report.number)
                }
                val commentList = withContext(Dispatchers.IO) {
                    container.githubApi.getComments(report.number)
                }

                issueDetails = issue
                comments = commentList

                // Synchronize DataStore with latest status
                if (issue.state != report.status) {
                    container.feedbackRepo.saveBugReport(
                        report.copy(status = issue.state)
                    )
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error fetching latest data: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(report.number) {
        fetchLatestData()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = issueDetails?.title ?: report.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Issue #${report.number}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    },
                    actions = {
                        IconButton(onClick = { fetchLatestData() }, enabled = !isLoading) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { padding ->
            if (isLoading && issueDetails == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            // Main description/body card
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val status = issueDetails?.state ?: report.status
                                        val isOpen = status.lowercase() == "open"
                                        val badgeColor = if (isOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

                                        Text(
                                            text = "Opened: ${issueDetails?.created_at ?: report.createdAt}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )

                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = badgeColor.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = status.uppercase(),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = badgeColor,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = issueDetails?.body ?: "No description provided.",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }

                        if (comments.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Comments",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }

                            items(comments) { comment ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = comment.user.login,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = comment.created_at,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = comment.body,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Divider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Reply Composer
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = replyText,
                            onValueChange = { replyText = it },
                            placeholder = { Text("Write a reply…") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 4,
                            enabled = !isPostingReply
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        photoLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    enabled = !isPostingReply
                                ) {
                                    Icon(
                                        Icons.Default.Refresh, // Reuse refresh or another icon since standard image might not be present
                                        contentDescription = "Attach image"
                                    )
                                }

                                if (selectedImageUri != null) {
                                    Text(
                                        text = "Image attached",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.clickable { selectedImageUri = null }
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    if (replyText.isBlank()) return@Button
                                    scope.launch {
                                        isPostingReply = true
                                        try {
                                            var screenshotUrl: String? = null
                                            if (selectedImageUri != null) {
                                                val base64 = withContext(Dispatchers.IO) {
                                                    ImageUploadHelper.uriToBase64(context, selectedImageUri!!)
                                                }
                                                val filename = ImageUploadHelper.generateUniqueFilename()
                                                val uploadRequest = UploadAssetRequest(
                                                    filename = filename,
                                                    contentBase64 = base64
                                                )
                                                val uploadResult = withContext(Dispatchers.IO) {
                                                    container.githubApi.uploadAsset(uploadRequest)
                                                }
                                                screenshotUrl = uploadResult.content?.download_url
                                            }

                                            val bodyBuilder = StringBuilder()
                                            bodyBuilder.append("## Reply\n\n$replyText\n\n")
                                            if (screenshotUrl != null) {
                                                bodyBuilder.append("## Attachment\n\n![Screenshot]($screenshotUrl)\n\n")
                                            }

                                            val commentRequest = PostCommentRequest(
                                                body = bodyBuilder.toString()
                                            )

                                            withContext(Dispatchers.IO) {
                                                container.githubApi.postComment(report.number, commentRequest)
                                            }

                                            replyText = ""
                                            selectedImageUri = null
                                            Toast.makeText(context, "Reply posted!", Toast.LENGTH_SHORT).show()
                                            fetchLatestData()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Error posting reply: ${e.message}", Toast.LENGTH_LONG).show()
                                        } finally {
                                            isPostingReply = false
                                        }
                                    }
                                },
                                enabled = replyText.isNotBlank() && !isPostingReply
                            ) {
                                if (isPostingReply) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Text("Reply")
                                }
                            }
                        }

                        if (imageBitmap != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Image(
                                    bitmap = imageBitmap,
                                    contentDescription = "Attached screenshot preview",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun rememberUriImage(uri: Uri?, context: Context): ImageBitmap? {
    return remember(uri) {
        if (uri == null) null else {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source).asImageBitmap()
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri).asImageBitmap()
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}
