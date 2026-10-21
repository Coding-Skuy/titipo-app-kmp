package id.titipo.app

import id.titipo.shared.auth.PenyimpanToken
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * JWT disimpan di iOS Keychain (kSecClassGenericPassword, hanya perangkat ini, setelah unlock pertama).
 * Keychain mengenkripsi item di level sistem; tidak ada service key titipo-lumbung di biner.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class PenyimpanTokenIos : PenyimpanToken {
    private fun dasar() = CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr).also {
        CFDictionaryAddValue(it, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(it, kSecAttrService, CFBridgingRetain(SERVIS))
        CFDictionaryAddValue(it, kSecAttrAccount, CFBridgingRetain(AKUN))
    }

    override suspend fun simpan(token: String) {
        hapus()
        val data = token.encodeToByteArray().let { b ->
            if (b.isEmpty()) NSData() else b.usePinned { NSData.create(bytes = it.addressOf(0), length = b.size.toULong()) }
        }
        val q = dasar()
        CFDictionaryAddValue(q, kSecValueData, CFBridgingRetain(data))
        CFDictionaryAddValue(q, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
        SecItemAdd(q, null)
    }

    override suspend fun baca(): String? = memScoped {
        val q = dasar()
        CFDictionaryAddValue(q, kSecReturnData, kCFBooleanTrue)
        CFDictionaryAddValue(q, kSecMatchLimit, kSecMatchLimitOne)
        val hasil = alloc<CFTypeRefVar>()
        if (SecItemCopyMatching(q, hasil.ptr) != errSecSuccess) return@memScoped null
        val data = CFBridgingRelease(hasil.value) as? NSData ?: return@memScoped null
        NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
    }

    override suspend fun hapus() {
        SecItemDelete(dasar())
    }

    private companion object {
        const val SERVIS = "id.titipo.app"
        const val AKUN = "jwt"
    }
}
