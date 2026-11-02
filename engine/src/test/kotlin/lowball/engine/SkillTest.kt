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
        val hardVsMedium = netPerRound(Difficulty.HARD, Difficulty.MEDIUM, 13)
        println("Hard vs Easy: $hardVsEasy, Medium vs Easy: $mediumVsEasy, Hard vs Medium: $hardVsMedium per round")
        assertTrue(hardVsEasy > 0, "Hard should beat Easy")
        assertTrue(mediumVsEasy > 0, "Medium should beat Easy")
        assertTrue(hardVsMedium > 0, "Hard should beat Medium")
    }

    /** A player who bets and raises the maximum every time, whatever cards they hold. */
    private fun bluffer(t: Table, seat: Int, drawing: Opponent): Action {
        val legal = t.legalActions()
        return when {
            t.phase == Phase.DRAW || t.phase == Phase.ANTE -> drawing.decide(t, seat)
            ActionType.SELL in legal && t.players[seat].chips < t.amountToCall(seat) + Table.MAX_WAGER ->
                Action.Sell(t.players[seat].items.last())
            ActionType.BET in legal -> Action.Bet(Table.WAGERS.last { it <= t.players[seat].chips })
            ActionType.RAISE in legal -> Action.Raise(Table.WAGERS.last { it <= t.players[seat].chips - t.amountToCall(seat) })
            ActionType.CALL in legal -> Action.Call
            ActionType.CHECK in legal -> Action.Check
            else -> Action.Fold
        }
    }

    /** The bluffer's average winnings per round in seat 0 against [opponents] computer players. */
    private fun blufferNet(difficulty: Difficulty, opponents: Int, seed: Int): Double {
        val random = Random(seed)
        var net = 0L
        var rounds = 0
        repeat(30) {
            val t = Table.newGame(opponents, difficulty, random = random)
            val ai = Opponent(difficulty, random)
            val drawing = Opponent(Difficulty.MEDIUM, random)
            while (t.phase != Phase.GAME_OVER && t.roundNumber < 150) {
                val before = t.players[0].chips
                t.startRound()
                if (t.phase == Phase.GAME_OVER) break
                while (t.phase != Phase.ROUND_OVER) {
                    val seat = t.toAct!!
                    t.act(if (seat == 0) bluffer(t, seat, drawing) else ai.decide(t, seat))
                }
                net += t.players[0].chips - before
                rounds++
            }
        }
        return net.toDouble() / rounds
    }

    @Test
    fun constantBluffingDoesNotPay() {
        for (difficulty in listOf(Difficulty.MEDIUM, Difficulty.HARD)) {
            for (opponents in 1..3) {
                val net = blufferNet(difficulty, opponents, opponents * 7 + difficulty.ordinal)
                println("Bluffer vs $opponents $difficulty: $net per round")
                assertTrue(net < 0, "Always betting the maximum should lose money against $difficulty")
            }
        }
    }
}
