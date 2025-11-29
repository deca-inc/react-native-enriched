package com.swmansion.enriched.spans

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.Spanned
import android.text.style.LeadingMarginSpan
import android.text.style.LineHeightSpan
import com.swmansion.enriched.spans.interfaces.EnrichedParagraphSpan
import com.swmansion.enriched.styles.HtmlStyle
import kotlin.math.ceil
import kotlin.math.roundToInt

class EnrichedOrderedListSpan(private var index: Int, private val htmlStyle: HtmlStyle) : LeadingMarginSpan, LineHeightSpan, EnrichedParagraphSpan {
  // Cache whether this is the first item in the list to avoid repeated lookups
  private var isFirstItemCached: Boolean? = null
  private var cacheCheckedForText: CharSequence? = null

  override fun getLeadingMargin(first: Boolean): Int {
    return htmlStyle.olMarginLeft + htmlStyle.olGapWidth
  }

  override fun drawLeadingMargin(
    canvas: Canvas,
    paint: Paint,
    x: Int,
    dir: Int,
    top: Int,
    baseline: Int,
    bottom: Int,
    t: CharSequence?,
    start: Int,
    end: Int,
    first: Boolean,
    layout: Layout?
  ) {
    if (first) {
      val text = "$index."
      val width = paint.measureText(text)

      val yPosition = baseline.toFloat()
      val xPosition = (htmlStyle.olMarginLeft + x - width / 2) * dir

      val originalColor = paint.color
      val originalTypeface = paint.typeface

      paint.color = htmlStyle.olMarkerColor ?: originalColor
      paint.typeface = getTypeface(htmlStyle.olMarkerFontWeight, originalTypeface)
      canvas.drawText(text, xPosition, yPosition, paint)

      paint.color = originalColor
      paint.typeface = originalTypeface
    }
  }

  private fun getTypeface(fontWeight: Int?, originalTypeface: Typeface): Typeface {
    return if (fontWeight == null) {
      originalTypeface
    } else if (android.os.Build.VERSION.SDK_INT >= 28) {
      Typeface.create(originalTypeface, fontWeight, false)
    } else {
      // Fallback for API < 28: only bold/normal supported
      if (fontWeight == Typeface.BOLD) {
        Typeface.create(originalTypeface, Typeface.BOLD)
      } else {
        Typeface.create(originalTypeface, Typeface.NORMAL)
      }
    }
  }

  fun getIndex(): Int {
    return index
  }

  fun setIndex(i: Int) {
    index = i
  }

  override fun chooseHeight(
    text: CharSequence?,
    start: Int,
    end: Int,
    spanstartv: Int,
    lineHeight: Int,
    fm: Paint.FontMetricsInt
  ) {
    // Early return if nothing to do
    if (text == null) return

    val spacingBefore = htmlStyle.olSpacingBefore
    val spacingAfter = htmlStyle.olSpacingAfter
    val listLineHeight = htmlStyle.olLineSpacing
    val itemSpacing = htmlStyle.olItemSpacing

    // Early return if no spacing to apply
    if (spacingBefore <= 0 && spacingAfter <= 0 && listLineHeight <= 0 && itemSpacing <= 0) return

    val spannedText = text as? Spanned ?: return
    val isFirstLineOfSpan = spannedText.getSpanStart(this) == start

    // Apply absolute line height first using approach similar to LineHeightSpan.Standard
    // but with better centering for list items
    if (listLineHeight > 0) {
      val desiredHeight = listLineHeight.roundToInt()
      val currentHeight = fm.descent - fm.ascent

      if (currentHeight > 0 && desiredHeight != currentHeight) {
        // Maintain the baseline position ratio to keep text centered
        // Calculate what percentage of the original height is above/below baseline
        val ascentRatio = fm.ascent.toFloat() / currentHeight
        val descentRatio = fm.descent.toFloat() / currentHeight

        // Apply those ratios to the new desired height
        fm.ascent = (desiredHeight * ascentRatio).roundToInt()
        fm.descent = (desiredHeight * descentRatio).roundToInt()

        // Adjust top/bottom to match
        fm.top = fm.ascent
        fm.bottom = fm.descent
      }
    }

    // Apply spacing before AFTER line height, but only on the first item of the list
    // Check if the previous paragraph (before the newline) was also a list item
    if (isFirstLineOfSpan && spacingBefore > 0) {
      // Use cached value if available for the same text
      val isFirstItem = if (cacheCheckedForText === text && isFirstItemCached != null) {
        isFirstItemCached!!
      } else {
        val spanStart = spannedText.getSpanStart(this)

        // Look backwards to find the previous paragraph
        // List items are separated by newlines, so check the character before the newline
        val hasPreviousListItem = if (spanStart > 1) {
          // Check if char at spanStart-1 is newline, and if so check spanStart-2 for list span
          val prevChar = text[spanStart - 1]
          if (prevChar == '\n') {
            // Check the character before the newline
            val previousSpans = spannedText.getSpans(spanStart - 2, spanStart - 2, EnrichedOrderedListSpan::class.java)
            previousSpans.isNotEmpty()
          } else {
            // No newline separator, check directly before
            val previousSpans = spannedText.getSpans(spanStart - 1, spanStart - 1, EnrichedOrderedListSpan::class.java)
            previousSpans.isNotEmpty()
          }
        } else {
          false
        }

        val result = !hasPreviousListItem
        // Cache the result
        isFirstItemCached = result
        cacheCheckedForText = text
        result
      }

      // Only apply spacingBefore if this is the first list item
      if (isFirstItem) {
        val addedSpacing = ceil(spacingBefore).toInt()
        fm.ascent -= addedSpacing
        fm.top -= addedSpacing
      }
    }

    // Apply item spacing and spacing after on the last line only
    val isLastLine = end >= text.length || text[end - 1] == '\n'
    if (isLastLine) {
      var totalSpacing = 0f

      // Add item spacing between list items
      if (itemSpacing > 0) {
        totalSpacing += itemSpacing
      }

      // Add spacing after on the very last line of the list
      // (This would need logic to detect if it's the last item in the list)
      // For now, just add it to every list item's last line
      if (spacingAfter > 0) {
        totalSpacing += spacingAfter
      }

      if (totalSpacing > 0) {
        val addedSpacing = ceil(totalSpacing).toInt()
        fm.descent += addedSpacing
        fm.bottom += addedSpacing
      }
    }
  }
}
