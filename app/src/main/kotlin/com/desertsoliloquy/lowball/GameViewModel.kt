package com.desertsoliloquy.lowball

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import lowball.engine.Action
import lowball.engine.ActionType
import lowball.engine.CardImages
import lowball.engine.Difficulty
import lowball.engine.Opponent
import lowball.engine.Phase
import lowball.engine.SavedGame
import lowball.engine.Table
import lowball.engine.TableSnapshot
import java.io.File

enum class Screen { DISCLAIMER, MENU, TABLE, HOW_TO }

enum class ThemeChoice { SYSTEM, LIGHT, DARK }

/**
 * Holds the game while the app is open, runs the computer players' turns,
 * and saves and loads games. The screens only read [view] and call the functions here.
 */
class GameViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val saveFile = File(app.filesDir, "saved_game.json")

    val images: CardImages = CardImages(
        app.assets.list(CARD_FOLDER).orEmpty().filter { it.endsWith(".webp") },
    )

    private var table: Table? = null
    private var opponent: Opponent? = null
    private var computerTurns: Job? = null

    var screen by mutableStateOf(Screen.DISCLAIMER)
        private set

    /** Where How-to goes back to. */
    private var previousScreen = Screen.MENU

    var theme by mutableStateOf(ThemeChoice.entries[prefs.getInt("theme", 0)])
        private set

    /** A frozen copy of the game for the screens to draw. Replaced after every move. */
    var view by mutableStateOf<TableSnapshot?>(null)
        private set

    /** The buttons the human may press right now. Empty while the computer is thinking. */
    var legal by mutableStateOf<List<ActionType>>(emptyList())
        private set

    var hasSavedGame by mutableStateOf(saveFile.exists())
        private set

    /** A short message such as "Game saved", shown briefly. */
    var notice by mutableStateOf<String?>(null)
        private set

    fun acceptDisclaimer() {
        screen = Screen.MENU
    }

    fun showHowTo() {
        previousScreen = screen
        screen = Screen.HOW_TO
    }

    fun closeHowTo() {
        screen = previousScreen
    }

    fun cycleTheme() {
        theme = ThemeChoice.entries[(theme.ordinal + 1) % ThemeChoice.entries.size]
        prefs.edit().putInt("theme", theme.ordinal).apply()
    }

    fun newGame(opponents: Int, difficulty: Difficulty) {
        val t = Table.newGame(opponents, difficulty, images = images)
        start(t)
        t.startRound()
        afterMove()
    }

    fun resumeGame() {
        val t = runCatching { SavedGame.decode(saveFile.readText(), images) }.getOrElse {
            notice = "Sorry, the saved game couldn't be read."
            return
        }
        start(t)
        afterMove()
    }

    fun saveGame() {
        val t = table ?: return
        saveFile.writeText(SavedGame.encode(t))
        hasSavedGame = true
        notice = "Game saved"
    }

    fun quitToMenu() {
        computerTurns?.cancel()
        table = null
        view = null
        legal = emptyList()
        screen = Screen.MENU
    }

    fun clearNotice() {
        notice = null
    }

    fun play(action: Action) {
        val t = table ?: return
        if (t.toAct != HUMAN_SEAT || action.type !in t.legalActions()) return
        t.act(action)
        afterMove()
    }

    fun nextRound() {
        val t = table ?: return
        if (t.phase != Phase.ROUND_OVER) return
        t.startRound()
        afterMove()
    }

    private fun start(t: Table) {
        computerTurns?.cancel()
        table = t
        opponent = Opponent(t.difficulty)
        screen = Screen.TABLE
    }

    /** Shows the new position, then lets the computer players move one at a time. */
    private fun afterMove() {
        refresh()
        computerTurns?.cancel()
        computerTurns = viewModelScope.launch {
            val t = table ?: return@launch
            while (true) {
                val seat = t.toAct ?: break
                if (t.players[seat].isHuman) break
                delay(COMPUTER_THINKING_MS)
                t.act(opponent!!.decide(t, seat))
                refresh()
            }
        }
    }

    private fun refresh() {
        val t = table ?: return
        view = t.snapshot()
        legal = if (t.toAct == HUMAN_SEAT) t.legalActions() else emptyList()
    }

    companion object {
        const val CARD_FOLDER = "cards"
        const val HUMAN_SEAT = 0
        private const val COMPUTER_THINKING_MS = 900L
    }
}
