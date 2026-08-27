package com.example.foldermind

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ClickableSpan
import android.view.View
import android.widget.TextView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tasklist.TaskListSpan
import android.text.method.LinkMovementMethod

fun setupInteractiveMarkdown(textView: TextView, markwon: Markwon, markdown: String, onToggle: (Int) -> Unit) {
    val spanned = markwon.toMarkdown(markdown)
    if (spanned is SpannableStringBuilder) {
        val taskListSpans = spanned.getSpans(0, spanned.length, TaskListSpan::class.java)

        // Sort spans by their start offset to reliably identify the block index
        val sortedSpans = taskListSpans.sortedBy { spanned.getSpanStart(it) }

        sortedSpans.forEachIndexed { index, taskListSpan ->
            val start = spanned.getSpanStart(taskListSpan)
            val end = spanned.getSpanEnd(taskListSpan)

            val clickableSpan = object : ClickableSpan() {
                override fun onClick(widget: View) {
                    onToggle(index)
                }
            }

            // Apply a clickable span over the checkbox
            spanned.setSpan(clickableSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    textView.text = spanned
    textView.movementMethod = LinkMovementMethod.getInstance()
}
