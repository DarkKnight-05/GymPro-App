package com.gymmanager.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.gymmanager.app.R
import com.gymmanager.app.ui.WildcardSemiItalic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true; delay(1200); onFinished() }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), Alignment.Center) {
        AnimatedVisibility(visible, enter = fadeIn()) {
            Column(horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.spacedBy(14.dp)) {
                Image(
                    painter = painterResource(R.drawable.bodytech_logo),
                    contentDescription = "BodyTech",
                    modifier = Modifier.size(width = 250.dp, height = 180.dp),
                    contentScale = ContentScale.Fit
                )
                Text("GYMPRO", style = MaterialTheme.typography.displaySmall, fontFamily = WildcardSemiItalic)
                Text("Smarter gym management", style = MaterialTheme.typography.bodyMedium)
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth=3.dp)
            }
        }
    }
}
