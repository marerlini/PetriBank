
package org.example.b18.statistics;

import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriSim;

import org.example.b18.model.LoadedPetriNet;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads the final marking from the PetriObj simulation
 * and formats B18 counters.
 */
public final class StatisticsCollector {

    private StatisticsCollector() {
    }

    public static SimulationResult collect(
            LoadedPetriNet loaded,
            PetriSim simulation,
            double simulationTime
    ) {
        Map<String, Integer> result = new LinkedHashMap<>();

        for (Map.Entry<String, Integer> entry :
                loaded.getPlaceIndexes().entrySet()) {

            int index = entry.getValue();
            PetriP place = simulation.getNet().getListP()[index];

            result.put(entry.getKey(), place.getMark());
        }

        return new SimulationResult(
                loaded.getSourceName(),
                simulationTime,
                result
        );
    }

    public static void printReport(SimulationResult result) {
        System.out.println();
        System.out.println("====================================================");
        System.out.println("Результати моделювання: " + result.getModelName());
        System.out.printf("Час моделювання: %.3f%n",
                result.getSimulationTime());
        System.out.println("====================================================");

        // Кількість обслужених клієнтів
        printIfPresent(result, "N1", "Обслужено у віконці 1");
        printIfPresent(result, "N2", "Обслужено у віконці 2");

        if (result.has("N1") && result.has("N2")) {
            System.out.println(
                    "Загалом обслужено: " + result.getTotalServed()
            );
        }

        // Відмови та зміни смуги
        printIfPresent(result, "L", "Відмови");
        printIfPresent(result, "J", "Зміни під'їзної смуги");

        // Показники модифікованої моделі
        if (result.has("X1") || result.has("X2")) {
            printIfPresent(
                    result,
                    "X1",
                    "Залишили віконце 1 та перейшли до черги виїзду"
            );

            printIfPresent(
                    result,
                    "X2",
                    "Залишили віконце 2 та перейшли до черги виїзду"
            );

            if (result.has("X1") && result.has("X2")) {
                System.out.println(
                        "Загалом залишили віконця: "
                                + (result.get("X1") + result.get("X2"))
                );
            }
        }

        printIfPresent(result, "NOUT", "Виїхали на вулицю");
        printIfPresent(result, "NU", "Невикористані дорожні інтервали");

        System.out.println("====================================================");
    }

    private static void printIfPresent(
            SimulationResult result,
            String code,
            String label
    ) {
        if (result.has(code)) {
            System.out.println(label + ": " + result.get(code));
        }
    }
}
