package com.tystr;

import com.motivewave.platform.sdk.common.*;
import com.motivewave.platform.sdk.common.desc.*;
import com.motivewave.platform.sdk.draw.Line;
import com.motivewave.platform.sdk.draw.Marker;
import com.motivewave.platform.sdk.draw.Text;
import com.motivewave.platform.sdk.study.Plot;
import com.motivewave.platform.sdk.study.StudyHeader;

import java.awt.*;
import java.awt.geom.Point2D;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.*;

/**
 * This study plots volume pivots and extensions
 * @author Tyler Stroud <tyler@tylerstroud.com>
 */
@StudyHeader(
        namespace = "com.tystr",
        id = "TYSTR_VOLUME_PIVOTS",
        name = "Session Volume Pivots",
        desc = "This study plots volume pivots.",
        overlay = true,
        requiresVolume = true,
        allowTickAggregate = true
)
public class VolumePivots extends com.motivewave.platform.sdk.study.Study {
    final static String SESSION_RTH = "RTH";
    final static String SESSION_JPY = "JPY";
    final static String SESSION_LONDON = "EURO/London";
    final static DayOfWeek[] tradingDays = new DayOfWeek[]{
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
    };

    // don't do this - used to reset bar colors for developing delta
    private Defaults defaults;
    private ArrayList<Line> lines;

    private SortedMap<Float, Integer> volumeByPrice;
    private SortedMap<Float, Integer> valueArea;
    private boolean calculating = false;

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
        grp.addRow(new DoubleDescriptor("ValueAreaPercent", "Value Area", 0.70, 0, 100, 0.01));
//        grp.addRow(new InputDescriptor("SessionInput", "Session", new String[]{SESSION_RTH, SESSION_JPY, SESSION_LONDON}, SESSION_RTH));

//        grp.addRow(new BooleanDescriptor("HighlightBarsLines", "Show Lines for Developing SVP", false));

        grp.addRow(new BooleanDescriptor("HighlightBars", "Highlight Bars", true));
//        grp.addRow(new BooleanDescriptor("SmoothingEnabled", "Enable Smoothing", false));
//        grp.addRow(new IntegerDescriptor("SmoothingBars", "Bars to Smooth", 5, 1, 20, 1));

//        sd.addDependency(new EnabledDependency("SmoothingEnabled", "SmoothingBars"));
//
        sd.addQuickSettings("SessionInput", "PivotLine", "HighExtensionLine", "LowExtensionLine");

        LocalTime rthOpenTime = LocalTime.of(9, 30);
        LocalDateTime rthOpenDateTime = LocalDateTime.of(LocalDate.now(), rthOpenTime);
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(rthOpenDateTime)) rthOpenDateTime = rthOpenDateTime.minusDays(1);

        lines = new ArrayList<>();
        volumeByPrice = new TreeMap<>();
        valueArea = new TreeMap<>();

    }

    @Override
    protected void calculate(int index, DataContext ctx) {
        DataSeries series = ctx.getDataSeries();
        Instrument instrument = series.getInstrument();

        ZonedDateTime sessionStart = ZonedDateTime.ofInstant(
                Instant.ofEpochMilli(instrument.getStartOfDay(series.getStartTime(index), true)), ZoneId.of("UTC")
        );
        ZonedDateTime sessionEnd = ZonedDateTime.ofInstant(
                Instant.ofEpochMilli(instrument.getEndOfDay(series.getStartTime(index), true)), ZoneId.of("UTC")
        );
        sessionEnd = sessionEnd.plusHours(1);

//        if (!instrument.isInsideTradingHours(series.getStartTime(index), true)) {
        ZonedDateTime barStart1 = ZonedDateTime.ofInstant(Instant.ofEpochMilli(series.getStartTime(index)), ZoneId.of("UTC"));
        if (barStart1.isAfter(sessionEnd)) {
            if (calculating) {
                debug("VALUE AREA FINAL VAL: " + valueArea.firstKey());
                debug("VALUE AREA FINAL VAH: " + valueArea.lastKey());

                float vah = valueArea.lastKey();
                float val = valueArea.firstKey();
                float breadth = vah - val;
                float pivot = vah - ((vah - val) / 2);

                debug("VALUE AREA PIVOT: " + pivot);

                Settings settings = getSettings();
                PathInfo svpPivotPathInfo = settings.getPath("PivotLine");
                PathInfo highExtensionPathInfo = settings.getPath("HighExtensionLine");
                PathInfo lowExtensionPathInfo = settings.getPath("LowExtensionLine");

                long lineStart = sessionStart.toEpochSecond()*1000;
                long lineEnd = sessionStart.plusDays(1).toEpochSecond()*1000;
                Line svpLine = LineBuilder.create(lineStart, pivot, lineEnd)
                        .setColor(svpPivotPathInfo.getColor()) .setFont(defaults.getFont()) .setStroke(svpPivotPathInfo.getStroke())
                        .setText("SVP: " + pivot)
                        .build();
                addFigure(Plot.PRICE, svpLine);

                // extensions
                float ext1a = vah + (breadth);
                float ext2a = vah + (breadth * 2);
                float ext1b = val - (breadth);
                float ext2b = val - (breadth * 2);
                Line svpHighExtensionLine1 = LineBuilder.create(lineStart, ext1a, lineEnd)
                        .setColor(highExtensionPathInfo.getColor())
                        .setFont(defaults.getFont())
                        .setStroke(highExtensionPathInfo.getStroke())
                        .setText("SVP High Ext 1: " + ext1a)
                        .build();
                Line svpHighExtensionLine2 = LineBuilder.create(lineStart, ext2a, lineEnd)
                        .setColor(highExtensionPathInfo.getColor())
                        .setFont(defaults.getFont())
                        .setStroke(highExtensionPathInfo.getStroke())
                        .setText("SVP High Ext 2: " + ext2a)
                        .build();
                Line svpLowExtensionLine1 = LineBuilder.create(lineStart, ext1b, lineEnd)
                        .setColor(lowExtensionPathInfo.getColor())
                        .setFont(defaults.getFont())
                        .setStroke(lowExtensionPathInfo.getStroke())
                        .setText("SVP Low Ext 1: " + ext1b)
                        .build();
                Line svpLowExtensionLine2 = LineBuilder.create(lineStart, ext2b, lineEnd)
                        .setColor(lowExtensionPathInfo.getColor())
                        .setFont(defaults.getFont())
                        .setStroke(lowExtensionPathInfo.getStroke())
                        .setText("SVP Low Ext 2: " + ext2b)
                        .build();
                addFigure(Plot.PRICE, svpHighExtensionLine1);
                addFigure(Plot.PRICE, svpHighExtensionLine2);
                addFigure(Plot.PRICE, svpLowExtensionLine1);
                addFigure(Plot.PRICE, svpLowExtensionLine2);
            }

            calculating = false;
            volumeByPrice = new TreeMap<>(); // @todo reinitiatlize thisd for next vp
            valueArea = new TreeMap<>();
            return;
        } else {
            //debug("inside trading hours");
            // if bar is after open + OFFSET (4 hours == 1:30pm EST)
            int OFFSET_HOURS = 4; // @todo make this configurable
            ZonedDateTime barStart = ZonedDateTime.ofInstant(Instant.ofEpochMilli(series.getStartTime(index)), ZoneId.of("UTC"));
            ZonedDateTime threshold = sessionStart.plusHours(OFFSET_HOURS).minusMinutes(1);


            // return if bar start is before threshold
            if (barStart.isBefore(threshold)) {
//                debug("before threshold, returning");
                return;
            } else if (barStart.isAfter(sessionEnd)) {
//                debug("After threshold, drawing pivots?");
//                debug("calculating: " + calculating);
                if (calculating) {
//                    debug("VALUE AREA FINAL VAL: " + valueArea.firstKey());
//                    debug("VALUE AREA FINAL VAH: " + valueArea.lastKey());
                    calculating = false;
                }
                return;
            }

            calculating = true;

            // debugging visual - highlight bars we should be using to calculate VP
            Marker arrow = new Marker(new Coordinate(series.getStartTime(index), series.getLow(index)), Enums.MarkerType.ARROW);
            arrow.setSize(Enums.Size.MEDIUM);
            arrow.setFillColor(Color.ORANGE);
            addFigure(Plot.PRICE, arrow);


            // @todo calculate volume by price
            float interval = (float) instrument.getTickSize();

            TickOperation t = new TickOperation() {
                @Override
                public void onTick(Tick tick) {
                    float price = tick.isAskTick() ? tick.getAskPrice() : tick.getBidPrice();
                    int volume = volumeByPrice.getOrDefault(price, 0);
                    volume += tick.getVolume();
                    volumeByPrice.put(price, volume);
                }
            };
            instrument.forEachTick(series.getStartTime(index), series.getEndTime(index), t);

            // calculate value area

            float volumePOC = Collections.max(volumeByPrice.entrySet(), Map.Entry.comparingByValue()).getKey();
            int totalVolume = volumeByPrice.values().stream().mapToInt(i -> i).sum();
            int runningVolume = 0;
//            debug("totalVolume: " + totalVolume);
//            debug("Volume POC: " + volumePOC);

            float developingPOC = volumePOC;
            valueArea = new TreeMap<>(); // Reset Value Area


            // Add volume POC to value area
            valueArea.put(volumePOC, volumeByPrice.get(volumePOC));

            float abovePrice1 = developingPOC + interval;
            float abovePrice2 = developingPOC + (interval * 2);
            float belowPrice1 = developingPOC; //- interval;
            float belowPrice2 = developingPOC; // - (interval * 2);
            boolean incrementAbove = false;
            int aboveIncrements = 0;
            int belowIncrements = -2;

            for (int i = 1; i <= volumeByPrice.size(); i++) {
                if (incrementAbove) {
                    aboveIncrements = aboveIncrements + 2;
                    abovePrice1 = developingPOC + (interval * (aboveIncrements + 1));
                    abovePrice2 = developingPOC + (interval * (aboveIncrements + 2));
//                    debug("abovePrice1: " + abovePrice1 + " abovePrice2: " +  abovePrice2 + "multiplier " + aboveIncrements);
                } else {
                    belowIncrements = belowIncrements + 2;
                    belowPrice1 = developingPOC - (interval * (belowIncrements + 1));
                    belowPrice2 = developingPOC - (interval * (belowIncrements + 2));
//                    debug("belowPrice1: " + belowPrice1 + " belowPrice2: " +  belowPrice2 + "multiplier " + belowIncrements);
                }

                int abovePrice1Volume = volumeByPrice.getOrDefault(abovePrice1, 0);
                int abovePrice2Volume = volumeByPrice.getOrDefault(abovePrice2, 0);
                int belowPrice1Volume = volumeByPrice.getOrDefault(belowPrice1, 0);
                int belowPrice2Volume = volumeByPrice.getOrDefault(belowPrice2, 0);

                int aboveSum = abovePrice1Volume + abovePrice2Volume;
                int belowSum = belowPrice1Volume + belowPrice2Volume;

                if (aboveSum > belowSum) {
                    incrementAbove = true;
                    valueArea.put(abovePrice1, abovePrice1Volume);
                    valueArea.put(abovePrice2, abovePrice2Volume);
                    runningVolume += abovePrice1Volume + abovePrice2Volume;
                } else {
                    incrementAbove = false;
                    valueArea.put(belowPrice1, belowPrice1Volume);
                    valueArea.put(belowPrice2, belowPrice2Volume);
                    runningVolume += belowPrice1Volume + belowPrice2Volume;
                }

                float valueAreaPercent = (float)runningVolume / totalVolume;
//                debug("running volume: " + runningVolume + " VA Percent: " + valueAreaPercent);
                if (valueAreaPercent > getSettings().getDouble("ValueAreaPercent")) break;
            }


        }
    }

    @Override
    protected void calculateValues(DataContext ctx) {
        super.calculateValues(ctx);
//        String session = getSettings().getInput("SessionInput").toString();
//        DataSeries series = ctx.getDataSeries();
//
//        Instrument instrument = series.getInstrument();
//
//        if (series.size() != 0) {
//            debug("DataSeries size is " + series.size());
//            SessionVolumePivot svp = calculateVolumesForSession(ctx, series, session);
//            debug("SessionVolumePivot: Session " + svp.getSession());
//            debug("SessionVolumePivot: Pivot" + svp.getPivot());
//            debug("SessionVolumePivot: High " + svp.getHigh());
//            debug("SessionVolumePivot: Low " + svp.getLow());
//            debug("SessionVolumePivot: Breadth " + svp.getBreadth());
//            //debug("SessionVolumePivot: Volume" + svp.getVolume());
//            debug("SessionVolumePivot: DataSeries Bar Index " + svp.getBarIndex());
//            clearFigures();
//
//            boolean showLines = getSettings().getBoolean("HighlightBarsLines");
//            // if current session only show lines if enab led
//            if (instrument.isInsideTradingHours(LocalDateTime.now().toEpochSecond(ZoneOffset.ofHours(-4)), true) && showLines) {
//                addFiguresForSessionDeltaPivot(svp, ctx.getDefaults());
//            } else if (!instrument.isInsideTradingHours(LocalDateTime.now().toEpochSecond(ZoneOffset.ofHours(-4)), true)) {
//                addFiguresForSessionDeltaPivot(svp, ctx.getDefaults());
//            }
//        } else {
//            debug("DataSeries size is 0, skipping SVP calculation");
//        }
//
//        super.calculateValues(ctx);


        long last = ctx.getDataSeries().getVisibleEndTime();
        for (Line line : lines) {
            line.setEnd(last, line.getEndValue());
        }
    }

    /**
     * Helper class for buidling lines to draw on the chart
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

    // Custom class to override the layout to position text where we want it
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