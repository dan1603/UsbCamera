package com.example.cameraapp.utils

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity

inline fun <reified T> AppCompatActivity.cleanLaunchActivity() {
    finish()
    startActivity(
        Intent(this, T::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    )
}