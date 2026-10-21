package id.titipo.app

import id.titipo.app.ui.vendor.ringkasItem
import id.titipo.shared.vendor.ItemMasuk
import kotlin.test.Test
import kotlin.test.assertEquals

class LayarVendorTest {
    @Test
    fun ringkasItemKartu() {
        assertEquals("Bayam 2 kg, Telur 1 kg", ringkasItem(listOf(ItemMasuk("Bayam", 2000), ItemMasuk("Telur", 1000))))
        assertEquals("", ringkasItem(emptyList()))
    }
}
