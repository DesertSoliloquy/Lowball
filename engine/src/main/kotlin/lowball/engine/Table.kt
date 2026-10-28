package lowball.engine

import kotlin.random.Random

enum class Phase {
    /** Players short of the ante choose to sell something or sit out. */
    ANTE,
    FIRST_BETTING,
    DRAW,
    SECOND_BETTING,

    /** The round is finished; [Table.lastResult] says who won. Call [Table.startRound] for the next. */
    ROUND_OVER,

    /** The human is out of money and belongings, or every opponent is. */
    GAME_OVER,
}

enum class ActionType { CHECK, BET, CALL, RAISE, FOLD, DRAW, SELL, SIT_OUT }

sealed class Action(val type: ActionType) {
    data object Check : Action(ActionType.CHECK)
    data object Bet : Action(ActionType.BET)
    data object Call : Action(ActionType.CALL)
    data object Raise : Action(ActionType.RAISE)
    data object Fold : Action(ActionType.FOLD)
    data object SitOut : Action(ActionType.SIT_OUT)

    /** Throws away the cards at [discards] (positions 0 to 4). An empty set stands pat. */
    data class Draw(val discards: Set<Int>) : Action(ActionType.DRAW)
    data class Sell(val item: Item) : Action(ActionType.SELL)
}

/** What happened when a round ended. */
data class RoundResult(
    val pot: Int,
    val winners: List<Int>,
    /** Each winner's share of the pot, by seat. */
    val shares: Map<Int, Int>,
    /** The hands shown down, by seat. Empty when everyone else folded. */
    val shownHands: Map<Int, LowHand>,
    /** Money each winner paid toward loans, by seat. */
    val loanPayments: Map<Int, Int>,
    /** Belongings returned when a loan was paid off, by seat. */
    val itemsReturned: Map<Int, List<Item>>,
) {
    val uncontested: Boolean get() = shownHands.isEmpty()
}

/** One line of the table's running commentary, e.g. "Whiskey Pete raises to $200". */
data class Event(val seat: Int?, val text: String)

/**
 * The game rules for one table: deals, betting, the draw, showdown and the house loans.
 * The screens ask [legalActions] for the buttons to show and pass the player's choice to [act].
 * Computer players' choices come from [Opponent.decide].
 */
class Table(
    val players: List<Player>,
    val difficulty: Difficulty,
    private val random: Random = Random.Default,
    private val images: CardImages? = null,
) {
    private val deck = Deck(random)

    var dealer: Int = 0
        private set
    var phase: Phase = Phase.ROUND_OVER
        private set
    var toAct: Int? = null
        private set
    var pot: Int = 0
        private set
    var roundNumber: Int = 0
        private set

    /** The amount each player must have put in during this betting round to stay in. */
    var currentBet: Int = 0
        private set

    /** Bets plus raises so far in this betting round (at most [MAX_BETS]). */
    var betsMade: Int = 0
        private set

    /** What each seat has put in during this betting round. */
    val contributed = IntArray(players.size)

    var backImage: String? = null
        private set
    var lastResult: RoundResult? = null
        private set

    /** Commentary for the current round. */
    val events: MutableList<Event> = mutableListOf()

    private val pending = mutableSetOf<Int>()
    private val queue = ArrayDeque<Int>()

    val human: Player get() = players.first { it.isHuman }

    val activeSeats: List<Int> get() = players.filter { it.inHand }.map { it.seat }

    fun amountToCall(seat: Int): Int = currentBet - contributed[seat]

    fun startRound() {
        check(phase == Phase.ROUND_OVER) { "Can't start a round during $phase" }
        for (p in players) {
            if (!p.eliminated && p.chips < ANTE && p.items.isEmpty()) {
                p.eliminated = true
                log(p.seat, "${p.name} is out of money and has nothing left to sell")
            }
        }
        if (human.eliminated || players.none { !it.isHuman && !it.eliminated }) {
            phase = Phase.GAME_OVER
            toAct = null
            return
        }

        roundNumber++
        if (roundNumber > 1) dealer = nextSeat(dealer) { !players[it].eliminated }
        events.clear()
        lastResult = null
        pot = 0
        for (p in players) {
            p.hand.clear()
            p.folded = p.eliminated
            p.cardsDrawn = null
            p.snowing = false
        }
        deck.shuffle()
        backImage = images?.pickBack(random)

        phase = Phase.ANTE
        queue.clear()
        for (seat in seatsFromLeftOfDealer()) {
            val p = players[seat]
            if (p.chips >= ANTE) payAnte(p) else queue.addLast(seat)
        }
        advanceAnte()
    }

    fun legalActions(): List<ActionType> {
        val seat = toAct ?: return emptyList()
        val p = players[seat]
        return when (phase) {
            Phase.ANTE -> listOf(ActionType.SELL, ActionType.SIT_OUT)
            Phase.DRAW -> listOf(ActionType.DRAW)
            Phase.FIRST_BETTING, Phase.SECOND_BETTING -> {
                val toCall = amountToCall(seat)
                buildList {
                    if (toCall == 0) {
                        add(ActionType.CHECK)
                        if (betsMade == 0 && p.chips >= BET_SIZE) add(ActionType.BET)
                    } else {
                        if (p.chips >= toCall) add(ActionType.CALL)
                        if (betsMade < MAX_BETS && p.chips >= toCall + BET_SIZE) add(ActionType.RAISE)
                        add(ActionType.FOLD)
                    }
                    if (p.chips < toCall + BET_SIZE && p.items.isNotEmpty()) add(ActionType.SELL)
                }
            }
            Phase.ROUND_OVER, Phase.GAME_OVER -> emptyList()
        }
    }

    fun act(action: Action) {
        val seat = checkNotNull(toAct) { "Nobody is due to act" }
        require(action.type in legalActions()) { "${action.type} isn't allowed now" }
        val p = players[seat]
        when (action) {
            is Action.Sell -> {
                p.sell(action.item)
                log(seat, "${p.name} sells ${action.item.displayName} to the house for $${action.item.price}")
                if (phase == Phase.ANTE && p.chips >= ANTE) {
                    payAnte(p)
                    queue.removeFirst()
                    advanceAnte()
                }
            }
            Action.SitOut -> {
                p.folded = true
                log(seat, "${p.name} sits out this round")
                queue.removeFirst()
                advanceAnte()
            }
            is Action.Draw -> draw(p, action.discards)
            Action.Check -> {
                log(seat, "${p.name} checks")
                pending.remove(seat)
                afterBettingAction(seat)
            }
            Action.Bet, Action.Raise -> {
                pay(p, amountToCall(seat) + BET_SIZE)
                currentBet += BET_SIZE
                betsMade++
                log(seat, if (action == Action.Bet) "${p.name} bets $$BET_SIZE" else "${p.name} raises to $$currentBet")
                pending.clear()
                pending.addAll(activeSeats - seat)
                afterBettingAction(seat)
            }
            Action.Call -> {
                pay(p, amountToCall(seat))
                log(seat, "${p.name} calls")
                pending.remove(seat)
                afterBettingAction(seat)
            }
            Action.Fold -> {
                p.folded = true
                log(seat, "${p.name} folds")
                pending.remove(seat)
                afterBettingAction(seat)
            }
        }
    }

    private fun payAnte(p: Player) {
        p.chips -= ANTE
        pot += ANTE
    }

    private fun pay(p: Player, amount: Int) {
        p.chips -= amount
        contributed[p.seat] += amount
        pot += amount
    }

    private fun advanceAnte() {
        if (queue.isNotEmpty()) {
            toAct = queue.first()
            return
        }
        if (activeSeats.size < 2) {
            awardUncontested()
            return
        }
        for (seat in seatsFromLeftOfDealer()) {
            players[seat].hand.addAll(deck.deal(HAND_SIZE).map(::withImage))
        }
        log(null, "Round $roundNumber: cards are dealt")
        startBetting(Phase.FIRST_BETTING)
    }

    private fun startBetting(next: Phase) {
        phase = next
        currentBet = 0
        betsMade = 0
        contributed.fill(0)
        pending.clear()
        pending.addAll(activeSeats)
        toAct = seatsFromLeftOfDealer().first { it in pending }
    }

    private fun afterBettingAction(seat: Int) {
        if (activeSeats.size == 1) {
            awardUncontested()
            return
        }
        if (pending.isNotEmpty()) {
            toAct = nextSeat(seat) { it in pending }
            return
        }
        if (phase == Phase.FIRST_BETTING) {
            phase = Phase.DRAW
            queue.clear()
            queue.addAll(seatsFromLeftOfDealer())
            toAct = queue.first()
        } else {
            showdown()
        }
    }

    private fun draw(p: Player, discards: Set<Int>) {
        require(discards.size <= MAX_DISCARDS) { "You can draw at most $MAX_DISCARDS cards" }
        require(discards.all { it in 0 until HAND_SIZE }) { "Card positions are 0 to 4" }
        for (index in discards) p.hand[index] = withImage(deck.deal())
        p.cardsDrawn = discards.size
        log(p.seat, if (discards.isEmpty()) "${p.name} stands pat" else "${p.name} draws ${discards.size}")
        queue.removeFirst()
        if (queue.isNotEmpty()) toAct = queue.first() else startBetting(Phase.SECOND_BETTING)
    }

    private fun showdown() {
        val hands = activeSeats.associateWith { HandEvaluator.evaluate(players[it].cards) }
        val best = hands.values.min()
        val winners = seatsFromLeftOfDealer().filter { hands[it] == best }
        finishRound(winners, hands)
    }

    private fun awardUncontested() {
        finishRound(activeSeats, emptyMap())
    }

    private fun finishRound(winners: List<Int>, shownHands: Map<Int, LowHand>) {
        val shares = mutableMapOf<Int, Int>()
        if (winners.isNotEmpty()) {
            val each = pot / winners.size
            winners.forEach { shares[it] = each }
            // An odd dollar goes to the first winner left of the dealer.
            shares[winners.first()] = each + pot % winners.size
        }
        val payments = mutableMapOf<Int, Int>()
        val returned = mutableMapOf<Int, List<Item>>()
        for ((seat, share) in shares) {
            val p = players[seat]
            p.chips += share
            val owed = p.loans.sumOf { it.balance }
            val payment = minOf(share * LOAN_REPAYMENT_PERCENT / 100, owed)
            if (payment > 0) {
                payments[seat] = payment
                val back = p.repayLoans(payment)
                if (back.isNotEmpty()) returned[seat] = back
            }
            val how = shownHands[seat]?.let { " with ${it.describe()}" } ?: ""
            log(seat, "${p.name} wins $$share$how")
        }
        lastResult = RoundResult(pot, winners, shares, shownHands, payments, returned)
        pot = 0
        toAct = null
        phase = Phase.ROUND_OVER
    }

    private fun withImage(card: Card) = DealtCard(card, images?.pickFront(card, random))

    private fun seatsFromLeftOfDealer(): List<Int> =
        (1..players.size).map { (dealer + it) % players.size }.filter { players[it].inHand }

    private fun nextSeat(from: Int, accept: (Int) -> Boolean): Int =
        (1..players.size).map { (from + it) % players.size }.first(accept)

    private fun log(seat: Int?, text: String) {
        events.add(Event(seat, text))
    }

    companion object {
        const val STARTING_CHIPS = 5_000
        const val ANTE = 25
        const val BET_SIZE = 100

        /** One bet plus three raises. */
        const val MAX_BETS = 4
        const val HAND_SIZE = 5
        const val MAX_DISCARDS = 3
        const val LOAN_REPAYMENT_PERCENT = 10

        /** Seat 0 is the human; opponents get random names from [OpponentNames]. */
        fun newGame(
            opponents: Int,
            difficulty: Difficulty,
            humanName: String = "You",
            random: Random = Random.Default,
            images: CardImages? = null,
        ): Table {
            require(opponents in 1..3) { "Play against 1, 2 or 3 opponents" }
            val names = OpponentNames.pick(opponents, random)
            val players = listOf(Player(0, humanName, true, STARTING_CHIPS)) +
                names.mapIndexed { i, name -> Player(i + 1, name, false, STARTING_CHIPS) }
            return Table(players, difficulty, random, images)
        }
    }
}
