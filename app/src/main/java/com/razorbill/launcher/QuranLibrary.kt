package com.razorbill.launcher

import android.content.Context
import android.os.Build
import android.os.storage.StorageManager
import java.io.File

/**
 * Looks for `<root>/Coran/<reciter-folder>/<NNN>.mp3` on any mounted storage volume
 * (USB key included) plus the app's own external files dirs. First reciter folder
 * found wins per surah number for now — picking a specific reciter is a later step.
 */
object QuranLibrary {
    fun scanForTracks(context: Context): Map<Int, File> {
        val result = mutableMapOf<Int, File>()
        val roots = mutableListOf<File>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val sm = context.getSystemService(StorageManager::class.java)
            sm?.storageVolumes?.forEach { volume ->
                volume.directory?.let { roots.add(it) }
            }
        }
        context.getExternalFilesDirs(null).forEach { dir ->
            if (dir != null) roots.add(dir)
        }

        roots.distinct().forEach { root ->
            val quranDir = File(root, "Coran")
            if (quranDir.isDirectory) {
                quranDir.listFiles { f -> f.isDirectory }?.forEach { reciterDir ->
                    reciterDir.listFiles { f -> f.extension.equals("mp3", ignoreCase = true) }
                        ?.forEach { file ->
                            val number = file.nameWithoutExtension.toIntOrNull()
                            if (number != null && number in 1..114 && number !in result) {
                                result[number] = file
                            }
                        }
                }
            }
        }
        return result
    }
}
