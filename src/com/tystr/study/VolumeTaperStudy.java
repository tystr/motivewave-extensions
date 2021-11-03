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
    private VolumeTaperCalculator calculator;

    @Override
    public void initialize(Defaults defaults) {
        SettingsDescriptor sd = createSD();
        SettingTab tab = sd.addTab("General");
        SettingGroup group = tab.addGroup("Inputs");
        group.addRow(new IntegerDescriptor("UpNumPrices", "Up Bar Number Of Prices", 3, 1, 9999, 1));
        group.addRow(new IntegerDescriptor("DownNumPrices", "Down Bar Number Of Prices", 3, 1, 9999, 1));

        sd.addQuickSettings("UpNumPrices","DownNumPrices");
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

        public void calculateTaper() {
            if (isBarCloseUp(index, series)) {
                if (!evaluateHigh()) return;
                if (!evaluateUpTaper()) return;

                Marker arrow = new Marker(new Coordinate(series.getStartTime(index), series.getHigh(index) + 2), Enums.MarkerType.CIRCLE);
                arrow.setSize(Enums.Size.LARGE);
                arrow.setFillColor(getDataContext().getDefaults().getRed());
                arrow.setOutlineColor(getDataContext().getDefaults().getRed());
                addFigure(Plot.PRICE, arrow);
                notifyRedraw();
            } else {
                if (!evaluateLow()) return;
                if (!evaluateDownTaper()) return;

                Marker arrow = new Marker(new Coordinate(series.getStartTime(index), series.getLow(index) - 2), Enums.MarkerType.CIRCLE);
                arrow.setSize(Enums.Size.LARGE);
                arrow.setFillColor(getDataContext().getDefaults().getGreen());
                arrow.setOutlineColor(getDataContext().getDefaults().getGreen());
                addFigure(Plot.PRICE, arrow);
                notifyRedraw();
            }
        }

        private boolean evaluateHigh() {
            float high = series.getHigh(index);
            int threshold = 0;

            boolean isTrue = bidByPrice.getOrDefault(high, 0) <= threshold;
            if (isTrue) {
                System.err.println("Bar High " + high + " has bid of " + bidByPrice.getOrDefault(high, 0));
            }

            return isTrue;
        }

        private boolean evaluateLow() {
            float low = series.getLow(index);
            int threshold = 0;

            boolean isTrue = askByPrice.getOrDefault(low, 0) <= threshold;
            if (isTrue) {
                System.err.println("Bar Low " + low + " has ask of " + askByPrice.getOrDefault(low, 0));
            }

            return isTrue;
        }

        private boolean evaluateUpTaper() {
            float increment = (float)series.getInstrument().getTickSize();
            float high = series.getHigh(index);
            int numberOfPrices = getSettings().getInteger("UpNumPrices");
            int lastBid = bidByPrice.getOrDefault(high, 0);

            float lastPrice = high;
            for (int i = 1; i < numberOfPrices; i++) {
                lastPrice = lastPrice - increment;
                int bid = bidByPrice.getOrDefault(lastPrice, 0);
                if (!(bid > lastBid)) {
                    // @todo make this more intelligent than just greater than check
                    return false;
                }

                lastBid = bid;
            }
            return true;
        }

        private boolean evaluateDownTaper() {
            float increment = (float)series.getInstrument().getTickSize();
            float high = series.getHigh(index);
            int numberOfPrices = getSettings().getInteger("DownNumPrices");
            int lastAsk = askByPrice.getOrDefault(high, 0);

            float lastPrice = high;
            for (int i = 1; i < numberOfPrices; i++) {
                lastPrice = lastPrice - increment;
                int ask = askByPrice.getOrDefault(lastPrice, 0);
                if (!(ask > lastAsk)) {
                    // @todo make this more intelligent than just greater than check
                    return false;
                }

                lastAsk = ask;
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
        Instrument instrument = series.getInstrument();

        int maxDays = 10; // @todo configure this
        int startIndex = 1;
        long threshold = instrument.getStartOfDay(series.getStartTime(), ctx.isRTH()) - ((maxDays + 1) * Util.MILLIS_IN_DAY);
        for (int i = series.size() - 1; i > 0; i--) {
            startIndex = i;
            if (series.getStartTime(i) < threshold) break;
        }
        calculator = new VolumeTaperCalculator(startIndex, series);
        isCalculating = true;
        instrument.forEachTick(series.getStartTime(startIndex), ctx.getCurrentTime() + Util.MILLIS_IN_MINUTE, ctx.isRTH(), calculator);
        isCalculating = false;
    }

    @Override
    public void onTick(DataContext ctx, Tick tick) {
        if (isCalculating || calculator == null) return;
        calculator.onTick(tick);
    }
}
