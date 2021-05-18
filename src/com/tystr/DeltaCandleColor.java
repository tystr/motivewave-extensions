package study_examples;

import com.motivewave.platform.sdk.common.*;
import com.motivewave.platform.sdk.common.desc.*;
import com.motivewave.platform.sdk.study.RuntimeDescriptor;
import com.motivewave.platform.sdk.study.Study;
import com.motivewave.platform.sdk.study.StudyHeader;

import java.awt.*;
import java.util.List;

/** Simple MACD example.  This example shows how to create a Study Graph
 that is based on the MACD study.  For simplicity code from the
 MotiveWave MACD study has been removed or altered. */
@StudyHeader(
        namespace="com.tystr",
        id="DeltaCandleColor",
        name="Delta Candle Color",
        desc="This study colors candles with high deltas",
        menu="Examples",
        overlay=true)
public class DeltaCandleColor extends Study
{
    // This enumeration defines the variables that we are going to store in the Data Series
    enum Values { MACD, SIGNAL, HIST };
    final static String HIST_IND = "histInd"; // Histogram Parameter

    /** This method initializes the settings and defines the runtime settings. */
    @Override
    public void initialize(Defaults defaults)
    {
        // Define the settings for this study
        // We are creating 2 tabs: 'General' and 'Display'
        var sd = createSD();
        var tab = sd.addTab("General");

        // Define the 'Inputs'
        var grp = tab.addGroup("Inputs");
        grp.addRow(new InputDescriptor(Inputs.INPUT, "Input", Enums.BarInput.CLOSE));
        grp.addRow(new IntegerDescriptor("PositiveDeltaThreshold", "Positive Threshold", 1000, 1, 9999, 1));
        grp.addRow(new ColorDescriptor("PositiveDeltaColor", "Positive Delta Candle Color", Color.GREEN));
        grp.addRow(new IntegerDescriptor("NegativeDeltaThreshold", "Negative Threshold", 1000, 1, 9999, 1));
        grp.addRow(new ColorDescriptor("NegativeDeltaColor", "Negative Delta Candle Color", Color.RED));
    }

    /** This method calculates the MACD values for the data at the given index. */
    @Override
    protected void calculate(int index, DataContext ctx)
    {
        int MAX_BARS = 500; // todo make this confiugrable

        DataSeries series = ctx.getDataSeries();
        if (index < series.size() - MAX_BARS) {
            return;
        }
        debug("DataSeries size: " + series.size());
//        if (!series.isComplete(index)) {
//            return;
//        }
        Settings settings = getSettings();
        Color positiveDeltaColor = settings.getColor("PositiveDeltaColor");
        List<Tick> ticks = ctx.getInstrument().getTicks(series.getStartTime(index), series.getEndTime(index));
        int delta = series.getInt(index, "Delta");
        if (!series.isComplete(index)) {
            delta = getDeltaForTicks(ticks);
            debug("calculated delta " + delta + " for index " + index);
            series.setInt(index, "Delta", delta);
            series.setComplete(index);
        } else {
            debug("found delta " + delta + " for index " + index);
        }
        //debug("delta for index " + index + ": " + delta);

        int positiveDeltaThreshold = settings.getInteger("PositiveDeltaThreshold", 1000);
        int negativeDeltaThreshold = - settings.getInteger("NegativeDeltaThreshold", 1000);
        debug("Using positive threshold " + positiveDeltaThreshold);
        debug("Using negative threshold " + negativeDeltaThreshold);
        if (delta > positiveDeltaThreshold) {
            series.setPriceBarColor(index, positiveDeltaColor);
        } else if (delta < negativeDeltaThreshold) {
            series.setPriceBarColor(index, settings.getColor("NegativeDeltaColor"));
        }
    }

    /**
     * Calculates the delta between bid and ask volume for the given list of ticks
     *
     * @param ticks A List of ticks over which delta is to be calculated
     * @return The delta
     */
    protected int getDeltaForTicks(List<Tick> ticks) {
        int delta = 0;
        for (Tick tick : ticks) {
            if (tick.isAskTick()) {
                delta += tick.getVolume();
            } else
                delta -= tick.getVolume();
        }

        return delta;
    }
}
