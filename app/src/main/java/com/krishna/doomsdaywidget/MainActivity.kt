package com.krishna.doomsdaywidget

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream

class MainActivity : Activity() {

    private val reqPick = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (24 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(pad, pad * 3, pad, pad)
        }
        val info = TextView(this).apply {
            text = "Widget ki background photo yahan se badlo.\n\n" +
                "Widget lagane ke liye: Home screen par long-press -> Widgets -> Doomsday Widget.\n"
            textSize = 16f
        }
        val pick = Button(this).apply {
            text = "Photo chuno"
            setOnClickListener { pickPhoto() }
        }
        val reset = Button(this).apply {
            text = "Default photo wapas"
            setOnClickListener { resetPhoto() }
        }
        root.addView(info)
        root.addView(pick)
        root.addView(reset)
        setContentView(root)
    }

    private fun pickPhoto() {
        val i = if (Build.VERSION.SDK_INT >= 33) {
            Intent(MediaStore.ACTION_PICK_IMAGES)
        } else {
            Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
        }
        startActivityForResult(i, reqPick)
    }

    private fun resetPhoto() {
        File(filesDir, "bg_large.jpg").delete()
        File(filesDir, "bg_small.jpg").delete()
        DoomsdayWidget.refresh(this)
        Toast.makeText(this, "Default photo wapas aa gayi", Toast.LENGTH_SHORT).show()
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != reqPick || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        Thread {
            val bmp = loadBitmap(uri)
            if (bmp == null) {
                runOnUiThread {
                    Toast.makeText(this, "Photo load nahi hui", Toast.LENGTH_SHORT).show()
                }
                return@Thread
            }
            save(cropTo(bmp, 640, 360), "bg_large.jpg")
            save(cropTo(bmp, 480, 480), "bg_small.jpg")
            runOnUiThread {
                DoomsdayWidget.refresh(this)
                Toast.makeText(this, "Photo set ho gayi", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    private fun loadBitmap(uri: Uri): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= 28) {
            val src = ImageDecoder.createSource(contentResolver, uri)
            ImageDecoder.decodeBitmap(src) { dec, info, _ ->
                dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val s = maxOf(info.size.width, info.size.height)
                if (s > 1600) {
                    val f = 1600f / s
                    dec.setTargetSize(
                        (info.size.width * f).toInt(),
                        (info.size.height * f).toInt()
                    )
                }
            }
        } else {
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        }
    } catch (e: Exception) {
        null
    }

    // Center-crop to the given aspect ratio, then scale to w x h
    private fun cropTo(src: Bitmap, w: Int, h: Int): Bitmap {
        val target = w.toFloat() / h
        val ratio = src.width.toFloat() / src.height
        val cw: Int
        val ch: Int
        if (ratio > target) {
            ch = src.height
            cw = (ch * target).toInt()
        } else {
            cw = src.width
            ch = (cw / target).toInt()
        }
        val x = (src.width - cw) / 2
        val y = (src.height - ch) / 2
        val cropped = Bitmap.createBitmap(src, x, y, cw, ch)
        return Bitmap.createScaledBitmap(cropped, w, h, true)
    }

    private fun save(bmp: Bitmap, name: String) {
        FileOutputStream(File(filesDir, name)).use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
    }
}
