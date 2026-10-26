package nl.pietvdberg.catgunner.autopilot.ui

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import nl.pietvdberg.catgunner.autopilot.R
import nl.pietvdberg.catgunner.autopilot.data.CalibrationPreview
import nl.pietvdberg.catgunner.autopilot.data.CalibrationStore
import nl.pietvdberg.catgunner.autopilot.data.PreviewStore
import nl.pietvdberg.catgunner.autopilot.domain.purchase.CalibrationProfile
import nl.pietvdberg.catgunner.autopilot.domain.purchase.NormalizedPoint
import nl.pietvdberg.catgunner.autopilot.domain.purchase.UpgradeGeometry
import nl.pietvdberg.catgunner.autopilot.service.SessionCommands
import nl.pietvdberg.catgunner.autopilot.vision.UpgradeKind

class CalibrationActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SessionCommands.pause()
        val snapshot = PreviewStore.preview.value
        if (snapshot == null || snapshot.gameVersion != CalibrationStore.gameVersion(this)) {
            Toast.makeText(this, R.string.calibration_missing_frame, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding * 2, padding, padding * 2)
        }
        content.addView(
            TextView(this).apply {
                setText(R.string.calibration_instructions)
                textSize =
                    18f
            }
        )
        val selection = TextView(this).apply { textSize = 18f }
        content.addView(selection)
        val targets = mutableMapOf<UpgradeKind, NormalizedPoint>()
        var selected = UpgradeKind.POWER
        lateinit var preview: TargetPreview
        fun updateSelection() {
            selection.text = getString(R.string.calibration_selected, selected.name, targets.size)
            preview.selected = selected
            preview.invalidate()
        }
        preview = TargetPreview(this).apply {
            configure(snapshot, targets) { point ->
                if (!UpgradeGeometry.contains(selected, point)) {
                    Toast.makeText(
                        this@CalibrationActivity,
                        R.string.calibration_outside,
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    targets[selected] = point
                    selected = UpgradeKind.entries.firstOrNull { it !in targets } ?: selected
                    updateSelection()
                }
            }
        }
        content.addView(preview)
        UpgradeKind.entries.forEach { kind ->
            content.addView(
                Button(this).apply {
                    text = kind.name
                    setOnClickListener {
                        selected = kind
                        updateSelection()
                    }
                }
            )
        }
        content.addView(
            Button(this).apply {
                setText(R.string.calibration_save)
                setOnClickListener {
                    val profile = CalibrationProfile(
                        snapshot.nativeWidth,
                        snapshot.nativeHeight,
                        snapshot.gameVersion,
                        targets.toMap()
                    )
                    if (!profile.isValid()) {
                        Toast.makeText(
                            this@CalibrationActivity,
                            R.string.calibration_incomplete,
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        try {
                            CalibrationStore(this@CalibrationActivity).save(profile)
                            Toast.makeText(
                                this@CalibrationActivity,
                                R.string.calibration_saved,
                                Toast.LENGTH_LONG
                            ).show()
                            finish()
                        } catch (error: Exception) {
                            Toast.makeText(
                                this@CalibrationActivity,
                                R.string.calibration_save_failed,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
        )
        updateSelection()
        setContentView(ScrollView(this).apply { addView(content) })
    }
}

/** Calibration overlays exist only in AutoPilot, never over the live game. */
private class TargetPreview(context: android.content.Context) : View(context) {
    private lateinit var snapshot: CalibrationPreview
    private lateinit var targets: Map<UpgradeKind, NormalizedPoint>
    private lateinit var choose: (NormalizedPoint) -> Unit
    private val sourceRect = Rect()
    private val destinationRect = RectF()
    private var top = 0

    fun configure(
        snapshot: CalibrationPreview,
        targets: Map<UpgradeKind, NormalizedPoint>,
        choose: (NormalizedPoint) -> Unit
    ) {
        this.snapshot = snapshot
        this.targets = targets
        this.choose = choose
        top = snapshot.bitmap.height * 1128 / 1280
        sourceRect.set(0, top, snapshot.bitmap.width, snapshot.bitmap.height)
        contentDescription = context.getString(R.string.calibration_preview_description)
    }

    var selected = UpgradeKind.POWER
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var chosen: NormalizedPoint? = null

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        if (!::snapshot.isInitialized) {
            setMeasuredDimension(width, 0)
            return
        }
        setMeasuredDimension(
            width,
            (
                width.toLong() * (snapshot.bitmap.height - top) /
                    snapshot.bitmap.width
                ).toInt()
        )
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        destinationRect.set(0f, 0f, width.toFloat(), height.toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!::snapshot.isInitialized) return
        canvas.drawBitmap(snapshot.bitmap, sourceRect, destinationRect, paint)
        fun screenY(normalized: Float): Float =
            (normalized * snapshot.bitmap.height - top) * height / (snapshot.bitmap.height - top)
        UpgradeKind.entries.forEach { kind ->
            val offset = kind.ordinal * 169
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3 * resources.displayMetrics.density
            paint.color = if (kind == selected) Color.YELLOW else Color.WHITE
            canvas.drawRect(
                (68f + offset) / 570 * width,
                screenY(1188f / 1280),
                (162f + offset) / 570 * width,
                screenY(1210f / 1280),
                paint
            )
            targets[kind]?.let { point ->
                paint.color = Color.RED
                canvas.drawCircle(
                    point.x * width,
                    screenY(point.y),
                    7 * resources.displayMetrics.density,
                    paint
                )
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            chosen = NormalizedPoint(
                event.x / width,
                (top + event.y / height * (snapshot.bitmap.height - top)) / snapshot.bitmap.height
            )
            performClick()
            return true
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        chosen?.let(choose)
        return true
    }
}
