package com.tystr.study;

import com.motivewave.platform.sdk.common.*;
import com.motivewave.platform.sdk.common.desc.*;
import com.motivewave.platform.sdk.draw.Line;
import com.motivewave.platform.sdk.study.Plot;
import com.motivewave.platform.sdk.study.Study;
import com.motivewave.platform.sdk.study.StudyHeader;
import com.tystr.VolumeProfile;
import com.tystr.study.overlay.PivotSet;

import java.util.*;

@StudyHeader(
        namespace="com.tystr.study",
        id="TYSTR_STUDY_VOLUME_PIVOTS",
        name="Volume Pivots",
        label="",
        desc="This study plots pivots based on prior volume profile data",
        menu="Tystr",
        overlay=true,
        studyOverlay=true,
        requiresVolume = true
)
public class VolumePivots extends Study
{
    enum Values { VAH, VAL, VAH_1, VAH_2, VAL_1, VAL_2, VA_PIVOT, TIMEFRAME, VOLUME_BY_PRICE};
    enum Intervals {DAILY, WEEKLY}

    TickOperation calculator;
    private boolean isCalculating = false;
    private boolean calculated = false;

    private final String TIMEFRAME = "Timeframe";
    private final String RTH_DATA = "rthData";
    private final String NUM_PRINTS = "numPrints";
    private final String PIVOT = "pivot";
    private final String EXTENSIONS_ABOVE = "extensionAbove";
    private final String EXTENSIONS_BELOW = "extensionBelow";

    private final List<Line> lines = new LinkedList<>();


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

        grp.addRow(new IntegerDescriptor(NUM_PRINTS, "Number of Prints", 10, 1, 9999, 1));
        grp.addRow(new DiscreteDescriptor(TIMEFRAME, "Interval", "Daily", timeframes));
        grp.addRow(new BooleanDescriptor(RTH_DATA, "RTH Data", true));

        // Pivots
        PathDescriptor pivotPathDescriptor = new PathDescriptor(PIVOT, "Pivot", defaults.getYellowLine(), 2.0f, null, true, true, true);
        PathDescriptor extensionsAboveDescriptor = new PathDescriptor(EXTENSIONS_ABOVE, "Extensions Above", defaults.getBlueLine().darker(), 1.0f, null, true, true, true);
        PathDescriptor extensionsBelowDescriptor = new PathDescriptor(EXTENSIONS_BELOW, "Extensions Below", defaults.getBlue().darker(), 1.0f, null, true, true, true);


//        PathInfo resistancePath = ctx.getSettings().getPath(Inputs.PATH);
//        PathInfo pivotPath = ctx.getSettings().getPath(Inputs.PATH2);
//        PathInfo supportPath = ctx.getSettings().getPath(Inputs.PATH3);
        grp.addRow(new PathDescriptor(Inputs.PATH, "Below Line", defaults.getBlueLine().darker(), 1.0f, null, true, true, true));
        grp.addRow(new PathDescriptor(Inputs.PATH2, "Pivot Line", defaults.getYellowLine(), 2.0f, null, true, true, true));
        grp.addRow(new PathDescriptor(Inputs.PATH3, "Above Line", defaults.getBlue().darker(), 1.0f, null, true, true, true));

        grp.addRow(pivotPathDescriptor);
        grp.addRow(extensionsAboveDescriptor);
        grp.addRow(extensionsBelowDescriptor);
        grp.addRow(new BooleanDescriptor(PivotSet.SHOW_PRICES, "Show Prices", false, false));


        grp.addRow(new DoubleDescriptor("ValueAreaPercent", "Value Area", 68.2, 0, 100, 0.10));

//        sd.addQuickSettings(PIVOT, EXTENSIONS_ABOVE, EXTENSIONS_BELOW);
        sd.addQuickSettings(Inputs.PATH,Inputs.PATH2, Inputs.PATH3);
        sd.addQuickSettings(PivotSet.SHOW_PRICES);

        clearFigures();
        lines.clear();
    }

    @Override
    public void clearState()
    {
        System.err.println("Clearing state...");
        super.clearState();
        lines.clear();
    }

    @Override
    protected void calculateValues(DataContext ctx) {
        DataSeries series = ctx.getDataSeries();
        if (series.size() == 0 || isCalculating) return;
        Instrument instrument = series.getInstrument();
        boolean isRTH = getSettings().getBoolean(RTH_DATA);

        int maxPrints = getSettings().getInteger(NUM_PRINTS);
        String interval = getSettings().getString(TIMEFRAME);
        if (Objects.equals(interval, "Weekly")) maxPrints *= 5;

        long start = series.getStartTime();
        for (int i = 0; i < maxPrints; i++) {
            start = Util.getStartOfPrevDay(start, series.getInstrument(), isRTH);
        }

        int startIndex = series.findIndex(start);
        calculator = new VPCalculator(startIndex, series, isRTH);
        isCalculating = true;
        instrument.forEachTick(series.getStartTime(startIndex), ctx.getCurrentTime() + Util.MILLIS_IN_MINUTE*5, isRTH, calculator);
        isCalculating = false;
        calculated = true;

//        clearFigures(Plot.PRICE);
//        removeFigures(lines);
//        for (Line l : lines) {
//            addFigure(Plot.PRICE, l);
//            System.err.println("Adding line " + l);
//            notifyRedraw();
//        }

//        Util.schedule(() -> calculate(this, finalStart, isRTH));
    }

//    protected void calculate(VolumePivots study, long start, boolean isRTH) {
//        DataContext ctx = study.getDataContext();
//        DataSeries series = ctx.getDataSeries();
//        Instrument instrument = ctx.getInstrument();
//
//        int startIndex = series.findIndex(start);
//        calculator = new VPCalculator(startIndex, series, isRTH);
//        isCalculating = true;
//        instrument.forEachTick(series.getStartTime(startIndex), ctx.getCurrentTime() + Util.MILLIS_IN_MINUTE*5, isRTH, calculator);
//        isCalculating = false;
//        calculated = true;
//
//        study.clearFigures(Plot.PRICE);
//        study.removeFigures(lines);
//        for (Line l : lines) {
//            study.addFigure(Plot.PRICE, l);
//            System.err.println("Adding line " + l);
//            study.notifyRedraw();
//        }
//    }
//
//    @Override
//    public void onTick(DataContext ctx, Tick tick) {
//        if (isCalculating || calculator == null) return;
//        calculator.onTick(tick);
//    }

    static class VolumeByPrice extends TreeMap<Float, Integer> {
        public void addVolumeAtPrice(float price, int volume) {
            int volumeAtPrice = getOrDefault(price, 0);
            volumeAtPrice += volume;
            put(price, volumeAtPrice);
        }
    }
    class VPCalculator implements TickOperation {
        private final DataSeries series;
        private int nextIndex;
        private final boolean rth;
        private final VolumeProfile volumeProfile;
        private VolumeByPrice volumeByPrice = new VolumeByPrice();

        private long nextEnd;

        public VPCalculator(int startIndex, DataSeries series, boolean isRth) {
            this.rth = isRth;
            this.series = series;
            this.nextIndex = startIndex;
            this.volumeProfile = new VolumeProfile();
            nextEnd = getEndForTimeframe(getSettings().getString("Timeframe"), series.getStartTime(startIndex));
        }

        public void onTick(Tick tick) {
            if (series.isComplete(series.findIndex(tick.getTime()))) {
//                System.err.println("Index " + series.findIndex(tick.getTime()) + "|" + nextIndex + " is complete...");
                return;
            }

            volumeProfile.addVolumeAtPrice(tick.isAskTick() ? tick.getAskPrice() : tick.getBidPrice(), tick.getVolume());
            volumeByPrice.addVolumeAtPrice(tick.isAskTick() ? tick.getAskPrice() : tick.getBidPrice(), tick.getVolume());
            if (tick.getTime() > series.getEndTime(nextIndex)) {
                calculate();
                series.setValue(nextIndex, Values.VOLUME_BY_PRICE, volumeByPrice);
                series.setComplete(nextIndex);
                volumeByPrice = new VolumeByPrice();
                nextIndex++;
            }

            // reset if after end of timeframe (daily, weekly, etc)
            if (tick.getTime() > nextEnd) {
                long s = Util.getStartOfNextDay(nextEnd, series.getInstrument(), rth);
                Coordinate start = new Coordinate(s, volumeProfile.getValueAreaMid());
                nextEnd = getEndForTimeframe(getSettings().getString("Timeframe"), tick.getTime());
                Coordinate end = new Coordinate(nextEnd, volumeProfile.getValueAreaMid());

//                System.err.println("start: " + start);
//                System.err.println("end: " + end);
//                System.err.println("--");
                // pivot
                Line pivot = new Line(start, end, getSettings().getPath(PIVOT));

                // extensions
                PathInfo extensionsAbove = getSettings().getPath(EXTENSIONS_ABOVE);
                PathInfo extensionsBelow = getSettings().getPath(EXTENSIONS_BELOW);

                float above1Price = volumeProfile.getValueAreaHigh() + (volumeProfile.getValueAreaBreadth() * 0.50f);
                Line above1 = new Line(new Coordinate(s, above1Price), new Coordinate(nextEnd, above1Price), extensionsAbove);

                float above2Price = volumeProfile.getValueAreaHigh() + (volumeProfile.getValueAreaBreadth() * 0.50f * 2); Line above2 = new Line(new Coordinate(s, above2Price), new Coordinate(nextEnd, above2Price), extensionsAbove); float above3Price = volumeProfile.getValueAreaHigh() + (volumeProfile.getValueAreaBreadth() * 0.50f * 3);
                Line above3 = new Line(new Coordinate(s, above3Price), new Coordinate(nextEnd, above3Price), extensionsAbove);

                float above4Price = volumeProfile.getValueAreaHigh() + (volumeProfile.getValueAreaBreadth() * 0.50f * 4);
                Line above4 = new Line(new Coordinate(s, above4Price), new Coordinate(nextEnd, above4Price), extensionsAbove);

                float below1Price = volumeProfile.getValueAreaLow() - (volumeProfile.getValueAreaBreadth() * 0.50f);
                Line below1 = new Line(new Coordinate(s, below1Price), new Coordinate(nextEnd, below1Price), extensionsBelow);

                float below2Price = volumeProfile.getValueAreaLow() - (volumeProfile.getValueAreaBreadth() * 0.50f * 2);
                Line below2 = new Line(new Coordinate(s, below2Price), new Coordinate(nextEnd, below2Price), extensionsBelow);

                float below3Price = volumeProfile.getValueAreaLow() - (volumeProfile.getValueAreaBreadth() * 0.50f * 3);
                Line below3 = new Line(new Coordinate(s, below3Price), new Coordinate(nextEnd, below3Price), extensionsBelow);

                float below4Price = volumeProfile.getValueAreaLow() - (volumeProfile.getValueAreaBreadth() * 0.50f * 4);
                Line below4 = new Line(new Coordinate(s, below4Price), new Coordinate(nextEnd, below4Price), extensionsBelow);


                PivotSet pivotSet = new PivotSet(s, nextEnd, volumeProfile.getValueAreaMid(), above1Price, above2Price, below1Price, below2Price);
                pivotSet.P = "Pivot";
                beginFigureUpdate();
                addFigure(Plot.PRICE, pivotSet);
                endFigureUpdate();
                notifyRedraw();

//                addFigure(Plot.PRICE, pivot);
//                addFigure(Plot.PRICE, above1);
//                addFigure(Plot.PRICE, above2);
//                addFigure(Plot.PRICE, above3);
//                addFigure(Plot.PRICE, above4);
//                addFigure(Plot.PRICE, below1);
//                addFigure(Plot.PRICE, below2);
//                addFigure(Plot.PRICE, below3);
//                addFigure(Plot.PRICE, below4);
//
//                lines.add(pivot);
//                lines.add(above1);
//                lines.add(above2);
//                lines.add(above3);
//                lines.add(above4);
//                lines.add(below1);
//                lines.add(below2);
//                lines.add(below3);
//                lines.add(below4);

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
