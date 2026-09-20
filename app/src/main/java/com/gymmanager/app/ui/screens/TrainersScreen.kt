package com.gymmanager.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.gymmanager.app.data.Branch
import com.gymmanager.app.data.TrainerAccount
import com.gymmanager.app.ui.MemberViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainersScreen(viewModel: MemberViewModel, onBack: () -> Unit) {
    val trainers by viewModel.trainerAccounts.collectAsState()
    val branches by viewModel.branches.collectAsState()
    var editing by remember { mutableStateOf<TrainerAccount?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var passwordTrainer by remember { mutableStateOf<TrainerAccount?>(null) }
    var actionTrainer by remember { mutableStateOf<TrainerAccount?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar={TopAppBar(title={Text("Trainers")},navigationIcon={TextButton(onClick=onBack){Text("Back")}})},floatingActionButton={FloatingActionButton(onClick={showAdd=true}){Text("+")}}){padding->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item{Text("Trainers can see and manage members only in their assigned branch.",Modifier.padding(16.dp),style=MaterialTheme.typography.bodySmall)}
            items(trainers,key={it.id}){trainer->
                val branchName=branches.firstOrNull{it.remoteId==trainer.branchRemoteId}?.name?:"No branch assigned"
                ListItem(
                    headlineContent={Text(trainer.name)},
                    supportingContent={Text("@${trainer.username} • $branchName • ${if(trainer.active)"Active" else "Inactive"}")},
                    trailingContent={
                        Row {
                            TextButton(onClick={passwordTrainer=trainer}){Text("Password")}
                            TextButton(onClick={editing=trainer}){Text("Edit")}
                            TextButton(onClick={actionTrainer=trainer}){Text(if(trainer.active)"Deactivate" else "Delete")}
                        }
                    }
                ); HorizontalDivider()
            }
            if(trainers.isEmpty())item{Box(Modifier.fillMaxWidth().padding(32.dp),Alignment.Center){Text("No trainers yet.")}}
        }
    }
    if(showAdd) TrainerDialog("Add Trainer",null,branches,{n,u,p,b->viewModel.addTrainerAccount(n,u,p,b,{showAdd=false},{error=it})},{showAdd=false})
    editing?.let{t->TrainerDialog("Edit Trainer",t,branches,{n,_,_,b->viewModel.updateTrainerAccount(t,n,b,{editing=null},{error=it})},{editing=null})}
    passwordTrainer?.let{t->ChangeTrainerPasswordDialog(t,{pw->viewModel.changeTrainerPassword(t,pw,{passwordTrainer=null},{error=it})},{passwordTrainer=null})}
    actionTrainer?.let{t->
        if(t.active){
            AlertDialog(onDismissRequest={actionTrainer=null},title={Text("Deactivate ${t.name}?")},text={Text("The trainer will be unable to log in. Their profile and historical records will be kept and can be reactivated later.")},confirmButton={TextButton(onClick={viewModel.deactivateTrainerAccount(t,{actionTrainer=null},{error=it})}){Text("Deactivate")}},dismissButton={TextButton(onClick={actionTrainer=null}){Text("Cancel")}})
        }else{
            AlertDialog(onDismissRequest={actionTrainer=null},title={Text("Delete ${t.name} permanently?")},text={Text("This removes the trainer account and profile permanently. Historical records are not removed.")},confirmButton={TextButton(onClick={viewModel.removeTrainerAccount(t,{actionTrainer=null},{error=it})}){Text("Delete permanently")}},dismissButton={TextButton(onClick={actionTrainer=null}){Text("Cancel")}})
        }
    }
    error?.let{AlertDialog(onDismissRequest={error=null},title={Text("Trainer action failed")},text={Text(it)},confirmButton={TextButton(onClick={error=null}){Text("OK")}})}
}

@Composable
private fun ChangeTrainerPasswordDialog(trainer:TrainerAccount,onSave:(String)->Unit,onCancel:()->Unit){
    var password by remember{mutableStateOf("")}; var confirm by remember{mutableStateOf("")}; var visible by remember{mutableStateOf(false)}; var confirmVisible by remember{mutableStateOf(false)}; var error by remember{mutableStateOf<String?>(null)}
    AlertDialog(onDismissRequest=onCancel,title={Text("Change password")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Text("Set a new password for ${trainer.name}.");PasswordField(password,{password=it},"New password",visible,{visible=!visible});PasswordField(confirm,{confirm=it},"Confirm password",confirmVisible,{confirmVisible=!confirmVisible});error?.let{Text(it,color=MaterialTheme.colorScheme.error)}}},confirmButton={TextButton(onClick={if(password.length<6)error="Password must be at least 6 characters." else if(password!=confirm)error="Passwords don't match." else onSave(password)}){Text("Save")}},dismissButton={TextButton(onClick=onCancel){Text("Cancel")}})
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrainerDialog(title:String,existing:TrainerAccount?,branches:List<Branch>,onSave:(String,String,String,Long?)->Unit,onCancel:()->Unit){
    var name by remember(existing?.id){mutableStateOf(existing?.name?:"")};var username by remember(existing?.id){mutableStateOf(existing?.username?:"")};var password by remember(existing?.id){mutableStateOf("")};var passwordVisible by remember{mutableStateOf(false)};var branchId by remember(existing?.id,branches){mutableStateOf(existing?.branchId?:branches.firstOrNull{it.remoteId==existing?.branchRemoteId}?.id?:branches.firstOrNull()?.id)};var expanded by remember{mutableStateOf(false)}
    AlertDialog(onDismissRequest=onCancel,title={Text(title)},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        OutlinedTextField(name,{name=it},label={Text("Trainer name")})
        OutlinedTextField(username,{if(existing==null)username=it},label={Text("Username")},supportingText={if(existing==null)Text("3–30 characters: letters, numbers, dots, underscores or hyphens.")},isError=existing==null && username.isNotBlank() && !username.trim().matches(Regex("[a-zA-Z0-9._-]{3,30}")),readOnly=existing!=null)
        if(existing==null)PasswordField(password,{password=it},"Password",passwordVisible,{passwordVisible=!passwordVisible})
        ExposedDropdownMenuBox(expanded,{expanded=it}){OutlinedTextField(branches.firstOrNull{it.id==branchId}?.name?:"Select branch",{},readOnly=true,label={Text("Assigned branch *")},modifier=Modifier.menuAnchor().fillMaxWidth());ExposedDropdownMenu(expanded,{expanded=false}){branches.forEach{b->DropdownMenuItem({Text(b.name)},{branchId=b.id;expanded=false})}}}
        Text(if(existing==null)"The trainer signs in with this username and password. No trainer email is required." else "Use the Password button on the trainer list to change this trainer's password.",style=MaterialTheme.typography.bodySmall)
    }},confirmButton={TextButton(onClick={if(name.isNotBlank()&&username.trim().matches(Regex("[a-zA-Z0-9._-]{3,30}"))&&branchId!=null&&(existing!=null||password.length>=6))onSave(name,username.trim(),password,branchId)}){Text("Save")}},dismissButton={TextButton(onClick=onCancel){Text("Cancel")}})
}

@Composable
private fun PasswordField(value:String,onValueChange:(String)->Unit,label:String,visible:Boolean,onToggle:()->Unit){
    OutlinedTextField(value=value,onValueChange=onValueChange,label={Text(label)},singleLine=true,visualTransformation=if(visible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),trailingIcon={
        IconButton(onClick=onToggle){Icon(if(visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,contentDescription=if(visible) "Hide password" else "Show password")}
    })
}
