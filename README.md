# Lowball

A California Lowball poker game for Android. You play against one to three
computer opponents at Easy, Medium or Hard. The lowest hand wins, aces are
low, and straights and flushes don't count.

No real money is involved. The game never uses the Internet or Bluetooth, and
the only thing it stores is your saved game.

## Building it

1. Open this folder in Android Studio (File > Open).
2. Wait for Android Studio to finish syncing.
3. Plug in your phone, or start an emulator, and press the green Run button.

## Card pictures

Put the WebP card pictures in `app/src/main/assets/cards/`, named like this:

| File | Meaning |
| --- | --- |
| `card_3d_01.webp` | Three of diamonds, first picture |
| `card_ac_08.webp` | Ace of clubs, eighth picture |
| `joker_01.webp` | A joker |
| `back_01.webp` | A card back |

Ranks are `a 2 3 4 5 6 7 8 9 t j q k` and suits are `c d h s`. A card can have
any number of pictures, and the game picks one at random each time it's dealt.
Cards without a picture are drawn as plain cards.

## How the code is organized

- `engine/` holds the rules: the deck, hand ranking, betting, the draw, the
  house loans, the computer opponents, and saving. It has automatic tests
  (`./gradlew :engine:test`).
- `app/` holds the Android screens.
