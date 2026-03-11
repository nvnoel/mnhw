package com.shinigami.client.ui

import android.app.Activity
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import android.widget.TextView

class DebugActivity : Activity() {

  companion object {
    private val errorMap = mapOf(
      "StringIndexOutOfBoundsException" to "Invalid string operation\n",
      "IndexOutOfBoundsException" to "Invalid list operation\n",
      "ArithmeticException" to "Invalid arithmetical operation\n",
      "NumberFormatException" to "Invalid toNumber block operation\n",
      "ActivityNotFoundException" to "Invalid intent operation\n"
    )
  }

  override fun onCreate(state: Bundle?) {
    super.onCreate(state)

    val error = intent?.getStringExtra("error") ?: ""
    val formatted = formatError(error)

    title = "$title Crashed"
    setContentView(createScrollView(formatted))
  }

  private fun formatError(raw: String): SpannableStringBuilder {
    val sb = SpannableStringBuilder()

    if (raw.isEmpty()) {
      sb.append("No error message available.")
      return sb
    }

    val lines = raw.split("\n")
    if (lines.isEmpty()) {
      sb.append("No error message available.")
      return sb
    }

    val errType = lines[0]
    errorMap[errType]?.let { sb.append(it) }

    lines.drop(1).forEach { line ->
      sb.append(line)
      sb.append("\n")
    }

    return sb
  }

  private fun createScrollView(msg: SpannableStringBuilder) =
    HorizontalScrollView(this).apply {
      addView(ScrollView(this@DebugActivity).apply {
        addView(TextView(this@DebugActivity).apply {
          text = msg
          setTextIsSelectable(true)
        })
      })
    }
}