package com.tystr;

import com.motivewave.platform.sdk.common.*;
import com.motivewave.platform.sdk.common.desc.*;
import com.motivewave.platform.sdk.study.Study;
import com.motivewave.platform.sdk.study.StudyHeader;
import com.tystr.delta.DeltaBar;

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
        requiresVolume = true,
        requiresBarUpdates=true
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
        grp.addRow(new IntegerDescriptor("PositiveDeltaThreshold", "Positive Threshold %", 20, 0, 100, 1));
        grp.addRow(new ColorDescriptor("PositiveDeltaColor", "Positive Delta Candle Color", defaults.getGreen()));
        grp.addRow(new IntegerDescriptor("NegativeDeltaThreshold", "Negative Threshold %", -20, -100, 0, 1));
        grp.addRow(new ColorDescriptor("NegativeDeltaColor", "Negative Delta Candle Color", defaults.getRed()));
        grp.addRow(new ColorDescriptor("NeutralDeltaColor", "Neutral Delta Candle Color", defaults.getOrange()));
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
        TickOperation calculator = new DeltaCalculator(startIndex, series);
        instrument.forEachTick(series.getStartTime(startIndex), ctx.getCurrentTime() + Util.MILLIS_IN_MINUTE, ctx.isRTH(), calculator);
    }

    private float getPositiveDeltaThreshold() {
        return (float) getSettings().getInteger("PositiveDeltaThreshold") / 100;
    }
    private float getNegativeDeltaThreshold() {
        return (float) getSettings().getInteger("NegativeDeltaThreshold") / 100;
    }

    class DeltaCalculator implements TickOperation {
        private final DataSeries series;
        private int nextIndex;
        private DeltaBar deltaBar;

        private long nextEnd;
        public DeltaCalculator(int startIndex, DataSeries series) {
            this.series = series;
            this.nextIndex = startIndex;
            this.nextEnd = series.getEndTime(startIndex);
            this.deltaBar = new DeltaBar();
        }

        public void onTick(Tick tick) {
            if (series.isComplete(nextIndex)) return; // nothing to do if this index is complete

            if (tick.getTime() > series.getEndTime(nextIndex)) { // Bar is complete, set color and reset delta
                if (deltaBar.isEmpty()) return;
                debug("delta percent (" + deltaBar.getVolume() + " / " + deltaBar.getDelta() + "): " + deltaBar.getDeltaPercent());
                if (deltaBar.getDeltaPercent() > getPositiveDeltaThreshold()) {
                    series.setPriceBarColor(nextIndex, getSettings().getColor("PositiveDeltaColor"));
                } else if (deltaBar.getDeltaPercent() < getNegativeDeltaThreshold()) {
                    series.setPriceBarColor(nextIndex, getSettings().getColor("NegativeDeltaColor"));
                } else {
                    series.setPriceBarColor(nextIndex, getSettings().getColor("NeutralDeltaColor"));
                }
                notifyRedraw();

                series.setValue(nextIndex, "DeltaBar", deltaBar);
                series.setComplete(nextIndex);

                // reset for next bar
                deltaBar = new DeltaBar();
                nextIndex++;
                nextEnd = series.getEndTime(nextIndex);
            }

            if (tick.isAskTick()) {
                deltaBar.addVolumeAtAsk(tick.getAskPrice(), tick.getVolume());
            } else {
                deltaBar.addVolumeAtBid(tick.getBidPrice(), tick.getVolume());
            }
            series.setInt(nextIndex, Values.DELTA, deltaBar.getDelta());
        }
    }
}
