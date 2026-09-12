package io.github.zhshuaii.tvinbox

import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import java.io.FileNotFoundException

/** The installer receives a grant for one exact APK, with read access only. */
class InboxFileProvider : FileProvider() {
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Read-only APK provider")
        return super.openFile(uri, mode)
    }
}
