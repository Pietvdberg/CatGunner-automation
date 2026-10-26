package nl.pietvdberg.catgunner.autopilot.domain.purchase

import nl.pietvdberg.catgunner.autopilot.vision.UpgradeKind

data class NormalizedPoint(val x: Float, val y: Float)

data class CalibrationProfile(
    val width: Int,
    val height: Int,
    val gameVersion: Long,
    val targets: Map<UpgradeKind, NormalizedPoint>,
    val schema: Int = 1
) {
    fun isValid(): Boolean = schema == 1 &&
        width > 0 &&
        height > width &&
        gameVersion > 0 &&
        targets.keys == UpgradeKind.entries.toSet() &&
        targets.all { (kind, point) -> UpgradeGeometry.contains(kind, point) }

    fun matches(width: Int, height: Int, version: Long): Boolean =
        isValid() && this.width == width && this.height == height && gameVersion == version
}

/** Inset regions observed in the supplied layout. Calibration must choose each actual tap point. */
object UpgradeGeometry {
    fun contains(kind: UpgradeKind, point: NormalizedPoint): Boolean {
        val offset = kind.ordinal * 169
        return point.x.isFinite() &&
            point.y.isFinite() &&
            point.x * 570 in (68f + offset)..(162f + offset) &&
            point.y * 1280 in 1188f..1210f
    }
}
