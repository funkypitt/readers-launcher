package com.freedomfighter.readerslauncher

import com.freedomfighter.readerslauncher.ui.asinIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AsinTest {
    @Test fun bare() = assertEquals("B00QJDONQY", asinIn(" b00qjdonqy "))
    @Test fun amazonPage() = assertEquals("B00QJDONQY", asinIn("https://www.amazon.fr/Le-Horla-Guy-Maupassant-ebook/dp/B00QJDONQY/ref=sr_1_1?keywords=horla"))
    @Test fun kindleShare() = assertEquals("B00QJDONQY", asinIn("I'm reading this: https://read.amazon.com/kp/kshare?asin=B00QJDONQY&id=abc&ref_=r_sa_glf_b_0"))
    @Test fun kindleLink() = assertEquals("B00QJDONQY", asinIn("kindle://book/?action=open&asin=B00QJDONQY"))
    @Test fun mobileProduct() = assertEquals("2070360024", asinIn("https://www.amazon.fr/gp/product/2070360024"))
    @Test fun nothing() = assertNull(asinIn("a title with no code in it"))
}
