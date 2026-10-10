package app.proudvocab.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.proudvocab.mobile.ui.AppRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as ProudVocabApp).container
        setContent { AppRoot(container) }
    }

    override fun onStop() {
        super.onStop()
        // Make sure words / progress hit the disk whenever the app leaves the foreground.
        (application as ProudVocabApp).container.flushAll()
    }
}
