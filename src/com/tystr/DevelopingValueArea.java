package com.tystr;

import com.motivewave.platform.sdk.common.*;
import com.motivewave.platform.sdk.common.desc.*;
import com.motivewave.platform.sdk.study.Study;
import com.motivewave.platform.sdk.study.StudyHeader;

import java.util.List;

@StudyHeader(
        namespace="com.tystr",
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

        grp.addRow(new DiscreteDescriptor("Timeframe", "Timeframe", "Daily", timeframes));

        desc.exportValue(new ValueDescriptor(Values.VAH, "VAH", new String[] {"VAH_LINE"}));
        desc.exportValue(new ValueDescriptor(Values.VAL, "VAL", new String[] {"VAL_LINE"}));
        desc.exportValue(new ValueDescriptor(Values.VA_PIVOT, "VA Mid", new String[] {"VALMID_LINE"}));

        PathDescriptor vahLinePathDescriptor = new PathDescriptor("VAH_LINE", "VAH Line", defaults.getBlueLine(), 1.0f, null, true, true, true);
        PathDescriptor valLinePathDescriptor = new PathDescriptor("VAL_LINE", "VAL Line", defaults.getRedLine(), 1.0f, null, true, true, true);
        PathDescriptor vah1LinePathDescriptor = new PathDescriptor("VAH_1_LINE", "VAH Ext 1 Line", defaults.getBlueLine(), 1.0f, null, true, true, true);
        PathDescriptor val1LinePathDescriptor = new PathDescriptor("VAL_1_LINE", "VAL Ext 1 Line", defaults.getRedLine(), 1.0f, null, true, true, true);
        PathDescriptor vamidLinePathDescriptor = new PathDescriptor("VAMID_LINE", "VA Mid Line", defaults.getYellowLine(), 1.0f, null, true, true, true);

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
        grp.addRow(new DoubleDescriptor("ValueAreaPercent", "Value Area", 68.2, 0, 100, 0.10));

        desc.declarePath(Values.VAH, "VAH_LINE");
        desc.declarePath(Values.VAL, "VAL_LINE");
        desc.declarePath(Values.VAH_1, "VAH_1_LINE");
        desc.declarePath(Values.VAL_1, "VAL_1_LINE");
        desc.declarePath(Values.VA_PIVOT, "VAMID_LINE");

        sd.addQuickSettings("VAH_LINE", "VAL_LINE", "VAH_1_LINE", "VAL_1_LINE", "VAMID_LINE", "ValueAreaPercent");
    }

    @Override
    protected void calculateValues(DataContext ctx) {
        DataSeries series = ctx.getDataSeries();
        Instrument instrument = series.getInstrument();

        int maxDays = 10; // @todo configure this
        int startIndex = 1;
        long threshold = instrument.getStartOfDay(series.getStartTime(), ctx.isRTH()) - ((maxDays+1) * Util.MILLIS_IN_DAY);
        for (int i = series.size()-1; i > 0; i--) {
            startIndex = i;
            if (series.getStartTime(i) < threshold) break;
        }
        TickOperation calculator = new VPCalculator(startIndex, series, ctx.isRTH());
        instrument.forEachTick(series.getStartTime(startIndex), ctx.getCurrentTime() + Util.MILLIS_IN_MINUTE, ctx.isRTH(), calculator);
    }

    class VPCalculator implements TickOperation {
        private final DataSeries series;
        private int nextIndex;
        private final boolean rth;
        private final VolumeProfile volumeProfile;
        private boolean calculating = false;
        private long nextEnd;

        public VPCalculator(int startIndex, DataSeries series, boolean isRth) {
            this.rth = isRth;
            this.series = series;
            this.nextIndex = startIndex;
            this.volumeProfile = new VolumeProfile();
            nextEnd = getEndForTimeframe(getSettings().getString("Timeframe"), series.getStartTime(startIndex));
        }

        public void onTick(Tick tick) {
            if (series.isComplete(nextIndex)) return; // nothing to do if this index is complete

            if (tick.getTime() > series.getEndTime(nextIndex)) {
                if (!volumeProfile.isEmpty()) {
                    volumeProfile.calculateValueArea();

                    double vah = volumeProfile.getValueAreaHigh();
                    double val = volumeProfile.getValueAreaLow();
                    double breadth = volumeProfile.getValueAreaBreadth();
                    double pivot = vah - (breadth / 2);
                    double vah_1 = vah + breadth;
                    double val_1 = val - breadth;

                    series.setDouble(nextIndex, Values.VAH, vah);
                    series.setDouble(nextIndex, Values.VAL,  val);
                    series.setDouble(nextIndex, Values.VAH_1, vah_1);
                    series.setDouble(nextIndex, Values.VAL_1, val_1);
                    series.setDouble(nextIndex, Values.VA_PIVOT, pivot);

                    notifyRedraw();
                    series.setComplete(nextIndex);

                }
                nextIndex++;
            }

            Instrument instrument = series.getInstrument();
            if (rth && !instrument.isInsideTradingHours(tick.getTime(), rth)) {
                if  (calculating) {
                    calculating = false;
                }
                return;
            }

            // reset if after end of timeframe (daily, weekly, etc)
            if (tick.getTime() > nextEnd) {
                nextEnd = getEndForTimeframe(getSettings().getString("Timeframe"), tick.getTime());
                volumeProfile.clear();
            }
            calculating = true;

            float price = tick.isAskTick() ? tick.getAskPrice() : tick.getBidPrice();
            volumeProfile.addVolumeAtPrice(price, tick.getVolume());
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
