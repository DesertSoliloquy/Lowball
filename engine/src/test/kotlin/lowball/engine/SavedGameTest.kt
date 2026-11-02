package lowball.engine

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class SavedGameTest {

    @Test
    fun aSavedGameResumesExactlyWhereItLeftOff() {
        val random = Random(5)
        val t = Table.newGame(3, Difficulty.HARD, random = random)
        t.human.chips = 10
        t.startRound()
        t.act(Action.Sell(Item.GOLD_WATCH))
        t.act(Action.Bet())

        val text = SavedGame.encode(t)
        val resumed = SavedGame.decode(text)

        assertEquals(t.snapshot(), resumed.snapshot())
        assertEquals(t.legalActions(), resumed.legalActions())

        // Both copies keep playing identically from here.
        val ai = Opponent(Difficulty.MEDIUM, Random(1))
        val ai2 = Opponent(Difficulty.MEDIUM, Random(1))
        while (t.phase != Phase.ROUND_OVER) {
            t.act(ai.decide(t, t.toAct!!))
            resumed.act(ai2.decide(resumed, resumed.toAct!!))
        }
        assertEquals(t.lastResult, resumed.lastResult)
        assertEquals(t.players.map { it.chips }, resumed.players.map { it.chips })
    }
}
