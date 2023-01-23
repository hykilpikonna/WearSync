package org.hydev.wearsync

import android.view.View
import com.google.android.material.snackbar.Snackbar

fun View.snack(msg: String) = Snackbar.make(this, msg, Snackbar.LENGTH_LONG)
    .setAction("Action", null).show()