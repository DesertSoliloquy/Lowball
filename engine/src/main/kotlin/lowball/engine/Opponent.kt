package lowball.engine

import kotlin.random.Random

/**
 * How the computer players think.
 *
 * Easy players keep too many high cards and call almost anything.
 * Medium players draw sensibly and bet good hands.
 * Hard players also weigh the size of the pot and notice who stood pat. They don't bluff: in testing,
 * standing pat on a bad hand (a "snow") lost them money, so [Style.snowChance] is zero for every level.
 * Medium and Hard players remember how often each player bets, and call a habitual bettor more often.
 */
class Opponent(private val difficulty: Difficulty, private val random: Random = Random.Default) {

    private val style = when (difficulty) {
        Difficulty.EASY -> Style(patLimit = 11, keepLimit = 10, raiseAt = 0.85, callAt = 0.2, noise = 0.15, snowChance = 0.0)
        Difficulty.MEDIUM -> Style(patLimit = 9, keepLimit = 8, raiseAt = 0.7, callAt = 0.38, noise = 0.05, snowChance = 0.0)
        Difficulty.HARD -> Style(patLimit = 9, keepLimit = 8, raiseAt = 0.62, callAt = 0.35, noise = 0.05, snowChance = 0.0)
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

        val noise = (random.nextDouble() * 2 - 1) * style.noise
        val strength = if (p.snowing) 0.9 else strength(table, p) + noise
        val potOdds = if (toCall == 0) 0.0 else toCall.toDouble() / (table.pot + toCall)

        // Only one player needs to call to catch a bluff, so calling doesn't get stricter with more players.
        // A player who bets far more often than usual is probably bluffing some of the time.
        val bettor = table.lastAggressor?.let { table.players[it] }
        val suspicion = when (difficulty) {
            Difficulty.EASY -> 0.0
            Difficulty.MEDIUM -> 0.6
            Difficulty.HARD -> 1.0
        } * ((bettor?.aggression ?: NORMAL_AGGRESSION) - NORMAL_AGGRESSION)
        val callStrength = if (p.snowing) 0.9 else strength(table, p, crowded = false) + noise + suspicion.coerceIn(0.0, 0.3)

        val wantsToRaise = strength >= style.raiseAt
        val wantsToCall = when (difficulty) {
            Difficulty.EASY -> callStrength >= style.callAt
            Difficulty.MEDIUM -> callStrength >= minOf(style.callAt, potOdds + 0.08)
            Difficulty.HARD -> callStrength >= minOf(style.callAt, potOdds + 0.02)
        }

        // Short of money for something worth doing: sell the cheapest belonging first.
        if (ActionType.SELL in legal && p.chips < toCall + Table.MAX_WAGER && (wantsToRaise || (wantsToCall && p.chips < toCall))) {
            return Action.Sell(p.items.last())
        }

        return when {
            wantsToRaise && ActionType.BET in legal -> Action.Bet(wager(strength, p.chips))
            wantsToRaise && ActionType.RAISE in legal -> Action.Raise(wager(strength, p.chips - toCall))
            ActionType.CHECK in legal -> Action.Check
            wantsToCall && ActionType.CALL in legal -> Action.Call
            else -> Action.Fold
        }
    }

    /** Bigger bets with better hands, a little randomness so it can't be read, never more than [canAfford]. */
    private fun wager(strength: Double, canAfford: Int): Int {
        val wanted = when {
            strength >= 0.9 -> Table.MAX_WAGER
            difficulty == Difficulty.EASY -> Table.WAGERS.random(random)
            else -> Table.WAGERS[((strength - style.raiseAt) / (1 - style.raiseAt) * 4).toInt().coerceIn(1, 3)]
        }
        return Table.WAGERS.last { it <= minOf(wanted, canAfford) }
    }

    /** A rough 0-to-1 guess at how good this hand is against the players still in. */
    fun strength(table: Table, p: Player, crowded: Boolean = true): Double {
        val made = HandEvaluator.evaluate(p.cards)
        val beforeDraw = table.phase == Phase.FIRST_BETTING
        var score = if (beforeDraw && !(made.category == Category.NO_PAIR && made.highCard <= patLimit(table))) {
            drawingStrength(p.cards)
        } else {
            madeStrength(made)
        }
        if (crowded) {
            // Against more players, a hand needs to be better to be worth betting.
            val others = table.activeSeats.count { it != p.seat }
            score -= 0.08 * (others - 1)
        }

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
