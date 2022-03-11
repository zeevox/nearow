package net.zeevox.nearow.ui.fragment

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.utils.ColorTemplate
import net.zeevox.nearow.R
import net.zeevox.nearow.databinding.FragmentStrokeRateDebuggerBinding

class StrokeRateDebuggerFragment : BaseFragment() {
    lateinit var chart: LineChart
    lateinit var barChart: BarChart

    private var _binding: FragmentStrokeRateDebuggerBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStrokeRateDebuggerBinding.inflate(inflater, container, false)
        return binding.root
    }


    /**
     * Called immediately after [.onCreateView]
     * has returned, but before any saved state has been restored in to the view.
     * This gives subclasses a chance to initialize themselves once
     * they know their view hierarchy has been completely created.  The fragment's
     * view hierarchy is not however attached to its parent at this point.
     * @param view The View returned by [.onCreateView].
     * @param savedInstanceState If non-null, this fragment is being re-constructed
     * from a previous saved state as given here.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupChart()
        setupBarChart()
    }

    private fun setupChart() {
        chart = binding.liveChart

        val data = LineData()
        data.setValueTextColor(Color.BLACK)

        // add empty data
        chart.data = data

        // disable description
        chart.description.isEnabled = false

        // get the legend (only possible after setting data)
        val l: Legend = chart.legend

        // modify the legend ...
        l.form = Legend.LegendForm.LINE
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
        barChart = binding.barChart
        val data = BarData()
        barChart.data = data
        chart.description.isEnabled = false
    }

    private fun createSet(): LineDataSet {
        val set = LineDataSet(null, "Acceleration Magnitude (m/s²)")
        set.axisDependency = YAxis.AxisDependency.LEFT
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

    private fun createBarSet(values: List<BarEntry>) {
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

    override fun onNewAccelerationReading(reading: Double) {
        binding.accelerationReading.text = String.format("%.3f", reading)
        addEntry(reading.toFloat())
    }

    override fun onStrokeRateUpdate(strokeRate: Double) {
        binding.strokeRate.text = getString(
            R.string.stroke_rate_display_placeholder_text,
            strokeRate,
            0.0
        )
    }

    override fun onLocationUpdate(speed: Float, totalDistance: Float) {
        // stroke rate debugger does not care about location
        return
    }

    override fun onNewAutocorrelationTable(array: DoubleArray) {
        val values = array.mapIndexed { index, frequency ->
            BarEntry(
                index.toFloat(),
                frequency.toFloat()
            )
        }

        createBarSet(values)
        barChart.invalidate()
    }
}