package com.desertsoliloquy.lowball.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.desertsoliloquy.lowball.GameViewModel
import com.desertsoliloquy.lowball.ThemeChoice
import lowball.engine.Difficulty
import lowball.engine.Table

@Composable
fun DisclaimerScreen(game: GameViewModel, modifier: Modifier) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("California Lowball", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Before you play", style = MaterialTheme.typography.titleMedium)
        Text("This is a game for fun only. No real money is used or exchanged, and nothing is bought or sold.")
        Text("The house loans and belongings in the game are make-believe.")
        Text("The game does not connect to the Internet or to any Bluetooth device.")
        Text("Nothing about you is collected. The only thing the game stores is your saved game, and only on this phone.")
        Spacer(Modifier.height(8.dp))
        Button(onClick = game::acceptDisclaimer, modifier = Modifier.fillMaxWidth()) {
            Text("I understand")
        }
    }
}

@Composable
fun MenuScreen(game: GameViewModel, modifier: Modifier) {
    var opponents by rememberSaveable { mutableIntStateOf(2) }
    var difficulty by rememberSaveable { mutableStateOf(Difficulty.MEDIUM) }

    Column(
        modifier.verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("California Lowball", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Lowest hand wins. Everyone starts with $${"%,d".format(Table.STARTING_CHIPS)}.",
            textAlign = TextAlign.Center,
        )

        Text("Opponents", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (n in 1..3) {
                FilterChip(selected = opponents == n, onClick = { opponents = n }, label = { Text("$n") })
            }
        }

        Text("Difficulty", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (d in Difficulty.entries) {
                FilterChip(selected = difficulty == d, onClick = { difficulty = d }, label = { Text(d.label) })
            }
        }

        Button(onClick = { game.newGame(opponents, difficulty) }, modifier = Modifier.fillMaxWidth()) {
            Text("New game")
        }
        OutlinedButton(
            onClick = game::resumeGame,
            enabled = game.hasSavedGame,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (game.hasSavedGame) "Resume saved game" else "No saved game yet")
        }
        game.notice?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = game::showHowTo) { Text("How to play") }
            TextButton(onClick = game::cycleTheme) { Text(game.theme.label) }
        }

        val missing = game.images.missingCards.size
        if (missing > 0) {
            Text(
                "$missing of 53 cards have no picture yet, so they're drawn as plain cards.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
        if (game.images.unrecognized.isNotEmpty()) {
            Text(
                "These picture names don't follow the naming scheme: " + game.images.unrecognized.joinToString(", "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

val Difficulty.label: String
    get() = when (this) {
        Difficulty.EASY -> "Easy"
        Difficulty.MEDIUM -> "Medium"
        Difficulty.HARD -> "Hard"
    }

val ThemeChoice.label: String
    get() = when (this) {
        ThemeChoice.SYSTEM -> "Display: like phone"
        ThemeChoice.LIGHT -> "Display: light"
        ThemeChoice.DARK -> "Display: dark"
    }

@Composable
fun HowToScreen(game: GameViewModel, modifier: Modifier) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("How to play", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        for ((heading, body) in HOW_TO) {
            Text(heading, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(body)
        }
        Button(onClick = game::closeHowTo, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}

private val HOW_TO = listOf(
    "The goal" to
        "The lowest hand wins. Aces are always low, and straights and flushes don't count. " +
        "The best possible hand is A-2-3-4-5, called the Wheel.",
    "Comparing hands" to
        "Compare the highest card in each hand first; the lower one wins. If they match, compare the next highest, " +
        "and so on. 7-5-4-3-2 beats 7-6-4-3-2 because 5 is lower than 6.",
    "Pairs" to
        "Any hand without a pair beats any hand with one. Between two paired hands the lowest pair wins, " +
        "and a pair of Aces is the lowest pair of all.",
    "The joker" to
        "The deck has one joker. It counts as the lowest card you don't already have. " +
        "With A-2-4-6 and the joker, the joker is a 3.",
    "A round" to
        "1. Everyone puts in a \$25 ante.\n" +
        "2. Everyone gets five cards.\n" +
        "3. First betting round.\n" +
        "4. The draw: stand pat, or throw away up to three cards and get new ones.\n" +
        "5. Second betting round.\n" +
        "6. Showdown: everyone still in shows their cards and the lowest hand takes the pot.",
    "Betting" to
        "Every bet and raise is \$100. Each betting round allows one bet and up to three raises. " +
        "Check means pass without betting. Call means match the bet. Fold means give up the hand.",
    "Short of money?" to
        "The house will buy your belongings, from your socks to your gold watch. Each sale is a loan: " +
        "whenever you win a pot, 10% goes toward your oldest loan, and the item comes back once it's paid off. " +
        "Tap Vault to see what the house is holding.",
    "Tips" to
        "Be patient and fold weak hands. Drawing three cards rarely works. " +
        "A player who stands pat probably has a good hand, unless they're bluffing.",
)
