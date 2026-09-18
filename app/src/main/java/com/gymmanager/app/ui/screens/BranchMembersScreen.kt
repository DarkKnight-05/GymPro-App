package com.gymmanager.app.ui.screens
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.gymmanager.app.data.Gender
import com.gymmanager.app.data.Member
import com.gymmanager.app.ui.MemberViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BranchMembersScreen(
    viewModel: MemberViewModel,
    branchId: Long,
    onBack: () -> Unit,
    onOpenGender: (Long, Gender) -> Unit
) {
    val branches by viewModel.branches.collectAsState()
    val allMembers by viewModel.activeMembersForBranch(branchId).collectAsState(initial = emptyList())
    val branch = branches.firstOrNull { it.id == branchId }
    val gentsCount = allMembers.count { it.gender == Gender.GENTS }
    val ladiesCount = allMembers.count { it.gender == Gender.LADY }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(branch?.name ?: "Branch members") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Members by gender",
                style = MaterialTheme.typography.titleMedium
            )
            GenderButton(
                title = "Gents",
                count = gentsCount,
                icon = Icons.Default.Male,
                onClick = { onOpenGender(branchId, Gender.GENTS) }
            )
            GenderButton(
                title = "Ladies",
                count = ladiesCount,
                icon = Icons.Default.Female,
                onClick = { onOpenGender(branchId, Gender.LADY) }
            )
        }
    }
}

@Composable
private fun GenderButton(
    title: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = title, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(16.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            AssistChip(onClick = onClick, label = { Text("$count") })
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.ChevronRight, "Open $title")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenderMembersScreen(
    viewModel: MemberViewModel,
    branchId: Long,
    gender: Gender,
    onBack: () -> Unit,
    onOpenMember: (Long) -> Unit,
    onEditMember: (Long) -> Unit
) {
    val branches by viewModel.branches.collectAsState()
    val allMembers by viewModel.activeMembersForBranch(branchId).collectAsState(initial = emptyList())
    val branch = branches.firstOrNull { it.id == branchId }
    var query by remember { mutableStateOf("") }
    val members = remember(allMembers, query, gender) {
        allMembers
            .filter { it.gender == gender }
            .filter {
                query.isBlank() ||
                    it.name.contains(query, ignoreCase = true) ||
                    it.phone.contains(query, ignoreCase = true)
            }
    }
    val title = if (gender == Gender.GENTS) "Gents" else "Ladies"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${branch?.name ?: "Branch"} • $title") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search name or phone") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Text(
                    "$title • ${members.size} member${if (members.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            if (members.isEmpty()) {
                item { Text("No ${title.lowercase()} found", Modifier.padding(horizontal = 8.dp)) }
            } else {
                items(members, key = { it.id }) { member ->
                    GenderMemberRow(member, onOpenMember = { onOpenMember(member.id) }, onEdit = { onEditMember(member.id) })
                }
            }
        }
    }
}

@Composable
private fun GenderMemberRow(member: Member, onOpenMember: () -> Unit, onEdit: () -> Unit) {
    val df = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val now = System.currentTimeMillis()
    val overdue = member.nextDueDateMillis < now
    val isNew = member.joinDateMillis in (now - 7L * 24 * 60 * 60 * 1000)..now
    ListItem(
        modifier = Modifier.clickable(onClick = onOpenMember),
        leadingContent = {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                Alignment.Center
            ) {
                if (member.photoUri != null) {
                    AsyncImage(member.photoUri, "Member photo", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Icon(Icons.Default.Person, null)
                }
            }
        },
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(member.name)
                if (isNew) {
                    Spacer(Modifier.width(8.dp))
                    AssistChip(onClick = {}, label = { Text("NEW") })
                }
            }
        },
        supportingContent = {
            Text("${member.phone} • Due ${df.format(Date(member.nextDueDateMillis))}")
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                if (overdue) {
                    Text("OVERDUE", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, "Edit")
                }
            }
        }
    )
}
