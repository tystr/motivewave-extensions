package com.tystr;

import com.motivewave.platform.sdk.common.*;
import com.motivewave.platform.sdk.common.desc.*;
import com.motivewave.platform.sdk.common.desc.FileDescriptor;
import com.motivewave.platform.sdk.draw.Figure;
import com.motivewave.platform.sdk.draw.Line;
import com.motivewave.platform.sdk.draw.Marker;
import com.motivewave.platform.sdk.order_mgmt.OrderContext;
import com.motivewave.platform.sdk.study.Plot;
import com.motivewave.platform.sdk.study.RuntimeDescriptor;
import com.motivewave.platform.sdk.study.Study;
import com.motivewave.platform.sdk.study.StudyHeader;
import study_examples.MyMovingAverage;

import java.awt.*;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.*;
import java.util.*;
import java.util.List;

@StudyHeader(
        namespace="com.tystr",
        id="DELTA_PIVOTS_3",
//        rb="study_examples.nls.strings", // locale specific strings are loaded from here
        name="Session Delta Pivots 3",
        label="Session Delta Pivots 3",
        desc="This study plots session delta pivots",
        menu="Tystr",
        overlay=true,
        studyOverlay=true,
        requiresVolume = true,
        supportsBarUpdates = false
)
public class DeltaPivots3 extends Study
{
    enum Values { DELTA };
    private ArrayList<Line> lines;

    private Instrument instrument;
    private SDPCalculator calculator;
    private boolean isCalculating;
    private int lastIndex = 0;

    @Override
    public void initialize(Defaults defaults)
    {
        clearFigures();
        clearState();
        var sd = createSD();
        var tab = sd.addTab("General");

        var grp = tab.addGroup("Lines and Extensions");
        grp.addRow(new PathDescriptor("PivotLine", "Pivot Line", defaults.getOrange(), 1.0f, null, true, false, false));
        grp.addRow(new PathDescriptor("HighExtensionLine", "High Extensions", defaults.getBlue(), 1.0f, null, true, false, false));
        grp.addRow(new PathDescriptor("LowExtensionLine", "Low Extensions", defaults.getRed(), 1.0f, null, true, false, false));

        // Volume to use for the rolling window
//        grp.addRow(new IntegerDescriptor("Volume", "Volume", 10000, 1, Integer.MAX_VALUE, 1));
        var windowGrp = tab.addGroup("Rolling Windows");
        windowGrp.addRow(new IntegerDescriptor("RthWindowSize", "RTH Window Size", 10, 1, Integer.MAX_VALUE, 1));
        windowGrp.addRow(new IntegerDescriptor("GbxWindowSize", "GBX Window Size", 10, 1, Integer.MAX_VALUE, 1));
        windowGrp.addRow(new IntegerDescriptor("EuroWindowSize", "Euro Window Size", 10, 1, Integer.MAX_VALUE, 1));
        windowGrp.addRow(new BooleanDescriptor("HighlightBars", "Color Bars", true));

        sd.addQuickSettings("PivotLine", "HighExtensionLine", "LowExtensionLine");

        // These are advanced or debug only settings - @todo remove from published version
        var advancedTab= sd.addTab("Advanced");
        SettingGroup advancedGroup = advancedTab.addGroup("Debug");
        advancedGroup.addRow(new BooleanDescriptor("HighlightWindows", "Show Session Window Start and End", false));


        lines = new ArrayList<>();


        // To plot lines between pivots
        RuntimeDescriptor desc = getRuntimeDescriptor();
        desc.exportValue(new ValueDescriptor("RthSDP", "RTH Sdp",
                new String[] {"RthSDP"}));
        desc.exportValue(new ValueDescriptor("GbxSDP", "Gbx SDP",
                new String[] {"GbxSDP"}));
        desc.exportValue(new ValueDescriptor("EuroSDP", "Euro SDP",
                new String[] {"EuroSDP"}));

        SettingGroup sdpLineGroup = tab.addGroup("SDP Lines");
        sdpLineGroup.addRow(new PathDescriptor("RthSDP", "RTH SDP", defaults.getBlue(), 1.0f, null, false, false, true));
        sdpLineGroup.addRow(new PathDescriptor("GbxSDP", "Gbx SDP", defaults.getYellow(), 1.0f, null, false, false, true));
        sdpLineGroup.addRow(new PathDescriptor("EuroSDP", "Euro SDP", defaults.getRed(), 1.0f, null, false, false, true));

        grp = tab.addGroup("CSV Output");
        grp.addRow(new BooleanDescriptor("WriteCsv", "Write Levels to CSV", true));
        grp.addRow(new StringDescriptor("CsvFilePath", "Path (filename will be SDP_<SYMBOL>.csv):", ""));
        sd.addDependency(new EnabledDependency("WriteCsv", "CsvFilePath"));

        desc.declarePath("RthSDP", "RthSDP");
        desc.declarePath("GbxSDP", "GbxSDP");
        desc.declarePath("EuroSDP", "EuroSDP");

        clearFigures();
    }

    @Override
    public void onBarUpdate(DataContext ctx) {}
    @Override
    public void onBarOpen(DataContext ctx) {}
    @Override
    public void onBarClose(DataContext ctx) {}

    @Override
    public void destroy()
    {
        super.destroy();
        if (instrument != null) instrument.removeListener(calculator);
    }

    @Override
    protected void calculateValues(DataContext ctx) {
        DataSeries series = ctx.getDataSeries();
//        System.err.println("Series size: " + series.size());
//        System.err.println("lastIndex: " + lastIndex);
        if (series.size() - lastIndex == 1) return; //Skip calculating on new bars
        lastIndex = series.size();
        if  (series.size() == 0 || isCalculating) return;
        instrument = series.getInstrument();

        int maxDays = 10; // @todo configure this
        int startIndex = 1;
        long threshold = instrument.getStartOfDay(series.getStartTime(), ctx.isRTH()) - ((maxDays+1) * Util.MILLIS_IN_DAY);
        for (int i = series.size()-1; i > 0; i--) {
            startIndex = i;
            if (series.getStartTime(i) < threshold) break;
        }

        int finalStartIndex = startIndex;
        Util.schedule(() -> {
            try {
                isCalculating = true;
                calculator = new SDPCalculator(finalStartIndex, series, ctx.getDefaults());
                instrument.forEachTick(series.getStartTime(finalStartIndex), ctx.getCurrentTime() + Util.MILLIS_IN_MINUTE, ctx.isRTH(), calculator);
            } finally {
                if (getSettings().getBoolean("WriteCsv")) {
                    writeFile(calculator.getLastSDP(), getSettings().getString("CsvFilePath"));
                }
                isCalculating = false;
            }
        });
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
     * Writes SDP levels to a csv file for use with the Cloud Levels study
     * @param sdp
     * @param filePath
     */
    protected void writeFile(SDPCalculator.SDP sdp, String filePath) {
        Path path = Paths.get(filePath);
        if (!Files.exists(path) || !Files.isWritable(path)) {
            System.err.println("Unable to write to " + path.toString());
            return;
        }

        StringBuilder content = new StringBuilder()
                .append("Symbol,Price Level,Note,Foreground Color,Background Color,Diameter\n");

        content.append(sdp.getInstrumentSymbol()).append(",")
                .append(sdp.getMid()).append(",")
                .append("SDP").append(",")
                .append("#ffffff").append(",")
                .append("ffa700").append(",")
                .append("2")
                .append("\n");

        this.appendRow(
                content,
                sdp.getInstrumentSymbol(),
                sdp.getExtensionAbove(100),
                "SDP 1A",
                "#ffffff",
                "#5757ff",
                "2"
        );
        this.appendRow(
                content,
                sdp.getInstrumentSymbol(),
                sdp.getExtensionAbove(200),
                "SDP 2A",
                "#ffffff",
                "#5757ff",
                "2"
        );
        this.appendRow(
                content,
                sdp.getInstrumentSymbol(),
                sdp.getExtensionAbove(300),
                "SDP 3A",
                "#ffffff",
                "#5757ff",
                "2"
        );

        this.appendRow(
                content,
                sdp.getInstrumentSymbol(),
                sdp.getExtensionBelow(100),
                "SDP 1B",
                "#ffffff",
                "#b20000",
                "2"
        );

        this.appendRow(
                content,
                sdp.getInstrumentSymbol(),
                sdp.getExtensionBelow(200),
                "SDP 2B",
                "#ffffff",
                "#b20000",
                "2"
        );
        this.appendRow(
                content,
                sdp.getInstrumentSymbol(),
                sdp.getExtensionBelow(300),
                "SDP 3B",
                "#ffffff",
                "#b20000",
                "2"
        );


        System.err.println("\nFILE CONTENTS\n");
        System.err.println(content.toString() + "\n");
        System.err.println("Using file path " + path.toString());
        String fileName = path.toString() + "/SDP_" + sdp.getInstrumentSymbol() + ".csv";

        try (Writer writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(fileName), StandardCharsets.UTF_8))) {
            writer.write(content.toString());
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
        System.err.println("levels written to " + fileName);
    }

    @Override
    public void onTick(DataContext ctx, Tick tick) {
        if (isCalculating || calculator == null) return;
        calculator.onTick(tick);
    }

    @Override
    public void onBarClose(OrderContext ctx) {
        return; // noop - this prevents a problem where things get redrawn improperly
    }

    class Window {
        private int startIndex;
        private final int size;
        private int deltaSum;
        private Map<Float, Integer> deltaByPrice;

        public Window(int startIndex, int size, Map<Float, Integer> deltaByPrice) {
            this.startIndex = startIndex;
            this.size = size;
            this.deltaByPrice = deltaByPrice;
            this.deltaSum = deltaByPrice.values().stream().mapToInt(i -> i).sum();
        }

        public int getDeltaSum() {
            return deltaSum;
        }
        public int getStartIndex() {
            return startIndex;
        }
    }

    class SDPCalculator implements TickOperation {
        private final DataSeries series;
        private int startIndex;
        private int nextIndex;
        private final boolean rth = true;
        private SortedMap<Float, Integer> deltaByPrice;
        private boolean calculating = false;

        private long nextEnd;
        private long nextStart;

        private long start;
        private long end;
        private long runningVolume = 0;

        private int maxDeltaWindowStartIndex = 0;
        private int maxDeltaWindowSum = 0;

        private Window maxDeltaWindow;
        private String currentSession = "";


        private long tradingDayStart;
        private long nextTradingDayStart;
        private long rthStart;
        private long rthEnd;
        private long gbxStart;
        private long gbxEnd;
        private long euroStart;
        private long euroEnd;

        private SDP lastSDP; // used to plot the most recent completed SDP

        private Defaults defaults;

        class SessionWindow {
            int startHour;
            int startMinute;
            int endHour;
            int endMinute;
            int rollingWindowSize;
            public SessionWindow(int startHour, int startMinute, int endHour, int endMinute, int rollingWindowSize) {
                this.startHour = startHour;
                this.startMinute = startMinute;
                this.endHour = endHour;
                this.endMinute = endMinute;
                this.rollingWindowSize = rollingWindowSize;
            }

            public int getEndHour() {
                return endHour;
            }

            public int getEndMinute() {
                return endMinute;
            }
        }

        class Bar {
            private final long start;
            private final long end;
            private final float open;
            private final float high;
            private final float low;
            private final float close;

            private int volume;
            private int delta;

            public Bar(long start, long end, float open, float high, float low, float close) {
                this.start = start;
                this.end = end;
                this.open = open;
                this.high = high;
                this.low = low;
                this.close = close;
            }

            public long getStart() {
                return start;
            }

            public long getEnd() {
                return end;
            }

            public boolean hasVolume() {
                return null != (Integer) volume;

            }
            public void setVolume(int volume) {
                this.volume = volume;
            }

            public int getVolume() {
                return volume;
            }

            public void setDelta(int delta) {
                this.delta = delta;
            }

            public int getDelta() {
                return delta;
            }

            public float getOpen() {
                return open;
            }

            public float getHigh() {
                return high;
            }

            public float getLow() {
                return low;
            }

            public float getClose() {
                return close;
            }
        }
        public SDPCalculator(int startIndex, DataSeries series, Defaults defaults) {
            this.startIndex = startIndex;
            this.series = series;
            this.nextIndex = startIndex;
            this.deltaByPrice = new TreeMap<>();
            this.defaults = defaults;




            Instrument instrument = series.getInstrument();
            this.tradingDayStart = instrument.getStartOfDay(series.getStartTime(nextIndex), false);
            this.nextTradingDayStart = Util.getStartOfNextDay(series.getStartTime(nextIndex), series.getInstrument(), false); // globex open of next day

            // compute session windows for the day
            // gbx session:
            gbxStart = this.tradingDayStart;
            gbxEnd = gbxStart + (9 * Util.MILLIS_IN_HOUR) + (25 * Util.MILLIS_IN_MINUTE);
            // euro session:
            euroStart = this.tradingDayStart +  (9 * Util.MILLIS_IN_HOUR) + (31 * Util.MILLIS_IN_MINUTE);
            euroEnd = euroStart + (5 * Util.MILLIS_IN_HOUR) ; // this should be 9:30am est
            // rth session:
            rthStart = this.tradingDayStart + (19 * Util.MILLIS_IN_HOUR) + (30 * Util.MILLIS_IN_MINUTE);
            rthEnd = rthStart + (2 * Util.MILLIS_IN_HOUR) + (20 * Util.MILLIS_IN_MINUTE);//instrument.getEndOfDay(tickTime, true) - Util.MILLIS_IN_HOUR - (10 * Util.MILLIS_IN_MINUTE);
        }

        public SDP getLastSDP() {
            return this.lastSDP;
        }
        private void calculateRollingWindow() {
            int windowSize = getRollingWindowSizeForSession(currentSession);

            int deltaSum = deltaByPrice.values().stream().mapToInt(i -> i).sum();
            series.setInt(nextIndex, Values.DELTA, deltaSum);

            series.setComplete(nextIndex);
            int windowStartIndex = nextIndex - windowSize;
            if (windowStartIndex < 1) {
                return; // not enough bars
            }
            int windowSum = 0;
            for (int i = nextIndex; i >= windowStartIndex; i--) {
                windowSum += series.getInt(i, Values.DELTA);
            }
            if (Math.abs(windowSum) >= Math.abs(maxDeltaWindowSum)) {
                maxDeltaWindowSum = windowSum;
                maxDeltaWindowStartIndex = windowStartIndex;
            }
            deltaByPrice.clear();
            notifyRedraw();
        }

        private int getRollingWindowSizeForSession(String session) {
            switch (session) {
                case "RTH":
                    return getSettings().getInteger("RthWindowSize");
                case "GBX":
                    return getSettings().getInteger("GbxWindowSize");
                case "EURO":
                    return getSettings().getInteger("EuroWindowSize");
                default:
                    error("No rolling window size configured, using default \"10\".");
                    return 10;
            }
        }


        private void colorBars() {
            Color barColor = maxDeltaWindowSum > 0 ? defaults.getGreen() : defaults.getRed();

//            Marker arrow = new Marker(new Coordinate(series.getStartTime(maxDeltaWindowStartIndex), series.getLow(maxDeltaWindowStartIndex) - 8), Enums.MarkerType.TRIANGLE);
//            arrow.setSize(Enums.Size.MEDIUM);
//            arrow.setFillColor(defaults.getRed());
//            arrow.setTextValue(currentSession + " SDP Window");
//            addFigure(Plot.PRICE, arrow);

            for (int i = maxDeltaWindowStartIndex; i < (maxDeltaWindowStartIndex + getRollingWindowSizeForSession(currentSession)); i++) {
//                series.setPriceBarColor(i, getSettings().getColor("WindowBarColor"));
                series.setPriceBarColor(i, barColor); // @todo set this on delta %
            }
        }

        class SDP {
            private final String instrumentSymbol;
            private final float high;
            private final float low;
            private final float mid;

            public SDP(float high, float low, String instrumentSymbol) {
                this.high = high;
                this.low = low;
                this.mid = high - ((high - low) / 2);
                this.instrumentSymbol = instrumentSymbol;
            }

            public float getHigh() {
                return high;
            }

            public float getLow() {
                return low;
            }
            public float getMid() {
                return mid;
            }

            public String getInstrumentSymbol() {
                return instrumentSymbol;
            }

            /**
             *
             * @param percent whole value e.g. 100 for 100%, 250 for 250%
             * @return the value of the extension
             */
            public float getExtensionAbove(int percent) {
                float breadth = high - low;
                return mid + (breadth / 2) + (breadth * (percent / 100f));
            }

            /**
             *
             * @param percent whole value e.g. 100 for 100%, 250 for 250%
             * @return the value of the extension
             */
            public float getExtensionBelow(int percent) {
                float breadth = high - low;
                return mid- (breadth / 2) - (breadth * (percent / 100f));
            }
        }

        private SDP calculateSDPFromWindow(int windowStart, int windowSize) {
            float high = Float.MIN_VALUE;
            float low = Float.MAX_VALUE;
            for (int i = windowStart; i < windowStart + windowSize; i++) {
                if (series.getHigh(i) > high) high = series.getHigh(i);
                if (series.getLow(i) < low) low = series.getLow(i);
            }

            return new SDP(high, low, series.getInstrument().getSymbol());
        }

        public void onTick(Tick tick) {

            if (series.isComplete(series.findIndex(tick.getTime()))) {
                //return; // nothing to do if this index is complete
            }
            long tickTime = tick.getTime();
            Instrument instrument = series.getInstrument();


            // if new day, recalculate sessions
            if (tickTime > nextTradingDayStart) {
                tradingDayStart = nextTradingDayStart;
                nextTradingDayStart = Util.getStartOfNextDay(tickTime, instrument, false);

                // gbx session:
                gbxStart = this.tradingDayStart;
                gbxEnd = gbxStart + (9 * Util.MILLIS_IN_HOUR) + (25 * Util.MILLIS_IN_MINUTE);
                // euro session:
                euroStart = this.tradingDayStart +  (9 * Util.MILLIS_IN_HOUR) + (31 * Util.MILLIS_IN_MINUTE);
                euroEnd = euroStart + (5 * Util.MILLIS_IN_HOUR); // this should be 9:30am est
                // rth session:
                rthStart = this.tradingDayStart + (19 * Util.MILLIS_IN_HOUR) + (30 * Util.MILLIS_IN_MINUTE);
                rthEnd = rthStart + (2 * Util.MILLIS_IN_HOUR) + (20 * Util.MILLIS_IN_MINUTE);//instrument.getEndOfDay(tickTime, true) - Util.MILLIS_IN_HOUR - (10 * Util.MILLIS_IN_MINUTE);
                // euro session:
//                debug("GBX: " + ZonedDateTime.ofInstant(Instant.ofEpochMilli(gbxStart), ZoneId.of("UTC")) + " -> " + ZonedDateTime.ofInstant(Instant.ofEpochMilli(gbxEnd), ZoneId.of("UTC")));
//                debug("EURO: " + ZonedDateTime.ofInstant(Instant.ofEpochMilli(euroStart), ZoneId.of("UTC")) + " -> " + ZonedDateTime.ofInstant(Instant.ofEpochMilli(euroEnd), ZoneId.of("UTC")));
//                debug("RTH: " + ZonedDateTime.ofInstant(Instant.ofEpochMilli(rthStart), ZoneId.of("UTC")) + " -> " + ZonedDateTime.ofInstant(Instant.ofEpochMilli(rthEnd), ZoneId.of("UTC")));
            }

            boolean insideWindow = false;
            long currentStart = 0;
            long currentEnd = 0;

            if (tickTime >= gbxStart && tickTime < gbxEnd) {
                insideWindow = true;
                currentSession = "GBX";
                currentStart = gbxStart;
                currentEnd = gbxEnd;
                nextEnd = currentEnd;
                nextStart = currentStart;
            } else if (tickTime >= euroStart && tickTime < euroEnd) {
                insideWindow = true;
                currentSession = "EURO";
                currentStart = euroStart;
                currentEnd = euroEnd;
                nextEnd = currentEnd;
                nextStart = currentStart;
            } else if (tickTime >= rthStart && tickTime < rthEnd) {
                insideWindow = true;
                currentSession = "RTH";
                currentStart = rthStart;
                currentEnd = rthEnd;
                nextEnd = currentEnd;
                nextStart = currentStart;
            }

            runningVolume += tick.getVolume();
//            if (runningVolume >= getIntervalForSession(currentSession))
                // Bar currentBar = new Bar(start, end, open, high, low, close);
                // barsForSession.add(currentBar)
                // if we have enough bars, calculate rolling window

            if (tickTime >= series.getEndTime(nextIndex)){
//                debug("nextIndex: " + nextIndex + " tickTimeIndex: " + series.findIndex(tickTime));
//                debug("barclose " + nextIndex + " GBX: " + ZonedDateTime.ofInstant(Instant.ofEpochMilli(gbxStart), ZoneId.of("UTC")) + " -> " + ZonedDateTime.ofInstant(Instant.ofEpochMilli(gbxEnd), ZoneId.of("UTC")));
//                debug("barclose EURO: " + nextIndex + ZonedDateTime.ofInstant(Instant.ofEpochMilli(euroStart), ZoneId.of("UTC")) + " -> " + ZonedDateTime.ofInstant(Instant.ofEpochMilli(euroEnd), ZoneId.of("UTC")));
//                debug("barclose RTH: " + nextIndex + ZonedDateTime.ofInstant(Instant.ofEpochMilli(rthStart), ZoneId.of("UTC")) + " -> " + ZonedDateTime.ofInstant(Instant.ofEpochMilli(rthEnd), ZoneId.of("UTC")));
//                debug("tickTime: " + tickTime + " insideWindow: " + insideWindow + " currentSession: " + currentSession);

                // Bar close inside session window, do rolling window calc if we have delta
                if (insideWindow && !deltaByPrice.isEmpty()) {
                    calculateRollingWindow();
                }
                nextIndex++;
            }

            if (calculating && !insideWindow) {
                calculating = false;

                // DEBUG: plot arrow marking end of session/period
                if (getSettings().getBoolean("HighlightWindows")) {
                    Marker arrow = new Marker(new Coordinate(series.getStartTime(nextIndex), series.getClose(nextIndex) - 16), Enums.MarkerType.TRIANGLE);
                    arrow.setSize(Enums.Size.LARGE);
                    arrow.setFillColor(defaults.getRed());
                    arrow.setTextValue(currentSession + "XXX");
                    addFigure(Plot.PRICE, arrow);
                }

                // color bars
//                debug("max delta for session " + currentSession + " starts at index " + maxDeltaWindowStartIndex);
                debug("Session: " + currentSession + " - maxDeltaWindowStartTime: " + ZonedDateTime.ofInstant(Instant.ofEpochMilli(series.getStartTime(maxDeltaWindowStartIndex)), ZoneId.of("UTC")));

                colorBars();

                // draw pivots

//                debug("Completed Window ending at " + nextEnd);
                SDP sdp;
                switch (currentSession) {
                    case "RTH":
                        sdp = calculateSDPFromWindow(maxDeltaWindowStartIndex, getRollingWindowSizeForSession("RTH"));
                        series.setFloat(maxDeltaWindowStartIndex, "RthSDP", sdp.getMid());
                        nextStart = gbxStart;
                        nextEnd = gbxEnd;
                        break;
                    case "GBX":
                        sdp = calculateSDPFromWindow(maxDeltaWindowStartIndex, getRollingWindowSizeForSession("GBX"));
                        series.setFloat(maxDeltaWindowStartIndex, "GbxSDP", sdp.getMid());
                        nextStart = euroStart;
                        nextEnd = euroEnd;
                        break;
                    case "EURO":
                        sdp = calculateSDPFromWindow(maxDeltaWindowStartIndex, getRollingWindowSizeForSession("EURO"));
                        series.setFloat(maxDeltaWindowStartIndex, "EuroSDP", sdp.getMid());
                        nextStart = rthStart;
                        nextEnd = rthEnd;
                        break;
                    default:
                        return;
                }
                lastSDP = sdp;
;


//                debug("currentSession: " + currentSession);
//                debug("currentTickTime: " + ZonedDateTime.ofInstant(Instant.ofEpochMilli(tickTime), ZoneId.of("UTC")));
//                debug ("New window starting at " +
//                        ZonedDateTime.ofInstant(Instant.ofEpochMilli(nextStart), ZoneId.of("UTC")) +
//                        " and ending at " + ZonedDateTime.ofInstant(Instant.ofEpochMilli(nextEnd), ZoneId.of("UTC"))
//                );

                // reset
                maxDeltaWindowStartIndex = 0;
                maxDeltaWindowSum = 0;
                return;
            }

            // if we are after or equal to next start and haven't begun calculating yet, set next start
            if (insideWindow) {
                if (!calculating) {
                    // starting a new session window, mark the start for debugging
                    if (getSettings().getBoolean("HighlightWindows")) {
                        Marker startArrow = new Marker(new Coordinate(series.getStartTime(series.findIndex(tick.getTime())), series.getLow(series.findIndex(tick.getTime())) - 2), Enums.MarkerType.TRIANGLE);
                        startArrow.setSize(Enums.Size.LARGE);
                        startArrow.setFillColor(defaults.getGreen());
                        startArrow.setTextValue(currentSession);
                        addFigure(Plot.PRICE, startArrow);
                    }
                    calculating = true;
                }

                if (tick.isAskTick()) {
                    float price = tick.getAskPrice();
                    int delta = deltaByPrice.getOrDefault(price, 0);
                    delta += tick.getVolume();
                    deltaByPrice.put(tick.getAskPrice(), delta);
                } else {
                    float price = tick.getBidPrice();
                    int delta = deltaByPrice.getOrDefault(price, 0);
                    delta -= tick.getVolume();
                    deltaByPrice.put(tick.getBidPrice(), delta);
                }
            }
        }
    }
}
