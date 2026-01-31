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
    private val onSave: (isOval: Boolean, capacity: Int, width: Float, height: Float, chairStyle: Int) -> Unit
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

    // Chair style buttons
    private lateinit var btnChairRound: ImageButton
    private lateinit var btnChairTop: ImageButton
    private lateinit var btnChairSimple: ImageButton
    private lateinit var btnChairPerson: ImageButton
    private lateinit var btnChairArc: ImageButton
    private lateinit var chairButtons: List<ImageButton>

    private var currentCapacity: Int = table.capacity
    private var currentChairStyle: Int = table.chairStyle

    // Size presets (width x height)
    private val sizeSmall = Pair(100f, 80f)
    private val sizeMedium = Pair(140f, 100f)
    private val sizeLarge = Pair(180f, 120f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_edit_table)

        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        // Set dialog width to 70% of screen width
        window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.70).toInt(),
            android.view.WindowManager.LayoutParams.WRAP_CONTENT
        )

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

        // Chair style buttons
        btnChairRound = findViewById(R.id.btnChairRound)
        btnChairTop = findViewById(R.id.btnChairTop)
        btnChairSimple = findViewById(R.id.btnChairSimple)
        btnChairPerson = findViewById(R.id.btnChairPerson)
        btnChairArc = findViewById(R.id.btnChairArc)

        chairButtons = listOf(btnChairRound, btnChairTop, btnChairSimple, btnChairPerson, btnChairArc)
    }

    private fun setupData() {
        // Set title
        val tableName = if (table.name.isNotEmpty()) table.name else context.getString(R.string.table_number, table.number)
        tvDialogTitle.text = context.getString(R.string.edit_table_title, tableName)

        // Set current shape
        if (table.isOval) {
            rbOval.isChecked = true
        } else {
            rbRectangle.isChecked = true
        }

        // Set current capacity (0 = no chairs)
        currentCapacity = table.capacity.coerceIn(0, 9)
        updateCapacityDisplay()

        // Set current size
        when {
            table.width <= sizeSmall.first -> rbSmall.isChecked = true
            table.width >= sizeLarge.first -> rbLarge.isChecked = true
            else -> rbMedium.isChecked = true
        }

        // Set current chair style
        currentChairStyle = table.chairStyle.coerceIn(0, 4)
        updateChairStyleSelection()
    }

    private fun setupListeners() {
        btnClose.setOnClickListener { dismiss() }
        btnCancel.setOnClickListener { dismiss() }

        btnCapacityMinus.setOnClickListener {
            if (currentCapacity > 0) {
                currentCapacity--
                updateCapacityDisplay()
            }
        }

        btnCapacityPlus.setOnClickListener {
            if (currentCapacity < 9) {  // Max 9 chairs
                currentCapacity++
                updateCapacityDisplay()
            }
        }

        // Chair style selection listeners
        btnChairRound.setOnClickListener { selectChairStyle(0) }
        btnChairTop.setOnClickListener { selectChairStyle(1) }
        btnChairSimple.setOnClickListener { selectChairStyle(2) }
        btnChairPerson.setOnClickListener { selectChairStyle(3) }
        btnChairArc.setOnClickListener { selectChairStyle(4) }

        btnSave.setOnClickListener {
            val isOval = rbOval.isChecked
            val (width, height) = when {
                rbSmall.isChecked -> sizeSmall
                rbLarge.isChecked -> sizeLarge
                else -> sizeMedium
            }

            onSave(isOval, currentCapacity, width, height, currentChairStyle)
            dismiss()
        }
    }

    private fun selectChairStyle(style: Int) {
        currentChairStyle = style
        updateChairStyleSelection()
    }

    private fun updateChairStyleSelection() {
        chairButtons.forEachIndexed { index, button ->
            button.isSelected = (index == currentChairStyle)
        }
    }

    private fun updateCapacityDisplay() {
        tvCapacity.text = currentCapacity.toString()
    }
}
