package com.tystr;

import com.motivewave.platform.sdk.common.*;
import com.motivewave.platform.sdk.common.desc.*;
import com.motivewave.platform.sdk.draw.Figure;
import com.motivewave.platform.sdk.draw.Line;
import com.motivewave.platform.sdk.draw.Marker;
import com.motivewave.platform.sdk.study.Plot;
import com.motivewave.platform.sdk.study.Study;
import com.motivewave.platform.sdk.study.StudyHeader;

import java.awt.*;
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
        requiresVolume = true
)
public class DeltaPivots3 extends Study
{
    enum Values { DELTA };
    private ArrayList<Line> lines;


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

        clearFigures();
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
        TickOperation calculator = new SDPCalculator(startIndex, series, ctx.getDefaults());
        debug("starting at index " + startIndex);
        instrument.forEachTick(series.getStartTime(startIndex), ctx.getCurrentTime() + Util.MILLIS_IN_MINUTE, ctx.isRTH(), calculator);
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
            int windowSize;
            if (session.equals("RTH")) {
                windowSize = getSettings().getInteger("RthWindowSize");
            } else if (session.equals("GBX")) {
                windowSize = getSettings().getInteger("GbxWindowSize");
            } else if (session.equals("EURO")) {
                windowSize = getSettings().getInteger("EuroWindowSize");
            } else {
                error("No rolling window size configured, using default \"10\".");
                windowSize = 10;
            }
            return windowSize;
        }


        private void colorBars() {
            Color barColor = maxDeltaWindowSum > 0 ? defaults.getGreen() : defaults.getRed();
            for (int i = maxDeltaWindowStartIndex; i < (maxDeltaWindowStartIndex + getRollingWindowSizeForSession(currentSession)); i++) {
//                series.setPriceBarColor(i, getSettings().getColor("WindowBarColor"));
                series.setPriceBarColor(i, barColor); // @todo set this on delta %
            }
        }

        public void onTick(Tick tick) {
            if (series.isComplete(series.findIndex(tick.getTime()))) {
                return; // nothing to do if this index is complete
            }
            long tickTime = tick.getTime();
            Instrument instrument = series.getInstrument();

            // if new day, recalculate sessions
            if (tickTime > nextTradingDayStart) {
                tradingDayStart = nextTradingDayStart;
                nextTradingDayStart = Util.getStartOfNextDay(tickTime, instrument, false);

                debug("Setting next trading day start: " + ZonedDateTime.ofInstant(Instant.ofEpochMilli(nextTradingDayStart), ZoneId.of("UTC")));
                // rth session:
                gbxStart = this.tradingDayStart;
                gbxEnd = gbxStart + (9 * Util.MILLIS_IN_HOUR) + (25 * Util.MILLIS_IN_MINUTE);
                // euro session:
                euroStart = this.tradingDayStart +  (9 * Util.MILLIS_IN_HOUR) + (31 * Util.MILLIS_IN_MINUTE);
                euroEnd = euroStart + (5 * Util.MILLIS_IN_HOUR); // this should be 9:30am est
                // rth session:
                rthStart = this.tradingDayStart + (19 * Util.MILLIS_IN_HOUR) + (30 * Util.MILLIS_IN_MINUTE);
                rthEnd = rthStart + (2 * Util.MILLIS_IN_HOUR) + (20 * Util.MILLIS_IN_MINUTE);//instrument.getEndOfDay(tickTime, true) - Util.MILLIS_IN_HOUR - (10 * Util.MILLIS_IN_MINUTE);
                // euro session:
                debug("GBX: " + ZonedDateTime.ofInstant(Instant.ofEpochMilli(gbxStart), ZoneId.of("UTC")) + " -> " + ZonedDateTime.ofInstant(Instant.ofEpochMilli(gbxEnd), ZoneId.of("UTC")));
                debug("EURO: " + ZonedDateTime.ofInstant(Instant.ofEpochMilli(euroStart), ZoneId.of("UTC")) + " -> " + ZonedDateTime.ofInstant(Instant.ofEpochMilli(euroEnd), ZoneId.of("UTC")));
                debug("RTH: " + ZonedDateTime.ofInstant(Instant.ofEpochMilli(rthStart), ZoneId.of("UTC")) + " -> " + ZonedDateTime.ofInstant(Instant.ofEpochMilli(rthEnd), ZoneId.of("UTC")));
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

            if (tickTime >= series.getEndTime(nextIndex)){
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
                    Marker arrow = new Marker(new Coordinate(series.getStartTime(nextIndex), series.getClose(nextIndex) - 8), Enums.MarkerType.TRIANGLE);
                    arrow.setSize(Enums.Size.LARGE);
                    arrow.setFillColor(defaults.getRed());
                    arrow.setTextValue(currentSession);
                    addFigure(Plot.PRICE, arrow);
                }

                // color bars
                debug("max delta for session " + currentSession + " starts at index " + maxDeltaWindowStartIndex);
                debug("maxDeltaWindowSTartTime:" + ZonedDateTime.ofInstant(Instant.ofEpochMilli(series.getStartTime(maxDeltaWindowStartIndex)), ZoneId.of("UTC")));
                colorBars();

                // draw pivots

                debug("Completed Window ending at " + ZonedDateTime.ofInstant(Instant.ofEpochMilli(nextEnd), ZoneId.of("UTC")));
//                debug("Completed Window ending at " + nextEnd);
                if (currentSession.equals("RTH")) {
                    nextStart = gbxStart;
                    nextEnd = gbxEnd;
                } else if (currentSession.equals("GBX")) {
                    nextStart = euroStart;
                    nextEnd = euroEnd;
                } else if (currentSession.equals("EURO")) {
                    nextStart = rthStart;
                    nextEnd = rthEnd;
                }

                debug("currentSession: " + currentSession);
                debug("currentTickTime: " + ZonedDateTime.ofInstant(Instant.ofEpochMilli(tickTime), ZoneId.of("UTC")));
                debug ("New window starting at " +
                        ZonedDateTime.ofInstant(Instant.ofEpochMilli(nextStart), ZoneId.of("UTC")) +
                        " and ending at " + ZonedDateTime.ofInstant(Instant.ofEpochMilli(nextEnd), ZoneId.of("UTC"))
                );

                // reset
                maxDeltaWindowStartIndex = 0;
                maxDeltaWindowSum = 0;
                return;
            }

            // if we are after or equal to next start and haven't begun calculating yet, set next start
            if (insideWindow) {
                if (!calculating) {
                    // make sure we're reset
                    maxDeltaWindowStartIndex = 0;
                    maxDeltaWindowSum = 0;
                    deltaByPrice.clear();
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
