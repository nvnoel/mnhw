package com.shinigami.client.manager

import android.content.Context
import android.content.DialogInterface
import android.text.InputType
import android.view.LayoutInflater
import android.view.inputmethod.InputMethodManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.shinigami.client.databinding.ViewDialogMessageBinding
import com.shinigami.client.databinding.ViewDialogPromptBinding
import com.shinigami.client.databinding.ViewDialogTitleBinding
import com.shinigami.client.R

object DialogManager {

    private fun createStyledBuilder(ctx: Context, title: String): MaterialAlertDialogBuilder {
        val titleBinding = ViewDialogTitleBinding.inflate(LayoutInflater.from(ctx))
        titleBinding.txtTitle.text = title

        return MaterialAlertDialogBuilder(ctx)
            .setCustomTitle(titleBinding.root)
            .setBackground(ctx.getDrawable(R.drawable.bg_dialog))
    }

    fun info(ctx: Context, title: String, msg: String, btnText: String = "OK", onDone: (() -> Unit)? = null) {
        val binding = ViewDialogMessageBinding.inflate(LayoutInflater.from(ctx))
        binding.txtMessage.text = msg

        createStyledBuilder(ctx, title)
            .setView(binding.root)
            .setPositiveButton(btnText) { d: DialogInterface, _: Int -> d.dismiss(); onDone?.invoke() }
            .setCancelable(false)
            .show()
    }

    fun error(ctx: Context, msg: String, onRetry: (() -> Unit)? = null) {
        val binding = ViewDialogMessageBinding.inflate(LayoutInflater.from(ctx))
        binding.txtMessage.text = msg

        val builder = createStyledBuilder(ctx, "Terjadi Kesalahan")
            .setView(binding.root)
            .setNegativeButton("Tutup") { d: DialogInterface, _: Int -> d.dismiss() }

        if (onRetry != null) {
            builder.setPositiveButton("Coba Lagi") { d: DialogInterface, _: Int -> onRetry(); d.dismiss() }
        }
        builder.show()
    }

    fun confirm(
        ctx: Context,
        title: String,
        msg: String,
        yesText: String,
        noText: String = "Batal",
        onYes: () -> Unit,
        onNo: (() -> Unit)? = null
    ) {
        val binding = ViewDialogMessageBinding.inflate(LayoutInflater.from(ctx))
        binding.txtMessage.text = msg

        createStyledBuilder(ctx, title)
            .setView(binding.root)
            .setPositiveButton(yesText) { _: DialogInterface, _: Int -> onYes() }
            .setNegativeButton(noText) { d: DialogInterface, _: Int -> onNo?.invoke(); d.dismiss() }
            .setCancelable(false)
            .show()
    }

    fun prompt(
        ctx: Context,
        title: String,
        msg: String,
        defaultInput: String = "",
        inputType: Int = InputType.TYPE_CLASS_TEXT,
        onDone: (String) -> Unit,
        onCancel: (() -> Unit)? = null
    ) {
        val binding = ViewDialogPromptBinding.inflate(LayoutInflater.from(ctx))
        binding.txtMessage.text = msg
        binding.input.setText(defaultInput)
        binding.input.inputType = inputType

        createStyledBuilder(ctx, title)
            .setView(binding.root)
            .setPositiveButton("OK") { _: DialogInterface, _: Int -> onDone(binding.input.text.toString()) }
            .setNegativeButton("Batal") { d: DialogInterface, _: Int -> onCancel?.invoke(); d.dismiss() }
            .setCancelable(false)
            .show()

        binding.input.requestFocus()
        binding.input.postDelayed({
            val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(binding.input, InputMethodManager.SHOW_IMPLICIT)
        }, 150)
    }
}