package org.example.b18.statistics;

public final class SimulationMetrics {
    public final double rho1;
    public final double rho2;
    public final double rho1Act;
    public final double rho1Blk;
    public final double rho2Act;
    public final double rho2Blk;
    public final double avgCustomers;
    public final double avgDepartureInterval;
    public final double avgTimeInBank;
    public final double avgQ1;
    public final double avgQ2;
    public final double avgQOut;
    public final double rejectionRatePercent;
    public final double laneChanges;
    public final double servedFromWindows;
    public final double exitedToStreet;

    public SimulationMetrics(
            double rho1, double rho2,
            double rho1Act, double rho1Blk,
            double rho2Act, double rho2Blk,
            double avgCustomers, double avgDepartureInterval, double avgTimeInBank,
            double avgQ1, double avgQ2, double avgQOut,
            double rejectionRatePercent, double laneChanges,
            double servedFromWindows, double exitedToStreet) {
        this.rho1 = rho1;
        this.rho2 = rho2;
        this.rho1Act = rho1Act;
        this.rho1Blk = rho1Blk;
        this.rho2Act = rho2Act;
        this.rho2Blk = rho2Blk;
        this.avgCustomers = avgCustomers;
        this.avgDepartureInterval = avgDepartureInterval;
        this.avgTimeInBank = avgTimeInBank;
        this.avgQ1 = avgQ1;
        this.avgQ2 = avgQ2;
        this.avgQOut = avgQOut;
        this.rejectionRatePercent = rejectionRatePercent;
        this.laneChanges = laneChanges;
        this.servedFromWindows = servedFromWindows;
        this.exitedToStreet = exitedToStreet;
    }
}