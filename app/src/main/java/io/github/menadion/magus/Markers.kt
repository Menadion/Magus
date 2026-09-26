package io.github.menadion.magus

import android.graphics.Shader
import android.graphics.Matrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ColorMatrix
import android.graphics.BitmapShader
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.util.TypedValue
import androidx.core.content.res.ResourcesCompat
import kotlin.math.max

// The pictures on the map: a name tag above a dot with the person's first letter, plus a badge for
// paused or quiet. One picture per person and state, drawn once and cached by MapLibre under its id.
// Spec: design/handoff/HANDOFF.md, sections 2 and 3.
//
// Each picture is drawn so its centre is the dot's centre. With the map anchoring pictures at their
// centre, the GPS point lands on the dot, not on the tag.
object Markers {
    enum class State { YOU, SHARING, PAUSED, QUIET }

    private const val PRIMARY = 0xFF2F55C4.toInt()
    private const val FAMILY_GREEN = 0xFF26803F.toInt()
    private const val PAUSED_GREY = 0xFF6E7482.toInt()
    private const val ON_SURFACE = 0xFF1A1B20.toInt()
    private const val ON_SURFACE_VARIANT = 0xFF45464F.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val SHADOW = 0x33000000

    fun state(member: Member, now: Long): State = when (member.dotState(now)) {
        "paused" -> State.PAUSED
        "quiet" -> State.QUIET
        else -> State.SHARING
    }

    // The id the map caches the picture under. Same inputs, same picture.
    fun id(state: State, name: String, selected: Boolean, you: Boolean = false, photo: ByteArray? = null, personColor: Int = FAMILY_GREEN) =
        "marker:" + state.name + ":" + (if (selected) "sel:" else "") + (if (you) "you:" else "") +
            Photos.key(photo) + ":" + Integer.toHexString(personColor) + ":" + name

    // you: this is my own dot, so it keeps the white centre even while paused.
    fun draw(
        context: Context,
        state: State,
        name: String,
        selected: Boolean,
        you: Boolean = false,
        photo: ByteArray? = null,
        personColor: Int = FAMILY_GREEN, // their own colour, from PersonColors
    ): Bitmap {
        val metrics = context.resources.displayMetrics
        val density = metrics.density
        fun dp(v: Float) = v * density
        fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, metrics)

        val bold = boldFont(context)
        val letter = name.trim().take(1).uppercase()
        val tagText = if (state == State.YOU || you) context.getString(R.string.you) else name
        val whiteCentre = state == State.YOU || you

        // Sizes from the handoff, in dp.
        val dotSize = when {
            selected -> 46f
            state == State.YOU -> 42f
            else -> 38f
        }
        val whiteRing = 3f
        val primaryRing = if (selected) 3f else 0f
        val outerRadius = dp(dotSize) / 2 + dp(primaryRing)
        val badgeSize = dp(20f)
        val badgePoke = dp(7f)
        val hasBadge = state == State.PAUSED || state == State.QUIET

        // Tag: text plus 3 dp above and below, 10 dp left and right.
        val tagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = bold
            textSize = sp(16f)
        }
        val tagTextWidth = tagPaint.measureText(tagText)
        val tagWidth = tagTextWidth + dp(20f)
        val tagHeight = dp(22f) + dp(6f)
        val gap = dp(4f)
        val margin = dp(6f) // room for the shadow

        // The dot sits in the vertical middle; the tag above it is mirrored by empty space below.
        val halfHeight = tagHeight + gap + outerRadius + margin
        val width = (max(tagWidth, outerRadius * 2 + badgePoke) + margin * 2).toInt() + 1
        val height = (halfHeight * 2).toInt() + 1
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.density = metrics.densityDpi
        val canvas = Canvas(bitmap)
        val cx = width / 2f
        val cy = height / 2f

        // Tag.
        val tagFill = if (selected || state == State.YOU) PRIMARY else WHITE
        val tagTextColor = when {
            selected || state == State.YOU -> WHITE
            state == State.PAUSED -> ON_SURFACE_VARIANT
            else -> ON_SURFACE
        }
        val tagTop = cy - outerRadius - gap - tagHeight
        val tagRect = RectF(cx - tagWidth / 2, tagTop, cx + tagWidth / 2, tagTop + tagHeight)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tagFill
            setShadowLayer(dp(5f), 0f, dp(1f), SHADOW)
        }
        canvas.drawRoundRect(tagRect, dp(10f), dp(10f), fill)
        tagPaint.color = tagTextColor
        canvas.drawText(
            tagText,
            cx - tagTextWidth / 2,
            tagRect.centerY() - (tagPaint.ascent() + tagPaint.descent()) / 2,
            tagPaint,
        )

        // Dot, outside in: primary ring (selected only), white ring, coloured centre.
        val plain = Paint(Paint.ANTI_ALIAS_FLAG)
        if (selected) {
            plain.color = PRIMARY
            canvas.drawCircle(cx, cy, outerRadius, plain)
        }
        plain.color = WHITE
        plain.setShadowLayer(dp(4f), 0f, dp(1f), SHADOW)
        canvas.drawCircle(cx, cy, dp(dotSize) / 2, plain)
        plain.clearShadowLayer()
        val innerRadius = dp(dotSize) / 2 - dp(whiteRing)
        plain.color = when (state) {
            State.YOU -> PRIMARY
            State.SHARING -> personColor
            State.PAUSED -> PAUSED_GREY
            State.QUIET -> WHITE
        }
        canvas.drawCircle(cx, cy, innerRadius, plain)
        if (state == State.QUIET) {
            // Hollow: a 4 dp ring in their colour inside the white ring, white middle.
            val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = personColor
                style = Paint.Style.STROKE
                strokeWidth = dp(4f)
            }
            canvas.drawCircle(cx, cy, innerRadius - dp(2f), ring)
        }

        // Middle: the picture if there is one, else a white centre for you, or the first letter.
        val picture = Photos.decode(photo)
        if (picture != null) {
            val ringWidth = if (state == State.QUIET) dp(4f) else dp(2.5f)
            val radius = innerRadius - ringWidth
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = BitmapShader(picture, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                    val scale = radius * 2 / picture.width
                    setLocalMatrix(Matrix().apply {
                        setScale(scale, scale)
                        postTranslate(cx - radius, cy - radius)
                    })
                }
                if (state == State.PAUSED) {
                    colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
                }
            }
            canvas.drawCircle(cx, cy, radius, paint)
        } else if (whiteCentre) {
            plain.color = WHITE
            canvas.drawCircle(cx, cy, dp(6f), plain)
        } else {
            val letterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = bold
                textSize = sp(16f)
                color = if (state == State.QUIET) personColor else WHITE
            }
            canvas.drawText(
                letter,
                cx - letterPaint.measureText(letter) / 2,
                cy - (letterPaint.ascent() + letterPaint.descent()) / 2,
                letterPaint,
            )
        }

        // Badge at the bottom right: pause bars or a clock.
        if (hasBadge) {
            val bx = cx + dp(dotSize) / 2 - badgeSize / 2 + badgePoke / 2
            val by = cy + dp(dotSize) / 2 - badgeSize / 2 + badgePoke / 2
            plain.color = WHITE
            canvas.drawCircle(bx, by, badgeSize / 2, plain)
            plain.color = ON_SURFACE
            canvas.drawCircle(bx, by, badgeSize / 2 - dp(2f), plain)
            val mark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = WHITE }
            if (state == State.PAUSED) {
                canvas.drawRect(bx - dp(3f), by - dp(3.5f), bx - dp(1f), by + dp(3.5f), mark)
                canvas.drawRect(bx + dp(1f), by - dp(3.5f), bx + dp(3f), by + dp(3.5f), mark)
            } else {
                mark.style = Paint.Style.STROKE
                mark.strokeWidth = dp(1.5f)
                canvas.drawCircle(bx, by, dp(5f), mark)
                canvas.drawLine(bx, by - dp(3f), bx, by, mark)
                canvas.drawLine(bx, by, bx + dp(2.2f), by, mark)
            }
        }
        return bitmap
    }

    private fun boldFont(context: Context): Typeface {
        val base = ResourcesCompat.getFont(context, R.font.figtree) ?: Typeface.DEFAULT
        return if (Build.VERSION.SDK_INT >= 28) {
            Typeface.create(base, 700, false)
        } else {
            Typeface.create(base, Typeface.BOLD)
        }
    }
}
