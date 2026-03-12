package com.tiffconverter.app

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var imageAdapter: ImageAdapter
    private lateinit var btnAddImages: Button
    private lateinit var btnConvert: Button
    private lateinit var btnClearAll: Button
    private lateinit var seekBarCompression: SeekBar
    private lateinit var tvCompressionValue: TextView
    private lateinit var tvImageCount: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView
    private lateinit var layoutCompressionOptions: LinearLayout

    // Pair<Uri, isPdf>
    private val selectedItems = mutableListOf<Pair<Uri, Boolean>>()
    private var cameraImageUri: Uri? = null
    private var compressionQuality = 80

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        uris.forEach { selectedItems.add(Pair(it, false)) }
        updateUI()
    }

    private val pdfLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        uris.forEach { selectedItems.add(Pair(it, true)) }
        updateUI()
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraImageUri != null) {
            selectedItems.add(Pair(cameraImageUri!!, false))
            updateUI()
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms.values.all { it }) showSourceDialog()
        else Toast.makeText(this, "Permissions required", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initViews()
        setupRecyclerView()
        setupListeners()
        updateUI()
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.recyclerView)
        btnAddImages = findViewById(R.id.btnAddImages)
        btnConvert = findViewById(R.id.btnConvert)
        btnClearAll = findViewById(R.id.btnClearAll)
        seekBarCompression = findViewById(R.id.seekBarCompression)
        tvCompressionValue = findViewById(R.id.tvCompressionValue)
        tvImageCount = findViewById(R.id.tvImageCount)
        progressBar = findViewById(R.id.progressBar)
        tvStatus = findViewById(R.id.tvStatus)
        layoutCompressionOptions = findViewById(R.id.layoutCompressionOptions)
    }

    private fun setupRecyclerView() {
        imageAdapter = ImageAdapter(selectedItems) { pos ->
            selectedItems.removeAt(pos)
            imageAdapter.notifyItemRemoved(pos)
            updateUI()
        }
        recyclerView.layoutManager = GridLayoutManager(this, 3)
        recyclerView.adapter = imageAdapter
    }

    private fun setupListeners() {
        btnAddImages.setOnClickListener { checkPermissionsAndOpenPicker() }

        btnClearAll.setOnClickListener {
            AlertDialog.Builder(this).setTitle("Clear All")
                .setMessage("Remove all files?")
                .setPositiveButton("Clear") { _, _ ->
                    selectedItems.clear()
                    imageAdapter.notifyDataSetChanged()
                    updateUI()
                }
                .setNegativeButton("Cancel", null).show()
        }

        btnConvert.setOnClickListener {
            if (selectedItems.isEmpty()) {
                Toast.makeText(this, "Add at least one file", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            convertToTiff()
        }

        seekBarCompression.max = 100
        seekBarCompression.progress = compressionQuality
        seekBarCompression.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, progress: Int, fromUser: Boolean) {
                compressionQuality = progress
                val label = when {
                    progress >= 90 -> "Maximum Quality"
                    progress >= 70 -> "High Quality"
                    progress >= 50 -> "Medium Quality"
                    progress >= 30 -> "Low Quality"
                    else -> "Minimum Size"
                }
                tvCompressionValue.text = "$progress% ($label)"
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
    }

    private fun checkPermissionsAndOpenPicker() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        if (needed.isEmpty()) showSourceDialog() else permissionLauncher.launch(needed.toTypedArray())
    }

    private fun showSourceDialog() {
        AlertDialog.Builder(this).setTitle("Add Files")
            .setItems(arrayOf("📷  Camera", "🖼️  Images from Gallery", "📄  PDF Files")) { _, which ->
                when (which) {
                    0 -> openCamera()
                    1 -> galleryLauncher.launch("image/*")
                    2 -> pdfLauncher.launch("application/pdf")
                }
            }.show()
    }

    private fun openCamera() {
        val file = File(cacheDir, "JPEG_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.jpg")
        cameraImageUri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
        cameraLauncher.launch(cameraImageUri)
    }

    private fun updateUI() {
        val count = selectedItems.size
        val imgCount = selectedItems.count { !it.second }
        val pdfCount = selectedItems.count { it.second }
        tvImageCount.text = when {
            count == 0 -> "No files selected"
            pdfCount == 0 -> "$imgCount image${if (imgCount > 1) "s" else ""} selected"
            imgCount == 0 -> "$pdfCount PDF${if (pdfCount > 1) "s" else ""} selected"
            else -> "$imgCount image${if (imgCount > 1) "s" else ""} + $pdfCount PDF${if (pdfCount > 1) "s" else ""} selected"
        }
        btnConvert.isEnabled = count > 0
        btnClearAll.visibility = if (count > 0) View.VISIBLE else View.GONE
        layoutCompressionOptions.visibility = if (count > 0) View.VISIBLE else View.GONE
        imageAdapter.notifyDataSetChanged()
    }

    private fun convertToTiff() {
        setLoading(true, "Preparing files...")
        Thread {
            try {
                val bitmaps = mutableListOf<Bitmap>()
                val total = selectedItems.size

                selectedItems.forEachIndexed { i, (uri, isPdf) ->
                    runOnUiThread {
                        tvStatus.text = "Processing file ${i + 1} of $total..."
                        progressBar.progress = (i.toFloat() / total * 40).toInt()
                    }
                    if (isPdf) {
                        bitmaps.addAll(renderPdfToBitmaps(uri))
                    } else {
                        val opts = BitmapFactory.Options().apply { inSampleSize = calcSampleSize(uri) }
                        contentResolver.openInputStream(uri)?.use {
                            BitmapFactory.decodeStream(it, null, opts)
                        }?.let { bitmaps.add(it) }
                    }
                }

                if (bitmaps.isEmpty()) {
                    runOnUiThread { setLoading(false); Toast.makeText(this, "No pages could be read", Toast.LENGTH_LONG).show() }
                    return@Thread
                }

                runOnUiThread { tvStatus.text = "Encoding TIFF (${bitmaps.size} pages)..."; progressBar.progress = 50 }
                val tiffData = TiffEncoder.encode(bitmaps, compressionQuality) { p ->
                    runOnUiThread { progressBar.progress = 50 + p / 2 }
                }

                runOnUiThread { tvStatus.text = "Saving..."; progressBar.progress = 95 }
                val fileName = "tiff_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.tif"
                val savedUri = saveFile(tiffData, fileName)
                val pageCount = bitmaps.size
                bitmaps.forEach { it.recycle() }

                runOnUiThread {
                    progressBar.progress = 100
                    setLoading(false)
                    if (savedUri != null) showSuccess(savedUri, fileName, tiffData.size, pageCount)
                    else Toast.makeText(this, "Failed to save file", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    setLoading(false)
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun renderPdfToBitmaps(uri: Uri): List<Bitmap> {
        val bitmaps = mutableListOf<Bitmap>()
        try {
            val pfd = contentResolver.openFileDescriptor(uri, "r") ?: return bitmaps
            val renderer = PdfRenderer(pfd)
            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                val scale = 2.0f
                val width = (page.width * scale).toInt()
                val height = (page.height * scale).toInt()
                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmaps.add(bmp)
                page.close()
            }
            renderer.close()
            pfd.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return bitmaps
    }

    private fun calcSampleSize(uri: Uri): Int {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        var s = 1
        while (opts.outWidth / s > 2048 || opts.outHeight / s > 2048) s *= 2
        return s
    }

    private fun saveFile(data: ByteArray, fileName: String): Uri? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val cv = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "image/tiff")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/TiffConverter")
            }
            contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)?.also { uri ->
                contentResolver.openOutputStream(uri)?.use { it.write(data) }
            }
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "TiffConverter").also { it.mkdirs() }
            val file = File(dir, fileName).also { FileOutputStream(it).use { o -> o.write(data) } }
            Uri.fromFile(file)
        }
    } catch (e: Exception) { null }

    private fun setLoading(loading: Boolean, msg: String = "") {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        tvStatus.visibility = if (loading) View.VISIBLE else View.GONE
        tvStatus.text = msg
        if (loading) progressBar.progress = 0
        btnConvert.isEnabled = !loading
        btnAddImages.isEnabled = !loading
        btnClearAll.isEnabled = !loading
        seekBarCompression.isEnabled = !loading
    }

    private fun showSuccess(uri: Uri, fileName: String, size: Int, pages: Int) {
        val sizeStr = if (size / 1024 > 1024) "%.1f MB".format(size / 1024.0 / 1024.0) else "${size / 1024} KB"
        AlertDialog.Builder(this).setTitle("✅ Conversion Complete!")
            .setMessage("File: $fileName\nSize: $sizeStr\nPages: $pages\nSaved to: Downloads/TiffConverter/")
            .setPositiveButton("Share") { _, _ ->
                startActivity(Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "image/tiff"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }, "Share TIFF"
                ))
            }
            .setNegativeButton("Done", null).show()
    }
}
