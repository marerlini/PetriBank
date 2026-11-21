package org.example.b18;

import org.example.b18.config.ModelParameters;
import org.example.b18.model.BasicModel;
import org.example.b18.model.LoadedPetriNet;
import org.example.b18.model.ModifiedModel;
import org.example.b18.simulation.SimulationRunner;
import org.example.b18.statistics.SimulationResult;
import org.example.b18.statistics.StatisticsCollector;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        String mode = args.length >= 1 ? args[0].trim().toLowerCase() : "modified";
        double simulationTime = ModelParameters.SIMULATION_TIME;

        if (args.length >= 2) {
            try {
                simulationTime = Double.parseDouble(args[1]);
            } catch (NumberFormatException ex) {
                System.err.println("Некоректний час моделювання: " + args[1]);
                System.err.println("Використання: Main [basic|modified] [simulationTime]");
                System.exit(2);
            }
        }

        try {
            LoadedPetriNet loaded;
            if ("basic".equals(mode) || "1".equals(mode)) {
                loaded = BasicModel.load();
            } else if ("modified".equals(mode) || "2".equals(mode)) {
                loaded = ModifiedModel.load();
            } else {
                System.err.println("Невідомий режим: " + mode);
                System.err.println("Використання: Main [basic|modified] [simulationTime]");
                System.exit(2);
                return;
            }

            SimulationResult result = SimulationRunner.run(loaded, simulationTime);
            StatisticsCollector.printReport(result);
        } catch (Exception ex) {
            System.err.println("Не вдалося виконати моделювання B18.");
            System.err.println("Причина: " + ex.getMessage());
            ex.printStackTrace();
            System.exit(1);
        }
    }
}
