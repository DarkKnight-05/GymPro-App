package com.gymmanager.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gymmanager.app.ui.MemberViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivedMembersScreen(
    viewModel: MemberViewModel,
    onBack: () -> Unit,
    onRestored: () -> Unit = {}
) {
    val archived by viewModel.archivedMembers.collectAsState()
    var query by remember { mutableStateOf("") }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Archived Members (${archived.size})") }, navigationIcon = {
            TextButton(onClick = onBack) { Text("Back") }
        })
    }) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; viewModel.setArchivedSearchQuery(it) },
                placeholder = { Text("Search by name or phone") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(12.dp)
            )

            if (archived.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (query.isBlank()) "No archived members" else "No matches found")
                }
            } else {
                LazyColumn {
                    items(archived, key = { it.id }) { member ->
                        ListItem(
                            headlineContent = { Text(member.name) },
                            supportingContent = { Text(member.phone) },
                            trailingContent = {
                                TextButton(onClick = {
                                    viewModel.restoreMember(member.id) { onRestored() }
                                }) {
                                    Text("Restore")
                                }
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
