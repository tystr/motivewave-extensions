package com.tystr;

import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.motivewave.platform.sdk.common.*;
import com.motivewave.platform.sdk.common.desc.*;
import com.motivewave.platform.sdk.draw.*;
import com.motivewave.platform.sdk.study.Plot;
import com.motivewave.platform.sdk.study.Study;
import com.motivewave.platform.sdk.study.StudyHeader;

@StudyHeader(
        namespace="com.tystr",
        id="TYSTR_RETRACE_ZONES",
        name="Tystr's Retrace Zones",
        desc="Plots retrace zones",
        menu="Tystr's Studies",
        overlay=true,
        studyOverlay=false,
        underlayByDefault = true,
        supportsBarUpdates=false
        // helpLink="http://example.com
        )
public class RetraceZones extends Study
{
    final static String HIGH_INPUT = "highInput", LOW_INPUT = "lowInput", REVERSAL = "reversal", REVERSAL_TICKS = "reversalTicks", USE_TICKS="useTicks";
    final static String PRICE_MOVEMENTS = "priceMovements", PRICE_LABELS = "priceLabels", RETRACE_LINE = "retraceLine";
    final static String REBID_COLOR = "RebidColor", REOFFER_COLOR = "ReofferColor", METHOD_INPUT = "MethodInput", METHOD_1 = "Method1", METHOD_2 = "Method2";

    @Override
    public void initialize(Defaults defaults)
    {
        var sd = createSD();
        var tab = sd.addTab(get("General"));

        var inputs = tab.addGroup(get("Settings"));
        inputs.addRow(new InputDescriptor(HIGH_INPUT, get("High Input"), Enums.BarInput.HIGH));
        inputs.addRow(new InputDescriptor(LOW_INPUT, get("Low Input"), Enums.BarInput.LOW));
        inputs.addRow(new IntegerDescriptor(REVERSAL_TICKS, get("Reversal Ticks"), 45, 1, 99999, 1), new BooleanDescriptor(USE_TICKS, get("Enabled"), true, false));
        inputs.addRow(new DoubleDescriptor(REVERSAL, get("Reversal %"), 1.0, 0.0001, 99.999, 0.0001));
//        inputs.addRow(new BooleanDescriptor(PRICE_MOVEMENTS, get("Price Movements"), true));
//        inputs.addRow(new BooleanDescriptor(PRICE_LABELS, get("Price Labels"), true));
//        inputs.addRow(new FontDescriptor(Inputs.FONT, get("Font"), defaults.getFont()));

        inputs.addRow(new InputDescriptor(METHOD_INPUT, "Method",new String[]{METHOD_1 , METHOD_2}, METHOD_1));

        var colors = tab.addGroup(get("Colors"));
//        colors.addRow(new PathDescriptor(Inputs.PATH, get("Line"), defaults.getLineColor(), 1.0f, null, true, false, false));
        //colors.addRow(new PathDescriptor(RETRACE_LINE, get("Retrace Line"), defaults.getLineColor(), 1.0f, new float[] {3f, 3f}, true, false, true));
        colors.addRow(new ColorDescriptor(REBID_COLOR, get("Rebid Zone"), Color.BLUE.darker()));
        colors.addRow(new ColorDescriptor(REOFFER_COLOR, get("Reoffer Zone"), Color.RED.darker().darker().darker()));

        // Quick Settings (Tool Bar and Popup Editor)
        //sd.addQuickSettings(HIGH_INPUT, LOW_INPUT, REVERSAL_TICKS, USE_TICKS, REVERSAL, PRICE_MOVEMENTS, PRICE_LABELS, Inputs.FONT, Inputs.PATH, RETRACE_LINE);
        sd.addQuickSettings(REBID_COLOR, REOFFER_COLOR, REVERSAL_TICKS, METHOD_INPUT);
        //sd.rowAlign(REBID_COLOR, REOFFER_COLOR, REVERSAL_TICKS);

        sd.addDependency(new EnabledDependency(USE_TICKS, REVERSAL_TICKS));
        sd.addDependency(new EnabledDependency(false, USE_TICKS, REVERSAL));

        var desc = createRD();
        desc.setLabelSettings(HIGH_INPUT, LOW_INPUT, REVERSAL);
        setMinBars(200);
    }

    @Override
    public void clearState()
    {
        super.clearState();
        pivotBar = -1;
        pivot = 0;
        unconfirmed.clear();
        prev = prev2 = null;
    }

    @Override
    protected void calculateValues(DataContext ctx)
    {
        var s = getSettings();
        Object highInput = s.getInput(HIGH_INPUT);
        Object lowInput = s.getInput(LOW_INPUT);
        double reversal = s.getDouble(REVERSAL, 1.0)/100.0;
        int reversalTicks = s.getInteger(REVERSAL_TICKS, 10);
        boolean useTicks = s.getBoolean(USE_TICKS, false);
        boolean movements = s.getBoolean(PRICE_MOVEMENTS, true);
        boolean priceLabels = s.getBoolean(PRICE_LABELS, false);
//        boolean priceLabels = s.getBoolean(PRICE_LABELS, true);
        var line = s.getPath(Inputs.PATH);
        var retraceLine = s.getPath(RETRACE_LINE);
        var defaults = ctx.getDefaults();
        var fi = s.getFont(Inputs.FONT);
        Font f = fi == null ? defaults.getFont() : fi.getFont();
        Color bgColor = defaults.getBackgroundColor();
        Color txtColor = Color.ORANGE; //line.getColor();
        String method = s.getInput(METHOD_INPUT).toString();
        Color rebidColor = s.getColor(REBID_COLOR);
        Color reofferColor = s.getColor(REOFFER_COLOR);
        debug("Using method " + method + ".");

        var series = ctx.getDataSeries();
        var instr = ctx.getInstrument();
        double tickAmount = reversalTicks * instr.getTickSize();

        if (pivotBar < 0) {
            // Initialize
            double high = series.getDouble(0, highInput);
            double low = series.getDouble(0, lowInput);
            double val = series.getDouble(1, highInput);
            if (val > high) up = true;
            else up = false;
            pivotBar = 0;
            pivot = up ? low : high;
        }

        List<Coordinate> points = new ArrayList();
        for(int i = pivotBar+1; i < series.size(); i++) {
            if (!series.isBarComplete(i)) break;
            double high = series.getDouble(i, highInput);
            double low = series.getDouble(i, lowInput);
            if (up) {
                if (method.equals(METHOD_2) && drawReoffer && series.getLow(i) < lastPivotLow) {
                    // Method 2 Peak to Breaking candle
//                    debug("LOW < PIVOT");
                    double zoneHigh = series.getLow(i) + ((lastPivotHigh - series.getLow(i)) * 0.70); //0.618);
                    double zoneLow = series.getLow(i) + ((lastPivotHigh - series.getLow(i)) * 0.50); //0.382);//0.5);
//                    debug("lastPivotHigh: " + lastPivotHigh);
//                    debug("lastPivotLow: " + lastPivotLow);
                    debug("Drawing reoffer zone from " + zoneHigh + " to " + zoneLow +", calculated using swing high of " + lastPivotHigh + " and swing low of " + lastPivotLow);
                    Box box = new Box(series.getStartTime(i), zoneHigh, series.getEndTime(series.size()-1), zoneLow);
                    box.setFillColor(reofferColor);
                    box.setLineColor(reofferColor);

//                    Label label = new Label(new Coordinate(series.getStartTime(i), zoneHigh+4), "LastPivotHigh: " + lastPivotHigh + ", lastPivotLow " + lastPivotLow);
//                    addFigure(Plot.PRICE, label);

                    reofferZones.add(box);
                    addFigure(Plot.PRICE, box);
                    drawReoffer = false;
                }
                if (useTicks ? high - pivot >= tickAmount : (1.0-reversal)*high >= pivot) {
                    // confirmed previous low
                    points.add(new Coordinate(series.getStartTime(pivotBar), series.getLow(pivotBar)));
                    if (method.equals(METHOD_1)) {
                         // Method 1 Peak to Peak
                        lastPivotLow = series.getLow(pivotBar);
                        double zoneHigh = lastPivotLow + ((lastPivotHigh - lastPivotLow) * 0.70); //0.618);
                        double zoneLow = lastPivotLow + ((lastPivotHigh - lastPivotLow) * 0.50); //0.382);//0.5);
//                        debug("lastPivotHigh: " + lastPivotHigh);
//                        debug("lastPivotLow: " + lastPivotLow);
                        debug("Drawing reoffer zone from " + zoneHigh + " to " + zoneLow+", calculated using swing high of " + lastPivotHigh + " and swing low of " + lastPivotLow);
                        Box box = new Box(series.getStartTime(i), zoneHigh, series.getEndTime(series.size()-1), zoneLow);
                        box.setFillColor(reofferColor);
                        box.setLineColor(reofferColor);
//                        Label label = new Label(new Coordinate(series.getStartTime(i), zoneHigh+4), "LastPivotHigh: " + lastPivotHigh + ", lastPivotLow " + lastPivotLow);
//                        addFigure(Plot.PRICE, label);

                        reofferZones.add(box);
                        addFigure(Plot.PRICE, box);
                        drawReoffer = false;
                    }



                    pivot = high;
                    pivotBar = i;
                    up=false;
                    drawReoffer = true;
                }
                else if (low < pivot) {
                    pivot = low;
                    pivotBar = i;
                }
            }
            else {
                if (method.equals(METHOD_2) && drawRebid && series.getHigh(i) > lastPivotHigh)  {
                    //debug("HIGH > PIVOT");
                    // calculate rebid zone
                    double zoneHigh = series.getHigh(i)- ((series.getHigh(i) - lastPivotLow) * 0.70); //0.618);
                    double zoneLow = series.getHigh(i) - ((series.getHigh(i) - lastPivotLow) * 0.50); //0.382); //0.5);
//                    debug("lastPivotHigh: " + lastPivotHigh);
//                    debug("lastPivotLow: " + lastPivotLow);
                    debug("Drawing rebid zone from " + zoneLow + " to " + zoneHigh + ", calculated using swing high of " + lastPivotHigh + " and swing low of " + lastPivotLow);
                    Box box = new Box(series.getStartTime(i), zoneLow, series.getEndTime(series.size()-1), zoneHigh);

                    box.setFillColor(rebidColor);
                    box.setLineColor(rebidColor);
//
//                    Label label = new Label(new Coordinate(series.getStartTime(i), zoneHigh+4), "LastPivotHigh: " + lastPivotHigh + ", lastPivotLow " + lastPivotLow);
//                    addFigure(Plot.PRICE, label);


                    rebidZones.add(box);
                    addFigure(Plot.PRICE, box);

                    drawRebid = false;
                }
                if (useTicks ? pivot - low >= tickAmount : (1.0+reversal)*low <= pivot) {
                    // confirmed previous max
                    points.add(new Coordinate(series.getStartTime(pivotBar), series.getHigh(pivotBar)));

                    if (method.equals(METHOD_1)) {
                        lastPivotHigh = series.getHigh(pivotBar);
                        double zoneHigh = lastPivotHigh - ((lastPivotHigh - lastPivotLow) * 0.70);
                        double zoneLow = lastPivotHigh - ((lastPivotHigh - lastPivotLow) * 0.50); //0.5);
//                        debug("lastPivotHigh: " + lastPivotHigh);
//                        debug("lastPivotLow: " + lastPivotLow);
                        debug("Drawing rebid zone from " + zoneLow + " to " + zoneHigh + ", calculated using swing high of " + lastPivotHigh + " and swing low of " + lastPivotLow);
                        Box box = new Box(series.getStartTime(i), zoneLow, series.getEndTime(series.size()-1), zoneHigh);

//                        Label label = new Label(new Coordinate(series.getStartTime(i), zoneHigh+4), "LastPivotHigh: " + lastPivotHigh + ", lastPivotLow " + lastPivotLow);
//                        addFigure(Plot.PRICE, label);

                        box.setFillColor(rebidColor);
                        box.setLineColor(rebidColor);

                        rebidZones.add(box);
                        addFigure(Plot.PRICE, box);
                    }

//                    debug("Setting last Pivot High: " + series.getHigh(pivotBar));
                     drawRebid = true;

                    pivot = low;
                    pivotBar = i;
                    up=true;
                }
                else if (high > pivot) {
                    //drawRebid = true;
                    pivot = high;
                    pivotBar = i;
                }
            }

            // remove invalidated zones

            for (Box box : rebidZones) {
                if (series.getClose(i) < box.getEndValue()) {
//                    debug("invalidating Rebid Zone: Box Start Value: " + box.getStartValue());
//                    debug("invalidating Rebid Zone: series.getLow(i): " + series.getLow(i));
//                    debug("removing rebid zone at " + box.getStartValue() + " - " + box.getEndValue());
                    removeFigure(Plot.PRICE, box);
                } else {
                    // extend box to current candle
                    box.setEnd(series.getEndTime(series.size()-1), box.getEndValue());
                }
            }
            for (Box box : reofferZones) {
                if (series.getClose(i) > box.getStartValue()) {
//                    debug("invalidating Reoffer Zone: Box Start Value: " + box.getStartValue());
//                    debug("invalidating Reoffer Zone: series.getLow(i): " + series.getLow(i));
//                    debug("removing reoffer zone at " + box.getStartValue() + " - " + box.getEndValue());
                    removeFigure(Plot.PRICE, box);
                } else {
                    // extend box to current candle
                    box.setEnd(series.getEndTime(series.size()-1), box.getEndValue());
                }
            }
        }

        // Build the ZigZag lines
        // For efficiency reasons, only build the delta
        beginFigureUpdate();

        for(var c : points) {
            // Zig Zag Lines
            if (prev != null) {
//                var l = new Line(prev, c, line);
//                addFigure(Plot.PRICE, l);
//                if (movements) {
//                    l.setText(instr.format(Math.abs(c.getValue() - prev.getValue())), f);
//                    l.getText().setBackground(bgColor);
//                }
                // draw rebid/reoffer zone here
                double value = c.getValue();
                double prevValue = prev.getValue();
//                debug("value: " + value);
//                debug("prevValue: " + prevValue);
            }

            // Retracements
            if (retraceLine != null && retraceLine.isEnabled() && prev2 != null) {
                var l = new Line(prev2, c, retraceLine);
                double l1 = Math.abs(c.getValue() - prev.getValue());
                double l2 = Math.abs(prev2.getValue() - prev.getValue());
                double rt = l1/l2;
                l.setText(Util.round(rt*100, 1)+"%", f);
//                addFigure(Plot.PRICE, l);
            }
            if (priceLabels) {
                Label lbl = new Label(instr.format(c.getValue()), f, txtColor, bgColor);
                lbl.setLocation(c);
                addFigure(lbl);
            }
            prev2 = prev;
            prev = c;
        }


        var tmp = new ArrayList(unconfirmed);
        unconfirmed.clear();

        var last = new Coordinate(series.getStartTime(pivotBar), up ? series.getLow(pivotBar) : series.getHigh(pivotBar));
        if (!last.equals(prev)) {
            // TODO: this code should be refactored with the code above
            if (prev != null) {
                var l = new Line(prev, last, line);
                unconfirmed.add(l);
                //addFigure(Plot.PRICE, l);
                if (movements) {
                    l.setText(instr.format(Math.abs(last.getValue() - prev.getValue())), f);
                    l.getText().setBackground(bgColor);
                }
            }
            // Retracements
            if (retraceLine != null && retraceLine.isEnabled() && prev2 != null) {
                var l = new Line(prev2, last, retraceLine);
                double l1 = Math.abs(last.getValue() - prev.getValue());
                double l2 = Math.abs(prev2.getValue() - prev.getValue());
                double rt = l1/l2;
                l.setText(Util.round(rt*100, 1)+"%", f);
                unconfirmed.add(l);
//                addFigure(Plot.PRICE, l);
            }
//            if (priceLabels) {
//                Label lbl = new Label(instr.format(last.getValue()), f, txtColor, bgColor);
//                lbl.setLocation(last);
//                unconfirmed.add(lbl);
//                addFigure(lbl);
//            }
            removeFigures(tmp);
        }

        endFigureUpdate();
    }

    private static class Zone {
        private final double high;
        private final double low;
        private final Color color;
        public Zone(double high, double low, Color color) {
            if (high <= low) throw new IllegalArgumentException("high must be greater than low");
            this.high = high;
            this.low = low;
            this.color = color;
        }
        public double getHigh() {
            return high;
        }
        public double getLow() {
            return low;
        }
        public Color getColor() {
            return color;
        }

        public Box createBox(long startTime, long endTime) {
            Box box = new Box(startTime, low, endTime, high);
            box.setFillColor(color);
            box.setLineColor(color);
            return box;
        }
    }

    boolean drawRebid = true; // we need to draw the rebid
    boolean drawReoffer = true;


    List<Box> rebidZones = new ArrayList<>();
    List<Box> reofferZones = new ArrayList<>();

    double lastPivotHigh = 0;
    double lastPivotLow = 0;
    private double pivot=0;
    private int pivotBar = -1;
    private boolean up;
    private Coordinate prev = null, prev2 = null;
    private List<Figure> unconfirmed = new ArrayList<>();
}
