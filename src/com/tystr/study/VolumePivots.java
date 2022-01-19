package com.tystr.study;

import com.motivewave.platform.sdk.common.*;
import com.motivewave.platform.sdk.common.desc.*;
import com.motivewave.platform.sdk.draw.Line;
import com.motivewave.platform.sdk.study.Plot;
import com.motivewave.platform.sdk.study.Study;
import com.motivewave.platform.sdk.study.StudyHeader;
import com.tystr.DeltaPivots3;
import com.tystr.VolumeProfile;
import com.tystr.study.overlay.PivotSet;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@StudyHeader(
        namespace="com.tystr.study",
        id="TYSTR_STUDY_VOLUME_PIVOTS",
        name="Volume Pivots",
        label="",
        desc="This study plots pivots based on prior volume profile data",
        menu="Tystr",
        overlay=true,
        studyOverlay=false,
        requiresVolume = true,
        requiresBarUpdates=true

)
public class VolumePivots extends Study
{
    enum Values { VAH, VAL, VAH_1, VAH_2, VAL_1, VAL_2, VA_PIVOT, TIMEFRAME, VOLUME_BY_PRICE};
    enum Intervals {DAILY, WEEKLY}

    VPCalculator calculator;
    private boolean isCalculating = false;
    private boolean calculated = false;

    private final String TIMEFRAME = "Timeframe";
    private final String RTH_DATA = "rthData";
    private final String NUM_PRINTS = "numPrints";
    private final String PIVOT = "pivot";
    private final String EXTENSIONS_ABOVE = "extensionAbove";
    private final String EXTENSIONS_BELOW = "extensionBelow";
    private final String USE_SERIES_BS = "useSeriesBS";

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

        grp.addRow(new BarSizeDescriptor(Inputs.BARSIZE, "Bar Size", BarSize.getBarSize(1440)), new BooleanDescriptor(USE_SERIES_BS, "Use Series Bar Size", false, false));

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
        sd.addDependency(new EnabledDependency(false, USE_SERIES_BS, Inputs.BARSIZE));



        grp = tab.addGroup("CSV Output");
        grp.addRow(new BooleanDescriptor("WriteCsv", "Write Levels to CSV", true));
        grp.addRow(new StringDescriptor("CsvFilePath", "Path (filename will be SDP_<SYMBOL>.csv):", ""));
        sd.addDependency(new EnabledDependency("WriteCsv", "CsvFilePath"));

//        clearFigures();
        setMinBars(2);
//        lines.clear();
    }

    @Override
    public void clearState()
    {
        System.err.println("Clearing state...");
        super.clearState();
        lines.clear();
        calculated = false;
    }

    @Override
    protected void calculateValues(DataContext ctx) {
//        DataSeries series11 = ctx.getDataSeries();
//        BarSize barSize = getSettings().getBarSize(Inputs.BARSIZE);

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

        notifyRedraw();

        if (getSettings().getBoolean("WriteCsv")) {
            writeFile(calculator.getLastVolumeProfile(), getSettings().getString("CsvFilePath"));
        }

//        clearFigures(Plot.PRICE);
//        removeFigures(lines);
//        for (Line l : lines) {
//            addFigure(Plot.PRICE, l);
//            System.err.println("Adding line " + l);
//            notifyRedraw();
//        }

//        Util.schedule(() -> calculate(this, finalStart, isRTH));
    }

    private void appendRow(StringBuilder builder, String symbol, float price, String note, String fColor, String bColor, String diameter) {
        builder.append(symbol).append(",")
                .append(price).append(",")
                .append(note).append(",")
                .append(fColor).append(",")
                .append(bColor).append(",")
                .append(diameter)
                .append("\n");
    }


    /**
     * Writes pivot levels to a file for use with cloud levels study
     * @param volumeProfile
     * @param filePath
     */
    protected void writeFile(VolumeProfile volumeProfile, String filePath) {
        Path path = Paths.get(filePath);
        if (!Files.exists(path) || !Files.isWritable(path)) {
            System.err.println("Unable to write to " + path.toString());
            return;
        }

        float above1Price = volumeProfile.getValueAreaHigh() + (volumeProfile.getValueAreaBreadth() * 0.50f);
        float above2Price = volumeProfile.getValueAreaHigh() + (volumeProfile.getValueAreaBreadth() * 0.50f * 2);
        float above3Price = volumeProfile.getValueAreaHigh() + (volumeProfile.getValueAreaBreadth() * 0.50f * 3);
        float above4Price = volumeProfile.getValueAreaHigh() + (volumeProfile.getValueAreaBreadth() * 0.50f * 4);
        float below1Price = volumeProfile.getValueAreaLow() - (volumeProfile.getValueAreaBreadth() * 0.50f);
        float below2Price = volumeProfile.getValueAreaLow() - (volumeProfile.getValueAreaBreadth() * 0.50f * 2);
        float below3Price = volumeProfile.getValueAreaLow() - (volumeProfile.getValueAreaBreadth() * 0.50f * 3);
        float below4Price = volumeProfile.getValueAreaLow() - (volumeProfile.getValueAreaBreadth() * 0.50f * 4);

        StringBuilder content = new StringBuilder()
                .append("Symbol,Price Level,Note,Foreground Color,Background Color,Diameter\n");

        content.append(volumeProfile.getInstrumentSymbol()).append(",")
                .append(volumeProfile.getValueAreaMid()).append(",")
                .append("VP").append(",")
                .append("#ffffff").append(",")
                .append("ffa700").append(",")
                .append("2")
                .append("\n");

        this.appendRow(
                content,
                volumeProfile.getInstrumentSymbol(),
                above1Price,
                "VP 1A",
                "#ffffff",
                "#5757ff",
                "2"
        );
        this.appendRow(
                content,
                volumeProfile.getInstrumentSymbol(),
                above2Price,
                "VP 2A",
                "#ffffff",
                "#5757ff",
                "2"
        );
        this.appendRow(
                content,
                volumeProfile.getInstrumentSymbol(),
                above3Price,
                "VP 3A",
                "#ffffff",
                "#5757ff",
                "2"
        );
        this.appendRow(
                content,
                volumeProfile.getInstrumentSymbol(),
                above4Price,
                "VP 4A",
                "#ffffff",
                "#5757ff",
                "2"
        );

        this.appendRow(
                content,
                volumeProfile.getInstrumentSymbol(),
                below1Price,
                "SDP 1B",
                "#ffffff",
                "#b20000",
                "2"
        );

        this.appendRow(
                content,
                volumeProfile.getInstrumentSymbol(),
                below2Price,
                "SDP 2B",
                "#ffffff",
                "#b20000",
                "2"
        );
        this.appendRow(
                content,
                volumeProfile.getInstrumentSymbol(),
                below3Price,
                "SDP 3B",
                "#ffffff",
                "#b20000",
                "2"
        );
        this.appendRow(
                content,
                volumeProfile.getInstrumentSymbol(),
                below4Price,
                "SDP 4B",
                "#ffffff",
                "#b20000",
                "2"
        );


        System.err.println("\nFILE CONTENTS\n");
        System.err.println(content.toString() + "\n");
        System.err.println("Using file path " + path.toString());
        String fileName = path.toString() + "/VP_" + volumeProfile.getInstrumentSymbol() + ".csv";

        try (Writer writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(fileName), StandardCharsets.UTF_8))) {
            writer.write(content.toString());
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
        System.err.println("levels written to " + fileName);
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

    static class VolumeProfile extends com.tystr.VolumeProfile {
        private String instrumentSymbol;

        public void setInstrumentSymbol(String instrumentSymbol) {
            this.instrumentSymbol = instrumentSymbol;
        }

        public String getInstrumentSymbol() {
            return instrumentSymbol;
        }
    }

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
        private VolumePivots.VolumeProfile lastVolumeProfile;
        private VolumeByPrice volumeByPrice = new VolumeByPrice();

        private long nextEnd;

        public VPCalculator(int startIndex, DataSeries series, boolean isRth) {
            this.rth = isRth;
            this.series = series;
            this.nextIndex = startIndex;
            this.volumeProfile = new VolumeProfile();
            nextEnd = getEndForTimeframe(getSettings().getString("Timeframe"), series.getStartTime(startIndex));
        }

        public VolumeProfile getLastVolumeProfile() {
            return lastVolumeProfile;
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

                float above1Price = volumeProfile.getValueAreaHigh() + (volumeProfile.getValueAreaBreadth() * 0.50f);
                float above2Price = volumeProfile.getValueAreaHigh() + (volumeProfile.getValueAreaBreadth() * 0.50f * 2);
                float above3Price = volumeProfile.getValueAreaHigh() + (volumeProfile.getValueAreaBreadth() * 0.50f * 3);
                float above4Price = volumeProfile.getValueAreaHigh() + (volumeProfile.getValueAreaBreadth() * 0.50f * 4);
                float below1Price = volumeProfile.getValueAreaLow() - (volumeProfile.getValueAreaBreadth() * 0.50f);
                float below2Price = volumeProfile.getValueAreaLow() - (volumeProfile.getValueAreaBreadth() * 0.50f * 2);
                float below3Price = volumeProfile.getValueAreaLow() - (volumeProfile.getValueAreaBreadth() * 0.50f * 3);
                float below4Price = volumeProfile.getValueAreaLow() - (volumeProfile.getValueAreaBreadth() * 0.50f * 4);

                PivotSet pivotSet = new PivotSet(s, nextEnd, volumeProfile.getValueAreaMid(), above1Price, above2Price, above3Price, above4Price, below1Price, below2Price, below3Price, below4Price);
                pivotSet.P = "Pivot";
                beginFigureUpdate();
                addFigure(Plot.PRICE, pivotSet);
                endFigureUpdate();
//                notifyRedraw();

                lastVolumeProfile = volumeProfile;
                lastVolumeProfile.setInstrumentSymbol(series.getInstrument().getSymbol());
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
