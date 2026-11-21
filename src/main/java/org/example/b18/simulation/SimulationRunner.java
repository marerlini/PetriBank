package org.example.b18.simulation;

import ua.stetsenkoinna.petriobj.PetriObjModel;
import ua.stetsenkoinna.petriobj.PetriSim;

import org.example.b18.model.LoadedPetriNet;
import org.example.b18.statistics.SimulationResult;
import org.example.b18.statistics.StatisticsCollector;

import java.util.ArrayList;

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

        PetriSim petriObject = new PetriSim(loaded.getNet());
        ArrayList<PetriSim> objects = new ArrayList<>();
        objects.add(petriObject);

        PetriObjModel model = new PetriObjModel(objects);
        model.go(simulationTime);

        return StatisticsCollector.collect(loaded, petriObject, simulationTime);
    }
}
