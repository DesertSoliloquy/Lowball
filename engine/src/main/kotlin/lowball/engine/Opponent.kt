package lowball.engine

import kotlin.random.Random

/**
 * How the computer players think.
 *
 * Easy players keep too many high cards and call almost anything.
 * Medium players draw sensibly and bet good hands.
 * Hard players also weigh the size of the pot, notice who stood pat, and sometimes bluff by
 * standing pat on a bad hand (a "snow").
 */
class Opponent(private val difficulty: Difficulty, private val random: Random = Random.Default) {

    private val style = when (difficulty) {
        Difficulty.EASY -> Style(patLimit = 11, keepLimit = 10, raiseAt = 0.85, callAt = 0.2, noise = 0.15, snowChance = 0.0)
        Difficulty.MEDIUM -> Style(patLimit = 9, keepLimit = 8, raiseAt = 0.7, callAt = 0.4, noise = 0.05, snowChance = 0.0)
        Difficulty.HARD -> Style(patLimit = 9, keepLimit = 8, raiseAt = 0.62, callAt = 0.4, noise = 0.05, snowChance = 0.06)
    }

    fun decide(table: Table, seat: Int): Action {
        val p = table.players[seat]
        return when (table.phase) {
            Phase.ANTE -> p.items.lastOrNull()?.let { Action.Sell(it) } ?: Action.SitOut
            Phase.DRAW -> Action.Draw(chooseDiscards(p, table))
            Phase.FIRST_BETTING, Phase.SECOND_BETTING -> chooseBet(table, p)
            Phase.ROUND_OVER, Phase.GAME_OVER -> error("Nothing to decide during ${table.phase}")
        }
    }

    /** Positions of the cards to throw away. An empty set means stand pat. */
    fun chooseDiscards(p: Player, table: Table? = null): Set<Int> {
        if (p.snowing) return emptySet()
        val made = HandEvaluator.evaluate(p.cards)
        if (made.category == Category.NO_PAIR && made.highCard <= patLimit(table)) return emptySet()

        val keepLimit = if (difficulty == Difficulty.HARD && sevenDraw(p)) 7 else style.keepLimit
        return discardsFor(p.cards, keepLimit)
    }

    private fun chooseBet(table: Table, p: Player): Action {
        val legal = table.legalActions()
        val toCall = table.amountToCall(p.seat)

        if (table.phase == Phase.FIRST_BETTING && !p.snowing && style.snowChance > 0 &&
            p.cardsDrawn == null && random.nextDouble() < style.snowChance
        ) {
            p.snowing = true
        }

        val strength = if (p.snowing) 0.9 else strength(table, p) + (random.nextDouble() * 2 - 1) * style.noise
        val potOdds = if (toCall == 0) 0.0 else toCall.toDouble() / (table.pot + toCall)

        val wantsToRaise = strength >= style.raiseAt
        val wantsToCall = when (difficulty) {
            Difficulty.HARD -> strength >= potOdds + 0.1 || strength >= style.callAt
            else -> strength >= style.callAt
        }

        // Short of money for something worth doing: sell the cheapest belonging first.
        if (ActionType.SELL in legal && p.chips < toCall + Table.BET_SIZE && (wantsToRaise || (wantsToCall && p.chips < toCall))) {
            return Action.Sell(p.items.last())
        }

        return when {
            wantsToRaise && ActionType.BET in legal -> Action.Bet
            wantsToRaise && ActionType.RAISE in legal -> Action.Raise
            ActionType.CHECK in legal -> Action.Check
            wantsToCall && ActionType.CALL in legal -> Action.Call
            else -> Action.Fold
        }
    }

    /** A rough 0-to-1 guess at how good this hand is against the players still in. */
    fun strength(table: Table, p: Player): Double {
        val made = HandEvaluator.evaluate(p.cards)
        val beforeDraw = table.phase == Phase.FIRST_BETTING
        var score = if (beforeDraw && !(made.category == Category.NO_PAIR && made.highCard <= patLimit(table))) {
            drawingStrength(p.cards)
        } else {
            madeStrength(made)
        }
        val others = table.activeSeats.count { it != p.seat }
        score -= 0.08 * (others - 1)

        if (difficulty == Difficulty.HARD && !beforeDraw) {
            // Someone who stood pat probably has a real hand; someone who drew three probably doesn't.
            val patOpponents = table.activeSeats.count { it != p.seat && table.players[it].cardsDrawn == 0 }
            val bigDrawers = table.activeSeats.count { it != p.seat && (table.players[it].cardsDrawn ?: 0) >= 3 }
            score += 0.05 * bigDrawers - 0.08 * patOpponents
        }
        return score.coerceIn(0.0, 1.0)
    }

    private fun patLimit(table: Table?): Int {
        // Heads-up, a hard player will stand pat on a ten.
        val headsUp = table != null && table.activeSeats.size == 2
        return if (difficulty == Difficulty.HARD && headsUp) 10 else style.patLimit
    }

    /** Four different cards seven or lower: draw one to them rather than keep an eight. */
    private fun sevenDraw(p: Player): Boolean =
        HandEvaluator.resolveJoker(p.cards).filter { it <= 7 }.distinct().size >= 4

    private data class Style(
        val patLimit: Int,
        val keepLimit: Int,
        val raiseAt: Double,
        val callAt: Double,
        val noise: Double,
        val snowChance: Double,
    )

    companion object {
        /**
         * Keeps the joker and the lowest card of each rank up to [keepLimit], throwing away pairs and
         * high cards (highest first), but never more than three cards.
         */
        fun discardsFor(cards: List<Card>, keepLimit: Int): Set<Int> {
            val seen = mutableSetOf<Int>()
            val candidates = mutableListOf<Pair<Int, Int>>() // position, how bad (higher = discard first)
            val order = cards.indices.sortedBy { cards[it].rank?.value ?: 0 }
            for (i in order) {
                val rank = cards[i].rank?.value ?: continue
                when {
                    rank in seen -> candidates.add(i to 100 + rank) // second card of a pair: always worst
                    rank > keepLimit -> { seen.add(rank); candidates.add(i to rank) }
                    else -> seen.add(rank)
                }
            }
            return candidates.sortedByDescending { it.second }.take(Table.MAX_DISCARDS).map { it.first }.toSet()
        }

        private fun madeStrength(hand: LowHand): Double = when {
            hand.category == Category.ONE_PAIR && hand.ranks[0] == 1 -> 0.12
            hand.category != Category.NO_PAIR -> 0.05
            else -> when (hand.highCard) {
                5 -> 1.0
                6 -> 0.95
                7 -> 0.85
                8 -> 0.72
                9 -> 0.6
                10 -> 0.45
                11 -> 0.35
                12 -> 0.25
                else -> 0.18
            }
        }

        /** Before the draw: how promising the cards worth keeping are. */
        private fun drawingStrength(cards: List<Card>): Double {
            val keep = cards.size - discardsFor(cards, 8).size
            val values = HandEvaluator.resolveJoker(cards).distinct().sorted().take(keep)
            val high = values.lastOrNull() ?: 13
            return when (keep) {
                4 -> when {
                    high <= 5 -> 0.6
                    high <= 7 -> 0.52
                    else -> 0.42
                }
                3 -> if (high <= 5) 0.32 else 0.26
                else -> 0.15
            }
        }
    }
}
