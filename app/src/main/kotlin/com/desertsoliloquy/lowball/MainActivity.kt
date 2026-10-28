package com.desertsoliloquy.lowball

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.desertsoliloquy.lowball.ui.DisclaimerScreen
import com.desertsoliloquy.lowball.ui.HowToScreen
import com.desertsoliloquy.lowball.ui.LowballTheme
import com.desertsoliloquy.lowball.ui.MenuScreen
import com.desertsoliloquy.lowball.ui.TableScreen

class MainActivity : ComponentActivity() {

    private val game: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dark = when (game.theme) {
                ThemeChoice.SYSTEM -> isSystemInDarkTheme()
                ThemeChoice.LIGHT -> false
                ThemeChoice.DARK -> true
            }
            LowballTheme(dark) {
                Surface(Modifier.fillMaxSize()) {
                    val modifier = Modifier.fillMaxSize().safeDrawingPadding()
                    BackHandler(enabled = game.screen == Screen.HOW_TO) { game.closeHowTo() }
                    when (game.screen) {
                        Screen.DISCLAIMER -> DisclaimerScreen(game, modifier)
                        Screen.MENU -> MenuScreen(game, modifier)
                        Screen.TABLE -> TableScreen(game, modifier)
                        Screen.HOW_TO -> HowToScreen(game, modifier)
                    }
                }
            }
        }
    }
}
