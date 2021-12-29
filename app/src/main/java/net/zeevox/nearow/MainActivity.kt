package net.zeevox.nearow

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.Legend.LegendForm
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.components.YAxis.AxisDependency
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.utils.ColorTemplate
import net.zeevox.nearow.databinding.ActivityMainBinding
import net.zeevox.nearow.input.DataCollectionService


class MainActivity : AppCompatActivity() {
    lateinit var binding: ActivityMainBinding

    lateinit var chart: LineChart

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Nearow)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.keepScreenOn = true

        LocalBroadcastManager.getInstance(this).registerReceiver(
            broadcastReceiver, IntentFilter(
                DataCollectionService.KEY_ON_SENSOR_CHANGED_ACTION
            )
        )

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
        }
    }
}
