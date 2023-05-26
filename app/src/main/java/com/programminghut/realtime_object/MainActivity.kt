package com.programminghut.realtime_object

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.TextureView
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.programminghut.realtime_object.ml.SsdMobilenetV11Metadata1
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp

import android.speech.tts.TextToSpeech
import java.util.Locale



class MainActivity : AppCompatActivity() {

    // déclaration des variables
    // L'utilisation de lateinit indique que la variable sera initialisée ultérieurement avant son utilisation.
    lateinit var labels:List<String> // Déclarant une variable labels de type List<String>.
    // Déclarant une variable colors qui est une liste d'entiers représentant des couleurs.
    // Ces couleurs seront utilisé pour spécifier les couleurs des rectangles encadrant les objets détectés.
    var colors = listOf<Int>(
        Color.BLUE, Color.GREEN, Color.RED, Color.CYAN, Color.GRAY, Color.BLACK,
        Color.DKGRAY, Color.MAGENTA, Color.YELLOW, Color.RED)
    // Déclarant une variable paint de type Paint.
    // La classe Paint est utilisée pour dessiner et styliser des éléments graphiques dans Android.
    val paint = Paint()
    // Déclarant une variable imageProcessor de type ImageProcessor.
    lateinit var imageProcessor: ImageProcessor
    // Déclarant une variable bitmap de type Bitmap, qui représente une image bitmap.
    lateinit var bitmap:Bitmap
    //Déclarant une variable imageView de type ImageView, qui est un widget utilisé pour afficher des images dans Android.
    lateinit var imageView: ImageView
    // Déclarant une variable cameraDevice de type CameraDevice, qui représente un périphérique photo ou vidéo.
    lateinit var cameraDevice: CameraDevice
    // Déclarant une variable handler de type Handler, utilisée pour effectuer des actions asynchrones dans Android.
    lateinit var handler: Handler
    // Déclarant une variable cameraManager de type CameraManager, utilisée pour interagir avec les périphériques de la caméra sur l'appareil.
    lateinit var cameraManager: CameraManager
    // Déclarant une variable textureView de type TextureView, un widget qui affiche une surface de texture.
    lateinit var textureView: TextureView
    // Déclarant une variable model de type SsdMobilenetV11Metadata1, qui représente un modèle utilisé pour la détection d'objets.
    lateinit var model:SsdMobilenetV11Metadata1
    // Déclarant une variable textToSpeech de type TextToSpeech, utilisée pour effectuer la synthèse vocale.
    private lateinit var textToSpeech: TextToSpeech


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Demander l'autorisation nécessaire
        get_permission()
        // Charger les labels à partir du fichier "labels.txt"
        labels = FileUtil.loadLabels(this, "labels.txt")
        // Configuration de l'imageProcessor pour redimensionner les images en entrée
        imageProcessor = ImageProcessor.Builder().add(ResizeOp(300, 300, ResizeOp.ResizeMethod.BILINEAR)).build()
        // Instancier le modèle SsdMobilenetV11Metadata1
        model = SsdMobilenetV11Metadata1.newInstance(this)
        // Créer un nouveau thread de gestion pour le traitement de la vidéo
        val handlerThread = HandlerThread("videoThread")
        handlerThread.start()
        handler = Handler(handlerThread.looper)
        // Récupérer la référence de l'imageView à partir de la vue
        imageView = findViewById(R.id.imageView)
        // Récupérer la référence du textureView à partir de la vue
        textureView = findViewById(R.id.textureView)
        // Définir un listener pour la textureView pour gérer les événements de surface
        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(p0: SurfaceTexture, p1: Int, p2: Int) {
                // Ouvrir la caméra lorsque la textureView est disponible
                open_camera()
            }
            override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture, p1: Int, p2: Int) {
                // La taille de la texture a changé
            }
            override fun onSurfaceTextureDestroyed(p0: SurfaceTexture): Boolean {
                // La texture a été détruite
                return false
            }
            override fun onSurfaceTextureUpdated(p0: SurfaceTexture) {
                // La texture a été mise à jour (nouvelle image)
                bitmap = textureView.bitmap!!
                var image = TensorImage.fromBitmap(bitmap)
                image = imageProcessor.process(image)
                // Processus de détection d'objets à l'aide du modèle
                val outputs = model.process(image)
                val locations = outputs.locationsAsTensorBuffer.floatArray
                val classes = outputs.classesAsTensorBuffer.floatArray
                val scores = outputs.scoresAsTensorBuffer.floatArray
                val numberOfDetections = outputs.numberOfDetectionsAsTensorBuffer.floatArray
                // Copier l'image bitmap dans une version mutable pour dessiner les rectangles et le texte
                var mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                val canvas = Canvas(mutable)
                val h = mutable.height
                val w = mutable.width
                paint.textSize = h / 15f
                paint.strokeWidth = h / 85f
                var x = 0
                scores.forEachIndexed { index, fl ->
                    x = index
                    x *= 4
                    if (fl > 0.5) {
                        // Dessiner un rectangle autour de l'objet détecté
                        paint.setColor(colors.get(index))
                        paint.style = Paint.Style.STROKE
                        canvas.drawRect(RectF(locations.get(x + 1) * w, locations.get(x) * h, locations.get(x + 3) * w, locations.get(x + 2) * h), paint)
                        // Dessiner le texte avec le label et le score de confiance
                        paint.style = Paint.Style.FILL
                        canvas.drawText(labels.get(classes.get(index).toInt()) + " " + fl.toString(), locations.get(x + 1) * w, locations.get(x) * h, paint)

                        // Initialiser le moteur de synthèse vocale et lire le label à voix haute
                        textToSpeech = TextToSpeech(this@MainActivity) { status ->
                            if (status == TextToSpeech.SUCCESS) {
                                textToSpeech.language = Locale.getDefault()
                                textToSpeech.speak(labels.get(classes.get(index).toInt()), TextToSpeech.QUEUE_FLUSH, null, null)
                            }
                        }
                    }
                }
                // Afficher l'image modifiée avec les rectangles et le texte dans l'imageView
                imageView.setImageBitmap(mutable)
            }
        }
        // Récupérer le gestionnaire de caméra du système
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }



    override fun onDestroy() {
        super.onDestroy()
        model.close()
    }
    @SuppressLint("MissingPermission")
    fun open_camera() {
        // Ouvrir la caméra en utilisant le premier ID de caméra de la liste
        cameraManager.openCamera(cameraManager.cameraIdList[0], object : CameraDevice.StateCallback() {
            override fun onOpened(p0: CameraDevice) {
                // La caméra est ouverte avec succès
                cameraDevice = p0
                // Obtenir la texture de surface de la textureView
                var surfaceTexture = textureView.surfaceTexture
                var surface = Surface(surfaceTexture)
                // Créer une demande de capture pour le mode d'aperçu
                var captureRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                captureRequest.addTarget(surface)
                // Créer une session de capture en utilisant la surface de sortie
                cameraDevice.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(p0: CameraCaptureSession) {
                        // La session de capture est configurée avec succès
                        p0.setRepeatingRequest(captureRequest.build(), null, null)
                    }
                    override fun onConfigureFailed(p0: CameraCaptureSession) {
                        // La configuration de la session de capture a échoué
                    }
                }, handler)
            }
            override fun onDisconnected(p0: CameraDevice) {
                // La caméra a été déconnectée
            }
            override fun onError(p0: CameraDevice, p1: Int) {
                // Une erreur s'est produite lors de l'ouverture de la caméra
            }
        }, handler)
    }


    fun get_permission(){
        if(ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 101)
        }
    }
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(grantResults[0] != PackageManager.PERMISSION_GRANTED){
            get_permission()
        }
    }
}