package com.swmansion.enriched.spans

import android.graphics.Canvas
import android.graphics.Paint
import android.text.Layout
import android.text.Spanned
import android.text.style.LeadingMarginSpan
import android.text.style.LineHeightSpan
import com.swmansion.enriched.spans.interfaces.EnrichedParagraphSpan
import com.swmansion.enriched.styles.HtmlStyle
import kotlin.math.ceil
import kotlin.math.roundToInt

// https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/text/style/BulletSpan.java
class EnrichedUnorderedListSpan(private val htmlStyle: HtmlStyle) : LeadingMarginSpan, LineHeightSpan, EnrichedParagraphSpan {
  // Cache whether this is the first item in the list to avoid repeated lookups
  private var isFirstItemCached: Boolean? = null
  private var cacheCheckedForText: CharSequence? = null

  override fun getLeadingMargin(p0: Boolean): Int {
    return htmlStyle.ulBulletSize + htmlStyle.ulGapWidth + htmlStyle.ulMarginLeft
  }

  override fun drawLeadingMargin(
    canvas: Canvas,
    paint: Paint,
    x: Int,
    dir: Int,
    top: Int,
    baseline: Int,
    bottom: Int,
    text: CharSequence,
    start: Int,
    end: Int,
    first: Boolean,
    layout: Layout?
  ) {
    val spannedText = text as Spanned

    // Only draw bullet on the first line of this list item
    if (spannedText.getSpanStart(this) == start) {
      val style = paint.style
      val oldColor = paint.color
      paint.color = htmlStyle.ulBulletColor
      paint.style = Paint.Style.FILL

      val bulletRadius = htmlStyle.ulBulletSize / 2f

      // Center bullet vertically using the text metrics
      // Use baseline as the anchor point and position relative to the font metrics
      // This ensures the bullet is centered with the actual text height, not the line box
      val fm = paint.fontMetricsInt
      val textCenter = baseline + (fm.ascent + fm.descent) / 2f
      val yPosition = textCenter
      val xPosition = x + dir * bulletRadius + htmlStyle.ulMarginLeft

      canvas.drawCircle(xPosition, yPosition, bulletRadius, paint)

      paint.color = oldColor
      paint.style = style
    }
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

    val spacingBefore = htmlStyle.ulSpacingBefore
    val spacingAfter = htmlStyle.ulSpacingAfter
    val listLineHeight = htmlStyle.ulLineSpacing
    val itemSpacing = htmlStyle.ulItemSpacing

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
            val previousSpans = spannedText.getSpans(spanStart - 2, spanStart - 2, EnrichedUnorderedListSpan::class.java)
            previousSpans.isNotEmpty()
          } else {
            // No newline separator, check directly before
            val previousSpans = spannedText.getSpans(spanStart - 1, spanStart - 1, EnrichedUnorderedListSpan::class.java)
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
