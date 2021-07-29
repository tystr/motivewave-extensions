package com.tystr;

import com.motivewave.platform.sdk.common.*;
import com.motivewave.platform.sdk.common.desc.*;
import com.motivewave.platform.sdk.draw.Marker;
import com.motivewave.platform.sdk.study.Plot;
import com.motivewave.platform.sdk.study.Study;
import com.motivewave.platform.sdk.study.StudyHeader;
import com.motivewave.platform.study.ma.VWAP;

import java.awt.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

@StudyHeader(
        namespace="com.tystr",
        id="VALUE_AREA_EXTENSION",
//        rb="study_examples.nls.strings", // locale specific strings are loaded from here
        name="Value Area Extension",
        label="Value Area Extension",
        desc="This study plots the developing Low, Mid, and High of the session Volume Value Area, as well as extensions above and below",
        menu="Tystr",
        overlay=true,
        studyOverlay=true,
        requiresVolume = true
)
public class ValueAreaExtension extends Study
{
    enum Values { MA, VAH, VAL, VAH_1, VAH_2, VAL_1, VAL_2, VA_PIVOT };
    enum Sessions { RTH, GLOBEX };

    private SortedMap<Float, Integer> volumeByPrice;
    private SortedMap<Integer, SortedMap<Float, Integer>> volumeByPriceByIndex;

    private SortedMap<Float, Integer> valueArea;
    private boolean isBarInsideWindow = false;


    /** This method initializes the study by doing the following:
     1. Define Settings (Design Time Information)
     2. Define Runtime Information (Label, Path and Exported Value) */
    @Override
    public void initialize(Defaults defaults)
    {
        // Describe the settings that may be configured by the user.
        // Settings may be organized using a combination of tabs and groups.
        var sd = createSD();
        var tab = sd.addTab("General");

        var grp = tab.addGroup(get("LBL_INPUTS"));
        // Declare the inputs that are used to calculate the moving average.
        // Note: the 'Inputs' class defines several common input keys.
        // You can use any alpha-numeric string that you like.
        grp.addRow(new InputDescriptor(Inputs.INPUT, get("Input"), Enums.BarInput.CLOSE));
        grp.addRow(new IntegerDescriptor(Inputs.PERIOD, get("Period"), 20, 1, 9999, 1));

        grp = tab.addGroup("Display");
        // Allow the user to change the settings for the path that will
        // draw the moving average on the graph.  In this case, we are going
        // to use the input key Inputs.PATH
        grp.addRow(new PathDescriptor(Inputs.PATH, get("Path"), null, 1.0f, null, true, true, false));

        // Describe the runtime settings using a 'RuntimeDescriptor'
        var desc = createRD();

        // Describe how to create the label.  The label uses the
        // 'label' attribute in the StudyHeader (see above) and adds the input values
        // defined below to generate a label.
//        desc.setLabelSettings(Inputs.INPUT, Inputs.PERIOD);
        // Exported values can be used to display cursor data
        // as well as provide input parameters for other studies,
        // generate alerts or scan for study patterns (see study scanner).
//        desc.exportValue(new ValueDescriptor(Values.MA, get("My MA"),
//                new String[] {Inputs.INPUT, Inputs.PERIOD}));
        // MotiveWave will automatically draw a path using the path settings
        // (described above with the key 'Inputs.LINE')  In this case
        // it will use the values generated in the 'calculate' method
        // and stored in the data series using the key 'Values.MA'
//        desc.declarePath(Values.MA, Inputs.PATH);



        desc.exportValue(new ValueDescriptor(Values.VAH, "VAH", new String[] {"VAH_LINE"}));
        desc.exportValue(new ValueDescriptor(Values.VAL, "VAL", new String[] {"VAL_LINE"}));
        desc.exportValue(new ValueDescriptor(Values.VAL, "VA Mid", new String[] {"VALMID_LINE"}));

        PathDescriptor vahLinePathDescriptor = new PathDescriptor("VAH_LINE", "VAH Line", null, 1.0f, null, true, true, true);
        PathDescriptor valLinePathDescriptor = new PathDescriptor("VAL_LINE", "VAL Line", null, 1.0f, null, true, true, true);
        PathDescriptor vah1LinePathDescriptor = new PathDescriptor("VAH_1_LINE", "VAH Ext 1 Line", null, 1.0f, null, true, true, true);
        PathDescriptor val1LinePathDescriptor = new PathDescriptor("VAL_1_LINE", "VAL Ext 1 Line", null, 1.0f, null, true, true, true);
        PathDescriptor vamidLinePathDescriptor = new PathDescriptor("VAMID_LINE", "VA Mid Line", null, 1.0f, null, true, true, true);


        vahLinePathDescriptor.setContinuous(false);
        valLinePathDescriptor.setContinuous(false);
        vah1LinePathDescriptor.setContinuous(false);
        val1LinePathDescriptor.setContinuous(false);

        vamidLinePathDescriptor.setContinuous(false);

        grp.addRow(vahLinePathDescriptor);
        grp.addRow(valLinePathDescriptor);
        grp.addRow(vah1LinePathDescriptor);
        grp.addRow(val1LinePathDescriptor);

        grp.addRow(vamidLinePathDescriptor);

        desc.declarePath(Values.VAH, "VAH_LINE");
        desc.declarePath(Values.VAL, "VAL_LINE");
        desc.declarePath(Values.VAH_1, "VAH_1_LINE");
        desc.declarePath(Values.VAL_1, "VAL_1_LINE");

        desc.declarePath(Values.VA_PIVOT, "VAMID_LINE");

        grp.addRow(new DoubleDescriptor("ValueAreaPercent", "Value Area", 68.2, 0, 100, 0.10));

        sd.addQuickSettings("VAH_LINE", "VAL_LINE", "VAH_1_LINE", "VAL_1_LINE", "VAMID_LINE", "ValueAreaPercent");
    }

    @Override
    public int getMinBars()
    {
        return getSettings().getInteger(Inputs.PERIOD)*2;
    }


    @Override
    protected void calculateValues(DataContext ctx) {
        DataSeries series = ctx.getDataSeries();
        Instrument instrument = series.getInstrument();

        int startIndex = series.size() - 1000;
        TickOperation calculator = new VPCalculator(startIndex, series);
        instrument.forEachTick(series.getStartTime(startIndex), ctx.getCurrentTime() + Util.MILLIS_IN_MINUTE, ctx.isRTH(), calculator);
    }

    class VPCalculator implements TickOperation {
        private final DataSeries series;
        private int startIndex;
        private int nextIndex;
        private final boolean rth = true;
        private SortedMap<Float, Integer> volumeByPrice;
        private boolean calculating = false;

        private long nextEnd;
        public VPCalculator(int startIndex, DataSeries series) {
            this.startIndex = startIndex;
            this.series = series;
            this.nextIndex = startIndex;
            this.volumeByPrice = new TreeMap<>();
            nextEnd = series.getInstrument().getEndOfDay(series.getStartTime(startIndex), rth);
            // weekly
            nextEnd = series.getInstrument().getEndOfWeek(series.getStartTime(startIndex), rth);
        }

        public void onTick(Tick tick) {
            if (tick.getTime() > series.getEndTime(nextIndex)) {
                calculateValueArea();
                notifyRedraw();
                series.setComplete(nextIndex);
                nextIndex++;
                debug("advanced index to " + nextIndex);

                double vah = (double) series.getDouble(nextIndex, Values.VAH, 0d);
                double val = (double) series.getDouble(nextIndex, Values.VAL, 0d);
                double vah_1 = (double) series.getDouble(nextIndex, Values.VAH_1, 0d);
                double val_1 = (double) series.getDouble(nextIndex, Values.VAL_1, 0d);
                double pivot = (double) series.getDouble(nextIndex, Values.VA_PIVOT, 0d);

//                debug("Index " + nextIndex + ": VAH " + vah);
//                debug("Index " + nextIndex + ": VAL " + val);
//                debug("Index " + nextIndex + ": VA Breadth " + (vah - val));
//                debug("Index " + nextIndex + ": VA Pivot (mid)" + pivot);
            }

            if (series.isComplete(nextIndex)) return;

            Instrument instrument = series.getInstrument();
            if (rth && !instrument.isInsideTradingHours(tick.getTime(), rth)) {
                if  (calculating) {
                    volumeByPrice.clear();
                    calculating = false;
                }
                return;
            }

            // restart at beginning of session if consecutive
            if (tick.getTime() > nextEnd) {
//                nextEnd = series.getInstrument().getEndOfDay(tick.getTime(), rth);
                // if weekly
                nextEnd = series.getInstrument().getEndOfWeek(tick.getTime(), rth);

                volumeByPrice.clear();
            }
            calculating = true;

            float price = tick.isAskTick() ? tick.getAskPrice() : tick.getBidPrice();
            int volume = volumeByPrice.getOrDefault(price, 0);
            volume += tick.getVolume();
            volumeByPrice.put(price, volume);

            if (volumeByPrice.isEmpty()) return;
        }

        private void calculateValueArea() {
            float interval = (float) series.getInstrument().getTickSize();
            float volumePOC = Collections.max(volumeByPrice.entrySet(), Map.Entry.comparingByValue()).getKey();
            int totalVolume = volumeByPrice.values().stream().mapToInt(i -> i).sum();
            int runningVolume = 0;
            valueArea = new TreeMap<>(); // Reset Value Area

            // Add volume POC to value area
            valueArea.put(volumePOC, volumeByPrice.get(volumePOC));

            float abovePrice1 = volumePOC + interval;
            float abovePrice2 = volumePOC + (interval * 2);
            float belowPrice1 = volumePOC; //- interval;
            float belowPrice2 = volumePOC; // - (interval * 2);
            boolean incrementAbove = false;
            int aboveIncrements = 0;
            int belowIncrements = -2;

            for (int i = 1; i <= volumeByPrice.size(); i++) {
                if (incrementAbove) {
                    aboveIncrements = aboveIncrements + 2;
                    abovePrice1 = volumePOC + (interval * (aboveIncrements + 1));
                    abovePrice2 = volumePOC + (interval * (aboveIncrements + 2));
                } else {
                    belowIncrements = belowIncrements + 2;
                    belowPrice1 = volumePOC - (interval * (belowIncrements + 1));
                    belowPrice2 = volumePOC - (interval * (belowIncrements + 2));
                }

                int abovePrice1Volume = volumeByPrice.getOrDefault(abovePrice1, 0);
                int abovePrice2Volume = volumeByPrice.getOrDefault(abovePrice2, 0);
                int belowPrice1Volume = volumeByPrice.getOrDefault(belowPrice1, 0);
                int belowPrice2Volume = volumeByPrice.getOrDefault(belowPrice2, 0);

                int aboveSum = abovePrice1Volume + abovePrice2Volume;
                int belowSum = belowPrice1Volume + belowPrice2Volume;

                if (aboveSum > belowSum) {
                    incrementAbove = true;
                    valueArea.put(abovePrice1, abovePrice1Volume);
                    valueArea.put(abovePrice2, abovePrice2Volume);
                    runningVolume += abovePrice1Volume + abovePrice2Volume;
                } else {
                    incrementAbove = false;
                    valueArea.put(belowPrice1, belowPrice1Volume);
                    valueArea.put(belowPrice2, belowPrice2Volume);
                    runningVolume += belowPrice1Volume + belowPrice2Volume;
                }

                float valueAreaPercent = (float) runningVolume / totalVolume;
                if (valueAreaPercent >= (getSettings().getDouble("ValueAreaPercent") / 100)) break;
            }

            double vah = valueArea.lastKey();
            double val = valueArea.firstKey();
            double breadth = vah - val;
            double pivot = vah - (breadth / 2);
            double vah_1 = vah + breadth;
            double val_1 = val - breadth;

            series.setDouble(nextIndex, Values.VAH, vah);
            series.setDouble(nextIndex, Values.VAL,  val);
            series.setDouble(nextIndex, Values.VAH_1, vah_1);
            series.setDouble(nextIndex, Values.VAL_1, val_1);
            series.setDouble(nextIndex, Values.VA_PIVOT, pivot);
        }
    }
}
