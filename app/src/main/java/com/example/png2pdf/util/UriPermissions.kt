package com.example.png2pdf.util

/**
 * 保持 content:// Uri 的持久读权限。
 *
 * 关键：从系统选择器拿到的 Uri 默认只在本次进程内有效。进程被回收后如果还想复用
 * 之前选过的图（例如把 Uri 落盘做"上次的选择"），就会抛 SecurityException。
 * 所以选完图立刻 takePersistableUriPermission。
 */
object UriPermissions {

    fun takeRead(context: android.content.Context, uri: android.net.Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    fun takeRead(context: android.content.Context, uris: List<android.net.Uri>) {
        uris.forEach { takeRead(context, it) }
    }

    fun release(context: android.content.Context, uri: android.net.Uri) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }
}
