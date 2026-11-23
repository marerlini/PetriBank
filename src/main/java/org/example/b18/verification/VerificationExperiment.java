package org.example.b18.verification;

import org.example.b18.config.ModelParameters;
import org.example.b18.model.BasicModel;
import org.example.b18.model.LoadedPetriNet;
import org.example.b18.model.ModifiedModel;
import org.example.b18.simulation.SimulationRunner;
import org.example.b18.statistics.SimulationMetrics;
import org.example.b18.statistics.SimulationResult;
import org.example.b18.statistics.StatisticsCollector;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class VerificationExperiment {

    private static final class Scenario {
        final int runId;
        final String changedParam;
        final String paramValue;
        final int initialCustomers;
        final Consumer<LoadedPetriNet> modifier;

        Scenario(int runId, String changedParam, String paramValue,
                 int initialCustomers, Consumer<LoadedPetriNet> modifier) {
            this.runId = runId;
            this.changedParam = changedParam;
            this.paramValue = paramValue;
            this.initialCustomers = initialCustomers;
            this.modifier = modifier;
        }
    }

    private VerificationExperiment() {
    }

    public static void main(String[] args) throws Exception {
        double simulationTime = args.length > 0
                ? Double.parseDouble(args[0])
                : ModelParameters.SIMULATION_TIME;
        int replications = args.length > 1
                ? Integer.parseInt(args[1])
                : 10;

        runBasicVerification(simulationTime, replications);
        System.out.println();
        runModifiedVerification(simulationTime, replications);
    }

    public static void runBasicVerification(double simulationTime, int replications) throws Exception {
        List<Scenario> scenarios = List.of(
                new Scenario(1, "Базовий (еталон)", "Tarr=0.5; Tserv=0.8", 6, net -> {}),
                new Scenario(2, "T_arr (TG)", "0.25 хв (було 0.5)", 6,
                        net -> setTransitionDelay(net, "TG", 0.25)),
                new Scenario(3, "T_arr (TG)", "1.00 хв (було 0.5)", 6,
                        net -> setTransitionDelay(net, "TG", 1.00)),
                new Scenario(4, "T_serv (TE1, TE2)", "0.40 хв (було 0.8)", 6, net -> {
                    setTransitionDelay(net, "TE1", 0.40);
                    setTransitionDelay(net, "TE2", 0.40);
                }),
                new Scenario(5, "T_serv (TE1, TE2)", "1.60 хв (було 0.8)", 6, net -> {
                    setTransitionDelay(net, "TE1", 1.60);
                    setTransitionDelay(net, "TE2", 1.60);
                }),
                new Scenario(6, "T_serv1 (лише TE1)", "0.40 хв (TE2=0.8)", 6,
                        net -> setTransitionDelay(net, "TE1", 0.40)),
                new Scenario(7, "Початковий стан N0", "0 авто (порожній банк)", 0, net -> {
                    setPlaceMark(net, "Q1", 0);
                    setPlaceMark(net, "F1", 3);
                    setPlaceMark(net, "Q2", 0);
                    setPlaceMark(net, "F2", 3);
                    setPlaceMark(net, "I1", 0);
                    setPlaceMark(net, "D1", 1);
                    setPlaceMark(net, "I2", 0);
                    setPlaceMark(net, "D2", 1);
                })
        );

        System.out.printf(Locale.US,
                "=== ВЕРИФІКАЦІЯ БАЗОВОЇ МОДЕЛІ (T=%.1f хв, повторів=%d) ===%n",
                simulationTime, replications);
        executeAndReport(BasicModel::load, scenarios, simulationTime, replications,
                false, "verification_basic.csv");
    }

    public static void runModifiedVerification(double simulationTime, int replications) throws Exception {
        List<Scenario> scenarios = List.of(
                new Scenario(1, "Базовий (еталон)", "P(VG)=0.5; Kout=3", 6, net -> {}),
                new Scenario(2, "T_arr (TG)", "0.25 хв (було 0.5)", 6,
                        net -> setTransitionDelay(net, "TG", 0.25)),
                new Scenario(3, "T_serv (TE1, TE2)", "1.40 хв (було 0.8)", 6, net -> {
                    setTransitionDelay(net, "TE1", 1.40);
                    setTransitionDelay(net, "TE2", 1.40);
                }),
                new Scenario(4, "P(VG) / P(VB)", "0.20 / 0.80 (щільно)", 6, net -> {
                    setTransitionProbability(net, "VG", 0.20);
                    setTransitionProbability(net, "VB", 0.80);
                }),
                new Scenario(5, "P(VG) / P(VB)", "0.80 / 0.20 (вільно)", 6, net -> {
                    setTransitionProbability(net, "VG", 0.80);
                    setTransitionProbability(net, "VB", 0.20);
                }),
                new Scenario(6, "T_gap / T_no", "0.90 / 0.70 хв (x2)", 6, net -> {
                    setTransitionDelay(net, "TGAP", 0.90);
                    setTransitionDelay(net, "TNO", 0.70);
                }),
                new Scenario(7, "Буфер виїзду FOUT", "1 місце (було 3)", 6,
                        net -> setPlaceMark(net, "FOUT", 1))
        );

        System.out.printf(Locale.US,
                "=== ВЕРИФІКАЦІЯ МОДИФІКОВАНОЇ МОДЕЛІ (T=%.1f хв, повторів=%d) ===%n",
                simulationTime, replications);
        executeAndReport(ModifiedModel::load, scenarios, simulationTime, replications,
                true, "verification_modified.csv");
    }

    @FunctionalInterface
    private interface ModelSupplier {
        LoadedPetriNet get() throws Exception;
    }

    private static void executeAndReport(
            ModelSupplier modelSupplier,
            List<Scenario> scenarios,
            double simulationTime,
            int replications,
            boolean isModified,
            String csvFileName) throws Exception {

        if (!isModified) {
            System.out.printf("%-4s | %-20s | %-22s | %6s | %6s | %6s | %6s | %6s | %6s | %6s | %6s | %6s%n",
                    "Run", "Змінений параметр", "Значення",
                    "rho1", "rho2", "N_sys", "T_dep", "W_sys", "Q1", "Q2", "Prej%", "J");
        } else {
            System.out.printf("%-4s | %-20s | %-22s | %6s | %6s | %6s | %6s | %6s | %6s | %6s | %6s | %6s | %6s | %6s%n",
                    "Run", "Змінений параметр", "Значення",
                    "rho1", "rho2", "blk1", "blk2", "N_sys", "T_dep", "W_sys", "Q1", "Q2", "Qout", "Prej%");
        }

        try (PrintWriter csv = new PrintWriter(csvFileName, StandardCharsets.UTF_8)) {
            if (!isModified) {
                csv.println("Run;ChangedParam;Value;rho1;rho2;N_sys;T_dep;W_sys;Q1;Q2;Prej_pct;J");
            } else {
                csv.println("Run;ChangedParam;Value;rho1;rho2;rho1_blk;rho2_blk;N_sys;T_dep;W_sys;Q1;Q2;Qout;Prej_pct;J");
            }

            for (Scenario sc : scenarios) {
                SimulationMetrics avg = runAveragedScenario(modelSupplier, sc, simulationTime, replications);

                if (!isModified) {
                    System.out.printf(Locale.US,
                            "%-4d | %-20s | %-22s | %6.3f | %6.3f | %6.3f | %6.3f | %6.3f | %6.3f | %6.3f | %6.2f | %6.1f%n",
                            sc.runId, sc.changedParam, sc.paramValue,
                            avg.rho1, avg.rho2, avg.avgCustomers, avg.avgDepartureInterval,
                            avg.avgTimeInBank, avg.avgQ1, avg.avgQ2, avg.rejectionRatePercent, avg.laneChanges);

                    csv.printf(Locale.US,
                            "%d;%s;%s;%.4f;%.4f;%.4f;%.4f;%.4f;%.4f;%.4f;%.2f;%.1f%n",
                            sc.runId, sc.changedParam, sc.paramValue,
                            avg.rho1, avg.rho2, avg.avgCustomers, avg.avgDepartureInterval,
                            avg.avgTimeInBank, avg.avgQ1, avg.avgQ2, avg.rejectionRatePercent, avg.laneChanges);
                } else {
                    System.out.printf(Locale.US,
                            "%-4d | %-20s | %-22s | %6.3f | %6.3f | %6.3f | %6.3f | %6.3f | %6.3f | %6.3f | %6.3f | %6.3f | %6.3f | %6.2f%n",
                            sc.runId, sc.changedParam, sc.paramValue,
                            avg.rho1, avg.rho2, avg.rho1Blk, avg.rho2Blk,
                            avg.avgCustomers, avg.avgDepartureInterval, avg.avgTimeInBank,
                            avg.avgQ1, avg.avgQ2, avg.avgQOut, avg.rejectionRatePercent);

                    csv.printf(Locale.US,
                            "%d;%s;%s;%.4f;%.4f;%.4f;%.4f;%.4f;%.4f;%.4f;%.4f;%.4f;%.4f;%.2f;%.1f%n",
                            sc.runId, sc.changedParam, sc.paramValue,
                            avg.rho1, avg.rho2, avg.rho1Blk, avg.rho2Blk,
                            avg.avgCustomers, avg.avgDepartureInterval, avg.avgTimeInBank,
                            avg.avgQ1, avg.avgQ2, avg.avgQOut, avg.rejectionRatePercent, avg.laneChanges);
                }
            }
        }
        System.out.println("Збережено таблицю у файл: " + csvFileName);
    }

    private static SimulationMetrics runAveragedScenario(
            ModelSupplier supplier, Scenario sc, double simulationTime, int replications) throws Exception {
        double rho1 = 0, rho2 = 0, rho1Act = 0, rho1Blk = 0, rho2Act = 0, rho2Blk = 0;
        double nSys = 0, tDep = 0, wSys = 0, q1 = 0, q2 = 0, qOut = 0, pRej = 0, j = 0, nDep = 0, nOut = 0;

        for (int r = 0; r < replications; r++) {
            LoadedPetriNet net = supplier.get();
            sc.modifier.accept(net);
            SimulationResult res = SimulationRunner.run(net, simulationTime);
            SimulationMetrics m = StatisticsCollector.calculateMetrics(res, sc.initialCustomers);

            rho1 += m.rho1;
            rho2 += m.rho2;
            rho1Act += m.rho1Act;
            rho1Blk += m.rho1Blk;
            rho2Act += m.rho2Act;
            rho2Blk += m.rho2Blk;
            nSys += m.avgCustomers;
            tDep += m.avgDepartureInterval;
            wSys += m.avgTimeInBank;
            q1 += m.avgQ1;
            q2 += m.avgQ2;
            qOut += m.avgQOut;
            pRej += m.rejectionRatePercent;
            j += m.laneChanges;
            nDep += m.servedFromWindows;
            nOut += m.exitedToStreet;
        }

        double k = replications;
        return new SimulationMetrics(
                rho1 / k, rho2 / k, rho1Act / k, rho1Blk / k, rho2Act / k, rho2Blk / k,
                nSys / k, tDep / k, wSys / k, q1 / k, q2 / k, qOut / k,
                pRej / k, j / k, nDep / k, nOut / k
        );
    }

    private static void setTransitionDelay(LoadedPetriNet loaded, String code, double meanDelay) {
        PetriT t = findTransitionByCode(loaded, code);
        t.setParameter(meanDelay);
    }

    private static void setTransitionProbability(LoadedPetriNet loaded, String code, double probability) {
        PetriT t = findTransitionByCode(loaded, code);
        t.setProbability(probability);
    }

    private static void setPlaceMark(LoadedPetriNet loaded, String code, int mark) {
        int idx = loaded.getPlaceIndex(code);
        PetriP p = loaded.getNet().getListP()[idx];
        p.setMark(mark);
    }

    private static PetriT findTransitionByCode(LoadedPetriNet loaded, String code) {
        for (PetriT t : loaded.getNet().getListT()) {
            String name = t.getName().trim();
            if (name.equals(code) || name.endsWith(" " + code) || name.endsWith(code)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Перехід з кодом '" + code + "' не знайдено в мережі.");
    }
}