package app.proudvocab.mobile.data

import android.util.AtomicFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

/**
 * Crash-safe text file: android.util.AtomicFile writes to a temp file and swaps it in, so a kill
 * during a write can never leave a half-written (corrupt) data file.
 * Writes are debounced; [flushNow] forces the latest state to disk (called when the app goes to background).
 */
class JsonStore(private val file: File, scope: CoroutineScope) {
    private val atomic = AtomicFile(file)
    private val pending = AtomicReference<(() -> String)?>(null)
    private val signal = Channel<Unit>(Channel.CONFLATED)
    private val writeLock = Any()

    init {
        scope.launch {
            for (ignored in signal) {
                delay(DEBOUNCE_MS)
                flushNow()
            }
        }
    }

    fun read(): String? = try {
        String(atomic.readFully(), Charsets.UTF_8)
    } catch (e: FileNotFoundException) {
        null
    } catch (e: IOException) {
        null
    }

    fun scheduleWrite(supplier: () -> String) {
        pending.set(supplier)
        signal.trySend(Unit)
    }

    /** Writes the most recently scheduled state synchronously. Safe to call from any thread. */
    fun flushNow() {
        val supplier = pending.getAndSet(null) ?: return
        val text = try {
            supplier()
        } catch (e: Exception) {
            return
        }
        synchronized(writeLock) {
            val out = try {
                atomic.startWrite()
            } catch (e: IOException) {
                return
            }
            try {
                out.write(text.toByteArray(Charsets.UTF_8))
                atomic.finishWrite(out)
            } catch (e: IOException) {
                atomic.failWrite(out)
            }
        }
    }

    /** Keeps an unreadable file around for debugging instead of silently overwriting it. */
    fun quarantineCorrupt() {
        try {
            if (file.exists()) file.renameTo(File(file.parentFile, file.name + ".corrupt-" + System.currentTimeMillis()))
        } catch (e: Exception) {
            // best effort only
        }
    }

    companion object {
        private const val DEBOUNCE_MS = 300L
    }
}
