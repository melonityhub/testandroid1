package app.proudvocab.mobile.data

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Date

/** Saves the last uncaught exception to a file so it can be viewed/copied from Settings and reported. */
object CrashLogger {
    private const val FILE_NAME = "last_crash.txt"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val sw = StringWriter()
                error.printStackTrace(PrintWriter(sw))
                val version = try {
                    app.packageManager.getPackageInfo(app.packageName, 0).versionName
                } catch (e: Exception) {
                    "?"
                }
                File(app.filesDir, FILE_NAME).writeText(
                    "time=${Date()}\napp=$version\nandroid=${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
                        "device=${Build.MANUFACTURER} ${Build.MODEL}\nthread=${thread.name}\n\n$sw"
                )
            } catch (ignored: Throwable) {
                // never let logging break the crash path
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: Context): String? = try {
        val f = File(context.applicationContext.filesDir, FILE_NAME)
        if (f.exists()) f.readText() else null
    } catch (e: Exception) {
        null
    }

    fun clear(context: Context) {
        try {
            File(context.applicationContext.filesDir, FILE_NAME).delete()
        } catch (ignored: Exception) {
        }
    }
}
