package lowball.engine

import kotlin.random.Random

/**
 * Betting for Hard players, after Chris and Tom Ferguson, "The Endgame in Poker".
 *
 * - Every bet and raise is the maximum, so the size of a bet says nothing about the hand.
 * - Before the draw, bet when the chance of winning is at least a fair share (one half heads-up).
 * - After the draw, bet the best hands for value and bluff with some of the worst, in the paper's
 *   proportion: bluffs make up bet/(pot + bet) of the value bets. Bluffs only happen heads-up.
 * - Facing a bet, call when the chance of winning is at least the pot odds. The bettor is assumed to
 *   bluff some of the time, and more often if they bet more than most players do, so a habitual
 *   bettor gets called.
 *
 * The chance of winning comes from dealing a few hundred imaginary hands to the other players that
 * fit what they did: how many cards they drew, and whether they bet. The player never peeks at real
 * cards. The paper covers two players and no raises; with more players or raises this is a close
 * approximation, not the exact answer.
 */
internal class HardBrain(private val random: Random) {

    fun chooseBet(table: Table, p: Player, me: Opponent): Action {
        val legal = table.legalActions()
        val toCall = table.amountToCall(p.seat)
        val players = table.activeSeats.size
        val beforeDraw = table.phase == Phase.FIRST_BETTING
        val bettor = table.lastAggressor?.takeIf { toCall > 0 && it != p.seat }
        val potOdds = if (toCall == 0) 0.0 else toCall.toDouble() / (table.pot + toCall)

        // How often the bettor is bluffing: a little to start with, more for someone who bets a lot.
        val bluffShare = bettor?.let {
            val habit = table.players[it].aggression / NORMAL_AGGRESSION
            (0.5 * potOdds * habit).coerceIn(0.05, 0.6)
        } ?: 0.0

        val win = winChance(table, p, me, bettor, bluffShare)
        val fairShare = 1.0 / players

        val wantsToRaise: Boolean
        val wantsToCall: Boolean
        if (toCall == 0) {
            wantsToCall = false
            wantsToRaise = when {
                beforeDraw -> win >= maxOf(0.5, fairShare + 0.1)
                win >= if (players == 2) 0.6 else 0.5 -> true
                // A measured bluff with one of the worst hands, heads-up only.
                players == 2 && win < 0.2 -> random.nextDouble() < 0.6 * bluffRatio(table.pot)
                else -> false
            }
        } else {
            wantsToCall = win >= potOdds
            wantsToRaise = if (beforeDraw) win >= maxOf(0.55, fairShare + 0.15) else win >= 0.65
        }

        if (ActionType.SELL in legal && p.chips < toCall + Table.MAX_WAGER &&
            (wantsToRaise || (wantsToCall && p.chips < toCall))
        ) {
            return Action.Sell(p.items.last())
        }

        return when {
            wantsToRaise && ActionType.BET in legal -> Action.Bet(largest(p.chips))
            wantsToRaise && ActionType.RAISE in legal -> Action.Raise(largest(p.chips - toCall))
            ActionType.CHECK in legal -> Action.Check
            (wantsToCall || wantsToRaise) && ActionType.CALL in legal -> Action.Call
            else -> Action.Fold
        }
    }

    /** The paper's ratio of bluffs to value bets for a maximum bet into [pot]. */
    private fun bluffRatio(pot: Int): Double = Table.MAX_WAGER.toDouble() / (pot + Table.MAX_WAGER)

    private fun largest(canAfford: Int): Int = Table.WAGERS.last { it <= minOf(Table.MAX_WAGER, canAfford) }

    /**
     * The share of imaginary deals this player wins (a tie counts as a part win).
     * [bettor]'s imaginary hand is a good one, except [bluffShare] of the time.
     */
    fun winChance(table: Table, p: Player, me: Opponent, bettor: Int?, bluffShare: Double, deals: Int = SAMPLES): Double {
        val beforeDraw = table.phase == Phase.FIRST_BETTING
        val others = table.activeSeats.filter { it != p.seat }
        if (others.isEmpty()) return 1.0
        val mine = p.cards
        val unseen = Deck.fullDeck() - mine.toSet()
        val myDiscards = if (beforeDraw) me.chooseDiscards(p, table) else emptySet()

        var total = 0.0
        repeat(deals) {
            val pool = unseen.toMutableList()
            val myHand = mine.toMutableList()
            for (i in myDiscards) myHand[i] = take(pool)
            val best = HandEvaluator.evaluate(myHand)

            var beaten = false
            var ties = 0
            for (seat in others) {
                val strong = seat == bettor && random.nextDouble() >= bluffShare
                val theirs = HandEvaluator.evaluate(imagine(pool, table.players[seat], beforeDraw, strong))
                val c = theirs.compareTo(best)
                if (c < 0) { beaten = true; break }
                if (c == 0) ties++
            }
            if (!beaten) total += 1.0 / (ties + 1)
        }
        return total / deals
    }

    /** An imaginary final hand for [other], dealt from [pool], that fits what they've done. */
    private fun imagine(pool: MutableList<Card>, other: Player, beforeDraw: Boolean, strong: Boolean): List<Card> {
        val drew = other.cardsDrawn
        var hand: List<Card> = emptyList()
        var discards: Set<Int> = emptySet()
        for (attempt in 0 until TRIES) {
            hand = peek(pool, Table.HAND_SIZE)
            discards = drawFor(hand)
            val fits = when {
                beforeDraw -> !strong || discards.size <= 1
                drew == null -> true
                drew != discards.size -> false
                !strong -> true
                drew == 0 -> true
                else -> null // decided after the draw below
            }
            if (fits == false) continue
            if (fits == null) {
                // A bettor after the draw: they probably made a hand nine-low or better.
                val after = drawFrom(pool, hand, discards, remove = false)
                if (isGood(after)) break else continue
            }
            break
        }
        repeat(Table.HAND_SIZE) { pool.removeAt(0) }
        return drawFrom(pool, hand, discards, remove = true)
    }

    /** What an ordinary sensible player throws away: nothing with a ten-low or better, else down to an eight. */
    private fun drawFor(hand: List<Card>): Set<Int> {
        val made = HandEvaluator.evaluate(hand)
        if (made.category == Category.NO_PAIR && made.highCard <= 10) return emptySet()
        return Opponent.discardsFor(hand, 8)
    }

    private fun isGood(hand: List<Card>): Boolean {
        val made = HandEvaluator.evaluate(hand)
        return made.category == Category.NO_PAIR && made.highCard <= 9
    }

    /** Shuffles [count] random cards to the front of [pool] without removing them. */
    private fun peek(pool: MutableList<Card>, count: Int): List<Card> {
        for (i in 0 until count) {
            val j = random.nextInt(i, pool.size)
            val t = pool[i]; pool[i] = pool[j]; pool[j] = t
        }
        return pool.subList(0, count).toList()
    }

    /** Replaces [discards] with cards from [pool]; when not [remove], only looks (cards after the hand). */
    private fun drawFrom(pool: MutableList<Card>, hand: List<Card>, discards: Set<Int>, remove: Boolean): List<Card> {
        val result = hand.toMutableList()
        var next = Table.HAND_SIZE
        for (i in discards.sorted()) {
            result[i] = if (remove) {
                take(pool)
            } else {
                val j = random.nextInt(next, pool.size)
                val t = pool[next]; pool[next] = pool[j]; pool[j] = t
                pool[next++]
            }
        }
        return result
    }

    private fun take(pool: MutableList<Card>): Card = pool.removeAt(random.nextInt(pool.size))

    companion object {
        const val SAMPLES = 300
        private const val TRIES = 40
    }
}
