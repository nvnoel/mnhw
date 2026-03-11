package com.shinigami.client.manager

import android.content.Context
import android.content.DialogInterface
import android.text.InputType
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.shinigami.client.R

object DialogManager {

  private fun MaterialAlertDialogBuilder.style(ctx: Context, title: String): MaterialAlertDialogBuilder {
    val titleView = LayoutInflater.from(ctx).inflate(R.layout.view_dialog_title, null)
    titleView.findViewById<TextView>(R.id.txt_title).text = title
    setCustomTitle(titleView)
    setBackground(ctx.getDrawable(R.drawable.bg_dialog))
    return this
  }

  fun info(ctx: Context, title: String, msg: String, btn: String = "OK", onDone: (() -> Unit)? = null) {
    val view = LayoutInflater.from(ctx).inflate(R.layout.view_dialog_message, null)
    view.findViewById<TextView>(R.id.txt_message).text = msg

    MaterialAlertDialogBuilder(ctx)
      .style(ctx, title)
      .setView(view)
      .setPositiveButton(btn) { d: DialogInterface, _: Int -> d.dismiss(); onDone?.invoke() }
      .setCancelable(false)
      .show()
  }

  fun error(ctx: Context, msg: String, retry: (() -> Unit)? = null) {
    val view = LayoutInflater.from(ctx).inflate(R.layout.view_dialog_message, null)
    view.findViewById<TextView>(R.id.txt_message).text = msg

    MaterialAlertDialogBuilder(ctx)
      .style(ctx, "Terjadi Kesalahan")
      .setView(view)
      .setNegativeButton("Tutup") { d: DialogInterface, _: Int -> d.dismiss() }
      .apply {
        retry?.let {
          setPositiveButton("Coba Lagi") { d: DialogInterface, _: Int -> it(); d.dismiss() }
        }
      }
      .show()
  }

  fun confirm(
    ctx: Context,
    title: String,
    msg: String,
    yes: String,
    no: String = "Batal",
    onYes: () -> Unit,
    onNo: (() -> Unit)? = null
  ) {
    val view = LayoutInflater.from(ctx).inflate(R.layout.view_dialog_message, null)
    view.findViewById<TextView>(R.id.txt_message).text = msg

    MaterialAlertDialogBuilder(ctx)
      .style(ctx, title)
      .setView(view)
      .setPositiveButton(yes) { _: DialogInterface, _: Int -> onYes() }
      .setNegativeButton(no) { d: DialogInterface, _: Int -> onNo?.invoke(); d.dismiss() }
      .setCancelable(false)
      .show()
  }

  fun prompt(
    ctx: Context,
    title: String,
    msg: String,
    default: String = "",
    type: Int = InputType.TYPE_CLASS_TEXT,
    onDone: (String) -> Unit,
    onCancel: (() -> Unit)? = null
  ) {
    val view = LayoutInflater.from(ctx).inflate(R.layout.view_dialog_prompt, null)
    val txtMessage = view.findViewById<TextView>(R.id.txt_message)
    val input = view.findViewById<EditText>(R.id.input)

    txtMessage.text = msg
    input.setText(default)
    input.inputType = type

    MaterialAlertDialogBuilder(ctx)
      .style(ctx, title)
      .setView(view)
      .setPositiveButton("OK") { _: DialogInterface, _: Int -> onDone(input.text.toString()) }
      .setNegativeButton("Batal") { d: DialogInterface, _: Int -> onCancel?.invoke(); d.dismiss() }
      .setCancelable(false)
      .show()

    input.requestFocus()
    input.postDelayed({
      val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
      imm?.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }, 100)
  }
}
