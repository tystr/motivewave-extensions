package com.tystr.study;

import com.motivewave.platform.sdk.common.*;
import com.motivewave.platform.sdk.common.desc.*;
import com.motivewave.platform.sdk.study.Study;
import com.motivewave.platform.sdk.study.StudyHeader;
import com.tystr.VolumeProfile;

import java.util.List;

@StudyHeader(
        namespace="com.tystr.study",
        id="TYSTR_DEVELOPING_VALUE_AREA",
        name="Developing Value Area",
        label="Developing Value Area",
        desc="This study plots the developing value area high, low, and mid for the chosen timeframe, as well as extensions above and below",
        menu="Tystr",
        overlay=true,
        studyOverlay=true,
        requiresVolume = true
)
public class DevelopingValueArea extends Study
{
    enum Values { MA, VAH, VAL, VAH_1, VAH_2, VAL_1, VAL_2, VA_PIVOT, TIMEFRAME};
    TickOperation calculator;
    private boolean isCalculating = false;

    private final String TIMEFRAME = "timeframe";
    private final String RTH_DATA = "rthData";
    private final String VALUE_AREA_HIGH = "vah";
    private final String VALUE_AREA_LOW = "val";
    private final String VALUE_AREA_MID = "vam";
    private final String VALUE_AREA_HIGH_EXTENSION_1 = "vah1";
    private final String VALUE_AREA_LOW_EXTENSION_1 = "val1";
    private final String VALUE_AREA_PERCENT = "vap";

    @Override
    public void initialize(Defaults defaults)
    {
        var sd = createSD();
        var tab = sd.addTab("General");
        SettingGroup grp = tab.addGroup("Display");

        var desc = createRD();

        List<NVP> timeframes = List.of(
                new NVP("Daily", "Daily"),
                new NVP("Weekly", "Weekly")
        );

        grp.addRow(new DiscreteDescriptor(TIMEFRAME, "Timeframe", "Daily", timeframes));
        grp.addRow(new BooleanDescriptor(RTH_DATA, "RTH Data", true));

        desc.exportValue(new ValueDescriptor(Values.VAH, "VAH", null));
        desc.exportValue(new ValueDescriptor(Values.VAL, "VAL", null));
        desc.exportValue(new ValueDescriptor(Values.VA_PIVOT, "VA Mid", null));

        PathDescriptor vahLinePathDescriptor = new PathDescriptor(VALUE_AREA_HIGH, "VAH Line", defaults.getBlueLine(), 1.0f, null, true, true, true);
        PathDescriptor valLinePathDescriptor = new PathDescriptor(VALUE_AREA_LOW, "VAL Line", defaults.getRedLine(), 1.0f, null, true, true, true);
        PathDescriptor vah1LinePathDescriptor = new PathDescriptor(VALUE_AREA_HIGH_EXTENSION_1, "VAH Ext 1 Line", defaults.getBlueLine(), 1.0f, null, true, true, true);
        PathDescriptor val1LinePathDescriptor = new PathDescriptor(VALUE_AREA_LOW_EXTENSION_1, "VAL Ext 1 Line", defaults.getRedLine(), 1.0f, null, true, true, true);
        PathDescriptor vamidLinePathDescriptor = new PathDescriptor(VALUE_AREA_MID, "VA Mid Line", defaults.getYellowLine(), 1.0f, null, true, true, true);

        // Make sure these are set to continuous = false so the study is not drown for bars for which there is no data (e.g. ETH bars when plotting RTH data only)
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
        grp.addRow(new DoubleDescriptor(VALUE_AREA_PERCENT, "Value Area", 68.2, 0, 100, 0.10));

        desc.declarePath(Values.VAH, VALUE_AREA_HIGH);
        desc.declarePath(Values.VAL, VALUE_AREA_LOW);
        desc.declarePath(Values.VAH_1, VALUE_AREA_HIGH_EXTENSION_1);
        desc.declarePath(Values.VAL_1, VALUE_AREA_LOW_EXTENSION_1);
        desc.declarePath(Values.VA_PIVOT, VALUE_AREA_MID);

        sd.addQuickSettings(VALUE_AREA_HIGH, VALUE_AREA_LOW, VALUE_AREA_HIGH_EXTENSION_1, VALUE_AREA_LOW_EXTENSION_1, VALUE_AREA_MID, VALUE_AREA_PERCENT);

        clearFigures();
    }

    @Override
    protected void calculateValues(DataContext ctx) {
        DataSeries series = ctx.getDataSeries();
        if (series.size() == 0 || isCalculating) return;
        Instrument instrument = series.getInstrument();
        boolean isRTH = getSettings().getBoolean(RTH_DATA);

        int maxPrints = 30; // @todo does this need to be configurable in settings (probably)?
        long start = series.getStartTime();
        for (int i = 0; i < maxPrints; i++) {
            start = Util.getStartOfPrevDay(start, series.getInstrument(), isRTH);
        }

        long finalStart = start;
        Util.schedule(() -> {
            int startIndex = series.findIndex(finalStart);
            calculator = new VPCalculator(startIndex, series, isRTH);
            isCalculating = true;
            instrument.forEachTick(series.getStartTime(startIndex), ctx.getCurrentTime() + Util.MILLIS_IN_MINUTE, isRTH, calculator);
            isCalculating = false;
            notifyRedraw();
        });
    }

    @Override
    public void onTick(DataContext ctx, Tick tick) {
        if (isCalculating || calculator == null) return;
        calculator.onTick(tick);
    }

    class VPCalculator implements TickOperation {
        private final DataSeries series;
        private int nextIndex;
        private final boolean rth;
        private final VolumeProfile volumeProfile;
        private long nextEnd;

        public VPCalculator(int startIndex, DataSeries series, boolean isRth) {
            this.rth = isRth;
            this.series = series;
            this.nextIndex = startIndex;
            this.volumeProfile = new VolumeProfile();
            nextEnd = getEndForTimeframe(getSettings().getString(TIMEFRAME), series.getStartTime(startIndex));
        }

        public void onTick(Tick tick) {
            volumeProfile.addVolumeAtPrice(tick.isAskTick() ? tick.getAskPrice() : tick.getBidPrice(), tick.getVolume());
            if (tick.getTime() > series.getEndTime(nextIndex)) {
                calculate();
                series.setComplete(nextIndex);
                nextIndex++;
            } else if (!isCalculating) {
                calculate();
            }

            // reset if after end of timeframe (daily, weekly, etc)
            if (tick.getTime() > nextEnd) {
                nextEnd = getEndForTimeframe(getSettings().getString(TIMEFRAME), tick.getTime());
                volumeProfile.clear();
            }
        }

        private void calculate() {
            if (volumeProfile.isEmpty()) return;
            volumeProfile.calculateValueArea();

            double vah = volumeProfile.getValueAreaHigh();
            double val = volumeProfile.getValueAreaLow();
            double breadth = volumeProfile.getValueAreaBreadth();
            double pivot = vah - (breadth / 2);
            double vah_1 = vah + breadth;
            double val_1 = val - breadth;

            series.setDouble(nextIndex, Values.VAH, vah);
            series.setDouble(nextIndex, Values.VAL, val);
            series.setDouble(nextIndex, Values.VAH_1, vah_1);
            series.setDouble(nextIndex, Values.VAL_1, val_1);
            series.setDouble(nextIndex, Values.VA_PIVOT, pivot);
        }

        private long getEndForTimeframe(String timeframe, long time) {
            switch (timeframe) {
                case "Daily":
                    return series.getInstrument().getEndOfDay(time, rth);
                case "Weekly":
                    return series.getInstrument().getEndOfWeek(time, rth);
                default:
                    throw new RuntimeException("Timeframe must be one of \"Daily\" or \"Weekly\", received \"" + timeframe + "\".");
            }
        }
    }
}
