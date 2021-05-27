package com.tystr;

import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.motivewave.platform.sdk.common.*;
import com.motivewave.platform.sdk.common.desc.BooleanDescriptor;
import com.motivewave.platform.sdk.common.desc.DoubleDescriptor;
import com.motivewave.platform.sdk.common.desc.EnabledDependency;
import com.motivewave.platform.sdk.common.desc.FontDescriptor;
import com.motivewave.platform.sdk.common.desc.InputDescriptor;
import com.motivewave.platform.sdk.common.desc.IntegerDescriptor;
import com.motivewave.platform.sdk.common.desc.PathDescriptor;
import com.motivewave.platform.sdk.draw.*;
import com.motivewave.platform.sdk.study.Plot;
import com.motivewave.platform.sdk.study.Study;
import com.motivewave.platform.sdk.study.StudyHeader;

/** Zig Zag */
@StudyHeader(
        namespace="com.tystr",
        id="TYSTR_RETRACE_ZONES",
        //rb="com.motivewave.platform.study.nls.strings",
        name="Tystr's Retrace Zones",
        desc="Plots retrace zones",
        menu="Menu overly",
        overlay=true,
        studyOverlay=false,
        underlayByDefault = true,
        supportsBarUpdates=false
        // helpLink="http://www.motivewave.com/studies/zig_zag.htm"
        )
public class RetraceZones extends Study
{
    final static String HIGH_INPUT = "highInput", LOW_INPUT = "lowInput", REVERSAL = "reversal", REVERSAL_TICKS = "reversalTicks", USE_TICKS="useTicks";
    final static String PRICE_MOVEMENTS = "priceMovements", PRICE_LABELS = "priceLabels", RETRACE_LINE = "retraceLine";

    @Override
    public void initialize(Defaults defaults)
    {
        var sd = createSD();
        var tab = sd.addTab(get("TAB_GENERAL"));

        var inputs = tab.addGroup(get("LBL_INPUTS"));
        inputs.addRow(new InputDescriptor(HIGH_INPUT, get("LBL_HIGH_INPUT"), Enums.BarInput.MIDPOINT));
        inputs.addRow(new InputDescriptor(LOW_INPUT, get("LBL_LOW_INPUT"), Enums.BarInput.MIDPOINT));
        inputs.addRow(new IntegerDescriptor(REVERSAL_TICKS, get("LBL_REVERSAL_TICKS"), 10, 1, 99999, 1), new BooleanDescriptor(USE_TICKS, get("LBL_ENABLED"), false, false));
        inputs.addRow(new DoubleDescriptor(REVERSAL, get("LBL_REVERSAL"), 1.0, 0.0001, 99.999, 0.0001));
        inputs.addRow(new BooleanDescriptor(PRICE_MOVEMENTS, get("LBL_PRICE_MOVEMENTS"), true));
        inputs.addRow(new BooleanDescriptor(PRICE_LABELS, get("LBL_PRICE_LABELS"), true));
        inputs.addRow(new FontDescriptor(Inputs.FONT, get("LBL_FONT"), defaults.getFont()));

        var colors = tab.addGroup(get("LBL_COLORS"));
        colors.addRow(new PathDescriptor(Inputs.PATH, get("LBL_LINE"), defaults.getLineColor(), 1.0f, null, true, false, false));
        colors.addRow(new PathDescriptor(RETRACE_LINE, get("LBL_RETRACE_LINE"), defaults.getLineColor(), 1.0f, new float[] {3f, 3f}, true, false, true));

        // Quick Settings (Tool Bar and Popup Editor)
        sd.addQuickSettings(HIGH_INPUT, LOW_INPUT, REVERSAL_TICKS, USE_TICKS, REVERSAL, PRICE_MOVEMENTS, PRICE_LABELS, Inputs.FONT, Inputs.PATH, RETRACE_LINE);
        sd.rowAlign(REVERSAL_TICKS, USE_TICKS);

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
        boolean priceLabels = s.getBoolean(PRICE_LABELS, true);
        var line = s.getPath(Inputs.PATH);
        var retraceLine = s.getPath(RETRACE_LINE);
        var defaults = ctx.getDefaults();
        var fi = s.getFont(Inputs.FONT);
        Font f = fi == null ? defaults.getFont() : fi.getFont();
        Color bgColor = defaults.getBackgroundColor();
        Color txtColor = line.getColor();

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
        Map<Integer, Integer> pivotBarIndexes = new HashMap<>();
        List<Double> pivotsList = new ArrayList<>();
        for(int i = pivotBar+1; i < series.size(); i++) {
            if (!series.isBarComplete(i)) break;
            double high = series.getDouble(i, highInput);
            double low = series.getDouble(i, lowInput);
            if (up) {
                if (drawReoffer && series.getLow(i) < lastPivotLow) {
                    debug("LOW < PIVOT");
                    double zoneHigh = series.getLow(i) + ((lastPivotHigh - series.getLow(i)) * 0.618);
                    double zoneLow = series.getLow(i) + ((lastPivotHigh - series.getLow(i)) * 0.382);//0.5);
                    debug("lastPivotHigh: " + lastPivotHigh);
                    debug("lastPivotLow: " + lastPivotLow);
                    debug("Drawing reoffer zone from " + zoneHigh + " to " + zoneLow);
                    Box box = new Box(series.getStartTime(i), zoneHigh, series.getEndTime(series.size()-1), zoneLow);
                    box.setFillColor(Color.RED.darker().darker().darker());
                    box.setLineColor(Color.RED.darker().darker().darker());

                    reofferZones.add(box);
                    addFigure(Plot.PRICE, box);
                    drawReoffer = false;
                }
                if (useTicks ? high - pivot >= tickAmount : (1.0-reversal)*high >= pivot) {
                    // confirmed previous low
                    points.add(new Coordinate(series.getStartTime(pivotBar), series.getLow(pivotBar)));
//                    Marker arrow = new Marker(new Coordinate(series.getStartTime(pivotBar), series.getHigh(pivotBar) - 2), Enums.MarkerType.CIRCLE);
//                    arrow.setSize(Enums.Size.LARGE);
//                    arrow.setFillColor(Color.BLUE);
//                    addFigure(Plot.PRICE, arrow);
                    lastPivotLow = series.getLow(pivotBar);

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
                if (drawRebid && series.getHigh(i) > lastPivotHigh)  {
                    debug("HIGH > PIVOT");
                    // calculate rebid zone
                    double zoneHigh = series.getHigh(i)- ((series.getHigh(i) - lastPivotLow) * 0.618);
                    double zoneLow = series.getHigh(i) - ((series.getHigh(i) - lastPivotLow) * 0.382); //0.5);
                    debug("lastPivotHigh: " + lastPivotHigh);
                    debug("lastPivotLow: " + lastPivotLow);
                    debug("Drawing rebid zone from " + zoneLow + " to " + zoneHigh);
                    Box box = new Box(series.getStartTime(i), zoneLow, series.getEndTime(series.size()-1), zoneHigh);

                    box.setFillColor(Color.BLUE.darker());
                    box.setLineColor(Color.BLUE.darker());

                    rebidZones.add(box);
                    addFigure(Plot.PRICE, box);

//                    Marker arrow = new Marker(new Coordinate(series.getStartTime(pivotBar), series.getHigh(pivotBar) + 2), Enums.MarkerType.CIRCLE);
//                    arrow.setSize(Enums.Size.LARGE);
//                    arrow.setOutlineColor(Color.GREEN);
//                    arrow.setFillColor(Color.GREEN);
//                    addFigure(Plot.PRICE, arrow);
                    drawRebid = false;
                }
                if (useTicks ? pivot - low >= tickAmount : (1.0+reversal)*low <= pivot) {
                    // confirmed previous max
                    points.add(new Coordinate(series.getStartTime(pivotBar), series.getHigh(pivotBar)));
//                    Marker arrow = new Marker(new Coordinate(series.getStartTime(pivotBar), series.getHigh(pivotBar) + 2), Enums.MarkerType.CIRCLE);
//                    arrow.setSize(Enums.Size.LARGE);
//                    arrow.setFillColor(Color.RED);
//                    addFigure(Plot.PRICE, arrow);
                    lastPivotHigh = series.getHigh(pivotBar);
                    debug("Setting last Pivot High: " + series.getHigh(pivotBar));
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
                addFigure(Plot.PRICE, l);
            }
//            if (priceLabels) {
//                Label lbl = new Label(instr.format(c.getValue()), f, txtColor, bgColor);
//                lbl.setLocation(c);
//                addFigure(lbl);
//            }
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
                addFigure(Plot.PRICE, l);
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

//    @Override
//    public void onBarClose(DataContext ctx) {
//        debug("BAR CLOSED");
//        DataSeries series = ctx.getDataSeries();
//
//            for (Box box : rebidZones) { //                debug("invalidating: Box Start Value: " + box.getStartValue());
//                debug("invalidating: series.getLow(i): " + series.getLow(series.size()));
//                if (series.getLow(series.size()) < box.getStartValue()) {
//                    debug("removing box at " + box.getStartValue() + " - " + box.getEndValue());
//                    removeFigure(Plot.PRICE, box);
//                } else {
//                    // extend box to current candle
//                    box.setEnd(series.getEndTime(series.size()-1), box.getEndValue());
//                }
//            }
//
//
//    }


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
