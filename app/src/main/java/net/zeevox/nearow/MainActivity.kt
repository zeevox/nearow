package net.zeevox.nearow

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.Legend.LegendForm
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.components.YAxis.AxisDependency
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.android.material.textview.MaterialTextView
import net.zeevox.nearow.databinding.ActivityMainBinding
import net.zeevox.nearow.input.DataCollectionService
import se.imagick.ft.slidingdft.DFTSlider
import se.imagick.ft.slidingdft.DFTSliderImpl


class MainActivity : AppCompatActivity() {
    lateinit var binding: ActivityMainBinding
    lateinit var chart: LineChart
    lateinit var barChart: BarChart
    lateinit var strokeRate: MaterialTextView

    val MAX_STROKE_RATE = 100

    val slider: DFTSlider = DFTSliderImpl(MAX_STROKE_RATE)

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Nearow)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.keepScreenOn = true

        strokeRate = findViewById(R.id.stroke_rate)

        LocalBroadcastManager.getInstance(this).registerReceiver(
            broadcastReceiver, IntentFilter(
                DataCollectionService.KEY_ON_SENSOR_CHANGED_ACTION
            )
        )

        setupChart()
        setupBarChart()
    }

    private fun setupChart() {
        chart = findViewById(R.id.live_chart)

        val data = LineData()
        data.setValueTextColor(Color.BLACK)

        // add empty data
        chart.data = data

        // disable description
        chart.description.isEnabled = false

        // get the legend (only possible after setting data)
        val l: Legend = chart.legend

        // modify the legend ...
        l.form = LegendForm.LINE
        l.textColor = Color.BLACK

        val xl: XAxis = chart.xAxis
        xl.textColor = Color.BLACK
        xl.setDrawGridLines(false)
        xl.setAvoidFirstLastClipping(true)
        xl.isEnabled = true

        val leftAxis: YAxis = chart.axisLeft
        leftAxis.textColor = Color.BLACK
        leftAxis.axisMinimum = 0f
        leftAxis.setDrawGridLines(true)

        val rightAxis: YAxis = chart.axisRight
        rightAxis.isEnabled = false
    }

    private fun setupBarChart() {
        barChart = findViewById(R.id.bar_chart)
        val data = BarData()
        barChart.data = data
        chart.description.isEnabled = false
    }

    override fun onResume() {
        super.onResume()
        startForegroundServiceForSensors(false)
    }

    private fun startForegroundServiceForSensors(background: Boolean) {
        val dataCollectionServiceIntent = Intent(this, DataCollectionService::class.java)
        dataCollectionServiceIntent.putExtra(DataCollectionService.KEY_BACKGROUND, background)
        ContextCompat.startForegroundService(this, dataCollectionServiceIntent)
    }

    override fun onPause() {
        super.onPause()
        startForegroundServiceForSensors(true)
    }

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver)
        super.onDestroy()
    }

    private fun createSet(): LineDataSet {
        val set = LineDataSet(null, "Acceleration Magnitude (m/s²)")
        set.axisDependency = AxisDependency.LEFT
        set.color = ColorTemplate.getHoloBlue()
        set.lineWidth = 2f
        set.setDrawCircles(false)
        set.mode = LineDataSet.Mode.CUBIC_BEZIER
        set.fillAlpha = 65
        set.fillColor = ColorTemplate.getHoloBlue()
        set.highLightColor = Color.rgb(244, 117, 117)
        set.valueTextColor = Color.BLACK
        set.valueTextSize = 9f
        set.setDrawValues(false)
        return set
    }

    private fun createFourierSet(values: List<BarEntry>) {
        val set1: BarDataSet

        if (barChart.data != null &&
            barChart.data.dataSetCount > 0
        ) {
            set1 = barChart.data.getDataSetByIndex(0) as BarDataSet
            set1.values = values
            barChart.data.notifyDataChanged()
            barChart.notifyDataSetChanged()
        } else barChart.data = BarData(listOf(BarDataSet(values, "DFT")))
    }


    private fun addEntry(value: Float) {
        val data: LineData = chart.data
        var set = data.getDataSetByIndex(0)

        if (set == null) {
            set = createSet()
            data.addDataSet(set)
        }

        data.addEntry(
            Entry(set.entryCount.toFloat(), value), 0
        )
        data.notifyDataChanged()

        // let the chart know it's data has changed
        chart.notifyDataSetChanged()

        // limit the number of visible entries
        chart.setVisibleXRangeMaximum(120F)
        // chart.setVisibleYRange(30, AxisDependency.LEFT);

        // move to the latest entry
        chart.moveViewToX(data.entryCount.toFloat())
    }

    private val broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val acceleration =
                intent.getFloatExtra(DataCollectionService.KEY_ACCELERATION_MAGNITUDE, -1.0f)
            binding.accelerationReading.text = acceleration.toString()
            addEntry(acceleration)

            slider.slide(acceleration.toDouble())

            val values = mutableListOf<BarEntry>()

            var maxMagnitude: Double = -1.0
            var maxFrequencyBin: Int = 0

            for (frequencyComponentNo in 1..MAX_STROKE_RATE) {
                Log.d(this.javaClass.simpleName, slider.latencyInSamples.toString())
                val polar =
                    slider.getPolar(frequencyComponentNo) // NB! See documentation about instance reuse.

                val complex =
                    slider.getComplex(frequencyComponentNo) // NB! See documentation about instance reuse.

                val realSum = slider.getRealSum(false)

                if (polar.magnitude > maxMagnitude) {
                    maxMagnitude = polar.magnitude
                    maxFrequencyBin = frequencyComponentNo
                }

                values.add(
                    BarEntry(
                        frequencyComponentNo.toFloat(),
                        polar.magnitude.toFloat()
                    )
                )

                Log.d(
                    this.javaClass.simpleName,
                    "$frequencyComponentNo ${polar.magnitude} ${polar.phase} ${complex.real} ${complex.imaginary} $realSum"
                )
            }

            createFourierSet(values)
            barChart.invalidate()

            strokeRate.text = (maxFrequencyBin.toFloat() * 50 / (MAX_STROKE_RATE * 2) * 60).toString() + "bpm"

        }
    }
}
