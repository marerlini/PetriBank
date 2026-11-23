package org.example.b18.simulation;

import ua.stetsenkoinna.petriobj.PetriObjModel;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriSim;

import org.example.b18.model.LoadedPetriNet;
import org.example.b18.statistics.SimulationResult;
import org.example.b18.statistics.StatisticsCollector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** Runs one PNML network with the PetriObjModelPaint simulation engine. */
public final class SimulationRunner {
    private SimulationRunner() {
    }

    public static SimulationResult run(LoadedPetriNet loaded, double simulationTime) throws Exception {
        if (loaded == null) {
            throw new IllegalArgumentException("Loaded Petri net must not be null");
        }
        if (!Double.isFinite(simulationTime) || simulationTime <= 0.0) {
            throw new IllegalArgumentException("Simulation time must be a positive finite number");
        }

        final Map<String, Double> timeIntegrals = new LinkedHashMap<>();
        for (String code : loaded.getPlaceIndexes().keySet()) {
            timeIntegrals.put(code, 0.0);
        }

        PetriSim petriObject = new PetriSim(loaded.getNet()) {
            @Override
            public void doStatistics(double dt) {
                super.doStatistics(dt);
                if (dt > 0.0) {
                    PetriP[] listP = getNet().getListP();
                    for (Map.Entry<String, Integer> entry : loaded.getPlaceIndexes().entrySet()) {
                        String code = entry.getKey();
                        int mark = listP[entry.getValue()].getMark();
                        timeIntegrals.put(code, timeIntegrals.get(code) + mark * dt);
                    }
                }
            }
        };

        ArrayList<PetriSim> objects = new ArrayList<>();
        objects.add(petriObject);

        PetriObjModel model = new PetriObjModel(objects);
        model.go(simulationTime);

        return StatisticsCollector.collect(loaded, petriObject, simulationTime, timeIntegrals);
    }
}