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
 *
 * @author Tyler Stroud <tyler@tylerstroud.com>
 */
@StudyHeader(
        namespace = "com.tystr",
        id = "TYSTR_VOLUME_PIVOTS",
        name = "Session Volume Pivots",
        desc = "This study plots volume pivots and extensions. These pivots are drawn at the midpoint of the " +
                "value area of a volume profile calculated over a window of bars during the previous session." +
                "Extensions (measured moves) are plotted above and below.",
        overlay = true,
        requiresVolume = true,
        allowTickAggregate = true
)
public class VolumePivots extends com.motivewave.platform.sdk.study.Study {
    // don't do this - used to reset bar colors for developing delta
    private Defaults defaults;
    private ArrayList<Line> lines;

    private SortedMap<Float, Integer> volumeByPrice;
    private SortedMap<Float, Integer> valueArea;

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
        grp.addRow(new DoubleDescriptor("ValueAreaPercent", "Value Area", 70, 0, 100, 0.10));

        grp.addRow(new BooleanDescriptor("HighlightBars", "Highlight Bars", true));
        sd.addQuickSettings("SessionInput", "PivotLine", "HighExtensionLine", "LowExtensionLine", "ValueAreaPercent");

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
            windowEnd = windowStart.plusHours(3).plusMinutes(30);
        } else {
            currentSession = Sessions.GLOBEX;
            long startOfEveningSession = instrument.getStartOfEveningSession(series.getStartTime(index));
            windowStart = ZonedDateTime.ofInstant(Instant.ofEpochMilli(startOfEveningSession), ZoneId.of("UTC"))
                    .minusDays(1).plusHours(11);
            windowEnd = windowStart.plusHours(6);
        }

        if ((barStart1.isAfter(windowStart) || barStart1.isEqual(windowStart)) && barStart1.isBefore(windowEnd)) {
            isBarInsideWindow = true;
            if (getSettings().getBoolean("HighlightWindows", false)) {
                Marker square = new Marker(new Coordinate(series.getStartTime(index), series.getLow(index)-2), Enums.MarkerType.TRIANGLE);
                square.setSize(Enums.Size.MEDIUM);
                square.setFillColor(currentSession == Sessions.RTH ? Color.ORANGE : Color.MAGENTA);
                addFigure(Plot.PRICE, square);
            }

            // Calculate volume by price

            SortedMap<Float, Integer> barVolumeByPrice = new TreeMap<>();
            TickOperation t = new TickOperation() {
                @Override
                public void onTick(Tick tick) {
                    float price = tick.isAskTick() ? tick.getAskPrice() : tick.getBidPrice();
                    int volume = volumeByPrice.getOrDefault(price, 0);
                    volume += tick.getVolume();
                    volumeByPrice.put(price, volume);
                    barVolumeByPrice.put(price, volume);
                }
            };
            instrument.forEachTick(series.getStartTime(index), series.getEndTime(index), t);
            series.setValue(index, "VolumeByPrice", barVolumeByPrice);
            volumeByPriceByIndex.put(index, barVolumeByPrice);
            series.setComplete(index);

            // Calculate Value Area

            float interval = (float) instrument.getTickSize();
            float volumePOC = Collections.max(volumeByPrice.entrySet(), Map.Entry.comparingByValue()).getKey();
            int totalVolume = volumeByPrice.values().stream().mapToInt(i -> i).sum();
            int runningVolume = 0;

            valueArea = new TreeMap<>(); // Reset Value Area

            // Add volume POC to value area
            valueArea.put(volumePOC, volumeByPrice.get(volumePOC));

            float abovePrice1 = volumePOC + interval;
            float abovePrice2 = volumePOC + (interval * 2);
            float belowPrice1 = volumePOC; //- interval;
            float belowPrice2 = volumePOC; // - (interval * 2);
            boolean incrementAbove = false;
            int aboveIncrements = 0;
            int belowIncrements = -2;

            for (int i = 1; i <= volumeByPrice.size(); i++) {
                if (incrementAbove) {
                    aboveIncrements = aboveIncrements + 2;
                    abovePrice1 = volumePOC + (interval * (aboveIncrements + 1));
                    abovePrice2 = volumePOC + (interval * (aboveIncrements + 2));
                } else {
                    belowIncrements = belowIncrements + 2;
                    belowPrice1 = volumePOC - (interval * (belowIncrements + 1));
                    belowPrice2 = volumePOC - (interval * (belowIncrements + 2));
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

                float valueAreaPercent = (float) runningVolume / totalVolume;
                if (valueAreaPercent > (getSettings().getDouble("ValueAreaPercent")/100)) break;
            }
        } else {
            if (isBarInsideWindow) {
                // first bar outside of window - draw pivots

                // debug - print volume by price
                if (getSettings().getBoolean("ShowVolumeByPrice", false)) {
                    Marker arrow = new Marker(new Coordinate(series.getStartTime(index), series.getLow(index)-2), Enums.MarkerType.TRIANGLE);
                    arrow.setSize(Enums.Size.LARGE);
                    arrow.setFillColor(Color.RED);
                    StringBuilder text = new StringBuilder("\n");
                    for (Map.Entry<Float, Integer> entry : volumeByPrice.entrySet()) {
                        text.append(entry.getKey()).append(" ").append(entry.getValue()).append("\n");
                    }
                    arrow.setTextValue(text.toString());
                    addFigure(Plot.PRICE, arrow);
                }

                float vah = valueArea.lastKey();
                float val = valueArea.firstKey();
                float breadth = vah - val;
                float pivot = vah - ((vah - val) / 2);
                debug("----------");
                debug("currentSession: " + currentSession);
                debug("Using value area percent: " + getSettings().getDouble("ValueAreaPercent"));
                debug("Value Area Low: " + valueArea.firstKey());
                debug("Value Area High: " + valueArea.lastKey());
                debug("Value Area Mid (Pivot): " + pivot);

                // Draw lines
                Settings settings = getSettings();
                PathInfo svpPivotPathInfo = settings.getPath("PivotLine");
                PathInfo highExtensionPathInfo = settings.getPath("HighExtensionLine");
                PathInfo lowExtensionPathInfo = settings.getPath("LowExtensionLine");

                long lineStart = series.getStartTime(index); // this is first bar after window, use this as start
                long lineEnd;

                if (instrument.isInsideTradingHours(series.getStartTime(index), true)) {
                    lineEnd = instrument.getEndOfDay(series.getStartTime(index), true);
                } else {
                    lineEnd = instrument.getStartOfDay(series.getStartTime(index), true) + Util.MILLIS_IN_DAY;
                }

                Line svpLine = LineBuilder.create(lineStart, pivot, lineEnd)
                        .setColor(svpPivotPathInfo.getColor()).setFont(defaults.getFont()).setStroke(svpPivotPathInfo.getStroke())
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

                // re-initialize VBP and VA for next window calculation
                volumeByPrice = new TreeMap<>();
                valueArea = new TreeMap<>();
                isBarInsideWindow = false;
            }
        }
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