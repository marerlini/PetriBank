package org.example.b18.experiment;

import org.example.b18.model.LoadedPetriNet;
import org.example.b18.model.ModifiedModel;
import org.example.b18.simulation.SimulationRunner;
import org.example.b18.statistics.SimulationMetrics;
import org.example.b18.statistics.SimulationResult;
import org.example.b18.statistics.StatisticsCollector;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public final class OptimizationTrial {

    private static final class Proposal {
        final int id;
        final String name;
        final Consumer<LoadedPetriNet> modifier;

        Proposal(int id, String name, Consumer<LoadedPetriNet> modifier) {
            this.id = id;
            this.name = name;
            this.modifier = modifier;
        }
    }

    private OptimizationTrial() {
    }

    public static void main(String[] args) throws Exception {
        double tMod = args.length > 0 ? Double.parseDouble(args[0]) : 2000.0;
        int replications = args.length > 1 ? Integer.parseInt(args[1]) : 30;

        List<Proposal> proposals = List.of(
                new Proposal(0, "Поточна модифікована модель", net -> {}),
                new Proposal(1, "Пропозиція 1: Смуга розгону (P(VG)=0.75)", net -> {
                    setTransitionProbability(net, "VG", 0.75);
                    setTransitionProbability(net, "VB", 0.25);
                    setTransitionDelay(net, "TGAP", 0.425);
                    setTransitionDelay(net, "TNO", 0.325);
                }),
                new Proposal(2, "Пропозиція 2: Буфер FOUT=6 + Tserv=0.60", net -> {
                    setPlaceMark(net, "FOUT", 6);
                    setTransitionDelay(net, "TE1", 0.60);
                    setTransitionDelay(net, "TE2", 0.60);
                }),
                new Proposal(3, "Пропозиція 3: Комплексна модернізація", net -> {
                    setTransitionProbability(net, "VG", 0.75);
                    setTransitionProbability(net, "VB", 0.25);
                    setTransitionDelay(net, "TGAP", 0.425);
                    setTransitionDelay(net, "TNO", 0.325);
                    setPlaceMark(net, "FOUT", 5);
                    setTransitionDelay(net, "TE1", 0.60);
                    setTransitionDelay(net, "TE2", 0.60);
                })
        );

        System.out.printf(Locale.US,
                "=== ПРОБНИЙ ЗАПУСК ПРОПОЗИЦІЙ (T=%.0f хв, N=%d) ===%n", tMod, replications);
        System.out.printf("%-3s | %-42s | %6s | %6s | %6s | %6s | %6s | %6s | %7s | %6s%n",
                "ID", "Варіант", "rho1", "rho2", "blk1", "blk2", "N_sys", "T_dep", "W_sys", "Prej%");

        try (PrintWriter csv = new PrintWriter("exp_proposals.csv", StandardCharsets.UTF_8)) {
            csv.println("ID;Variant;rho1;rho2;rho1_blk;rho2_blk;N_sys;T_dep;W_sys;Q1;Q2;Qout;Prej_pct;J;N_dep;N_out");

            for (Proposal p : proposals) {
                SimulationMetrics avg = runAveraged(p, tMod, replications);

                System.out.printf(Locale.US,
                        "%-3d | %-42s | %6.4f | %6.4f | %6.4f | %6.4f | %6.4f | %6.4f | %7.4f | %6.2f%n",
                        p.id, p.name, avg.rho1, avg.rho2, avg.rho1Blk, avg.rho2Blk,
                        avg.avgCustomers, avg.avgDepartureInterval, avg.avgTimeInBank,
                        avg.rejectionRatePercent);

                csv.printf(Locale.US,
                        "%d;%s;%.4f;%.4f;%.4f;%.4f;%.4f;%.4f;%.4f;%.4f;%.4f;%.4f;%.2f;%.1f;%.1f;%.1f%n",
                        p.id, p.name, avg.rho1, avg.rho2, avg.rho1Blk, avg.rho2Blk,
                        avg.avgCustomers, avg.avgDepartureInterval, avg.avgTimeInBank,
                        avg.avgQ1, avg.avgQ2, avg.avgQOut, avg.rejectionRatePercent,
                        avg.laneChanges, avg.servedFromWindows, avg.exitedToStreet);
            }
        }
        System.out.println("Збережено: exp_proposals.csv");
    }

    private static SimulationMetrics runAveraged(Proposal p, double tMod, int replications) throws Exception {
        double rho1 = 0, rho2 = 0, r1a = 0, r1b = 0, r2a = 0, r2b = 0;
        double nSys = 0, tDep = 0, wSys = 0, q1 = 0, q2 = 0, qOut = 0, pRej = 0, j = 0, nDep = 0, nOut = 0;

        for (int i = 0; i < replications; i++) {
            LoadedPetriNet net = ModifiedModel.load();
            p.modifier.accept(net);
            SimulationResult res = SimulationRunner.run(net, tMod);
            SimulationMetrics m = StatisticsCollector.calculateMetrics(res);

            rho1 += m.rho1;
            rho2 += m.rho2;
            r1a += m.rho1Act;
            r1b += m.rho1Blk;
            r2a += m.rho2Act;
            r2b += m.rho2Blk;
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
                rho1 / k, rho2 / k, r1a / k, r1b / k, r2a / k, r2b / k,
                nSys / k, tDep / k, wSys / k, q1 / k, q2 / k, qOut / k,
                pRej / k, j / k, nDep / k, nOut / k
        );
    }

    private static void setTransitionDelay(LoadedPetriNet loaded, String code, double meanDelay) {
        findTransition(loaded, code).setParameter(meanDelay);
    }

    private static void setTransitionProbability(LoadedPetriNet loaded, String code, double prob) {
        findTransition(loaded, code).setProbability(prob);
    }

    private static void setPlaceMark(LoadedPetriNet loaded, String code, int mark) {
        int idx = loaded.getPlaceIndex(code);
        PetriP p = loaded.getNet().getListP()[idx];
        p.setMark(mark);
    }

    private static PetriT findTransition(LoadedPetriNet loaded, String code) {
        for (PetriT t : loaded.getNet().getListT()) {
            String name = t.getName().trim();
            if (name.equals(code) || name.endsWith(" " + code) || name.endsWith(code)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Перехід не знайдено: " + code);
    }
}