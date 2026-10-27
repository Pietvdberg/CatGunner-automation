package nl.pietvdberg.catgunner.autopilot.vision

import android.graphics.Bitmap
import com.google.mlkit.vision.text.Text

/** Native OCR text is kept intact. A level proof can only validate its already-recognized digits. */
object NativeOcrToken {
    fun from(element: Text.Element, bitmap: Bitmap, top: Int, frameHeight: Int): UpgradeOcrToken? {
        val box = element.boundingBox ?: return null
        val token = UpgradeOcrToken(
            element.text,
            box.exactCenterX() * 570 / bitmap.width,
            (top + box.exactCenterY()) * 1280 / frameHeight
        )
        if (UpgradeTextParser.level(token.text) != null) return token
        val kind = UpgradeKind.entries.singleOrNull {
            UpgradeOcrParser.belongsTo(token, it, OcrField.LEVEL)
        } ?: return token
        val offset = kind.ordinal * 169
        val left = ((118L + offset) * bitmap.width / 570).toInt()
        val right = ((198L + offset) * bitmap.width / 570).toInt()
        val wordTop = (1154L * frameHeight / 1280).toInt() - top
        val bottom = (1179L * frameHeight / 1280).toInt() - top
        val width = right - left
        val height = bottom - wordTop
        if (left < 0 ||
            wordTop < 0 ||
            right > bitmap.width ||
            bottom > bitmap.height ||
            width !in 1..512 ||
            height !in 8..128
        ) {
            return token
        }
        val colors = IntArray(width * height)
        bitmap.getPixels(colors, 0, width, left, wordTop, width, height)
        return token.copy(
            provenLevel = LevelMarkerProof.level(token.text, width, height, colors, kind)
        )
    }
}
