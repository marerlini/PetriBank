package org.example.b18.model;

import org.example.b18.config.ModelParameters;

import java.io.InputStream;

public final class BasicModel {
    private BasicModel() {
    }

    public static LoadedPetriNet load() throws Exception {
        InputStream stream = BasicModel.class.getResourceAsStream(ModelParameters.BASIC_MODEL_RESOURCE);
        if (stream == null) {
            throw new IllegalStateException("Basic PNML resource was not found: " +
                    ModelParameters.BASIC_MODEL_RESOURCE);
        }
        try (InputStream input = stream) {
            return PnmlModelLoader.load(input, "B18 - основна модель");
        }
    }
}
