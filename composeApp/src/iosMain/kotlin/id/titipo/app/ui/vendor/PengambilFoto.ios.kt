package id.titipo.app.ui.vendor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import id.titipo.shared.config.KonfigurasiBisnis
import id.titipo.shared.vendor.KompresFoto
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToFile
import platform.UIKit.UIApplication
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import kotlin.coroutines.resume

@Composable
actual fun rememberPengambilFoto(): PengambilFoto = remember { PengambilFotoIos() }

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class PengambilFotoIos : PengambilFoto {
    // delegate harus dipegang kuat selama picker tampil (UIKit hanya menyimpan referensi lemah)
    private var delegasi: PenerimaFoto? = null

    override suspend fun ambil(): HasilFoto? = suspendCancellableCoroutine { lanjut ->
        val picker = UIImagePickerController()
        picker.sourceType = if (UIImagePickerController.isSourceTypeAvailable(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera)) {
            UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
        } else {
            UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary
        }
        val d = PenerimaFoto { gambar ->
            delegasi = null
            lanjut.resume(gambar?.let { kompres(it, KonfigurasiBisnis.BAWAAN.batasUkuranFotoByte) })
        }
        delegasi = d
        picker.delegate = d
        val akar: UIViewController? = UIApplication.sharedApplication.keyWindow?.rootViewController
        if (akar == null) { delegasi = null; lanjut.resume(null) } else akar.presentViewController(picker, true, null)
    }

    private fun kompres(asli: UIImage, batasByte: Int): HasilFoto? {
        val (lebarAsli, tinggiAsli) = asli.size.useContents { width.toInt() to height.toInt() }
        if (lebarAsli <= 0 || tinggiAsli <= 0) return null
        val (l, t) = KompresFoto.skala(lebarAsli, tinggiAsli)
        UIGraphicsBeginImageContextWithOptions(CGSizeMake(l.toDouble(), t.toDouble()), true, 1.0)
        asli.drawInRect(CGRectMake(0.0, 0.0, l.toDouble(), t.toDouble()))
        val kecil = UIGraphicsGetImageFromCurrentImageContext() ?: run { UIGraphicsEndImageContext(); return null }
        UIGraphicsEndImageContext()

        fun encode(kualitas: Int): NSData? = UIImageJPEGRepresentation(kecil, kualitas / 100.0)
        val kualitas = KompresFoto.cariKualitas(batasByte) { (encode(it)?.length?.toInt()) ?: Int.MAX_VALUE } ?: return null
        val data = encode(kualitas) ?: return null
        val path = NSTemporaryDirectory() + "serah_${NSDate().timeIntervalSince1970.toLong()}.jpg"
        if (!data.writeToFile(path, true)) return null
        return HasilFoto(path, data.length.toInt(), l, t)
    }
}

@OptIn(BetaInteropApi::class)
private class PenerimaFoto(
    private val selesai: (UIImage?) -> Unit,
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {
    override fun imagePickerController(picker: UIImagePickerController, didFinishPickingMediaWithInfo: Map<Any?, *>) {
        val gambar = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        picker.dismissViewControllerAnimated(true, null)
        selesai(gambar)
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, null)
        selesai(null)
    }
}
