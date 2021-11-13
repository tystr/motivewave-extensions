package com.tystr.study;

import com.motivewave.platform.sdk.common.*;
import com.motivewave.platform.sdk.common.desc.*;
import com.motivewave.platform.sdk.draw.Marker;
import com.motivewave.platform.sdk.study.Plot;
import com.motivewave.platform.sdk.study.Study;
import com.motivewave.platform.sdk.study.StudyHeader;
import com.motivewave.platform.sdk.common.Coordinate;
import com.motivewave.platform.sdk.common.DataContext;
import com.motivewave.platform.sdk.common.Defaults;
import com.motivewave.platform.sdk.common.Enums;

import javax.swing.text.html.Option;
import java.util.*;

@StudyHeader(
        namespace="com.tystr.study",
        id="TYSTR_VOLUME_TAPER",
        name="Volume Taper Study",
//        label="Volume Taper Study Label",
        desc="This study indicates taper in volume at bar high/lows",
        overlay=true,
        signals=true,
        requiresVolume = true
)

public class VolumeTaperStudy extends Study {
    private boolean isCalculating = false;
    private boolean calculated = false;
    private VolumeTaperCalculator calculator;
    enum Values {VOLUME_TAPER}

    private final String BULLISH_BAR_MIN_PRICES = "UpNumPrices";
    private final String BEARISH_BAR_MIN_PRICES = "DownNumPrices";
    private final String MIN_BULLISH_BAR_SIZE = "MinBullishBarSize";
    private final String MIN_BEARISH_BAR_SIZE = "MinBearishBarSize";
    private final String SHOW_DELTA_TRIGGERS = "showDeltaTriggers";
    private final String BEARISH_DELTA_PERCENT_THRESHOLD = "bearishDeltaPercentThreshold";
    private final String BULLISH_DELTA_PERCENT_THRESHOLD = "bullishDeltaPercentThreshold";
    private final String OFFSET_ABOVE_IN_TICKS = "offsetAboveInTicks";
    private final String OFFSET_BELOW_IN_TICKS = "offsetBelowInTicks";
    private final String TICK_INTERVAL = "tickInterval";

    @Override
    public void initialize(Defaults defaults) {
        SettingsDescriptor sd = createSD();
        SettingTab tab = sd.addTab("General");
        SettingGroup group;

        group = tab.addGroup("Inputs");
        group.addRow(new IntegerDescriptor("UpNumPrices", "Up Bar Number Of Prices", 3, 1, 9999, 1));
        group.addRow(new IntegerDescriptor("DownNumPrices", "Down Bar Number Of Prices", 3, 1, 9999, 1));
        group.addRow(new IntegerDescriptor("MinBarSize", "Minimum Size of Bar (ticks)", 5, 1, 9999, 1));

        group.addRow(new IntegerDescriptor(TICK_INTERVAL, "Tick Interval", 1, 1, 9999, 1));

        group.addRow(new BooleanDescriptor(SHOW_DELTA_TRIGGERS, "Show Delta Triggers", false, false));
        group.addRow(new DoubleDescriptor(BEARISH_DELTA_PERCENT_THRESHOLD, "Bearish Delta % Threshold", -0.10, -1, 1, 0.01));
        group.addRow(new DoubleDescriptor(BULLISH_DELTA_PERCENT_THRESHOLD, "Bearish Delta % Threshold", 0.10, -1, 1, 0.01));

        sd.addDependency(new EnabledDependency(SHOW_DELTA_TRIGGERS, BEARISH_DELTA_PERCENT_THRESHOLD, BULLISH_DELTA_PERCENT_THRESHOLD));

        group = tab.addGroup("Display");
        group.addRow(new IntegerDescriptor(OFFSET_ABOVE_IN_TICKS, "Offset Above (ticks)", 2, 1, 9999, 1));
        group.addRow(new IntegerDescriptor(OFFSET_BELOW_IN_TICKS, "Offset Below (ticks)", 2, 1, 9999, 1));

        sd.addQuickSettings("UpNumPrices","DownNumPrices", "MinBarSize", BEARISH_DELTA_PERCENT_THRESHOLD, TICK_INTERVAL, OFFSET_ABOVE_IN_TICKS, OFFSET_BELOW_IN_TICKS);
    }


    class VolumeTaperCalculator implements TickOperation {
        private final Map<Float, Integer> deltaByPrice = new TreeMap<>();
        private final Map<Float, Integer> bidByPrice = new TreeMap<>();
        private final Map<Float, Integer> askByPrice = new TreeMap<>();

        private final DataSeries series;
        private int index;

        public VolumeTaperCalculator(int startIndex, DataSeries series) {
            this.index = startIndex;
            this.series = series;
        }

        public void onTick(Tick tick) {
            if (tick.getTime() > series.getEndTime(index)) {
                calculateTaper();
                series.setComplete(index);
                calculateFollowThrough();
                reset();
                index++;
            }
            if (tick.isAskTick()) {
                float price = tick.getAskPrice();

                int delta = deltaByPrice.getOrDefault(price, 0);
                delta += tick.getVolume();
                deltaByPrice.put(price, delta);

                int ask = askByPrice.getOrDefault(price, 0);
                ask += tick.getVolume();
                askByPrice.put(price, ask);
            } else {
                float price = tick.getBidPrice();
                int delta = deltaByPrice.getOrDefault(price, 0);
                delta -= tick.getVolume();
                deltaByPrice.put(tick.getBidPrice(), delta);

                int bid = bidByPrice.getOrDefault(price, 0);
                bid += tick.getVolume();
                bidByPrice.put(price, bid);
            }
        }

        public void calculateFollowThrough() {
            if (!getSettings().getBoolean(SHOW_DELTA_TRIGGERS)) return;
            int numBarsToEvaluate = 1;
            if (index - numBarsToEvaluate < 0) return; // not enough bars

            if (null != series.getBoolean(index - 1, Values.VOLUME_TAPER) && series.getBoolean(index - 1, Values.VOLUME_TAPER)) {
                float delta = deltaByPrice.values().stream().mapToInt(Integer::valueOf).sum();
                float deltaPercent = delta / series.getVolume(index);
                if (isBarCloseUp(index-1, series)) {

                    if (!(deltaPercent < getSettings().getDouble(BEARISH_DELTA_PERCENT_THRESHOLD))) return;
                    Marker marker = new Marker(new Coordinate(series.getStartTime(index), series.getHigh(index) + 2), Enums.MarkerType.SQUARE);
                    marker.setSize(Enums.Size.LARGE);
                    marker.setFillColor(getDataContext().getDefaults().getRed());
                    marker.setOutlineColor(getDataContext().getDefaults().getRed());
//                    marker.setTextValue("Delta %: " + deltaPercent);
                    addFigure(Plot.PRICE, marker);
                } else {
                    if (!(deltaPercent > getSettings().getDouble(BULLISH_DELTA_PERCENT_THRESHOLD))) return;
                    Marker marker = new Marker(new Coordinate(series.getStartTime(index), series.getLow(index) - 2 ), Enums.MarkerType.SQUARE);
                    marker.setSize(Enums.Size.LARGE);
                    marker.setFillColor(getDataContext().getDefaults().getGreen());
                    marker.setOutlineColor(getDataContext().getDefaults().getGreen());
//                    marker.setTextValue("Delta %: " + deltaPercent);
                    addFigure(Plot.PRICE, marker);
                }
            }
        }

        public void calculateTaper() {
            series.setBoolean(index, Values.VOLUME_TAPER, false);
            if (!evaluateBarSize()) return;

            if (isBarCloseUp(index, series)) {
                if (!evaluateHigh()) return;
                if (!evaluateUpTaper()) return;

                Marker arrow = new Marker(new Coordinate(series.getStartTime(index), series.getHigh(index) + getSettings().getInteger(OFFSET_ABOVE_IN_TICKS)), Enums.MarkerType.CIRCLE);
                arrow.setSize(Enums.Size.LARGE);
                arrow.setFillColor(getDataContext().getDefaults().getRed());
                arrow.setOutlineColor(getDataContext().getDefaults().getRed());
                addFigure(Plot.PRICE, arrow);
                series.setBoolean(index, Values.VOLUME_TAPER, true);
            } else {
                if (!evaluateLow()) return;
                if (!evaluateDownTaper()) return;

                Marker arrow = new Marker(new Coordinate(series.getStartTime(index), series.getLow(index) - getSettings().getInteger(OFFSET_BELOW_IN_TICKS)), Enums.MarkerType.CIRCLE);
                arrow.setSize(Enums.Size.LARGE);
                arrow.setFillColor(getDataContext().getDefaults().getGreen());
                arrow.setOutlineColor(getDataContext().getDefaults().getGreen());
                addFigure(Plot.PRICE, arrow);
                series.setBoolean(index, Values.VOLUME_TAPER, true);
            }
        }

        private boolean evaluateBarSize() {
            int minBarSize = getSettings().getInteger("MinBarSize");
            double barBreadthInTicks = (series.getHigh(index) - series.getLow(index)) / series.getInstrument().getTickSize();
            return barBreadthInTicks >= minBarSize;
        }

        private boolean evaluateHigh() {
            float high = series.getHigh(index);
            int threshold = 0;

            return bidByPrice.getOrDefault(high, 0) <= threshold;
        }

        private boolean evaluateLow() {
            float low = series.getLow(index);
            int threshold = 0;

            return askByPrice.getOrDefault(low, 0) <= threshold;
        }

        private boolean evaluateUpTaper() {
            float increment = (float)series.getInstrument().getTickSize();
            float high = series.getHigh(index);
            int numberOfPrices = getSettings().getInteger("UpNumPrices");
            int tickInterval = getSettings().getInteger(TICK_INTERVAL);

            int lastBid = bidByPrice.getOrDefault(high, 0);
            if (lastBid > 0) return false;
            int lastAsk = askByPrice.getOrDefault(high, 0);

            float lastPrice = high;
            for (int i = 0; i < numberOfPrices; i++) {
                lastPrice = lastPrice - increment;
                int ask = askByPrice.getOrDefault(lastPrice, 0);
                for (int j = 0; j < tickInterval-1; j++) {
                    ask = ask + askByPrice.getOrDefault(lastPrice - increment, 0);
                }
                if (!(ask > lastAsk)) {
                    // @todo make this more intelligent than just greater than check
                    return false;
                }

                lastAsk = ask;
            }
            return true;
        }

        private boolean evaluateDownTaper() {
            float increment = (float)series.getInstrument().getTickSize();
            float low = series.getLow(index);
            int numberOfPrices = getSettings().getInteger("DownNumPrices");
            int tickInterval = getSettings().getInteger(TICK_INTERVAL);

            int lastAsk = askByPrice.getOrDefault(low, 0);
            if (lastAsk > 0) return false;
            int lastBid = bidByPrice.getOrDefault(low, 0);

            float lastPrice = low;
            for (int i = 0; i < numberOfPrices; i++) {
                lastPrice = lastPrice + increment;
                int bid = bidByPrice.getOrDefault(lastPrice, 0);
                for (int j = 0; j < tickInterval-1; j++) {
                    bid = bid + bidByPrice.getOrDefault(lastPrice + increment, 0);
                }
                if (!(bid > lastBid)) {
                    // @todo make this more intelligent than just greater than check
                    return false;
                }

                lastBid = bid;
            }
            return true;
        }

        public void reset() {
            deltaByPrice.clear();
            bidByPrice.clear();
            askByPrice.clear();
        }

        private boolean isBarCloseUp(int index, DataSeries series) {
            return series.getOpen(index) < series.getClose(index);
        }
    }

    @Override
    protected void calculateValues(DataContext ctx) {
        DataSeries series = ctx.getDataSeries();
        if (series.size() == 0 || isCalculating) return;
        Instrument instrument = series.getInstrument();

        int startIndex = 1;
        calculator = new VolumeTaperCalculator(startIndex, series);
        isCalculating = true;
        instrument.forEachTick(series.getStartTime(startIndex), ctx.getCurrentTime() + Util.MILLIS_IN_MINUTE*5, ctx.isRTH(), calculator);
        isCalculating = false;
        calculated = true;
        notifyRedraw();
    }

    @Override
    public void onTick(DataContext ctx, Tick tick) {
        if (isCalculating || calculator == null) return;
        calculator.onTick(tick);
    }
}
