package com.fahmi.metronome

import android.app.Activity
import android.graphics.Color
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import kotlin.math.PI
import kotlin.math.sin

class MainActivity : Activity() {

    private val sampleRate = 44100
    @Volatile private var bpm = 100
    @Volatile private var beats = 4
    @Volatile private var running = false
    private var worker: Thread? = null

    private lateinit var bpmText: TextView
    private lateinit var beatText: TextView
    private lateinit var seek: SeekBar
    private lateinit var startBtn: Button
    private val taps = mutableListOf<Long>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        setBpm(bpm)
    }

    override fun onPause() {
        super.onPause()
        stop()
    }

    // ---------- Moteur audio ----------
    private fun click(freq: Double): ShortArray {
        val n = sampleRate * 40 / 1000
        return ShortArray(n) { i ->
            val env = 1.0 - i.toDouble() / n
            (sin(2 * PI * freq * i / sampleRate) * env * Short.MAX_VALUE * 0.8).toInt().toShort()
        }
    }

    private fun start() {
        if (running) return
        running = true
        startBtn.text = "STOP"
        worker = Thread {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(
                    AudioTrack.getMinBufferSize(
                        sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
                    )
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            track.play()
            val accent = click(1500.0)
            val normal = click(1000.0)
            var beat = 0
            while (running) {
                val len = sampleRate * 60 / bpm
                val buf = ShortArray(len)
                val c = if (beat == 0) accent else normal
                System.arraycopy(c, 0, buf, 0, minOf(c.size, len))
                val shown = beat + 1
                runOnUiThread { beatText.text = "$shown / $beats" }
                track.write(buf, 0, len) // l'écriture bloquante cadence le tempo
                beat = (beat + 1) % beats
            }
            track.stop()
            track.release()
        }.also { it.start() }
    }

    private fun stop() {
        running = false
        worker = null
        if (::startBtn.isInitialized) startBtn.text = "START"
    }

    // ---------- Logique ----------
    private fun setBpm(v: Int) {
        bpm = v.coerceIn(30, 240)
        bpmText.text = bpm.toString()
        seek.progress = bpm - 30
    }

    private fun tapTempo() {
        val now = SystemClock.elapsedRealtime()
        if (taps.isNotEmpty() && now - taps.last() > 2000) taps.clear()
        taps.add(now)
        if (taps.size > 5) taps.removeAt(0)
        if (taps.size >= 2) {
            val avg = (taps.last() - taps.first()) / (taps.size - 1)
            setBpm((60000 / avg).toInt())
        }
    }

    // ---------- Interface (sans XML) ----------
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun btn(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        textSize = 18f
        setOnClickListener { onClick() }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(dp(24), dp(24), dp(24), dp(24))
            keepScreenOn = true
        }

        bpmText = TextView(this).apply {
            textSize = 96f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        val unit = TextView(this).apply {
            text = "BPM"
            textSize = 18f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
        }
        beatText = TextView(this).apply {
            text = "– / $beats"
            textSize = 32f
            setTextColor(Color.parseColor("#4FC3F7"))
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(16))
        }

        seek = SeekBar(this).apply {
            max = 210
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    if (fromUser) setBpm(p + 30)
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        row.addView(btn("−5") { setBpm(bpm - 5) })
        row.addView(btn("−1") { setBpm(bpm - 1) })
        row.addView(btn("+1") { setBpm(bpm + 1) })
        row.addView(btn("+5") { setBpm(bpm + 5) })

        val beatsBtn = btn("Temps : $beats") {}
        beatsBtn.setOnClickListener {
            val options = listOf(2, 3, 4, 6, 8)
            beats = options[(options.indexOf(beats) + 1) % options.size]
            beatsBtn.text = "Temps : $beats"
            beatText.text = "– / $beats"
        }

        startBtn = btn("START") { if (running) stop() else start() }
        startBtn.textSize = 28f

        root.addView(bpmText, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        root.addView(unit, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        root.addView(beatText, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        root.addView(seek, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        root.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        root.addView(beatsBtn, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        root.addView(btn("TAP TEMPO") { tapTempo() }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        root.addView(startBtn, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        setContentView(root)
    }
}
