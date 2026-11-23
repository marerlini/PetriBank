package org.example.b18.statistics;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Final marking snapshot and derived counters after one simulation run. */
public final class SimulationResult {
    private final String modelName;
    private final double simulationTime;
    private final Map<String, Integer> markings;
    private final Map<String, Double> timeAverages;

    public SimulationResult(String modelName, double simulationTime, Map<String, Integer> markings) {
        this(modelName, simulationTime, markings, Collections.emptyMap());
    }

    public SimulationResult(
            String modelName,
            double simulationTime,
            Map<String, Integer> markings,
            Map<String, Double> timeAverages
    ) {
        this.modelName = modelName;
        this.simulationTime = simulationTime;
        this.markings = Collections.unmodifiableMap(new LinkedHashMap<>(markings));
        this.timeAverages = Collections.unmodifiableMap(new LinkedHashMap<>(timeAverages));
    }

    public String getModelName() {
        return modelName;
    }

    public double getSimulationTime() {
        return simulationTime;
    }

    public Map<String, Integer> getMarkings() {
        return markings;
    }

    public Map<String, Double> getTimeAverages() {
        return timeAverages;
    }

    public int get(String placeCode) {
        Integer value = markings.get(placeCode);
        if (value == null) {
            throw new IllegalArgumentException("No final marking recorded for place: " + placeCode);
        }
        return value;
    }

    public boolean has(String placeCode) {
        return markings.containsKey(placeCode);
    }

    public double getAverage(String placeCode) {
        return timeAverages.getOrDefault(placeCode, 0.0);
    }

    public int getTotalServed() {
        return get("N1") + get("N2");
    }
}