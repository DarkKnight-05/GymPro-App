package com.gymmanager.app.ui.screens
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gymmanager.app.auth.Role
import com.gymmanager.app.data.Branch
import com.gymmanager.app.ui.MemberViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberListScreen(
    viewModel: MemberViewModel, role: Role, onAddMember: () -> Unit,
    onOpenMember: (Long) -> Unit, onEditMember: (Long) -> Unit, onOpenDashboard: () -> Unit,
    onOpenArchived: () -> Unit, onOpenReports: () -> Unit, onOpenBranches: () -> Unit,
    onOpenExpiry: () -> Unit, onOpenSettings: () -> Unit, onOpenTrainers: () -> Unit, onLogout: () -> Unit,
    onOpenBranch: (Long) -> Unit
) {
    val members by viewModel.activeMembers.collectAsState()
    val branches by viewModel.branches.collectAsState()
    val trainerBranchId by viewModel.currentBranchId.collectAsState()
    var drawerOpen by remember { mutableStateOf(false) }
    val visibleBranches = if (role == Role.TRAINER && trainerBranchId != null) branches.filter { it.id == trainerBranchId } else branches
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    LaunchedEffect(drawerOpen) { if (drawerOpen) drawerState.open() else drawerState.close() }

    ModalNavigationDrawer(drawerState = drawerState, drawerContent = {
        ModalDrawerSheet {
            Spacer(Modifier.height(20.dp))
            Text("GYMPRO", Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.titleLarge)
            NavigationDrawerItem(label={Text("Dashboard")}, selected=false, onClick={drawerOpen=false;onOpenDashboard()}, icon={Icon(Icons.Default.Dashboard,null)})
            NavigationDrawerItem(label={Text("Members")}, selected=true, onClick={drawerOpen=false}, icon={Icon(Icons.Default.People,null)})
            NavigationDrawerItem(label={Text("Archived Members")}, selected=false, onClick={drawerOpen=false;onOpenArchived()}, icon={Icon(Icons.Default.Archive,null)})
            NavigationDrawerItem(label={Text("Membership Expiry")}, selected=false, onClick={drawerOpen=false;onOpenExpiry()}, icon={Icon(Icons.Default.Event,null)})
            if (role == Role.ADMIN) {
                NavigationDrawerItem(label={Text("Daily Fee Review")}, selected=false, onClick={drawerOpen=false;onOpenReports()}, icon={Icon(Icons.Default.Payments,null)})
                NavigationDrawerItem(label={Text("Trainers")}, selected=false, onClick={drawerOpen=false;onOpenTrainers()}, icon={Icon(Icons.Default.Badge,null)})
                NavigationDrawerItem(label={Text("Branches")}, selected=false, onClick={drawerOpen=false;onOpenBranches()}, icon={Icon(Icons.Default.Business,null)})
            }
            NavigationDrawerItem(label={Text("Settings")}, selected=false, onClick={drawerOpen=false;onOpenSettings()}, icon={Icon(Icons.Default.Settings,null)})
            Spacer(Modifier.weight(1f))
            NavigationDrawerItem(label={Text("Logout")}, selected=false, onClick={drawerOpen=false;onLogout()}, icon={Icon(Icons.Default.Logout,null)})
        }
    }) {
        Scaffold(topBar={TopAppBar(title={Text("Members")}, navigationIcon={IconButton(onClick={drawerOpen=true}){Icon(Icons.Default.Menu,"Menu")}})},
            floatingActionButton={FloatingActionButton(onClick=onAddMember){Icon(Icons.Default.Add,"Add member")}}) { padding ->
            if (visibleBranches.isEmpty()) Box(Modifier.padding(padding).fillMaxSize(),Alignment.Center){Text("No branches found. Use + to add a member.")}
            else LazyColumn(Modifier.padding(padding).fillMaxSize(),contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                item{Text(if(role==Role.ADMIN)"Members by branch" else "Your branch",style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(4.dp))}
                items(visibleBranches,key={it.id}){branch->
                    val count=members.count{it.branchId==branch.id&&!it.isArchived}
                    BranchMemberCard(branch,count){onOpenBranch(branch.id)}
                }
            }
        }
    }
}
@Composable private fun BranchMemberCard(branch: Branch,count:Int,onClick:()->Unit){
    Card(Modifier.fillMaxWidth().clickable(onClick=onClick)){Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically){
        Icon(Icons.Default.Business,null,Modifier.size(34.dp));Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)){Text(branch.name,style=MaterialTheme.typography.titleLarge);Text("$count active member${if(count==1)"" else "s"}")}
        Icon(Icons.Default.ChevronRight,"Open branch")
    }}
}
