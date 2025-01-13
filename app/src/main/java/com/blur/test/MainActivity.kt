package com.blur.test


import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.blur.test.databinding.ActivityMainBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var selectedImage: Bitmap? = null
    private var radius: Float = 25f
    private var bitmapScale: Float = 0.2f

    private val photoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, it)
            selectedImage = bitmap
            applyRealtimeBlur()
        } ?: Toast.makeText(this, "No image selected.", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initView()
        initClickListener()
        initSeekBars()
    }

    private fun initView(){
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    private fun initClickListener(){
        binding.btnSelectImage.setOnClickListener {
            photoPickerLauncher.launch("image/*")
        }
    }

    private fun initSeekBars() {

        with(binding){
            // Blur Radius SeekBar
            seekBarRadius.max = 25
            seekBarRadius.progress = radius.toInt()
            tvRadiusValue.text = "Radius: $radius"
            seekBarRadius.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    radius = progress.coerceAtLeast(1).toFloat()
                    tvRadiusValue.text = "Radius: $radius"
                    applyRealtimeBlur()
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {}
                override fun onStopTrackingTouch(seekBar: SeekBar) {}
            })

            // Bitmap Scale SeekBar
            seekBarScale.max = 10
            seekBarScale.progress = (bitmapScale * 10).toInt()
            tvScaleValue.text = "Scale: $bitmapScale"
            seekBarScale.setOnSeekBarChangeListener(object :
                SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    bitmapScale = (progress / 10f).coerceIn(0.1f, 1.0f) // 0.1 ~ 1.0 범위 제한
                    tvScaleValue.text = "Scale: $bitmapScale"
                    applyRealtimeBlur()
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {}
                override fun onStopTrackingTouch(seekBar: SeekBar) {}
            })
        }
    }

    private fun applyRealtimeBlur() {
        selectedImage?.let { bitmap ->
            val options = getFaceDetectorOptions()
            val image = InputImage.fromBitmap(bitmap, 0)
            val detector = FaceDetection.getClient(options)

            detector.process(image)
                .addOnSuccessListener { faces ->
                    if (faces.isNotEmpty()) {
                        val blurredBitmap = applyBlurOnFaces(bitmap, faces)
                        binding.ivProcessed.setImageBitmap(blurredBitmap)
                    } else {
                        Toast.makeText(this, "No faces detected.", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    e.printStackTrace()
                    Toast.makeText(this, "Face detection failed.", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun applyBlurOnFaces(originalBitmap: Bitmap, faces: List<Face>): Bitmap {
        val blurredBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(blurredBitmap)
        val paint = Paint()

        for (face in faces) {
            val bounds = face.boundingBox
            val faceBitmap = Bitmap.createBitmap(
                originalBitmap,
                bounds.left.coerceAtLeast(0),
                bounds.top.coerceAtLeast(0),
                bounds.width().coerceAtMost(originalBitmap.width - bounds.left),
                bounds.height().coerceAtMost(originalBitmap.height - bounds.top)
            )

            val blurredFace = faceBitmap.blur(radius, bitmapScale)

            canvas.drawBitmap(
                blurredFace,
                bounds.left.toFloat(),
                bounds.top.toFloat(),
                paint
            )
        }
        return blurredBitmap
    }

    private fun Bitmap.blur(radius: Float, bitmapScale: Float): Bitmap {
        val width = (this.width * bitmapScale).toInt().coerceAtLeast(1) // 최소값 1 보장
        val height = (this.height * bitmapScale).toInt().coerceAtLeast(1) // 최소값 1 보장

        val inputBitmap = Bitmap.createScaledBitmap(this, width, height, false)
        val outputBitmap = Bitmap.createBitmap(inputBitmap)

        val rs = android.renderscript.RenderScript.create(this@MainActivity)
        val intrinsicBlur = android.renderscript.ScriptIntrinsicBlur.create(
            rs,
            android.renderscript.Element.U8_4(rs)
        )

        intrinsicBlur.setRadius(radius.coerceIn(1f, 25f)) // 유효 범위로 조정
        val input = android.renderscript.Allocation.createFromBitmap(rs, inputBitmap)
        val output = android.renderscript.Allocation.createFromBitmap(rs, outputBitmap)

        intrinsicBlur.setInput(input)
        intrinsicBlur.forEach(output)
        output.copyTo(outputBitmap)

        return Bitmap.createScaledBitmap(outputBitmap, this.width, this.height, false)
    }

    private fun getFaceDetectorOptions(): FaceDetectorOptions {
        return FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .build()
    }
}
