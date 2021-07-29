package com.tystr;

import com.motivewave.platform.sdk.common.*;
import com.motivewave.platform.sdk.common.desc.*;
import com.motivewave.platform.sdk.study.Study;
import com.motivewave.platform.sdk.study.StudyHeader;

@StudyHeader(
        namespace="com.tystr",
        id="DELTA_CANDLE_COLOR",
//        rb="study_examples.nls.strings", // locale specific strings are loaded from here
        name="Delta Candle Color",
        label="Delta Candle Color",
        desc="This study colors candles based on configured delta thresholds.",
        menu="Tystr",
        overlay=true,
        studyOverlay=true,
        requiresVolume = true
)
public class DeltaCandleColor extends Study
{
    enum Values { DELTA };

    @Override
    public void initialize(Defaults defaults)
    {
        var sd = createSD();
        var tab = sd.addTab("General");

        var grp = tab.addGroup("Display");
        grp.addRow(new IntegerDescriptor("PositiveDeltaThreshold", "Positive Threshold", 100, 0, Integer.MAX_VALUE, 1));
        grp.addRow(new ColorDescriptor("PositiveDeltaColor", "Positive Delta Candle Color", defaults.getGreen()));
        grp.addRow(new IntegerDescriptor("NegativeDeltaThreshold", "Negative Threshold", -100, Integer.MIN_VALUE, 0, 1));
        grp.addRow(new ColorDescriptor("NegativeDeltaColor", "Negative Delta Candle Color", defaults.getRed()));
        grp.addRow(new ColorDescriptor("NeutralDeltaColor", "Neutral Delta Candle Color", defaults.getYellow()));
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
        TickOperation calculator = new DeltaCalculator(startIndex, series, ctx.getDefaults());
        instrument.forEachTick(series.getStartTime(startIndex), ctx.getCurrentTime() + Util.MILLIS_IN_MINUTE, ctx.isRTH(), calculator);
    }

    class DeltaCalculator implements TickOperation {
        private final DataSeries series;
        private int nextIndex;
        private final boolean rth = true;
        private int totalDelta = 0;
        private boolean calculating = false;
        private final Defaults defaults;

        private long nextEnd;
        public DeltaCalculator(int startIndex, DataSeries series, Defaults defaults) {
            this.series = series;
            this.nextIndex = startIndex;
            nextEnd = series.getEndTime(startIndex);
            this.defaults = defaults;
        }

        public void onTick(Tick tick) {
            if (series.isComplete(nextIndex)) return; // nothing to do if this index is complete

            if (tick.getTime() > series.getEndTime(nextIndex)) { // Bar is complete, set color and reset delta
                if (series.getInt(nextIndex, Values.DELTA) > getSettings().getInteger("PositiveDeltaThreshold")) {
                    series.setPriceBarColor(nextIndex, getSettings().getColor("PositiveDeltaColor"));
                } else if (series.getInt(nextIndex, Values.DELTA) < getSettings().getInteger("NegativeDeltaThreshold")) {
                    series.setPriceBarColor(nextIndex, getSettings().getColor("NegativeDeltaColor"));
                } else {
                    series.setPriceBarColor(nextIndex, getSettings().getColor("NeutralDeltaColor"));
                }
                notifyRedraw();
                series.setComplete(nextIndex);
                totalDelta = 0;
                nextIndex++;
                nextEnd = series.getEndTime(nextIndex);
            }

            calculating = true;

            if (tick.isAskTick()) {
                totalDelta += tick.getVolume();
            } else {
                totalDelta -= tick.getVolume();
            }
            series.setInt(nextIndex, Values.DELTA, totalDelta);
        }

        private long getEndForTimeframe(String timeframe, long time) {
            switch (timeframe) {
                case "Daily":
                    debug("using daily");
                    return series.getInstrument().getEndOfDay(time, rth);
                case "Weekly":
                    debug("using weekly");
                    return series.getInstrument().getEndOfWeek(time, rth);
                default:
                    throw new RuntimeException("Timeframe must be one of \"Daily\" or \"Weekly\", received \"" + timeframe + "\".");
            }
        }
    }
}
