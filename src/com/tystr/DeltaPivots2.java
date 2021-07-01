package com.tystr;

import com.motivewave.platform.sdk.common.*;
import com.motivewave.platform.sdk.common.desc.*;
import com.motivewave.platform.sdk.draw.Line;
import com.motivewave.platform.sdk.draw.Marker;
import com.motivewave.platform.sdk.draw.Text;
import com.motivewave.platform.sdk.study.Plot;
import com.motivewave.platform.sdk.study.StudyHeader;
import com.tystr.delta.DeltaBar;
import com.tystr.delta.SessionDeltaPivot;

import java.awt.*;
import java.awt.geom.Point2D;
import java.time.*;
import java.util.*;

/**
 * This study plots volume pivots and extensions
 *
 * @author Tyler Stroud <tyler@tylerstroud.com>
 */
@StudyHeader(
        namespace = "com.tystr",
        id = "TYSTR_DELTA_PIVOTS_2",
        name = "Session Delta Pivots 2",
        desc = "This study plots session delta pivots",
        overlay = true,
        requiresVolume = true,
        allowTickAggregate = true
)
public class DeltaPivots2 extends com.motivewave.platform.sdk.study.Study {
    // don't do this - used to reset bar colors for developing delta
    private Defaults defaults;
    private ArrayList<Line> lines;

    private SortedMap<Float, Integer> volumeByPrice;
    private SortedMap<Float, Integer> valueArea;


    private int maxRollingWindowDeltaSum = 0;
    private int maxRollingDeltaWindowDeltaStartIndex = 0;

    private float sdp;

    private enum Sessions {
        RTH, // rth window, 1:30pm - 4:30pm
        GLOBEX //
    }

    private SortedMap<Integer, SortedMap<Float, Integer>> volumeByPriceByIndex;
    private boolean isBarInsideWindow = false;

    @Override
    public void initialize(Defaults defaults) {
        this.defaults = defaults;
        clearFigures();
        clearState();
        var sd = createSD();
        var tab = sd.addTab("General");

        var grp = tab.addGroup("");
        grp.addRow(new PathDescriptor("PivotLine", "Pivot Line", Color.ORANGE, 1.0f, null, true, false, false));
        grp.addRow(new PathDescriptor("HighExtensionLine", "High Extensions", Color.BLUE, 1.0f, null, true, false, false));
        grp.addRow(new PathDescriptor("LowExtensionLine", "Low Extensions", Color.RED, 1.0f, null, true, false, false));
//        grp.addRow(new DoubleDescriptor("ValueAreaPercent", "Value Area", 70, 0, 100, 0.10));


        grp.addRow(new IntegerDescriptor("SmoothingBars", "Bars to Smooth", 7, 1, 20, 1));


        grp.addRow(new BooleanDescriptor("HighlightBars", "Highlight Bars", true));
        sd.addQuickSettings("SmoothingBars", "PivotLine", "HighExtensionLine", "LowExtensionLine", "ValueAreaPercent");

        // These are advanced or debug only settings - @todo remove from published version
        var advancedTab= sd.addTab("Advanced");
        SettingGroup advancedGroup = advancedTab.addGroup("Debug");
        advancedGroup.addRow(new BooleanDescriptor("HighlightWindows", "Highlight Window", false));
        advancedGroup.addRow(new BooleanDescriptor("ShowVolumeByPrice", "Show Volume By Price", false));

        LocalTime rthOpenTime = LocalTime.of(9, 30);
        LocalDateTime rthOpenDateTime = LocalDateTime.of(LocalDate.now(), rthOpenTime);
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(rthOpenDateTime)) rthOpenDateTime = rthOpenDateTime.minusDays(1);

        lines = new ArrayList<>();
        volumeByPrice = new TreeMap<>();
        valueArea = new TreeMap<>();
        volumeByPriceByIndex = new TreeMap<>();

        clearFigures();
    }

    private interface WindowInterface {
        public ZonedDateTime getStartTime();
        public ZonedDateTime getEndTime();
    }

    private static class Window implements WindowInterface {
        private final ZonedDateTime start;
        private final ZonedDateTime end;
        public Window(ZonedDateTime start, ZonedDateTime end) {
            this.start = start;
            this.end = end;
        }
        public ZonedDateTime getStartTime() {
            return start;
        }
        public ZonedDateTime getEndTime() {
            return end;
        }

        public static WindowInterface createWindowForInstrument(Sessions session, Instrument instrument) {
            long startOfDay = instrument.getLastTimestamp(); // @todo this is wrong
            ZonedDateTime windowStart = ZonedDateTime.ofInstant(Instant.ofEpochMilli(startOfDay), ZoneId.of("UTC")).plusHours(4);

            return new Window(windowStart, windowStart.plusHours(2).plusMinutes(20));
        }
    }


    @Override
    protected void calculate(int index, DataContext ctx) {
        DataSeries series = ctx.getDataSeries();
        ZonedDateTime barStart1 = ZonedDateTime.ofInstant(Instant.ofEpochMilli(series.getStartTime(index)), ZoneId.of("UTC"));
        Instrument instrument = series.getInstrument();
        Sessions currentSession = null; //Sessions.RTH;

        long startOfDay = instrument.getStartOfDay(series.getStartTime(index), true);

        // Set window based on bar time. Bars within the window will be used to calculate volume profile for the window
        // Window to use to calculate volume and value area

        ZonedDateTime windowStart;
        ZonedDateTime windowEnd;
        if (instrument.isInsideTradingHours(series.getStartTime(index), true)) {
            currentSession = Sessions.RTH;
            windowStart = ZonedDateTime.ofInstant(Instant.ofEpochMilli(startOfDay), ZoneId.of("UTC")).plusHours(4);
            windowEnd = windowStart.plusHours(2).plusMinutes(20); // 1550 EST
        } else {
            currentSession = Sessions.GLOBEX;
            long startOfEveningSession = instrument.getStartOfEveningSession(series.getStartTime(index));
            windowStart = ZonedDateTime.ofInstant(Instant.ofEpochMilli(startOfEveningSession), ZoneId.of("UTC"))
                    .minusDays(1).plusHours(11);
            windowEnd = windowStart.plusHours(5); // 0830 EST
        }

        // if bar within window, do calc

        if ((barStart1.isAfter(windowStart) || barStart1.isEqual(windowStart)) && barStart1.isBefore(windowEnd)) {
            isBarInsideWindow = true;
//            debug("INSIDE");
            if (getSettings().getBoolean("HighlightWindows", true)) {
                Marker square = new Marker(new Coordinate(series.getStartTime(index), series.getLow(index)-2), Enums.MarkerType.SQUARE);
                square.setSize(Enums.Size.MEDIUM);
                square.setFillColor(currentSession == Sessions.RTH ? Color.ORANGE : Color.MAGENTA);
                addFigure(Plot.PRICE, square);

//                debug("HIGHLIGHTING WINDOW");
            }





            // Calculate delta for window

            int numBars = getSettings().getInteger("SmoothingBars", 10);

            // iterate over rolling window and calculate deltas
            int rollingWindowDeltaSum = 0;
            int rollingWindowStart = index - numBars;

//            debug("numBars" + numBars);
//            debug("rollingWindowStart: " + rollingWindowStart);
//            debug("seriesStartIndex: " + series.getStartIndex());
            if (rollingWindowStart <= series.getStartIndex()) {
//                debug ("returning, not enough bars at index: " + index);
                return;
            }

            for (int i = index; i >= rollingWindowStart; i--) {
                ZonedDateTime currentBarStart = ZonedDateTime.ofInstant(Instant.ofEpochMilli(series.getStartTime(index)), ZoneId.of("UTC"));
                if (currentBarStart.isBefore(windowStart)) {
                    // we are outside of the window
                    return;
                }

                var deltaBar = (DeltaBar) series.getValue(i, "Delta");
                if (null == deltaBar) {
                    Map<Float, Integer> deltasByPrice = getDeltasByPrice(series.getStartTime(i), series.getEndTime(i), instrument);
                    deltaBar = new DeltaBar(deltasByPrice);
                    series.setValue(i, "Delta", deltaBar);
//                    debug("calculated delta: " + deltaBar.getDelta());
                } else {
//                    debug("found deltaBar at index: " + i);
                }
//                debug("DELTABAR at index (" + i + "): " + deltaBar);
                rollingWindowDeltaSum += Math.abs(deltaBar.getDelta());

                if (rollingWindowDeltaSum > maxRollingWindowDeltaSum) {
                    maxRollingWindowDeltaSum = rollingWindowDeltaSum;
                    maxRollingDeltaWindowDeltaStartIndex = rollingWindowStart;
                }
            }

//            debug("Calculated MAX rollingWindowDeltaSum: " + maxRollingWindowDeltaSum  + " starting at index " + maxRollingDeltaWindowDeltaStartIndex);
            series.setComplete(index);
        } else {
            // Bar start is outside of the current window
            if (isBarInsideWindow) {
                // first bar outside of window - draw pivots

                if (maxRollingDeltaWindowDeltaStartIndex == 0) {
//                    debug("window start index is 0....");
                    return;
                }


                int numBars = getSettings().getInteger("SmoothingBars", 10);

                // Calculate High and Low of the rolling window
                float rollingWindowHigh = Float.NEGATIVE_INFINITY;
                float rollingWindowLow = Float.POSITIVE_INFINITY;
                for (int i = maxRollingDeltaWindowDeltaStartIndex; i <= maxRollingDeltaWindowDeltaStartIndex + numBars; i++) {
                    if (series.getHigh(i) > rollingWindowHigh) rollingWindowHigh = series.getHigh(i);
                    if (series.getLow(i) < rollingWindowLow) rollingWindowLow = series.getLow(i);
                    // check if color bars enabled
                    series.setPriceBarColor(i, Color.GREEN);

                    // Mark bars used for SDP
//                    Marker square = new Marker(new Coordinate(series.getStartTime(i), series.getLow(i)-2), Enums.MarkerType.SQUARE);
//                    square.setSize(Enums.Size.MEDIUM);
//                    square.setFillColor(Color.CYAN);
//                    addFigure(Plot.PRICE, square);
                }

                debug("DELTABAR at index (" + maxRollingDeltaWindowDeltaStartIndex + "): " +(DeltaBar) series.getValue(maxRollingDeltaWindowDeltaStartIndex, "Delta") );

                SessionDeltaPivot sdp = new SessionDeltaPivot(
                        (DeltaBar) series.getValue(maxRollingDeltaWindowDeltaStartIndex, "Delta"),
                        maxRollingDeltaWindowDeltaStartIndex,
                        rollingWindowHigh,
                        rollingWindowLow,
                        "session",
                        series.getStartTime(maxRollingDeltaWindowDeltaStartIndex),
                        false,
                        series.getEndTime(maxRollingDeltaWindowDeltaStartIndex + numBars)
                );

                debug("SessionDeltaPivot: Session " + sdp.getSession());
                debug("SessionDeltaPivot: Pivot" + sdp.getPivot());
                debug("SessionDeltaPivot: High " + sdp.getHigh());
                debug("SessionDeltaPivot: Low " + sdp.getLow());
                debug("SessionDeltaPivot: Breadth " + sdp.getBreadth());
                debug("SessionDeltaPivot: Delta " + sdp.getDelta());
                debug("SessionDeltaPivot: Delta POC " + sdp.getDeltaPoc());
                debug("SessionDeltaPivot: DataSeries Bar Index " + sdp.getBarIndex());


                // Reset for next window

                maxRollingDeltaWindowDeltaStartIndex = 0;
                rollingWindowHigh = 0;
                rollingWindowLow = 0;
                maxRollingWindowDeltaSum = 0;

                isBarInsideWindow = false;
//
//
//
//                if (true) return;
//
//                // debug - print volume by price
//                if (getSettings().getBoolean("ShowVolumeByPrice", false)) {
//                    Marker arrow = new Marker(new Coordinate(series.getStartTime(index), series.getLow(index)-2), Enums.MarkerType.TRIANGLE);
//                    arrow.setSize(Enums.Size.LARGE);
//                    arrow.setFillColor(Color.RED);
//                    StringBuilder text = new StringBuilder("\n");
//                    for (Map.Entry<Float, Integer> entry : volumeByPrice.entrySet()) {
//                        text.append(entry.getKey()).append(" ").append(entry.getValue()).append("\n");
//                    }
//                    arrow.setTextValue(text.toString());
//                    addFigure(Plot.PRICE, arrow);
//                }
//
//                float vah = valueArea.lastKey();
//                float val = valueArea.firstKey();
//                float breadth = vah - val;
//                float pivot = vah - ((vah - val) / 2);
//                debug("----------");
//                debug("currentSession: " + currentSession);
//                debug("Using value area percent: " + getSettings().getDouble("ValueAreaPercent"));
//                debug("Value Area Low: " + valueArea.firstKey());
//                debug("Value Area High: " + valueArea.lastKey());
//                debug("Value Area Mid (Pivot): " + pivot);
//
                // Draw lines
                Settings settings = getSettings();
                PathInfo SDPPivotPathInfo = settings.getPath("PivotLine");
                PathInfo highExtensionPathInfo = settings.getPath("HighExtensionLine");
                PathInfo lowExtensionPathInfo = settings.getPath("LowExtensionLine");

                long lineStart = series.getStartTime(index); // this is first bar after window, use this as start
                long lineEnd;

                if (!instrument.isInsideTradingHours(series.getStartTime(index), true)) {
                    lineEnd = instrument.getEndOfDay(series.getStartTime(index), true);
                } else {
                    lineEnd = instrument.getStartOfDay(series.getStartTime(index), true) + Util.MILLIS_IN_DAY;
                }

                float pivot = sdp.getPivot();

                Line SDPLine = LineBuilder.create(lineStart, pivot, lineEnd)
                        .setColor(SDPPivotPathInfo.getColor()).setFont(defaults.getFont()).setStroke(SDPPivotPathInfo.getStroke())
                        .setText("SDP: " + pivot)
                        .build();
                addFigure(Plot.PRICE, SDPLine);

//                Line SDPHigh = LineBuilder.create(lineStart, sdp.getHigh(), lineEnd)
//                        .setColor(SDPPivotPathInfo.getColor()).setFont(defaults.getFont()).setStroke(SDPPivotPathInfo.getStroke())
//                        .setText("SDP Low: " + sdp.getHigh())
//                        .build();
//                addFigure(Plot.PRICE, SDPHigh);
//
//                Line SDPLow = LineBuilder.create(lineStart, sdp.getLow(), lineEnd)
//                        .setColor(SDPPivotPathInfo.getColor()).setFont(defaults.getFont()).setStroke(SDPPivotPathInfo.getStroke())
//                        .setText("SDP High: " + sdp.getLow())
//                        .build();
//                addFigure(Plot.PRICE, SDPLow);

                // extensions
                float ext1a = sdp.getExtensionAbove(100);
                float ext2a = sdp.getExtensionAbove(200);
                float ext1b = sdp.getExtensionBelow(100);
                float ext2b = sdp.getExtensionBelow(200);
                Line SDPHighExtensionLine1 = LineBuilder.create(lineStart, ext1a, lineEnd)
                        .setColor(highExtensionPathInfo.getColor())
                        .setFont(defaults.getFont())
                        .setStroke(highExtensionPathInfo.getStroke())
                        .setText("SDP High Ext 1: " + ext1a)
                        .build();
                Line SDPHighExtensionLine2 = LineBuilder.create(lineStart, ext2a, lineEnd)
                        .setColor(highExtensionPathInfo.getColor())
                        .setFont(defaults.getFont())
                        .setStroke(highExtensionPathInfo.getStroke())
                        .setText("SDP High Ext 2: " + ext2a)
                        .build();
                Line SDPLowExtensionLine1 = LineBuilder.create(lineStart, ext1b, lineEnd)
                        .setColor(lowExtensionPathInfo.getColor())
                        .setFont(defaults.getFont())
                        .setStroke(lowExtensionPathInfo.getStroke())
                        .setText("SDP Low Ext 1: " + ext1b)
                        .build();
                Line SDPLowExtensionLine2 = LineBuilder.create(lineStart, ext2b, lineEnd)
                        .setColor(lowExtensionPathInfo.getColor())
                        .setFont(defaults.getFont())
                        .setStroke(lowExtensionPathInfo.getStroke())
                        .setText("SDP Low Ext 2: " + ext2b)
                        .build();
                addFigure(Plot.PRICE, SDPHighExtensionLine1);
                addFigure(Plot.PRICE, SDPHighExtensionLine2);
                addFigure(Plot.PRICE, SDPLowExtensionLine1);
                addFigure(Plot.PRICE, SDPLowExtensionLine2);

                // re-initialize VBP and VA for next window calculation
                volumeByPrice = new TreeMap<>();
                valueArea = new TreeMap<>();
                isBarInsideWindow = false;
            }
        }
    }

//
//    @Override
//    protected void postcalculate(DataContext ctx) {
//        debug("postcalculate");
//        DataSeries series = ctx.getDataSeries();
//        int numBars = getSettings().getInteger("SmoothingBars", 10);
//
//        // Calculate High and Low of the rolling window
//        float rollingWindowHigh = Float.NEGATIVE_INFINITY;
//        float rollingWindowLow = Float.POSITIVE_INFINITY;
//        for (int i = maxRollingDeltaWindowDeltaStartIndex; i <= maxRollingDeltaWindowDeltaStartIndex + numBars; i++) {
//            if (series.getHigh(i) > rollingWindowHigh) rollingWindowHigh = series.getHigh(i);
//            if (series.getLow(i) < rollingWindowLow) rollingWindowLow = series.getLow(i);
//            series.setPriceBarColor(i, Color.GREEN);
//
//            // Mark bars used for SDP
//            Marker square = new Marker(new Coordinate(series.getStartTime(i), series.getLow(i)-2), Enums.MarkerType.SQUARE);
//            square.setSize(Enums.Size.MEDIUM);
//            square.setFillColor(Color.CYAN);
//            addFigure(Plot.PRICE, square);
//        }
//
//
//        SessionDeltaPivot sdp = new SessionDeltaPivot(
//                (DeltaBar) series.getValue(maxRollingDeltaWindowDeltaStartIndex, "Delta"),
//                maxRollingDeltaWindowDeltaStartIndex,
//                rollingWindowHigh,
//                rollingWindowLow,
//                "session",
//                series.getStartTime(maxRollingDeltaWindowDeltaStartIndex),
//                false,
//                series.getEndTime(maxRollingDeltaWindowDeltaStartIndex + numBars)
//        );
//
//        debug("SessionDeltaPivot: Session " + sdp.getSession());
//        debug("SessionDeltaPivot: Pivot" + sdp.getPivot());
//        debug("SessionDeltaPivot: High " + sdp.getHigh());
//        debug("SessionDeltaPivot: Low " + sdp.getLow());
//        debug("SessionDeltaPivot: Breadth " + sdp.getBreadth());
//        debug("SessionDeltaPivot: Delta " + sdp.getDelta());
//        debug("SessionDeltaPivot: Delta POC " + sdp.getDeltaPoc());
//        debug("SessionDeltaPivot: DataSeries Bar Index " + sdp.getBarIndex());
//    }

    /**
     *
     * @param startTime
     * @param endTime
     * @param instrument
     * @return
     */
    private Map<Float, Integer> getDeltasByPrice(long startTime, long endTime, Instrument instrument) {
        Map<Float, Integer> deltasByPrice = new HashMap<Float, Integer>();

        TickOperation t = tick -> {
            if (tick.isAskTick()) {
                int deltaAtPrice = deltasByPrice.getOrDefault(tick.getAskPrice(), 0);
                deltaAtPrice += tick.getVolume();
                deltasByPrice.put(tick.getAskPrice(), deltaAtPrice);
            } else {
                int deltaAtPrice = deltasByPrice.getOrDefault(tick.getBidPrice(), 0);
                deltaAtPrice -= tick.getVolume();
                deltasByPrice.put(tick.getBidPrice(), deltaAtPrice);
            }
        };
        instrument.forEachTick(startTime, endTime, t);

        return deltasByPrice;
    }

    @Override
    protected void calculateValues(DataContext ctx) {
        super.calculateValues(ctx);

        long last = ctx.getDataSeries().getVisibleEndTime();
        for (Line line : lines) {
            line.setEnd(last, line.getEndValue());
        }
    }

    /**
     * Builder class providing a fluent interface for building lines to draw on the chart
     */
    private static class LineBuilder {
        private final Coordinate coordinate1;
        private final Coordinate coordinate2;
        private Color color;
        private Stroke stroke;
        private String text;
        private Font font;

        private LineBuilder(long startTime, double value, long endTime) {
            this.coordinate1 = new Coordinate(startTime, value);
            this.coordinate2 = new Coordinate(endTime, value);
        }

        public static LineBuilder create(long startTime, double value, long endTime) {
            return new LineBuilder(startTime, value, endTime);
        }

        public LineBuilder setColor(Color color) {
            this.color = color;
            return this;
        }

        public LineBuilder setStroke(Stroke stroke) {
            this.stroke = stroke;
            return this;
        }

        public LineBuilder setText(String text) {
            this.text = text;
            return this;
        }

        public LineBuilder setFont(Font font) {
            this.font = font.deriveFont(Font.PLAIN, 16);
            return this;
        }

        public Line build() {
            MyLine line = new MyLine(coordinate1, coordinate2);
            line.setColor(color);
            line.setStroke(stroke);
            line.setExtendRightBounds(false);
            line.setText(text, font);

            return line;
        }
    }

    /**
     * Custom Line class used to override the location of the line's text
     */
    private static class MyLine extends Line {
        public MyLine(Coordinate var1, Coordinate var2) {
            super(var1, var2);
        }

        @Override
        public void layout(DrawContext ctx) {
            super.layout(ctx);
            Text text = super.getText();
            text.setHAlign(Enums.TextAlign.RIGHT);
            Point2D end = ctx.translate(super.getEndTime(), super.getEndValue());
            text.setLocation(end.getX() - text.getWidth(), end.getY());
            text.setShowOutline(false);
        }
    }
}