package com.desertsoliloquy.lowball.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.desertsoliloquy.lowball.GameViewModel
import kotlinx.coroutines.delay
import lowball.engine.Action
import lowball.engine.ActionType
import lowball.engine.Phase
import lowball.engine.PlayerSnapshot
import lowball.engine.Table
import lowball.engine.TableSnapshot

private fun money(amount: Int) = "$" + "%,d".format(amount)

@Composable
fun TableScreen(game: GameViewModel, modifier: Modifier) {
    val view = game.view ?: return
    val colors = LocalTableColors.current
    var showVault by remember { mutableStateOf(false) }
    var confirmQuit by remember { mutableStateOf(false) }
    var showSell by remember { mutableStateOf(false) }

    // Cards picked for discarding. Cleared whenever a new hand is dealt.
    var discards by remember(view.roundNumber) { mutableStateOf(emptySet<Int>()) }

    LaunchedEffect(game.notice) {
        if (game.notice != null) {
            delay(2_000)
            game.clearNotice()
        }
    }

    BoxWithConstraints(modifier.background(colors.felt)) {
        // Cards grow with the screen: five of yours fill the width, opponents' take a bit over half.
        val handCard = ((maxWidth - 56.dp) / 5).coerceAtMost(200.dp)
        val opponentCard = ((maxWidth * 0.6f - 24.dp) / 5).coerceAtMost(120.dp)
        Column(Modifier.fillMaxSize()) {
            TopBar(
                view = view,
                onSave = game::saveGame,
                onVault = { showVault = true },
                onHowTo = game::showHowTo,
                onTheme = game::cycleTheme,
                onQuit = { confirmQuit = true },
            )

            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (p in view.players.filter { !it.isHuman }) {
                    OpponentRow(p, view, opponentCard)
                }
                PotAndNews(view, game.notice)
            }

            HumanArea(
                view = view,
                cardWidth = handCard,
                discards = discards,
                onToggle = { i ->
                    discards = when {
                        i in discards -> discards - i
                        discards.size < Table.MAX_DISCARDS -> discards + i
                        else -> discards
                    }
                },
            )

            ActionBar(
                view = view,
                legal = game.legal,
                discards = discards,
                onAction = { action ->
                    if (action is Action.Draw) discards = emptySet()
                    game.play(action)
                },
                onSell = { showSell = true },
                onNextRound = game::nextRound,
                onNewGame = game::quitToMenu,
            )
        }
    }

    if (showVault) VaultDialog(view) { showVault = false }
    if (showSell) {
        SellDialog(
            human = view.players.first { it.isHuman },
            onSell = { item ->
                game.play(Action.Sell(item))
                showSell = false
            },
            onClose = { showSell = false },
        )
    }
    if (confirmQuit) {
        AlertDialog(
            onDismissRequest = { confirmQuit = false },
            title = { Text("Quit this game?") },
            text = { Text("You can save it first and pick up where you left off later.") },
            confirmButton = {
                TextButton(onClick = {
                    game.saveGame()
                    confirmQuit = false
                    game.quitToMenu()
                }) { Text("Save and quit") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        confirmQuit = false
                        game.quitToMenu()
                    }) { Text("Quit") }
                    TextButton(onClick = { confirmQuit = false }) { Text("Keep playing") }
                }
            },
        )
    }
}

@Composable
private fun TopBar(
    view: TableSnapshot,
    onSave: () -> Unit,
    onVault: () -> Unit,
    onHowTo: () -> Unit,
    onTheme: () -> Unit,
    onQuit: () -> Unit,
) {
    val colors = LocalTableColors.current
    Column(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.25f)).padding(horizontal = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Round ${view.roundNumber} · ${view.difficulty.label}",
                color = colors.feltText,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            TextButton(onClick = onTheme) { Text("Light/Dark", color = colors.feltText) }
        }
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onSave) { Text("Save", color = colors.feltText) }
            TextButton(onClick = onVault) { Text("Vault", color = colors.feltText) }
            TextButton(onClick = onHowTo) { Text("How to", color = colors.feltText) }
            TextButton(onClick = onQuit) { Text("Quit", color = colors.feltText) }
        }
    }
}

@Composable
private fun OpponentRow(p: PlayerSnapshot, view: TableSnapshot, cardWidth: Dp) {
    val colors = LocalTableColors.current
    val showdown = view.phase == Phase.ROUND_OVER && view.lastResult?.shownHands?.containsKey(p.seat) == true
    val acting = view.toAct == p.seat
    Row(
        Modifier
            .fillMaxWidth()
            .border(2.dp, if (acting) colors.highlight else Color.Transparent, RoundedCornerShape(8.dp))
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.name, color = colors.feltText, fontWeight = FontWeight.Bold)
                if (view.dealer == p.seat) DealerButton()
            }
            Text(money(p.chips), color = colors.feltText)
            Text(status(p, view), color = colors.feltText, style = MaterialTheme.typography.bodySmall)
            if (showdown) {
                Text(
                    view.lastResult!!.shownHands[p.seat]!!.describe(),
                    color = colors.highlight,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (p.hand.isNotEmpty() && !p.folded) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (dealt in p.hand) {
                    if (showdown) CardFace(dealt.card, dealt.image, cardWidth) else CardBack(view.backImage, cardWidth)
                }
            }
        }
    }
}

@Composable
private fun DealerButton() {
    Box(
        Modifier.padding(start = 6.dp).background(Color.White, CircleShape).padding(horizontal = 6.dp),
    ) {
        Text("D", color = Color.Black, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

private fun status(p: PlayerSnapshot, view: TableSnapshot): String = when {
    p.eliminated -> "Out of the game"
    p.folded -> "Folded"
    view.toAct == p.seat && view.phase != Phase.ROUND_OVER -> "Thinking…"
    p.cardsDrawn == 0 -> "Stood pat"
    p.cardsDrawn != null -> "Drew ${p.cardsDrawn}"
    view.contributed[p.seat] > 0 -> "In for ${money(view.contributed[p.seat])}"
    else -> ""
}

@Composable
private fun PotAndNews(view: TableSnapshot, notice: String?) {
    val colors = LocalTableColors.current
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        val potShown = view.lastResult?.pot ?: view.pot
        Text(
            "Pot ${money(potShown)}",
            color = colors.highlight,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        if (view.currentBet > 0 && view.phase != Phase.ROUND_OVER) {
            Text("Current bet ${money(view.currentBet)}", color = colors.feltText)
        }
        notice?.let { Text(it, color = colors.highlight) }
        Spacer(Modifier.height(4.dp))
        for (event in view.events.takeLast(5)) {
            Text(event.text, color = colors.feltText, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun HumanArea(view: TableSnapshot, cardWidth: Dp, discards: Set<Int>, onToggle: (Int) -> Unit) {
    val colors = LocalTableColors.current
    val me = view.players.first { it.isHuman }
    val drawing = view.phase == Phase.DRAW && view.toAct == me.seat
    Column(
        Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.15f)).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("You: ${money(me.chips)}", color = colors.feltText, fontWeight = FontWeight.Bold)
            if (view.dealer == me.seat) DealerButton()
            if (me.loans.isNotEmpty()) {
                Text(
                    "  ·  owe the house ${money(me.loans.sumOf { it.balance })}",
                    color = colors.feltText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (me.hand.isNotEmpty()) {
            val myHand = lowball.engine.HandEvaluator.evaluate(me.hand.map { it.card })
            Text(myHand.describe(), color = colors.highlight, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                me.hand.forEachIndexed { i, dealt ->
                    val picked = i in discards
                    CardFace(
                        dealt.card,
                        dealt.image,
                        cardWidth,
                        Modifier
                            .offset(y = if (picked) -(cardWidth / 5) else 0.dp)
                            .border(3.dp, if (picked) colors.highlight else Color.Transparent, RoundedCornerShape(6.dp))
                            .clickable(enabled = drawing) { onToggle(i) },
                    )
                }
            }
            if (drawing) {
                Text(
                    "Tap up to three cards to throw away",
                    color = colors.feltText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        } else if (me.folded && !me.eliminated) {
            Text("You're sitting out this round", color = colors.feltText)
        }
    }
}

@Composable
private fun ActionBar(
    view: TableSnapshot,
    legal: List<ActionType>,
    discards: Set<Int>,
    onAction: (Action) -> Unit,
    onSell: () -> Unit,
    onNextRound: () -> Unit,
    onNewGame: () -> Unit,
) {
    val me = view.players.first { it.isHuman }
    val toCall = view.currentBet - view.contributed[me.seat]
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when {
            view.phase == Phase.GAME_OVER -> {
                Text(gameOverText(view), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Button(onClick = onNewGame, modifier = Modifier.fillMaxWidth()) { Text("Back to menu") }
            }
            view.phase == Phase.ROUND_OVER -> {
                Text(resultText(view), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Button(onClick = onNextRound, modifier = Modifier.fillMaxWidth()) { Text("Deal next hand") }
            }
            legal.isEmpty() -> {
                val who = view.toAct?.let { view.players[it].name } ?: ""
                Text("Waiting for $who…", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            ActionType.DRAW in legal -> {
                Button(onClick = { onAction(Action.Draw(discards)) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (discards.isEmpty()) "Stand pat" else "Draw ${discards.size}")
                }
            }
            view.phase == Phase.ANTE -> {
                Text(
                    "You need ${money(Table.ANTE)} for the ante. Sell something to the house, or sit this round out.",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onSell, modifier = Modifier.weight(1f)) { Text("Sell to house") }
                    OutlinedButton(onClick = { onAction(Action.SitOut) }, modifier = Modifier.weight(1f)) { Text("Sit out") }
                }
            }
            else -> {
                if (toCall > 0) {
                    Text("${money(toCall)} to call", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (ActionType.CHECK in legal) {
                        Button(onClick = { onAction(Action.Check) }, modifier = Modifier.weight(1f)) { Text("Check") }
                    }
                    if (ActionType.CALL in legal) {
                        Button(onClick = { onAction(Action.Call) }, modifier = Modifier.weight(1f)) {
                            Text("Call ${money(toCall)}")
                        }
                    }
                    if (ActionType.FOLD in legal) {
                        OutlinedButton(onClick = { onAction(Action.Fold) }, modifier = Modifier.weight(1f)) { Text("Fold") }
                    }
                }
                val betting = ActionType.BET in legal
                if (betting || ActionType.RAISE in legal) {
                    Text(
                        if (betting) "Bet" else "Raise by",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (amount in Table.WAGERS) {
                            Button(
                                onClick = { onAction(if (betting) Action.Bet(amount) else Action.Raise(amount)) },
                                enabled = me.chips >= toCall + amount,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(money(amount))
                            }
                        }
                    }
                }
                if (ActionType.SELL in legal) {
                    OutlinedButton(onClick = onSell, modifier = Modifier.fillMaxWidth()) {
                        Text("Short of money? Sell to the house")
                    }
                }
            }
        }
    }
}

private fun resultText(view: TableSnapshot): String {
    val result = view.lastResult ?: return ""
    val names = result.winners.map { view.players[it].name }
    val lines = mutableListOf<String>()
    lines += when {
        names.isEmpty() -> "Nobody played this round."
        names.size == 1 && result.uncontested -> "${names[0]} wins ${money(result.pot)}. Everyone else folded."
        names.size == 1 -> "${names[0]} wins ${money(result.pot)} with ${result.shownHands[result.winners[0]]!!.describe()}."
        else -> "${names.joinToString(" and ")} split ${money(result.pot)}."
    }
    for ((seat, paid) in result.loanPayments) {
        lines += "${view.players[seat].name} paid the house ${money(paid)} toward loans."
    }
    for ((seat, items) in result.itemsReturned) {
        lines += "${view.players[seat].name} got back: ${items.joinToString(", ") { it.displayName }}."
    }
    return lines.joinToString("\n")
}

private fun gameOverText(view: TableSnapshot): String {
    val me = view.players.first { it.isHuman }
    return if (me.eliminated) {
        "Game over. You're out of money and the house has everything, including your underwear."
    } else {
        "You cleaned out the table! You finish with ${money(me.chips)} after ${view.roundNumber} rounds."
    }
}

@Composable
private fun VaultDialog(view: TableSnapshot, onClose: () -> Unit) {
    val loans = view.players.flatMap { p -> p.loans.map { p to it } }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("The House Vault") },
        text = {
            if (loans.isEmpty()) {
                Text("The house isn't holding anything. Yet.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(loans) { (p, loan) ->
                        Column {
                            Text("${loan.item.displayName}, from ${p.name}", fontWeight = FontWeight.Bold)
                            Text("Loaned ${money(loan.amount)} · still owes ${money(loan.balance)}")
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } },
    )
}

@Composable
private fun SellDialog(human: PlayerSnapshot, onSell: (lowball.engine.Item) -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Sell to the house") },
        text = {
            LazyColumn {
                items(human.items) { item ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onSell(item) }.padding(vertical = 10.dp),
                    ) {
                        Text(item.displayName, modifier = Modifier.weight(1f))
                        Text(money(item.price), fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider()
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Never mind") } },
    )
}
