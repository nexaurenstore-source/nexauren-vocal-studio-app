package com.nexauren.vocalstudio

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.util.Base64

class MainActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private var chooser: ValueCallback<Array<Uri>>? = null
    private var pendingPermission: PermissionRequest? = null
    private var exportName = "Nexauren_Vocal_Studio.wav"
    private val exportParts = StringBuilder()
    private val micRequest = 41
    private val storageRequest = 42

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        setContentView(web)
        WebView.setWebContentsDebuggingEnabled(false)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = true
            allowContentAccess = true
            builtInZoomControls = false
            displayZoomControls = false
            setSupportZoom(false)
        }
        web.addJavascriptInterface(AppBridge(), "Android")
        web.webViewClient = object : WebViewClient() {}
        web.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    val origin = request.origin?.toString() ?: ""
                    val audioOnly = request.resources.all { it == PermissionRequest.RESOURCE_AUDIO_CAPTURE }
                    if (!audioOnly || !origin.startsWith("file:")) { request.deny(); return@runOnUiThread }
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
                    } else {
                        pendingPermission = request
                        ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.RECORD_AUDIO), micRequest)
                    }
                }
            }
            override fun onPermissionRequestCanceled(request: PermissionRequest) { if (pendingPermission == request) pendingPermission = null }
            override fun onShowFileChooser(v: WebView?, callback: ValueCallback<Array<Uri>>?, params: FileChooserParams?): Boolean {
                chooser?.onReceiveValue(null); chooser = callback
                return try { startActivityForResult(params?.createIntent() ?: Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type="audio/*"; addCategory(Intent.CATEGORY_OPENABLE); putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true) }); true } catch(e:Exception){chooser=null;false}
            }
        }
        requestMicIfNeeded()
        web.loadUrl("file:///android_asset/index.html")
    }

    private fun requestMicIfNeeded() { if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), micRequest) }
    private fun requestStorageIfNeeded() { if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), storageRequest) else Toast.makeText(this,"Export storage is ready",Toast.LENGTH_SHORT).show() }

    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,results:IntArray){super.onRequestPermissionsResult(requestCode,permissions,results);if(requestCode==micRequest){val p=pendingPermission;pendingPermission=null;if(results.isNotEmpty()&&results[0]==PackageManager.PERMISSION_GRANTED&&p!=null)p.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) else p?.deny();web.evaluateJavascript("window.dispatchEvent(new Event('androidPermissionChanged'))",null)}}

    private fun saveExport(){try{val bytes=Base64.decode(exportParts.toString(),Base64.DEFAULT);val values=ContentValues().apply{put(MediaStore.Audio.Media.DISPLAY_NAME,exportName);put(MediaStore.Audio.Media.MIME_TYPE,"audio/wav");if(Build.VERSION.SDK_INT>=29){put(MediaStore.Audio.Media.RELATIVE_PATH,Environment.DIRECTORY_MUSIC+"/Nexauren Vocal Studio");put(MediaStore.Audio.Media.IS_PENDING,1)}};val uri=contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,values)?:throw Exception("MediaStore insert failed");contentResolver.openOutputStream(uri)?.use{it.write(bytes)}?:throw Exception("Output stream failed");if(Build.VERSION.SDK_INT>=29){val done=ContentValues();done.put(MediaStore.Audio.Media.IS_PENDING,0);contentResolver.update(uri,done,null,null)};Toast.makeText(this,"Exported to Music/Nexauren Vocal Studio",Toast.LENGTH_LONG).show()}catch(e:Exception){Toast.makeText(this,"Export failed: ${e.message}",Toast.LENGTH_LONG).show()}}

    inner class AppBridge {
        @JavascriptInterface fun requestMicrophone()=runOnUiThread{requestMicIfNeeded()}
        @JavascriptInterface fun requestStorage()=runOnUiThread{requestStorageIfNeeded()}
        @JavascriptInterface fun startExport(name:String){exportName=name;exportParts.setLength(0)}
        @JavascriptInterface fun appendExportChunk(chunk:String){exportParts.append(chunk)}
        @JavascriptInterface fun finishExport()=runOnUiThread{saveExport()}
    }
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){super.onActivityResult(requestCode,resultCode,data);if(requestCode==1001){chooser?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode,data));chooser=null}}
    override fun onBackPressed(){if(web.canGoBack())web.goBack()else super.onBackPressed()}
}