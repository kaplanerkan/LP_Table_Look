package com.lotus.lptablelook.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.Window
import android.widget.ImageView
import android.widget.TextView
import com.lotus.lptablelook.R

class ProgressDialog(context: Context) {

    private val dialog: Dialog = Dialog(context)
    private val tvTitle: TextView
    private val tvMessage: TextView
    private val ivIcon: ImageView

    init {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCancelable(false)

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_progress, null)
        dialog.setContentView(view)

        tvTitle = view.findViewById(R.id.tvProgressTitle)
        tvMessage = view.findViewById(R.id.tvProgressMessage)
        ivIcon = view.findViewById(R.id.ivProgressIcon)
    }

    fun show(title: String, message: String) {
        tvTitle.text = title
        tvMessage.text = message
        if (!dialog.isShowing) {
            dialog.show()
        }
    }

    fun updateMessage(message: String) {
        tvMessage.text = message
    }

    fun dismiss() {
        if (dialog.isShowing) {
            dialog.dismiss()
        }
    }

    fun isShowing(): Boolean = dialog.isShowing

    companion object {
        fun showConnectionTest(context: Context): ProgressDialog {
            return ProgressDialog(context).apply {
                show("Verbindung wird getestet", "Bitte warten...")
            }
        }

        fun showSync(context: Context): ProgressDialog {
            return ProgressDialog(context).apply {
                show("Synchronisierung", "Daten werden geladen...")
            }
        }

        fun showTableLoading(context: Context, tableName: String): ProgressDialog {
            return ProgressDialog(context).apply {
                show("Tisch wird geladen", "$tableName - Daten werden abgerufen...")
            }
        }
    }
}
