package ito.telegram.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import ito.telegram.adult.AgeEstimator
import ito.telegram.adult.AgeGate
import ito.telegram.core.Lang
import ito.telegram.ui.SectionCard
import ito.telegram.ui.faNum
import ito.telegram.ui.rememberPrefs
import java.util.concurrent.Executors

private class ScanState {
    var samples = mutableListOf<Float>()
    var challenge = AgeGate.Challenge()
    var faces = 0
    var eyesWereOpen = false
    var hint by mutableStateOf("چهره‌ات را داخل کادر بگیر")
    var progress by mutableStateOf(0f)
    var verdict by mutableStateOf<AgeGate.Verdict?>(null)
    var frames = 0
}

@SuppressLint("UnsafeOptInUsageError")
@OptIn(ExperimentalGetImage::class)
@Composable
fun AgeScanScreen(nav: NavController) {
    val ctx = LocalContext.current
    val prefs = rememberPrefs()
    val lifecycleOwner = LocalLifecycleOwner.current
    val state = remember { ScanState() }
    val estimator = remember { AgeEstimator(ctx) }
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }

    DisposableEffect(Unit) {
        onDispose { estimator.close() }
    }

    Column(Modifier.fillMaxSize()) {
        SectionCard(
            title = "تأیید سن — حالت سخت‌گیر",
            subtitle = "همه‌چیز روی همین گوشی پردازش می‌شود. هیچ فریمی ذخیره یا ارسال نمی‌شود.",
        ) {
            if (!estimator.ready) {
                Text(
                    "مدل تخمین سن بارگذاری نشد (${estimator.error ?: "نامشخص"}). در حالت سخت‌گیر یعنی دسترسی داده نمی‌شود.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(state.hint, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            val v = state.verdict
            when (v) {
                is AgeGate.Verdict.Allowed -> Text(
                    Lang.t("adult_pass") + " (تخمین: ${faNum(v.age)} سال)",
                    color = MaterialTheme.colorScheme.primary,
                )
                is AgeGate.Verdict.Denied -> Text(
                    Lang.t("adult_fail") + " — ${v.reason}",
                    color = MaterialTheme.colorScheme.error,
                )
                is AgeGate.Verdict.Inconclusive -> Text("نامطمئن: ${v.reason}")
                null -> Unit
            }
        }

        if (!granted) {
            SectionCard(title = "دوربین لازم است") {
                Text("بدون دوربین جلو نمی‌شود سن را تخمین زد.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("اجازه‌ی دوربین") }
            }
        } else {
            Box(Modifier.fillMaxWidth().height(320.dp).padding(12.dp)) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        val previewView = PreviewView(context)
                        val executor = Executors.newSingleThreadExecutor()
                        val providerFuture = ProcessCameraProvider.getInstance(context)
                        providerFuture.addListener({
                            try {
                                val provider = providerFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }
                                val options = FaceDetectorOptions.Builder()
                                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                                    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                                    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                                    .setMinFaceSize(0.25f)
                                    .build()
                                val detector = FaceDetection.getClient(options)

                                val analysis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()
                                analysis.setAnalyzer(executor) { proxy ->
                                    val media = proxy.image
                                    if (media == null) {
                                        proxy.close()
                                        return@setAnalyzer
                                    }
                                    val rotation = proxy.imageInfo.rotationDegrees
                                    val input = InputImage.fromMediaImage(media, rotation)
                                    detector.process(input)
                                        .addOnSuccessListener { faces ->
                                            try {
                                                handleFaces(state, estimator, faces, proxy, rotation, prefs)
                                            } catch (_: Throwable) {
                                            } finally {
                                                proxy.close()
                                            }
                                        }
                                        .addOnFailureListener {
                                            proxy.close()
                                        }
                                }

                                provider.unbindAll()
                                provider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_FRONT_CAMERA,
                                    preview,
                                    analysis,
                                )
                            } catch (t: Throwable) {
                                state.hint = "دوربین باز نشد: ${t.message}"
                            }
                        }, ContextCompat.getMainExecutor(context))
                        previewView
                    },
                )
            }
            SectionCard(title = "تست زنده‌بودن") {
                Text("۱) یک بار پلک بزن  ۲) سرت را کمی بچرخان  ۳) لبخند بزن", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(6.dp))
                Text(
                    "پلک: ${tick(state.challenge.blink)}   چرخش: ${tick(state.challenge.turn)}   لبخند: ${tick(state.challenge.smile)}   نمونه: ${faNum(state.samples.size)}",
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = {
                    state.samples.clear()
                    state.challenge = AgeGate.Challenge()
                    state.verdict = null
                    state.progress = 0f
                    state.hint = "از اول: چهره‌ات را داخل کادر بگیر"
                }) { Text("شروع دوباره") }
            }
        }

        if (state.verdict is AgeGate.Verdict.Allowed) {
            Button(
                onClick = { nav.popBackStack() },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            ) { Text("برگرد به بخش بزرگسال") }
        }
    }
}

private fun tick(b: Boolean) = if (b) "✔" else "…"

@OptIn(ExperimentalGetImage::class)
private fun handleFaces(
    state: ScanState,
    estimator: AgeEstimator,
    faces: List<com.google.mlkit.vision.face.Face>,
    proxy: androidx.camera.core.ImageProxy,
    rotation: Int,
    prefs: ito.telegram.core.Prefs,
) {
    state.faces = faces.size
    if (faces.isEmpty()) {
        state.hint = Lang.t("no_face")
        return
    }
    if (faces.size > 1) {
        state.hint = "فقط یک نفر جلوی دوربین باشد"
        return
    }
    val face = faces[0]
    val left = face.leftEyeOpenProbability ?: -1f
    val right = face.rightEyeOpenProbability ?: -1f
    if (left >= 0f && right >= 0f) {
        if (left > 0.7f && right > 0.7f) state.eyesWereOpen = true
        if (state.eyesWereOpen && left < 0.25f && right < 0.25f) {
            state.challenge = state.challenge.copy(blink = true)
        }
    }
    if (kotlin.math.abs(face.headEulerAngleY) > 20f) {
        state.challenge = state.challenge.copy(turn = true)
    }
    if ((face.smilingProbability ?: 0f) > 0.7f) {
        state.challenge = state.challenge.copy(smile = true)
    }

    state.frames++
    if (state.frames % 3 == 0 && state.samples.size < 14) {
        val bitmap = try {
            estimator.rotate(proxy.toBitmap(), rotation)
        } catch (t: Throwable) {
            null
        }
        if (bitmap != null) {
            val crop = estimator.cropFace(bitmap, face.boundingBox)
            if (crop != null) {
                estimator.estimate(crop)?.let { state.samples.add(it) }
            }
        }
    }

    state.progress = (state.samples.size / AgeGate.MIN_SAMPLES.toFloat()).coerceAtMost(1f)
    state.hint = when {
        !state.challenge.ok -> "تست زنده‌بودن را کامل کن (پلک، چرخش سر یا لبخند)"
        state.samples.size < AgeGate.MIN_SAMPLES -> "نگه دار… در حال نمونه‌برداری"
        else -> "در حال تصمیم‌گیری"
    }

    if (state.samples.size >= AgeGate.MIN_SAMPLES && state.challenge.ok) {
        val verdict = AgeGate.decide(
            samples = state.samples.toList(),
            challenge = state.challenge,
            strict = true,
            faces = state.faces,
            modelReady = estimator.ready,
        )
        state.verdict = verdict
        if (verdict is AgeGate.Verdict.Allowed) {
            AgeGate.store(prefs, verdict.age)
        } else if (verdict is AgeGate.Verdict.Denied) {
            AgeGate.lock(prefs)
        }
    }
}
