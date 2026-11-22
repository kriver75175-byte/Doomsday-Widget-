package com.krishna.doomsdaywidget

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
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
    private val reqVideo = 2

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
        val video = Button(this).apply {
            text = "Video chuno (2 sec loop)"
            setOnClickListener { pickVideo() }
        }
        val reset = Button(this).apply {
            text = "Default photo wapas"
            setOnClickListener { resetPhoto() }
        }
        root.addView(info)
        root.addView(pick)
        root.addView(video)
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

    private fun pickVideo() {
        val i = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "video/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(i, reqVideo)
    }

    private fun clearFrames() {
        for (i in 0 until DoomsdayWidget.FRAMES) {
            File(filesDir, "f_l_$i.jpg").delete()
            File(filesDir, "f_s_$i.jpg").delete()
        }
    }

    // First 2 seconds of the video -> 12 frames (6 fps), saved for the widget
    private fun extractFrames(uri: Uri): Boolean {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(this, uri)
            val durMs = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            if (durMs <= 0L) return false
            val spanUs = minOf(durMs, 2000L) * 1000L
            val n = DoomsdayWidget.FRAMES
            val large = ArrayList<Bitmap>()
            val small = ArrayList<Bitmap>()
            for (i in 0 until n) {
                val fr = r.getFrameAtTime(spanUs * i / n, MediaMetadataRetriever.OPTION_CLOSEST)
                    ?: return false
                large.add(cropTo(fr, 400, 225))
                small.add(cropTo(fr, 300, 300))
            }
            clearFrames()
            for (i in 0 until n) {
                save(large[i], "f_l_$i.jpg")
                save(small[i], "f_s_$i.jpg")
            }
            return true
        } catch (e: Exception) {
            return false
        } finally {
            r.release()
        }
    }

    private fun resetPhoto() {
        File(filesDir, "bg_large.jpg").delete()
        File(filesDir, "bg_small.jpg").delete()
        clearFrames()
        DoomsdayWidget.refresh(this)
        Toast.makeText(this, "Default photo wapas aa gayi", Toast.LENGTH_SHORT).show()
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        if (requestCode == reqVideo) {
            Toast.makeText(this, "Video process ho rahi hai...", Toast.LENGTH_SHORT).show()
            Thread {
                val ok = extractFrames(uri)
                runOnUiThread {
                    if (ok) DoomsdayWidget.refresh(this)
                    Toast.makeText(
                        this,
                        if (ok) "Video set ho gayi" else "Video load nahi hui",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }.start()
            return
        }
        if (requestCode != reqPick) return
        Thread {
            val bmp = loadBitmap(uri)
            if (bmp == null) {
                runOnUiThread {
                    Toast.makeText(this, "Photo load nahi hui", Toast.LENGTH_SHORT).show()
                }
                return@Thread
            }
            clearFrames()
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
