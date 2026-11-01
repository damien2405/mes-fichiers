package com.damien.mesfichiers

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
    AnimatedContent(
        targetState = top,
        contentKey = { it.uid },
        transitionSpec = {
            if (AppModel.forward) {
                (slideInHorizontally(tween(280)) { it / 5 } + fadeIn(tween(280))) togetherWith
                    (slideOutHorizontally(tween(280)) { -it / 8 } + fadeOut(tween(200)))
            } else {
                (slideInHorizontally(tween(280)) { -it / 8 } + fadeIn(tween(280))) togetherWith
                    (slideOutHorizontally(tween(280)) { it / 5 } + fadeOut(tween(200)))
            }
        },
        label = "navigation"
    ) { screen ->
        holder.SaveableStateProvider(screen.uid) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                when (screen) {
                    Screen.Home -> HomeScreen()
                    Screen.Network -> NetworkScreen()
                    is Screen.Browse -> BrowseScreen(screen)
                    is Screen.Form -> FormScreen(screen)
                    is Screen.Viewer -> ViewerScreen(screen)
                }
            }
        }
    }
}
