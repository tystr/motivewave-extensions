package com.tystr;

import com.motivewave.platform.sdk.common.*;
import com.motivewave.platform.sdk.draw.Marker;
import com.motivewave.platform.sdk.study.Plot;
import com.motivewave.platform.sdk.study.Study;
import com.motivewave.platform.sdk.study.StudyHeader;
import com.motivewave.platform.sdk.common.Coordinate;
import com.motivewave.platform.sdk.common.DataContext;
import com.motivewave.platform.sdk.common.Defaults;
import com.motivewave.platform.sdk.common.Enums;
import com.motivewave.platform.sdk.common.Inputs;
import com.motivewave.platform.sdk.common.desc.InputDescriptor;
import com.motivewave.platform.sdk.common.desc.IntegerDescriptor;
import com.motivewave.platform.sdk.common.desc.MAMethodDescriptor;
import com.motivewave.platform.sdk.common.desc.MarkerDescriptor;
import com.motivewave.platform.sdk.common.desc.PathDescriptor;


import java.awt.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Moving Average Cross. This study consists of two moving averages:
 Fast MA (shorter period), Slow MA. Signals are generated when the
 Fast MA moves above or below the Slow MA. Markers are also displayed
 where these crosses occur. */
@StudyHeader(
        namespace="com.tystr",
        id="ORDERFLOW",
        name="Orderflow Study",
        label="Orderflow Study Label",
        desc="Powered by Orderflop",
        //menu="Examples",
        overlay=true,
        signals=true)

public class Orderflow extends Study {
    enum Values { FAST_MA, SLOW_MA };
    enum Signals { CROSS_ABOVE, CROSS_BELOW };

    @Override
    public void initialize(Defaults defaults)
    {
        // User Settings
        var sd = createSD();
        var tab = sd.addTab("General");

        // Fast MA (shorter period)
        var grp = tab.addGroup("Fast MA");
        grp.addRow(new InputDescriptor(Inputs.INPUT, "Fast Input", Enums.BarInput.CLOSE));
        grp.addRow(new MAMethodDescriptor(Inputs.METHOD, "Fast Method", Enums.MAMethod.EMA));
        grp.addRow(new IntegerDescriptor(Inputs.PERIOD, "Fast Period", 10, 1, 9999, 1));

        // Slow MA (shorter period)
        grp = tab.addGroup("Slow MA");
        grp.addRow(new InputDescriptor(Inputs.INPUT2, "Slow Input", Enums.BarInput.CLOSE));
        grp.addRow(new MAMethodDescriptor(Inputs.METHOD2, "Slow Method", Enums.MAMethod.EMA));
        grp.addRow(new IntegerDescriptor(Inputs.PERIOD2, "Slow Period", 20, 1, 9999, 1));

        tab = sd.addTab("Display");

        grp = tab.addGroup("Lines");
        grp.addRow(new PathDescriptor(Inputs.PATH, "Fast MA", defaults.getGreenLine(), 1.0f, null, true, false, false));
        grp.addRow(new PathDescriptor(Inputs.PATH2, "Slow MA", defaults.getBlueLine(), 1.0f, null, true, false, false));

        grp = tab.addGroup("Markers");
        grp.addRow(new MarkerDescriptor(Inputs.UP_MARKER, "Up Marker", Enums.MarkerType.TRIANGLE, Enums.Size.SMALL, defaults.getGreen(), defaults.getLineColor(), true, true));
        grp.addRow(new MarkerDescriptor(Inputs.DOWN_MARKER, "Down Marker", Enums.MarkerType.TRIANGLE, Enums.Size.SMALL, defaults.getRed(), defaults.getLineColor(), true, true));
    }

    @Override
    protected void calculate(int index, DataContext ctx) {



        DataSeries series = ctx.getDataSeries();

        List<SwingPoint> swingPoints = series.calcSwingPoints(8);
        for (SwingPoint swingPoint : swingPoints) {
            debug(swingPoint.toString());

            float arrowLocation = 0;
            if (swingPoint.isTop()) {
                arrowLocation = series.getHigh(swingPoint.getIndex()) + 1;
            } else {
                arrowLocation = series.getLow(swingPoint.getIndex()) - 1;
            }
            debug("arrowLoaction: " + arrowLocation);
            Marker arrow = new Marker(new Coordinate(series.getStartTime(swingPoint.getIndex()), arrowLocation), Enums.MarkerType.CIRCLE);
            arrow.setSize(Enums.Size.LARGE);
            addFigure( arrow);
        }
        if (true) return;




        Bar bar = getDeltaForTicksAsDeltaBar(
                series.getInstrument().getTicks(
                        series.getStartTime(index), series.getEndTime(index)
                )
        );
        series.setValue(index, "Bar", bar);

        Object bids = series.getValue(index, "BidsByPrice");
        Bar barValue = (Bar) series.getValue(index, "Bar");
        Map<Float, Long> bidValue = barValue.getBidByPrice();
        Map<Float, Long> askValue = barValue.getAskByPrice();

        float barLow = series.getLow(index);
        // Detect completed auction
        long bidAtLow = bidValue.getOrDefault(barLow, -1L); //why is this null @todo
        long askAtLow = askValue.getOrDefault(barLow, 0L);

        debug("Bid at Low: " + bidAtLow);
        debug("Ask at Low: " + askAtLow);

        if (bidAtLow > 0 && askAtLow == 0) {
            // See if next price is a buy imbalance
            float nextPrice = barLow + (float) series.getInstrument().getTickSize();
            debug("barLow is " + barLow);
            debug("Using next price " + nextPrice);
            long bidAtNextPrice = bidValue.getOrDefault(nextPrice, 0L);
            long askAtNextPrice = askValue.getOrDefault(nextPrice, 0L);
            debug("Bid at next price: " + bidAtNextPrice);
            debug("Ask at next priuce: " + askAtNextPrice);
            if (askAtNextPrice > (bidAtLow * 4)) {
                debug("Ask " + askAtNextPrice + " is grater than " + (bidAtLow*4));
                Marker arrow = new Marker(new Coordinate(series.getStartTime(index), series.getClose(index) - 2), Enums.MarkerType.ARROW);
                arrow.setTextValue(series.getPositiveDM(index) + " / " + series.getNegativeDM(index));
                arrow.setSize(Enums.Size.MEDIUM);

                arrow.setFillColor(Color.ORANGE);
                addFigure(arrow);
            }

        }
    }

    private class Bar {
        private final Map<Float, Integer> deltasByPrice;
        private final int delta;
        private final Map<Float, Long> bidByPrice;
        private final Map<Float, Long> askByPrice;

        public Bar(Map<Float, Integer> deltasByPrice, Map<Float, Long> bidByPrice, Map<Float, Long> askByPrice) {
            this.deltasByPrice = deltasByPrice;
            this.bidByPrice = bidByPrice;
            this.askByPrice = askByPrice;
            this.delta = calcDelta();
        }

        public Map<Float, Integer> getDeltasByPrice() {
            return deltasByPrice;
        }

        public Map<Float, Long> getBidByPrice() {
            return bidByPrice;
        }

        public Map<Float, Long> getAskByPrice() {
            return askByPrice;
        }

        public int getDelta() {
            return delta;
        }

        public float getDeltaPOC() {
            return Collections.max(deltasByPrice.entrySet(), Map.Entry.comparingByValue()).getKey();
        }

        private int calcDelta() {
            return deltasByPrice.values().stream().mapToInt(Integer::valueOf).sum();

        }
    }


    /**
     * Calculate bid/ask volume and delta for the given List of ticks
     *
     * @param ticks List of ticks
     * @return Bar
     */
    protected Bar getDeltaForTicksAsDeltaBar(List<Tick> ticks) {
        Map<Float, Integer> deltasByPrice = new HashMap<Float, Integer>();
        Map<Float, Long> bidsByPrice = new HashMap<Float, Long>();
        Map<Float, Long> asksByPrice = new HashMap<Float, Long>();
        for (Tick tick : ticks) {
            if (tick.isAskTick()) {
                int deltaAtPrice = deltasByPrice.getOrDefault(tick.getAskPrice(), 0);
                long askAtPrice = asksByPrice.getOrDefault(tick.getAskPrice(), 0L);
                deltaAtPrice += tick.getVolume();
                askAtPrice += tick.getVolume();
                deltasByPrice.put(tick.getAskPrice(), deltaAtPrice);
                asksByPrice.put(tick.getAskPrice(), askAtPrice);
            } else {
                int deltaAtPrice = deltasByPrice.getOrDefault(tick.getBidPrice(), 0);
                long bidAtPrice = bidsByPrice.getOrDefault(tick.getBidPrice(), 0L);
                deltaAtPrice -= tick.getVolume();
                bidAtPrice += tick.getVolume();
                deltasByPrice.put(tick.getBidPrice(), deltaAtPrice);
                bidsByPrice.put(tick.getBidPrice(), bidAtPrice);
            }
        }

        return new Bar(deltasByPrice, bidsByPrice, asksByPrice);
    }



//        int fastPeriod=getSettings().getInteger(Inputs.PERIOD);
//        int slowPeriod=getSettings().getInteger(Inputs.PERIOD2);
//        if (index < Math.max(fastPeriod, slowPeriod)) return; // not enough data
//
//        var series=ctx.getDataSeries();
//
//        // Calculate and store the fast and slow MAs
//        Double fastMA=series.ma(getSettings().getMAMethod(Inputs.METHOD), index, fastPeriod, getSettings().getInput(Inputs.INPUT));
//        Double slowMA=series.ma(getSettings().getMAMethod(Inputs.METHOD2), index, slowPeriod, getSettings().getInput(Inputs.INPUT2));
//        if (fastMA == null || slowMA == null) return;
//
//        series.setDouble(index, study_examples.SampleMACross.Values.FAST_MA, fastMA);
//        series.setDouble(index, study_examples.SampleMACross.Values.SLOW_MA, slowMA);
//
//        if (!series.isBarComplete(index)) return;
//
//        // Check to see if a cross occurred and raise signal.
//        var c=new Coordinate(series.getStartTime(index), slowMA);
//        if (crossedAbove(series, index, study_examples.SampleMACross.Values.FAST_MA, study_examples.SampleMACross.Values.SLOW_MA)) {
//            var marker=getSettings().getMarker(Inputs.UP_MARKER);
//            if (marker.isEnabled()) addFigure(new Marker(c, Enums.Position.BOTTOM, marker));
//            ctx.signal(index, study_examples.SampleMACross.Signals.CROSS_ABOVE, "Fast MA Crossed Above!", series.getClose(index));
//        }
//        else if (crossedBelow(series, index, study_examples.SampleMACross.Values.FAST_MA, study_examples.SampleMACross.Values.SLOW_MA)) {
//            var marker=getSettings().getMarker(Inputs.DOWN_MARKER);
//            if (marker.isEnabled()) addFigure(new Marker(c, Enums.Position.TOP, marker));
//            ctx.signal(index, study_examples.SampleMACross.Signals.CROSS_BELOW, "Fast MA Crossed Below!", series.getClose(index));
//        }
//
//        series.setComplete(index);
//    }
}
