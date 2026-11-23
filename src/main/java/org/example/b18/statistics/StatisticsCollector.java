package org.example.b18.statistics;

import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriSim;

import org.example.b18.model.LoadedPetriNet;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads the final marking and time-integrated statistics from the PetriObj simulation
 * and formats B18 counters and performance metrics.
 */
public final class StatisticsCollector {

    // Початкова кількість автомобілів у банку (Q1=2, Q2=2, I1=1, I2=1)
    private static final int INITIAL_CUSTOMERS_IN_BANK = 6;

    private StatisticsCollector() {
    }

    public static SimulationResult collect(
            LoadedPetriNet loaded,
            PetriSim simulation,
            double simulationTime
    ) {
        return collect(loaded, simulation, simulationTime, Collections.emptyMap());
    }

    public static SimulationResult collect(
            LoadedPetriNet loaded,
            PetriSim simulation,
            double simulationTime,
            Map<String, Double> timeIntegrals
    ) {
        Map<String, Integer> result = new LinkedHashMap<>();
        Map<String, Double> averages = new LinkedHashMap<>();

        for (Map.Entry<String, Integer> entry :
                loaded.getPlaceIndexes().entrySet()) {

            String code = entry.getKey();
            int index = entry.getValue();
            PetriP place = simulation.getNet().getListP()[index];

            result.put(code, place.getMark());

            double integral = timeIntegrals.getOrDefault(code, 0.0);
            averages.put(code, simulationTime > 0.0 ? integral / simulationTime : 0.0);
        }

        return new SimulationResult(
                loaded.getSourceName(),
                simulationTime,
                result,
                averages
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

        printCalculatedStatistics(result);
    }

    private static void printCalculatedStatistics(SimulationResult result) {
        double tMod = result.getSimulationTime();
        boolean isModified = result.has("QOUT") || result.has("X1") || result.has("E1");

        // 1. Середнє завантаження кожного касира:
        // Оскільки під час обслуговування маркер знаходиться всередині переходу (Tl або TE),
        // зайнятість касира визначається як 1 - D (коли D=0, касир зайнятий або заблокований).
        double util1Total = Math.max(0.0, 1.0 - result.getAverage("D1"));
        double util2Total = Math.max(0.0, 1.0 - result.getAverage("D2"));
        double util1Blocked = result.getAverage("E1");
        double util2Blocked = result.getAverage("E2");
        double util1Active = Math.max(0.0, util1Total - util1Blocked);
        double util2Active = Math.max(0.0, util2Total - util2Blocked);

        // 5. Середня кількість клієнтів у кожній черзі
        double avgQ1 = result.getAverage("Q1");
        double avgQ2 = result.getAverage("Q2");
        double avgQOut = result.getAverage("QOUT");

        // 2. Середня кількість клієнтів у банку (черги + клієнти біля вікон)
        double avgCustomersAtWindowsAndLanes = avgQ1 + avgQ2 + util1Total + util2Total;
        double avgCustomersTotal = avgCustomersAtWindowsAndLanes + avgQOut;

        // 3. Середній інтервал часу між від'їздами клієнтів від вікон
        int departuresFromWindows = isModified && result.has("X1") && result.has("X2")
                ? (result.get("X1") + result.get("X2"))
                : result.getTotalServed();
        double avgDepartureInterval = departuresFromWindows > 0
                ? tMod / departuresFromWindows
                : Double.POSITIVE_INFINITY;

        // 4. Середній час перебування клієнта в банку (за законом Літтла)
        double avgTimeInBank = departuresFromWindows > 0
                ? (avgCustomersTotal * tMod) / departuresFromWindows
                : 0.0;

        // 6. Відсоток клієнтів, яким відмовлено в обслуговуванні
        // На момент завершення моделі у кожному вікні, де D == 0, обслуговується 1 клієнт
        int busyWindowsAtEnd = (result.get("D1") == 0 ? 1 : 0) + (result.get("D2") == 0 ? 1 : 0);
        int finalInSystem = result.get("Q1") + result.get("Q2")
                + busyWindowsAtEnd
                + (result.has("QOUT") ? result.get("QOUT") : 0)
                + (result.has("PA") ? result.get("PA") : 0);

        int lostCustomers = result.get("L");
        int servedOrExited = isModified && result.has("NOUT")
                ? result.get("NOUT")
                : result.getTotalServed();
        int totalArrived = Math.max(lostCustomers, servedOrExited + finalInSystem + lostCustomers - INITIAL_CUSTOMERS_IN_BANK);
        double rejectionRatePercent = totalArrived > 0
                ? (100.0 * lostCustomers) / totalArrived
                : 0.0;

        // 7. Кількість змін під'їзних смуг
        int laneChanges = result.get("J");

        System.out.println("Статистичні характеристики (за умовою завдання В18):");
        System.out.println("----------------------------------------------------");
        if (isModified) {
            System.out.printf("1. Середнє завантаження касира 1: %.4f (обслуг.: %.4f, очік. виїзду: %.4f)%n",
                    util1Total, util1Active, util1Blocked);
            System.out.printf("   Середнє завантаження касира 2: %.4f (обслуг.: %.4f, очік. виїзду: %.4f)%n",
                    util2Total, util2Active, util2Blocked);
            System.out.printf("2. Середня кількість клієнтів у банку: %.4f (смуги+вікна: %.4f, буфер виїзду: %.4f)%n",
                    avgCustomersTotal, avgCustomersAtWindowsAndLanes, avgQOut);
        } else {
            System.out.printf("1. Середнє завантаження касира 1: %.4f%n", util1Total);
            System.out.printf("   Середнє завантаження касира 2: %.4f%n", util2Total);
            System.out.printf("2. Середня кількість клієнтів у банку: %.4f%n", avgCustomersTotal);
        }

        System.out.printf("3. Середній інтервал часу між від'їздами від вікон: %.4f%n", avgDepartureInterval);
        if (isModified && result.has("NOUT") && result.get("NOUT") > 0) {
            System.out.printf("   Середній інтервал виїзду на вулицю: %.4f%n", tMod / result.get("NOUT"));
        }

        System.out.printf("4. Середній час перебування клієнта в банку: %.4f%n", avgTimeInBank);
        System.out.printf("5. Середня кількість клієнтів у черзі 1: %.4f%n", avgQ1);
        System.out.printf("   Середня кількість клієнтів у черзі 2: %.4f%n", avgQ2);
        if (isModified) {
            System.out.printf("   Середня кількість клієнтів у черзі на виїзд (QOUT): %.4f%n", avgQOut);
        }

        System.out.printf("6. Відсоток клієнтів, яким відмовлено: %.2f%% (%d з %d прибулих)%n",
                rejectionRatePercent, lostCustomers, totalArrived);
        System.out.printf("7. Кількість змін під'їзних смуг: %d%n", laneChanges);
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

//    допоміжний метод верифікації
    public static SimulationMetrics calculateMetrics(SimulationResult result) {
        return calculateMetrics(result, INITIAL_CUSTOMERS_IN_BANK);
    }

    public static SimulationMetrics calculateMetrics(SimulationResult result, int initialCustomers) {
        double simTime = result.getSimulationTime();
        boolean isModified = result.has("QOUT");

        double util1Total = Math.max(0.0, 1.0 - result.getAverage("D1"));
        double util2Total = Math.max(0.0, 1.0 - result.getAverage("D2"));

        double util1Blocked = isModified && result.has("E1") ? result.getAverage("E1") : 0.0;
        double util2Blocked = isModified && result.has("E2") ? result.getAverage("E2") : 0.0;
        double util1Active = Math.max(0.0, util1Total - util1Blocked);
        double util2Active = Math.max(0.0, util2Total - util2Blocked);

        double avgQ1 = result.getAverage("Q1");
        double avgQ2 = result.getAverage("Q2");
        double avgQOut = isModified ? result.getAverage("QOUT") : 0.0;

        double avgCustomers = avgQ1 + avgQ2 + util1Total + util2Total + avgQOut;

        int servedFromWindows = isModified
                ? (result.get("X1") + result.get("X2"))
                : (result.get("N1") + result.get("N2"));
        int exitedToStreet = isModified ? result.get("NOUT") : servedFromWindows;

        double avgDepartureInterval = servedFromWindows > 0 ? simTime / servedFromWindows : Double.NaN;
        double avgTimeInBank = servedFromWindows > 0 ? (avgCustomers * simTime) / servedFromWindows : Double.NaN;

        int lostCustomers = result.get("L");
        int busyWindowsAtEnd = (result.get("D1") == 0 ? 1 : 0) + (result.get("D2") == 0 ? 1 : 0);
        int finalInSystem = result.get("Q1") + result.get("Q2")
                + busyWindowsAtEnd
                + (isModified ? result.get("QOUT") : 0)
                + (result.has("PA") ? result.get("PA") : 0);

        int totalArrived = Math.max(
                lostCustomers,
                exitedToStreet + finalInSystem + lostCustomers - initialCustomers
        );
        double rejectionRatePercent = totalArrived > 0 ? (100.0 * lostCustomers) / totalArrived : 0.0;
        int laneChanges = result.get("J");

        return new SimulationMetrics(
                util1Total, util2Total,
                util1Active, util1Blocked,
                util2Active, util2Blocked,
                avgCustomers, avgDepartureInterval, avgTimeInBank,
                avgQ1, avgQ2, avgQOut,
                rejectionRatePercent, laneChanges,
                servedFromWindows, exitedToStreet
        );
    }
}