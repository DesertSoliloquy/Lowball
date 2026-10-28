package lowball.engine

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TableTest {

    private fun table(opponents: Int = 2, seed: Int = 1) =
        Table.newGame(opponents, Difficulty.MEDIUM, random = Random(seed))

    @Test
    fun deckHas53Cards() {
        val deck = Deck.fullDeck()
        assertEquals(53, deck.size)
        assertEquals(53, deck.toSet().size)
        assertEquals(1, deck.count { it.isJoker })
    }

    @Test
    fun everyoneAntesAndGetsFiveCards() {
        val t = table(opponents = 3)
        t.startRound()
        assertEquals(Phase.FIRST_BETTING, t.phase)
        assertEquals(4 * Table.ANTE, t.pot)
        t.players.forEach {
            assertEquals(5, it.hand.size)
            assertEquals(Table.STARTING_CHIPS - Table.ANTE, it.chips)
        }
        // The player left of the dealer (seat 0) acts first.
        assertEquals(1, t.toAct)
    }

    @Test
    fun oneBetAndThreeRaisesThenOnlyCalls() {
        val t = table(opponents = 1)
        t.startRound()
        t.act(Action.Bet) // seat 1
        t.act(Action.Raise) // seat 0
        t.act(Action.Raise)
        t.act(Action.Raise)
        assertEquals(400, t.currentBet)
        assertEquals(listOf(ActionType.CALL, ActionType.FOLD), t.legalActions())
        t.act(Action.Call)
        assertEquals(Phase.DRAW, t.phase)
        assertEquals(2 * (Table.ANTE + 400), t.pot)
    }

    @Test
    fun checkingAroundGoesToTheDraw() {
        val t = table(opponents = 2)
        t.startRound()
        repeat(3) { t.act(Action.Check) }
        assertEquals(Phase.DRAW, t.phase)
    }

    @Test
    fun drawReplacesCardsAndIsCapped() {
        val t = table(opponents = 1)
        t.startRound()
        repeat(2) { t.act(Action.Check) }
        val seat = t.toAct!!
        val before = t.players[seat].cards
        t.act(Action.Draw(setOf(0, 1, 2)))
        val after = t.players[seat].cards
        assertEquals(before.drop(3), after.drop(3))
        assertEquals(3, t.players[seat].cardsDrawn)
        assertTrue(runCatching { t.act(Action.Draw(setOf(0, 1, 2, 3))) }.isFailure)
    }

    @Test
    fun lastPlayerStandingWinsWithoutShowing() {
        val t = table(opponents = 1)
        t.startRound()
        t.act(Action.Bet)
        t.act(Action.Fold)
        assertEquals(Phase.ROUND_OVER, t.phase)
        val result = assertNotNull(t.lastResult)
        assertTrue(result.uncontested)
        assertEquals(listOf(1), result.winners)
        assertEquals(Table.STARTING_CHIPS + Table.ANTE, t.players[1].chips)
    }

    @Test
    fun showdownPaysTheLowestHand() {
        val t = table(opponents = 2, seed = 7)
        t.startRound()
        repeat(3) { t.act(Action.Check) }
        repeat(3) { t.act(Action.Draw(emptySet())) }
        repeat(3) { t.act(Action.Check) }
        val result = assertNotNull(t.lastResult)
        val best = result.shownHands.values.min()
        result.winners.forEach { assertEquals(best, result.shownHands[it]) }
        assertEquals(3 * Table.ANTE, result.shares.values.sum())
    }

    @Test
    fun shortPlayerMustSellOrSitOut() {
        val t = table(opponents = 1)
        t.human.chips = 10
        t.startRound()
        assertEquals(Phase.ANTE, t.phase)
        assertEquals(0, t.toAct)
        t.act(Action.Sell(Item.UNDERWEAR))
        assertEquals(15, t.human.chips) // still short of the $25 ante
        t.act(Action.Sell(Item.LEFT_SOCK))
        assertEquals(Phase.FIRST_BETTING, t.phase)
        assertEquals(0, t.human.chips)
        assertEquals(listOf(Item.UNDERWEAR, Item.LEFT_SOCK), t.human.loans.map { it.item })
    }

    @Test
    fun sittingOutHeadsUpGivesTheAntesAway() {
        val t = table(opponents = 1)
        t.human.chips = 0
        t.startRound()
        t.act(Action.SitOut)
        assertEquals(Phase.ROUND_OVER, t.phase)
        assertEquals(listOf(1), t.lastResult!!.winners)
    }

    @Test
    fun brokeWithNothingToSellEndsTheGame() {
        val t = table(opponents = 1)
        t.human.chips = 0
        t.human.items.clear()
        t.startRound()
        assertEquals(Phase.GAME_OVER, t.phase)
        assertTrue(t.human.eliminated)
    }

    @Test
    fun winnersRepayTenPercentTowardTheOldestLoan() {
        val p = Player(0, "Test", true, 0)
        p.sell(Item.LEFT_SHOE) // $50
        p.sell(Item.IPHONE) // $500
        p.chips += 600 // won a $600 pot
        val back = p.repayLoans(60)
        assertEquals(listOf(Item.LEFT_SHOE), back)
        assertTrue(Item.LEFT_SHOE in p.items)
        assertEquals(1, p.loans.size)
        assertEquals(490, p.loans[0].balance)
        assertEquals(500, p.loans[0].amount)
        assertEquals(550 + 600 - 60, p.chips)
    }

    @Test
    fun shortStackedCallerIsOfferedTheHouse() {
        val t = table(opponents = 1)
        t.human.chips = 75
        t.startRound()
        t.act(Action.Bet) // seat 1 bets, human has $50 left
        assertFalse(ActionType.CALL in t.legalActions())
        assertTrue(ActionType.SELL in t.legalActions())
        t.act(Action.Sell(Item.PANTS))
        assertTrue(ActionType.CALL in t.legalActions())
    }

    /** Lets the computer play every seat, including the human's, for many rounds. */
    @Test
    fun computerPlayersFinishThousandsOfRounds() {
        for (difficulty in Difficulty.entries) {
            for (opponents in 1..3) {
                val random = Random(difficulty.ordinal * 10 + opponents)
                val t = Table.newGame(opponents, difficulty, random = random)
                val ai = Opponent(difficulty, random)
                var rounds = 0
                while (rounds < 1_000 && t.phase != Phase.GAME_OVER) {
                    t.startRound()
                    var steps = 0
                    while (t.phase != Phase.ROUND_OVER && t.phase != Phase.GAME_OVER) {
                        t.act(ai.decide(t, t.toAct!!))
                        assertTrue(++steps < 500, "Round never ended")
                    }
                    t.players.forEach { assertTrue(it.chips >= 0, "${it.name} went below zero") }
                    rounds++
                }
            }
        }
    }
}
