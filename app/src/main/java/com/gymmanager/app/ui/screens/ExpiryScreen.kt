package com.gymmanager.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gymmanager.app.data.Member
import com.gymmanager.app.ui.MemberViewModel
import com.gymmanager.app.util.WhatsAppHelper
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpiryScreen(viewModel: MemberViewModel, onBack: () -> Unit, onOpenMember: (Long) -> Unit) {
    val expired by viewModel.expiredMembers.collectAsState()
    val expiringSoon by viewModel.expiringSoonMembers.collectAsState()
    val branches by viewModel.branches.collectAsState()
    var tab by remember { mutableStateOf(0) }
    var selectedBranchRemoteId by remember { mutableStateOf<String?>(null) }

    val allMembers = if (tab == 0) expired else expiringSoon
    val selectedBranch = branches.firstOrNull { it.remoteId == selectedBranchRemoteId }
    val branchMembers = if (selectedBranchRemoteId == null) allMembers
    else allMembers.filter { it.branchRemoteId == selectedBranchRemoteId }

    Scaffold(topBar = {
        TopAppBar(title = { Text(if (selectedBranch == null) "Membership Status" else selectedBranch.name) }, navigationIcon = {
            TextButton(onClick = {
                if (selectedBranchRemoteId != null) selectedBranchRemoteId = null else onBack()
            }) { Text(if (selectedBranchRemoteId != null) "Back" else "Back") }
        })
    }) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0; selectedBranchRemoteId = null }, text = { Text("Expired (${expired.size})") })
                Tab(selected = tab == 1, onClick = { tab = 1; selectedBranchRemoteId = null }, text = { Text("Expiring in 5 Days (${expiringSoon.size})") })
            }

            if (selectedBranchRemoteId == null) {
                if (allMembers.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(if (tab == 0) "No expired memberships" else "No memberships expiring in the next 5 days")
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(branches.filter { branch -> allMembers.any { it.branchRemoteId == branch.remoteId } }, key = { it.remoteId }) { branch ->
                            val count = allMembers.count { it.branchRemoteId == branch.remoteId }
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .clickable { selectedBranchRemoteId = branch.remoteId },
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                ListItem(
                                    headlineContent = { Text(branch.name) },
                                    supportingContent = { Text(if (tab == 0) "Expired memberships" else "Memberships expiring in the next 5 days") },
                                    trailingContent = { Text(count.toString(), style = MaterialTheme.typography.titleLarge) }
                                )
                            }
                        }
                    }
                }
            } else {
                if (branchMembers.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(if (tab == 0) "No expired memberships" else "No memberships expiring in the next 5 days")
                    }
                } else {
                    LazyColumn {
                        items(branchMembers, key = { it.id }) { member ->
                            ExpiryRow(member = member, onClick = { onOpenMember(member.id) })
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpiryRow(member: Member, onClick: () -> Unit) {
    val context = LocalContext.current
    val df = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    ListItem(
        headlineContent = { Text(member.name) },
        supportingContent = { Text("Due ${df.format(Date(member.nextDueDateMillis))} · ${member.phone}") },
        trailingContent = {
            TextButton(onClick = { WhatsAppHelper.sendFeeReminder(context, member) }) {
                Text("Remind")
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
