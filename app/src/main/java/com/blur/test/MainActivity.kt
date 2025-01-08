package com.blur.test


import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.blur.test.databinding.ActivityMainBinding
import com.bumptech.glide.Glide
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var selectedImageUri: Uri? = null
    private var blurredImageUri: Uri? = null
    private var blurProcessingTime: Long = 0

    private val photoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            binding.ivOriginal.setImageURI(uri)
            loadAndProcessImage(uri)
        } else {
            Toast.makeText(this, "Need to select image", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnProcessImage.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val request =
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                photoPickerLauncher.launch(request)
            } else {
                Toast.makeText(
                    this,
                    "PhotoPicker is available above Android 13",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun loadAndProcessImage(uri: Uri) {
        Glide.with(this)
            .asBitmap()
            .load(uri)
            .into(object : com.bumptech.glide.request.target.CustomTarget<Bitmap>() {
                override fun onResourceReady(
                    resource: Bitmap,
                    transition: com.bumptech.glide.request.transition.Transition<in Bitmap>?
                ) {
                    detectAndBlurFaces(resource)
                }

                override fun onLoadCleared(placeholder: Drawable?) {}
            })
    }

    private fun detectAndBlurFaces(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val options = getFaceDetectorOptions()
        val detector = FaceDetection.getClient(options)

        detector.process(image)
            .addOnSuccessListener { faces ->
                handleFaceDetectionSuccess(bitmap, faces)
            }
            .addOnFailureListener { e ->
                handleFaceDetectionFailure(e)
            }
    }

    private fun getFaceDetectorOptions(): FaceDetectorOptions {
        return FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .build()
    }

    private fun handleFaceDetectionSuccess(bitmap: Bitmap, faces: List<Face>) {
        if (faces.isNotEmpty()) {
            val blurredBitmap = applyBlurOnFaces(bitmap, faces) // 얼굴 블러 처리
            saveBlurredImage(blurredBitmap) // 블러 처리된 이미지 저장
            binding.ivProcessed.setImageBitmap(blurredBitmap) // 결과 표시
            Toast.makeText(this, "blurProcessingTime : ${blurProcessingTime}ms", Toast.LENGTH_SHORT).show()

            // 이미지 URI 로그 출력
            Log.d("ImageURLs", "Original Image URI: $selectedImageUri")
            Log.d("ImageURLs", "Blurred Image URI: $blurredImageUri")
        } else {
            Toast.makeText(this, "No faces detected.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleFaceDetectionFailure(exception: Exception) {
        exception.printStackTrace()
        Toast.makeText(this, "Face detection failed: ${exception.message}", Toast.LENGTH_SHORT)
            .show()
    }

    private fun applyBlurOnFaces(originalBitmap: Bitmap, faces: List<Face>): Bitmap {
        // 실행 시간 측정 시작
        val startTime = System.currentTimeMillis()

        val blurredBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(blurredBitmap)
        val paint = Paint()

        for (face in faces) {
            val bounds = face.boundingBox

            // 얼굴 영역 크롭
            val faceBitmap = Bitmap.createBitmap(
                originalBitmap,
                bounds.left.coerceAtLeast(0),
                bounds.top.coerceAtLeast(0),
                bounds.width().coerceAtMost(originalBitmap.width - bounds.left),
                bounds.height().coerceAtMost(originalBitmap.height - bounds.top)
            )

            // 얼굴 영역 블러 처리
            val blurredFace = faceBitmap.blur()

            // 블러 처리된 얼굴을 원본 위에 그리기
            canvas.drawBitmap(
                blurredFace,
                bounds.left.toFloat(),
                bounds.top.toFloat(),
                paint
            )
        }

        // 실행 시간 측정 종료
        val elapsedTime = System.currentTimeMillis() - startTime
        blurProcessingTime = elapsedTime
        Log.d("BlurProcessing", "applyBlurOnFaces 실행 시간: $elapsedTime ms")

        return blurredBitmap
    }

    private fun saveBlurredImage(bitmap: Bitmap) {
        val filename = "blurred_image_${System.currentTimeMillis()}.jpg"
        val resolver = contentResolver

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/BlurredImages")
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
            resolver.openOutputStream(uri).use { outputStream ->
                outputStream?.let { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }
            }
            blurredImageUri = uri // 블러 처리된 이미지 URI 저장
            Log.d("SaveBlurredImage", "Blurred image saved: $uri")
        } else {
            Log.e("SaveBlurredImage", "Failed to save blurred image.")
        }
    }

    private fun Bitmap.blur(): Bitmap {
        val radius = 25f // Blur radius
        val bitmapScale = 0.2f // Scaling for performance

        val width = (this.width * bitmapScale).toInt()
        val height = (this.height * bitmapScale).toInt()

        val inputBitmap = Bitmap.createScaledBitmap(this, width, height, false)
        val outputBitmap = Bitmap.createBitmap(inputBitmap)

        val rs = android.renderscript.RenderScript.create(this@MainActivity)
        val intrinsicBlur = android.renderscript.ScriptIntrinsicBlur.create(
            rs,
            android.renderscript.Element.U8_4(rs)
        )
        val input = android.renderscript.Allocation.createFromBitmap(rs, inputBitmap)
        val output = android.renderscript.Allocation.createFromBitmap(rs, outputBitmap)

        intrinsicBlur.setRadius(radius)
        intrinsicBlur.setInput(input)
        intrinsicBlur.forEach(output)
        output.copyTo(outputBitmap)

        return Bitmap.createScaledBitmap(outputBitmap, this.width, this.height, false)
    }
}

