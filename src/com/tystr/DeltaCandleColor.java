package com.tystr;

import com.motivewave.platform.sdk.common.*;
import com.motivewave.platform.sdk.common.desc.*;
import com.motivewave.platform.sdk.study.RuntimeDescriptor;
import com.motivewave.platform.sdk.study.Study;
import com.motivewave.platform.sdk.study.StudyHeader;
import com.tystr.delta.DeltaBar;

@StudyHeader(
        namespace="com.tystr",
        id="DELTA_CANDLE_COLOR",
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
    enum Values { DELTA, DELTA_PERCENT};

    @Override
    public void initialize(Defaults defaults)
    {
        var sd = createSD();
        var tab = sd.addTab("General");

        SettingGroup thresholdGroup = tab.addGroup("Thresholds");
        thresholdGroup.addRow(new IntegerDescriptor("PositiveDeltaThreshold", "Positive Threshold %", 20, 0, 100, 1));
        thresholdGroup.addRow(new IntegerDescriptor("NegativeDeltaThreshold", "Negative Threshold %", -20, -100, 0, 1));
        thresholdGroup.addRow(
                new IntegerDescriptor("NeutralDeltaLowThreshold", "Neutral Low Threshold %", -5, -100, 0, 1),
                new IntegerDescriptor("NeutralDeltaHighThreshold", "Neutral High Threshold %", 5, 0, 100, 1)
        );

        SettingGroup colorGroup = tab.addGroup("Colors");
        colorGroup.addRow(new ColorDescriptor("PositiveDeltaColor", "Positive Delta Candle Color", defaults.getGreen().darker()));
        colorGroup.addRow(new ColorDescriptor("NegativeDeltaColor", "Negative Delta Candle Color", defaults.getRed()));
        colorGroup.addRow(new ColorDescriptor("NeutralDeltaColor", "Neutral Delta Candle Color", defaults.getOrange()));

        sd.addQuickSettings(
                "PositiveDeltaThreshold",
                "NegativeDeltaThreshold",
                "NeutralDeltaLowThreshold",
                "NeutralDeltaHighThreshold",
                "PositiveDeltaColor",
                "NegativeDeltaColor",
                "NeutralDeltaColor"
        );

        RuntimeDescriptor rd = getRuntimeDescriptor();
        rd.exportValue(new ValueDescriptor(Values.DELTA, "Delta", null));
        rd.exportValue(new ValueDescriptor(Values.DELTA_PERCENT, "Delta %", null));
    }

    @Override
    protected void calculateValues(DataContext ctx) {
        DataSeries series = ctx.getDataSeries();
        Instrument instrument = series.getInstrument();

        int startIndex = 1;
        TickOperation calculator = new DeltaCalculator(startIndex, series);
        instrument.forEachTick(series.getStartTime(startIndex), ctx.getCurrentTime() + Util.MILLIS_IN_MINUTE, ctx.isRTH(), calculator);
    }

    private float getPositiveDeltaThreshold() {
        return (float) getSettings().getInteger("PositiveDeltaThreshold") / 100;
    }
    private float getNegativeDeltaThreshold() {
        return (float) getSettings().getInteger("NegativeDeltaThreshold") / 100;
    }
    private float getNeutralDeltaLowThreshold() {
        return (float) getSettings().getInteger("NeutralDeltaLowThreshold") / 100;
    }
    private float getNeutralDeltaHighThreshold() {
        return (float) getSettings().getInteger("NeutralDeltaHighThreshold") / 100;
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

                float deltaPercent = deltaBar.getDeltaPercent();
                if (deltaPercent > getPositiveDeltaThreshold()) {
                    series.setPriceBarColor(nextIndex, getSettings().getColor("PositiveDeltaColor"));
                } else if (deltaPercent < getNegativeDeltaThreshold()) {
                    series.setPriceBarColor(nextIndex, getSettings().getColor("NegativeDeltaColor"));
                } else if (deltaPercent > getNeutralDeltaLowThreshold() && deltaPercent < getNeutralDeltaHighThreshold()) {
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
            series.setFloat(nextIndex, Values.DELTA_PERCENT, deltaBar.getDeltaPercent());
        }
    }
}
