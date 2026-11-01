package com.damien.mesfichiers

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppModel.init(applicationContext)
        setContent {
            AppTheme {
                Root()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // relit l'autorisation « accès à tous les fichiers » au retour des réglages
        AppModel.permTick++
    }
}

@Composable
fun Root() {
    val holder = rememberSaveableStateHolder()
    BackHandler(enabled = AppModel.stack.size > 1) { AppModel.back() }
    val top = AppModel.stack.last()
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        holder.SaveableStateProvider(top.uid) {
            when (top) {
                Screen.Home -> HomeScreen()
                Screen.Network -> NetworkScreen()
                is Screen.Browse -> BrowseScreen(top)
                is Screen.Form -> FormScreen(top)
                is Screen.Viewer -> ViewerScreen(top)
            }
        }
    }
}
