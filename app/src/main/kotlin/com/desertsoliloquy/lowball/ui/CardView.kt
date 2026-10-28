package com.desertsoliloquy.lowball.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.desertsoliloquy.lowball.GameViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lowball.engine.Card
import lowball.engine.Suit

/** Card pictures recently shown, so they aren't read from storage over and over. */
private object PictureCache {
    private val cache = LruCache<String, ImageBitmap>(40)

    suspend fun load(context: Context, name: String): ImageBitmap? {
        cache.get(name)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open("${GameViewModel.CARD_FOLDER}/$name.webp").use { stream ->
                    // Half size is plenty for a phone screen and uses a quarter of the memory.
                    val options = BitmapFactory.Options().apply { inSampleSize = 2 }
                    BitmapFactory.decodeStream(stream, null, options)?.asImageBitmap()
                }
            }.getOrNull()?.also { cache.put(name, it) }
        }
    }
}

private val CardShape = RoundedCornerShape(6.dp)

/** A card face up. Uses the chosen picture, or draws a plain card when there is none. */
@Composable
fun CardFace(card: Card, image: String?, width: Dp, modifier: Modifier = Modifier) {
    val picture = rememberPicture(image)
    Box(
        modifier
            .width(width)
            .aspectRatio(5f / 7f)
            .clip(CardShape)
            .background(Color.White)
            .border(1.dp, Color.Gray, CardShape),
        contentAlignment = Alignment.Center,
    ) {
        if (picture != null) {
            Image(picture, card.toString(), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            PlainFace(card, width)
        }
    }
}

/** A card face down, showing this round's back picture. */
@Composable
fun CardBack(image: String?, width: Dp, modifier: Modifier = Modifier) {
    val picture = rememberPicture(image)
    Box(
        modifier
            .width(width)
            .aspectRatio(5f / 7f)
            .clip(CardShape)
            .background(Color(0xFF7A1F1F))
            .border(1.dp, Color.Gray, CardShape),
    ) {
        if (picture != null) {
            Image(picture, "Card back", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Box(Modifier.fillMaxSize().padding(4.dp).border(1.dp, Color(0xFFE0C080), RoundedCornerShape(4.dp)))
        }
    }
}

@Composable
private fun rememberPicture(name: String?): ImageBitmap? {
    val context = LocalContext.current
    val picture by produceState<ImageBitmap?>(null, name) {
        value = name?.let { PictureCache.load(context, it) }
    }
    return picture
}

@Composable
private fun PlainFace(card: Card, width: Dp) {
    val red = card.suit == Suit.HEARTS || card.suit == Suit.DIAMONDS
    val label = if (card.isJoker) "JOKER" else rankLabel(card) + "\n" + suitSymbol(card.suit!!)
    Text(
        label,
        color = if (red) Color(0xFFC62828) else Color.Black,
        fontWeight = FontWeight.Bold,
        fontSize = (width.value / if (card.isJoker) 6 else 3).sp,
        lineHeight = (width.value / 2.6).sp,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

private fun rankLabel(card: Card): String = when (card.rank!!.value) {
    1 -> "A"; 11 -> "J"; 12 -> "Q"; 13 -> "K"
    else -> card.rank!!.value.toString()
}

fun suitSymbol(suit: Suit): String = when (suit) {
    Suit.CLUBS -> "♣"
    Suit.DIAMONDS -> "♦"
    Suit.HEARTS -> "♥"
    Suit.SPADES -> "♠"
}
