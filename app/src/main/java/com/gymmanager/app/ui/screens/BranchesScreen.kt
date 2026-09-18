package com.gymmanager.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gymmanager.app.data.Branch
import com.gymmanager.app.ui.MemberViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BranchesScreen(viewModel: MemberViewModel, onBack: () -> Unit) {
    val branches by viewModel.branches.collectAsState()
    var editing by remember { mutableStateOf<Branch?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Branch?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    Scaffold(topBar={TopAppBar(title={Text("Branches")},navigationIcon={TextButton(onClick=onBack){Text("Back")}})},floatingActionButton={FloatingActionButton(onClick={showAdd=true}){Text("+")}}){padding->
        LazyColumn(Modifier.padding(padding).fillMaxSize()){
            items(branches,key={it.id}){b->ListItem(headlineContent={Text(b.name)},supportingContent={if(b.place.isNotBlank())Text(b.place)},trailingContent={Row{TextButton(onClick={editing=b}){Text("Edit")};TextButton(onClick={deleting=b}){Text("Delete")}}});HorizontalDivider()}
            if(branches.isEmpty())item{Box(Modifier.fillMaxWidth().padding(32.dp),Alignment.Center){Text("No branches yet.")}}
        }
    }
    if(showAdd) BranchDialog("Add Branch",null,{n,p->viewModel.addBranch(n,p);showAdd=false},{showAdd=false})
    editing?.let{b->BranchDialog("Edit Branch",b,{n,p->viewModel.updateBranch(b.copy(name=n,place=p));editing=null},{editing=null})}
    deleting?.let{b->AlertDialog(onDismissRequest={deleting=null},title={Text("Delete ${b.name}?")},text={Text("A branch can only be deleted when it has no active or archived members.")},confirmButton={TextButton(onClick={viewModel.deleteBranch(b){ok->if(!ok)error="${b.name} still has members. Archive or move them first."};deleting=null}){Text("Delete")}},dismissButton={TextButton(onClick={deleting=null}){Text("Cancel")}})}
    error?.let{AlertDialog(onDismissRequest={error=null},title={Text("Cannot delete branch")},text={Text(it)},confirmButton={TextButton(onClick={error=null}){Text("OK")}})}
}

@Composable private fun BranchDialog(title:String,existing:Branch?,onSave:(String,String)->Unit,onCancel:()->Unit){var name by remember(existing?.id){mutableStateOf(existing?.name?:"")};var place by remember(existing?.id){mutableStateOf(existing?.place?:"")};AlertDialog(onDismissRequest=onCancel,title={Text(title)},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(name,{name=it},label={Text("Branch name")});OutlinedTextField(place,{place=it},label={Text("Place")})}},confirmButton={TextButton(onClick={if(name.isNotBlank())onSave(name.trim(),place.trim())}){Text("Save")}},dismissButton={TextButton(onClick=onCancel){Text("Cancel")}})}
