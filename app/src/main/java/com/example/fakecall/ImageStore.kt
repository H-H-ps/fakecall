package com.example.fakecall

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

object ImageStore {

    fun file(ctx: Context, name: String) = File(ctx.filesDir, name)

    /** ينسخ الصورة المختارة إلى التخزين الداخلي (بحجم معقول) ويرجع اسم الملف. */
    fun saveFromUri(ctx: Context, uri: Uri): String? = runCatching {
        val source = ImageDecoder.createSource(ctx.contentResolver, uri)
        val bmp = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > 1600) decoder.setTargetSampleSize(longest / 1600)
        }
        val name = "contact_${System.currentTimeMillis()}.jpg"
        FileOutputStream(file(ctx, name)).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        name
    }.getOrNull()

    fun delete(ctx: Context, name: String?) {
        if (name != null) file(ctx, name).delete()
    }

    fun loadBitmap(ctx: Context, contact: Contact): Bitmap {
        contact.photoFile?.let { n ->
            BitmapFactory.decodeFile(file(ctx, n).absolutePath)?.let { return it }
        }
        return BitmapFactory.decodeResource(ctx.resources, R.drawable.default_caller)
    }

    /** مربع من منتصف الصورة (للأيقونات الصغيرة والإشعارات). */
    fun squareIcon(src: Bitmap, size: Int = 256): Bitmap {
        val side = minOf(src.width, src.height)
        val x = (src.width - side) / 2
        val y = (src.height - side) / 2
        val cropped = Bitmap.createBitmap(src, x, y, side, side)
        return Bitmap.createScaledBitmap(cropped, size, size, true)
    }
}
