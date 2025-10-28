package com.example.emvnfc

import android.content.ClipData
import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.emvnfc.data.SampleTlvProvider
import com.example.emvnfc.model.EmvLogEntry
import com.example.emvnfc.model.LogField
import com.example.emvnfc.model.LogSource
import com.example.emvnfc.nfc.NfcEvent
import com.example.emvnfc.nfc.NfcReaderManager
import com.example.emvnfc.ui.NfcViewModel
import com.example.emvnfc.ui.ReaderStatus
import com.example.emvnfc.ui.ReaderUiState
import com.example.emvnfc.ui.theme.EmvNfcTheme
import kotlinx.coroutines.MainScope

class MainActivity : ComponentActivity() {

    private val viewModel: NfcViewModel by viewModels {
        NfcViewModel.factory(applicationContext)
    }
    private val activityScope = MainScope()

    private val readerManager by lazy {
        NfcReaderManager(activityScope) { NfcAdapter.getDefaultAdapter(this) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val adapter = NfcAdapter.getDefaultAdapter(this)
        viewModel.onNfcAvailabilityChanged(adapter != null)
        setContent {
            EmvNfcApp(viewModel, readerManager)
        }
    }

    override fun onResume() {
        super.onResume()
        readerManager.enable(this)
        viewModel.onReaderModeEnabled()
    }

    override fun onPause() {
        super.onPause()
        readerManager.disable(this)
        viewModel.onReaderModeDisabled()
    }
}

@Composable
fun EmvNfcApp(viewModel: NfcViewModel, readerManager: NfcReaderManager) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        readerManager.events.collect { event ->
            viewModel.onNfcEvent(event)
        }
    }

    EmvNfcTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = { AppTopBar() }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatusCard(uiState)
                ActionRow(
                    nfcEnabled = uiState.nfcAvailable,
                    hasLogs = uiState.logs.isNotEmpty(),
                    onSample = { viewModel.ingestSample(SampleTlvProvider.purchaseRecord) },
                    onClear = { viewModel.clearLogs() },
                    onShare = {
                        val logFile = viewModel.logFile()
                        if (logFile != null) {
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                logFile
                            )
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/json"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                clipData = android.content.ClipData.newUri(context.contentResolver, "logs", uri)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_logs)))
                        } else {
                            viewModel.buildShareText()?.let { shareText ->
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_logs)))
                            }
                        }
                    }
                )
                LogList(uiState.logs)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar() {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Nfc, contentDescription = null)
                Spacer(modifier = Modifier.padding(4.dp))
                Text(text = stringResource(id = R.string.app_name))
            }
        }
    )
}

@Composable
private fun StatusCard(uiState: ReaderUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = when (uiState.status) {
                ReaderStatus.Idle -> stringResource(id = R.string.status_idle)
                ReaderStatus.WaitingForCard -> stringResource(id = R.string.status_waiting)
                ReaderStatus.Reading -> stringResource(id = R.string.status_reading)
                ReaderStatus.Completed -> stringResource(id = R.string.status_completed)
                ReaderStatus.Error -> stringResource(id = R.string.status_error)
            })
            if (!uiState.nfcAvailable) {
                Text(text = stringResource(id = R.string.nfc_unavailable), color = MaterialTheme.colorScheme.error)
            }
            uiState.message?.let { message ->
                Text(text = message, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ActionRow(
    nfcEnabled: Boolean,
    hasLogs: Boolean,
    onSample: () -> Unit,
    onClear: () -> Unit,
    onShare: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(onClick = onSample) {
            Text(text = stringResource(id = R.string.load_sample))
        }
        Button(onClick = onClear, enabled = nfcEnabled) {
            Text(text = stringResource(id = R.string.clear_logs))
        }
        Button(onClick = onShare, enabled = hasLogs) {
            Text(text = stringResource(id = R.string.share_logs))
        }
    }
}

@Composable
private fun LogList(logs: List<EmvLogEntry>) {
    if (logs.isEmpty()) {
        Text(text = stringResource(id = R.string.no_logs))
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(logs, key = { it.id }) { entry ->
            LogCard(entry)
        }
    }
}

@Composable
private fun LogCard(entry: EmvLogEntry) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "${entry.source} – ${entry.timestampMillis}", style = MaterialTheme.typography.labelLarge)
            entry.fields.forEach { field ->
                LogFieldRow(field)
            }
        }
    }
}

@Composable
private fun LogFieldRow(field: LogField) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = "Tag ${field.tag}", style = MaterialTheme.typography.labelSmall)
        Text(text = field.interpretation, style = MaterialTheme.typography.bodyLarge)
        Text(text = "Hex: ${field.rawHex}", style = MaterialTheme.typography.bodySmall)
    }
}

@Preview(showBackground = true)
@Composable
fun EmvNfcPreview() {
    EmvNfcTheme {
        Scaffold { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding)) {
                StatusCard(
                    ReaderUiState(
                        status = ReaderStatus.WaitingForCard,
                        logs = listOf(
                            EmvLogEntry(
                                timestampMillis = 0,
                                source = LogSource.SAMPLE,
                                fields = listOf(
                                    LogField("84", "A0000000031010", "A0000000031010"),
                                    LogField("9F02", "000000001234", "12.34")
                                )
                            )
                        )
                    )
                )
            }
        }
    }
}