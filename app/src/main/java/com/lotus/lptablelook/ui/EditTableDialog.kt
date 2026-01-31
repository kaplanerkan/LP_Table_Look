package com.lotus.lptablelook.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Window
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import com.lotus.lptablelook.R
import com.lotus.lptablelook.model.Table

class EditTableDialog(
    context: Context,
    private val table: Table,
    private val onSave: (isOval: Boolean, capacity: Int, width: Float, height: Float) -> Unit
) : Dialog(context) {

    private lateinit var tvDialogTitle: TextView
    private lateinit var tvCapacity: TextView
    private lateinit var rgShape: RadioGroup
    private lateinit var rbRectangle: RadioButton
    private lateinit var rbOval: RadioButton
    private lateinit var rgSize: RadioGroup
    private lateinit var rbSmall: RadioButton
    private lateinit var rbMedium: RadioButton
    private lateinit var rbLarge: RadioButton
    private lateinit var btnCapacityMinus: ImageButton
    private lateinit var btnCapacityPlus: ImageButton
    private lateinit var btnClose: ImageButton
    private lateinit var btnCancel: com.google.android.material.button.MaterialButton
    private lateinit var btnSave: com.google.android.material.button.MaterialButton

    private var currentCapacity: Int = table.capacity

    // Size presets (width x height)
    private val sizeSmall = Pair(100f, 80f)
    private val sizeMedium = Pair(140f, 100f)
    private val sizeLarge = Pair(180f, 120f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_edit_table)

        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        initViews()
        setupData()
        setupListeners()
    }

    private fun initViews() {
        tvDialogTitle = findViewById(R.id.tvDialogTitle)
        tvCapacity = findViewById(R.id.tvCapacity)
        rgShape = findViewById(R.id.rgShape)
        rbRectangle = findViewById(R.id.rbRectangle)
        rbOval = findViewById(R.id.rbOval)
        rgSize = findViewById(R.id.rgSize)
        rbSmall = findViewById(R.id.rbSmall)
        rbMedium = findViewById(R.id.rbMedium)
        rbLarge = findViewById(R.id.rbLarge)
        btnCapacityMinus = findViewById(R.id.btnCapacityMinus)
        btnCapacityPlus = findViewById(R.id.btnCapacityPlus)
        btnClose = findViewById(R.id.btnClose)
        btnCancel = findViewById(R.id.btnCancel)
        btnSave = findViewById(R.id.btnSave)
    }

    private fun setupData() {
        // Set title
        val tableName = if (table.name.isNotEmpty()) table.name else "Tisch ${table.number}"
        tvDialogTitle.text = "$tableName bearbeiten"

        // Set current shape
        if (table.isOval) {
            rbOval.isChecked = true
        } else {
            rbRectangle.isChecked = true
        }

        // Set current capacity
        currentCapacity = table.capacity
        updateCapacityDisplay()

        // Set current size
        when {
            table.width <= sizeSmall.first -> rbSmall.isChecked = true
            table.width >= sizeLarge.first -> rbLarge.isChecked = true
            else -> rbMedium.isChecked = true
        }
    }

    private fun setupListeners() {
        btnClose.setOnClickListener { dismiss() }
        btnCancel.setOnClickListener { dismiss() }

        btnCapacityMinus.setOnClickListener {
            if (currentCapacity > 1) {
                currentCapacity--
                updateCapacityDisplay()
            }
        }

        btnCapacityPlus.setOnClickListener {
            if (currentCapacity < 20) {
                currentCapacity++
                updateCapacityDisplay()
            }
        }

        btnSave.setOnClickListener {
            val isOval = rbOval.isChecked
            val (width, height) = when {
                rbSmall.isChecked -> sizeSmall
                rbLarge.isChecked -> sizeLarge
                else -> sizeMedium
            }

            onSave(isOval, currentCapacity, width, height)
            dismiss()
        }
    }

    private fun updateCapacityDisplay() {
        tvCapacity.text = currentCapacity.toString()
    }
}
