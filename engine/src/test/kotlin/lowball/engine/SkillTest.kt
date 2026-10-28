package lowball.engine

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

class SkillTest {

    /** Plays [strong] in seat 0 against [weak] heads-up and returns seat 0's net winnings per round. */
    private fun netPerRound(strong: Difficulty, weak: Difficulty, seed: Int): Double {
        val random = Random(seed)
        var net = 0L
        var rounds = 0
        repeat(40) {
            val t = Table.newGame(1, weak, random = random)
            val brains = listOf(Opponent(strong, random), Opponent(weak, random))
            while (rounds < 100_000 && t.phase != Phase.GAME_OVER && t.roundNumber < 200) {
                val before = t.players[0].chips
                t.startRound()
                if (t.phase == Phase.GAME_OVER) break
                while (t.phase != Phase.ROUND_OVER) t.act(brains[t.toAct!!].decide(t, t.toAct!!))
                net += t.players[0].chips - before
                rounds++
            }
        }
        return net.toDouble() / rounds
    }

    @Test
    fun harderOpponentsWinMoreMoney() {
        val hardVsEasy = netPerRound(Difficulty.HARD, Difficulty.EASY, 11)
        val mediumVsEasy = netPerRound(Difficulty.MEDIUM, Difficulty.EASY, 12)
        println("Hard vs Easy: $hardVsEasy per round, Medium vs Easy: $mediumVsEasy per round")
        assertTrue(hardVsEasy > 0, "Hard should beat Easy")
        assertTrue(mediumVsEasy > 0, "Medium should beat Easy")
    }
}
