package org.example.b18.experiment;

import org.example.b18.model.BasicModel;
import org.example.b18.model.LoadedPetriNet;
import org.example.b18.model.ModifiedModel;
import org.example.b18.simulation.SimulationRunner;
import org.example.b18.statistics.SimulationMetrics;
import org.example.b18.statistics.SimulationResult;
import org.example.b18.statistics.StatisticsCollector;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.ToDoubleFunction;

public final class ExperimentalStudy {

    @FunctionalInterface
    private interface ModelSupplier {
        LoadedPetriNet get() throws Exception;
    }

    private static final class MetricDef {
        final String code;
        final String label;
        final ToDoubleFunction<SimulationMetrics> extractor;

        MetricDef(String code, String label, ToDoubleFunction<SimulationMetrics> extractor) {
            this.code = code;
            this.label = label;
            this.extractor = extractor;
        }
    }

    private static final class SummaryStats {
        final int n;
        final double mean;
        final double std;
        final double se;
        final double ciHalfWidth;
        final double min;
        final double max;

        SummaryStats(int n, double mean, double std, double se, double ciHalfWidth, double min, double max) {
            this.n = n;
            this.mean = mean;
            this.std = std;
            this.se = se;
            this.ciHalfWidth = ciHalfWidth;
            this.min = min;
            this.max = max;
        }
    }

    private static final List<MetricDef> METRICS = List.of(
            new MetricDef("rho1", "Завантаження касира 1", m -> m.rho1),
            new MetricDef("rho2", "Завантаження касира 2", m -> m.rho2),
            new MetricDef("rho1_act", "Активна робота касира 1", m -> m.rho1Act),
            new MetricDef("rho1_blk", "Блокування касира 1", m -> m.rho1Blk),
            new MetricDef("rho2_act", "Активна робота касира 2", m -> m.rho2Act),
            new MetricDef("rho2_blk", "Блокування касира 2", m -> m.rho2Blk),
            new MetricDef("N_sys", "Середня кількість авто в банку", m -> m.avgCustomers),
            new MetricDef("T_dep", "Інтервал між від'їздами від вікон", m -> m.avgDepartureInterval),
            new MetricDef("W_sys", "Середній час перебування в банку", m -> m.avgTimeInBank),
            new MetricDef("Q1", "Середня довжина черги 1", m -> m.avgQ1),
            new MetricDef("Q2", "Середня довжина черги 2", m -> m.avgQ2),
            new MetricDef("Qout", "Середня довжина черги виїзду", m -> m.avgQOut),
            new MetricDef("Prej_pct", "Відсоток відмов (%)", m -> m.rejectionRatePercent),
            new MetricDef("J", "Кількість змін смуг", m -> m.laneChanges),
            new MetricDef("N_dep", "Обслужено біля вікон", m -> m.servedFromWindows),
            new MetricDef("N_out", "Виїхало на вулицю", m -> m.exitedToStreet)
    );

    private ExperimentalStudy() {
    }

    static void main(String[] args) throws Exception {
        double chosenHorizon = args.length > 0 ? Double.parseDouble(args[0]) : 2000.0;
        int mainReplications = args.length > 1 ? Integer.parseInt(args[1]) : 30;

        runTransientAnalysis();
        System.out.println();

        runSampleSizeEstimation(chosenHorizon, 20);
        System.out.println();

        runMainComparisonExperiment(chosenHorizon, mainReplications);
    }

    /**
     * Етап 1 (4.1): Дослідження перехідного періоду та збіжності оцінок при зростанні T_mod.
     */
    private static void runTransientAnalysis() throws Exception {
        double[] horizons = {100.0, 250.0, 500.0, 1000.0, 2000.0, 5000.0, 10000.0};
        int repsPerHorizon = 10;

        System.out.println("=== ЕТАП 1 (4.1): ДОСЛІДЖЕННЯ ПЕРЕХІДНОГО ПЕРІОДУ ===");
        System.out.printf("%-8s | %7s | %7s | %7s | %7s | %7s || %7s | %7s | %7s | %7s | %7s%n",
                "T_mod", "B_Nsys", "B_Tdep", "B_Wsys", "B_Prej%", "B_J",
                "M_Nsys", "M_Tdep", "M_Wsys", "M_Prej%", "M_J");

        try (PrintWriter csv = new PrintWriter("exp_transient.csv", StandardCharsets.UTF_8)) {
            csv.println("T_mod;Basic_N_sys;Basic_T_dep;Basic_W_sys;Basic_Prej_pct;Basic_J;"
                    + "Mod_N_sys;Mod_T_dep;Mod_W_sys;Mod_Prej_pct;Mod_J");

            for (double tMod : horizons) {
                List<SimulationMetrics> basicBatch = runBatch(BasicModel::load, tMod, repsPerHorizon);
                List<SimulationMetrics> modBatch = runBatch(ModifiedModel::load, tMod, repsPerHorizon);

                double bN = computeStats(basicBatch, m -> m.avgCustomers).mean;
                double bT = computeStats(basicBatch, m -> m.avgDepartureInterval).mean;
                double bW = computeStats(basicBatch, m -> m.avgTimeInBank).mean;
                double bP = computeStats(basicBatch, m -> m.rejectionRatePercent).mean;
                double bJ = computeStats(basicBatch, m -> m.laneChanges).mean;

                double mN = computeStats(modBatch, m -> m.avgCustomers).mean;
                double mT = computeStats(modBatch, m -> m.avgDepartureInterval).mean;
                double mW = computeStats(modBatch, m -> m.avgTimeInBank).mean;
                double mP = computeStats(modBatch, m -> m.rejectionRatePercent).mean;
                double mJ = computeStats(modBatch, m -> m.laneChanges).mean;

                System.out.printf(Locale.US,
                        "%-8.0f | %7.4f | %7.4f | %7.4f | %7.2f | %7.1f || %7.4f | %7.4f | %7.4f | %7.2f | %7.1f%n",
                        tMod, bN, bT, bW, bP, bJ, mN, mT, mW, mP, mJ);

                csv.printf(Locale.US,
                        "%.0f;%.4f;%.4f;%.4f;%.2f;%.1f;%.4f;%.4f;%.4f;%.2f;%.1f%n",
                        tMod, bN, bT, bW, bP, bJ, mN, mT, mW, mP, mJ);
            }
        }
        System.out.println("Збережено: exp_transient.csv");
    }

    /**
     * Етап 2 (4.1): Розрахунок необхідної кількості повторів за Чебишевим (gamma=0.95, delta=5%)
     * та критерієм Стьюдента за пілотною вибіркою.
     */
    private static void runSampleSizeEstimation(double tMod, int pilotRuns) throws Exception {
        System.out.printf(Locale.US,
                "=== ЕТАП 2 (4.1): РОЗРАХУНОК КІЛЬКОСТІ ПОВТОРІВ (T=%.0f хв, пілот=%d) ===%n",
                tMod, pilotRuns);

        List<SimulationMetrics> basicPilot = runBatch(BasicModel::load, tMod, pilotRuns);
        List<SimulationMetrics> modPilot = runBatch(ModifiedModel::load, tMod, pilotRuns);

        double gamma = 0.95;
        double relError = 0.05; // 5% відносна похибка
        double tCritPilot = studentT95(pilotRuns - 1);

        System.out.printf("%-10s | %-10s | %9s | %9s | %9s | %10s | %10s%n",
                "Модель", "Показник", "Середнє", "СКВ (s)", "Eps (5%)", "N_Чебишев", "N_Стьюдент");

        try (PrintWriter csv = new PrintWriter("exp_sample_size.csv", StandardCharsets.UTF_8)) {
            csv.println("Model;Metric;Mean;Std;Eps_5pct;N_Chebyshev;N_Student");

            String[] keyCodes = {"rho1", "rho2", "N_sys", "T_dep", "W_sys", "Prej_pct", "J"};
            for (String code : keyCodes) {
                MetricDef def = findMetric(code);
                writeSampleSizeRow("Basic", def, basicPilot, gamma, relError, tCritPilot, csv);
            }
            for (String code : keyCodes) {
                MetricDef def = findMetric(code);
                writeSampleSizeRow("Modified", def, modPilot, gamma, relError, tCritPilot, csv);
            }
        }
        System.out.println("Збережено: exp_sample_size.csv");
    }

    private static void writeSampleSizeRow(
            String modelName, MetricDef def, List<SimulationMetrics> batch,
            double gamma, double relError, double tCrit, PrintWriter csv) {

        SummaryStats st = computeStats(batch, def.extractor);
        double eps = Math.abs(st.mean) * relError;
        long nCheb = eps > 1e-9
                ? (long) Math.ceil((st.std * st.std) / (eps * eps * (1.0 - gamma)))
                : 1;
        long nStud = eps > 1e-9
                ? (long) Math.ceil(Math.pow((tCrit * st.std) / eps, 2))
                : 1;

        System.out.printf(Locale.US, "%-10s | %-10s | %9.4f | %9.4f | %9.4f | %10d | %10d%n",
                modelName, def.code, st.mean, st.std, eps, nCheb, nStud);

        csv.printf(Locale.US, "%s;%s;%.4f;%.4f;%.4f;%d;%d%n",
                modelName, def.code, st.mean, st.std, eps, nCheb, nStud);
    }

    /**
     * Етап 3 (4.2 та 4.3): Повна серія прогонів для базової та модифікованої моделей
     * і формування зведеної порівняльної таблиці.
     */
    private static void runMainComparisonExperiment(double tMod, int replications) throws Exception {
        System.out.printf(Locale.US,
                "=== ЕТАП 3 (4.2 - 4.3): ОСНОВНИЙ ЕКСПЕРИМЕНТ (T=%.0f хв, N=%d повторів) ===%n",
                tMod, replications);

        List<SimulationMetrics> basicRuns = runBatch(BasicModel::load, tMod, replications);
        List<SimulationMetrics> modRuns = runBatch(ModifiedModel::load, tMod, replications);

        saveRawRuns("exp_runs_basic.csv", basicRuns);
        saveRawRuns("exp_runs_modified.csv", modRuns);

        System.out.printf("%-10s | %10s | %10s | %10s || %10s | %10s | %10s || %10s | %9s%n",
                "Показник", "Base_Mean", "Base_SD", "Base_CI95",
                "Mod_Mean", "Mod_SD", "Mod_CI95", "Diff_Abs", "Diff_%");

        try (PrintWriter csv = new PrintWriter("exp_comparison.csv", StandardCharsets.UTF_8)) {
            csv.println("Code;Label;Base_Mean;Base_SD;Base_SE;Base_CI95_Half;Base_Min;Base_Max;"
                    + "Mod_Mean;Mod_SD;Mod_SE;Mod_CI95_Half;Mod_Min;Mod_Max;Diff_Abs;Diff_Pct");

            for (MetricDef def : METRICS) {
                SummaryStats b = computeStats(basicRuns, def.extractor);
                SummaryStats m = computeStats(modRuns, def.extractor);

                double diffAbs = m.mean - b.mean;
                double diffPct = Math.abs(b.mean) > 1e-9 ? (diffAbs / b.mean) * 100.0 : Double.NaN;

                System.out.printf(Locale.US,
                        "%-10s | %10.4f | %10.4f | ±%9.4f || %10.4f | %10.4f | ±%9.4f || %+10.4f | %+8.2f%%%n",
                        def.code, b.mean, b.std, b.ciHalfWidth,
                        m.mean, m.std, m.ciHalfWidth, diffAbs, diffPct);

                csv.printf(Locale.US,
                        "%s;%s;%.4f;%.4f;%.4f;%.4f;%.4f;%.4f;%.4f;%.4f;%.4f;%.4f;%.4f;%.4f;%.4f;%.2f%n",
                        def.code, def.label,
                        b.mean, b.std, b.se, b.ciHalfWidth, b.min, b.max,
                        m.mean, m.std, m.se, m.ciHalfWidth, m.min, m.max,
                        diffAbs, diffPct);
            }
        }
        System.out.println("Збережено: exp_runs_basic.csv, exp_runs_modified.csv, exp_comparison.csv");
    }

    private static List<SimulationMetrics> runBatch(
            ModelSupplier supplier, double tMod, int replications) throws Exception {
        List<SimulationMetrics> list = new ArrayList<>(replications);
        for (int i = 0; i < replications; i++) {
            LoadedPetriNet net = supplier.get();
            SimulationResult res = SimulationRunner.run(net, tMod);
            list.add(StatisticsCollector.calculateMetrics(res));
        }
        return list;
    }

    private static void saveRawRuns(String fileName, List<SimulationMetrics> runs) throws Exception {
        try (PrintWriter csv = new PrintWriter(fileName, StandardCharsets.UTF_8)) {
            csv.println("Run;rho1;rho2;rho1_act;rho1_blk;rho2_act;rho2_blk;N_sys;T_dep;W_sys;Q1;Q2;Qout;Prej_pct;J;N_dep;N_out");
            for (int i = 0; i < runs.size(); i++) {
                SimulationMetrics m = runs.get(i);
                csv.printf(Locale.US,
                        "%d;%.4f;%.4f;%.4f;%.4f;%.4f;%.4f;%.4f;%.4f;%.4f;%.4f;%.4f;%.4f;%.2f;%.0f;%.0f;%.0f%n",
                        i + 1, m.rho1, m.rho2, m.rho1Act, m.rho1Blk, m.rho2Act, m.rho2Blk,
                        m.avgCustomers, m.avgDepartureInterval, m.avgTimeInBank,
                        m.avgQ1, m.avgQ2, m.avgQOut, m.rejectionRatePercent,
                        m.laneChanges, m.servedFromWindows, m.exitedToStreet);
            }
        }
    }

    private static SummaryStats computeStats(
            List<SimulationMetrics> runs, ToDoubleFunction<SimulationMetrics> extractor) {
        int n = runs.size();
        double sum = 0.0;
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;

        for (SimulationMetrics m : runs) {
            double v = extractor.applyAsDouble(m);
            sum += v;
            if (v < min) min = v;
            if (v > max) max = v;
        }
        double mean = sum / n;

        double sqDiff = 0.0;
        for (SimulationMetrics m : runs) {
            double d = extractor.applyAsDouble(m) - mean;
            sqDiff += d * d;
        }
        double std = n > 1 ? Math.sqrt(sqDiff / (n - 1)) : 0.0;
        double se = std / Math.sqrt(n);
        double ciHalf = studentT95(n - 1) * se;

        return new SummaryStats(n, mean, std, se, ciHalf, min, max);
    }

    private static MetricDef findMetric(String code) {
        return METRICS.stream()
                .filter(m -> m.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Невідомий код метрики: " + code));
    }

    private static double studentT95(int df) {
        double[] table = {
                12.706, 4.303, 3.182, 2.776, 2.571, 2.447, 2.365, 2.306, 2.262, 2.228,
                2.201, 2.179, 2.160, 2.145, 2.131, 2.120, 2.110, 2.101, 2.093, 2.086,
                2.080, 2.074, 2.069, 2.064, 2.060, 2.056, 2.052, 2.048, 2.045, 2.042
        };
        if (df <= 0) return 1.96;
        if (df <= table.length) return table[df - 1];
        if (df <= 40) return 2.021;
        if (df <= 60) return 2.000;
        if (df <= 120) return 1.980;
        return 1.960;
    }
}