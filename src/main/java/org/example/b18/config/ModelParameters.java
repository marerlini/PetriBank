package org.example.b18.config;

/**
 * Parameters used by the B18 runner.
 *
 * The actual Petri-net structure and transition parameters are read from the
 * two PNML files in src/main/resources/models. Keep SIMULATION_TIME equal to
 * the modelling horizon used in PetriObjModelPaint when comparing results.
 */
public final class ModelParameters {
    private ModelParameters() {
    }

    /** Default horizon; change it to the same value used in the graphical editor. */
    public static final double SIMULATION_TIME = 1_000.0;

    /** Assumption used when building the modified PNML model. */
    public static final double TAU = 0.4;

    public static final String BASIC_MODEL_RESOURCE = "/models/basic-model.pnml";
    public static final String MODIFIED_MODEL_RESOURCE = "/models/modified-model.pnml";
}
