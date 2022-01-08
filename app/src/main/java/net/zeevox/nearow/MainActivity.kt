package net.zeevox.nearow

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.os.Bundle
import android.os.IBinder
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
import net.zeevox.nearow.fourier.SlidingDFT
import net.zeevox.nearow.input.DataCollectionService


class MainActivity : AppCompatActivity(), DataCollectionService.DataUpdateListener {
    lateinit var binding: ActivityMainBinding
    lateinit var chart: LineChart
    lateinit var barChart: BarChart
    lateinit var strokeRate: MaterialTextView

    private lateinit var mService: DataCollectionService
    private var mBound: Boolean = false


    /** Defines callbacks for service binding, passed to bindService()  */
    private val connection = object : ServiceConnection {

        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            val binder = service as DataCollectionService.LocalBinder
            mService = binder.getService()
            mBound = true

            // Listen to callbacks from the service
            mService.setListener(this@MainActivity)
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            mBound = false
        }
    }

    private val slider: SlidingDFT =
        SlidingDFT(
            DataCollectionService.SAMPLE_BUFFER_SIZE,
            DataCollectionService.COMPONENTS_PER_SAMPLE
        )

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Nearow)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.keepScreenOn = true

        strokeRate = findViewById(R.id.stroke_rate)

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
//        leftAxis.axisMinimum = 0f
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

    override fun onStart() {
        super.onStart()
        // Bind to LocalService
        Intent(this, DataCollectionService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        unbindService(connection)
        mBound = false
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
//        chart.setVisibleYRange(-10f, 10f, AxisDependency.LEFT);

        // move to the latest entry
        chart.moveViewToX(data.entryCount.toFloat())
    }

    private fun frequencyComponentToRate(
        wavelength: Double,
        samplingRate: Float,
        sampleBufferSize: Int,
    ): Double {
        return wavelength * samplingRate / sampleBufferSize * 60
    }

    override fun onNewAccelerometerReadings(readings: FloatArray, samplingRate: Float) {
//        val acceleration = sqrt(readings.map { x -> x * x }.reduce { x, y -> x + y })
        val acceleration = readings[0]

        binding.accelerationReading.text = acceleration.toString()
        addEntry(acceleration)

        slider.slide(acceleration.toDouble())

        val values = slider.sliderFrequencies.map { frequency ->
            BarEntry(
                frequency.wavelength.toFloat(),
                frequency.polar.magnitude.toFloat()
            )
        }

        createFourierSet(values)
        barChart.invalidate()

        val bestWavelength: Double = slider.getMaximallyCorrelatedFrequency()?.wavelength ?: 0.0

        strokeRate.text = getString(R.string.stroke_rate_display_placeholder_text,
            frequencyComponentToRate(
                bestWavelength,
                samplingRate,
                DataCollectionService.SAMPLE_BUFFER_SIZE,
            ),
            samplingRate
        )
    }
}
