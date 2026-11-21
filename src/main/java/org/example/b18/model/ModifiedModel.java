package org.example.b18.model;

import org.example.b18.config.ModelParameters;

import java.io.InputStream;

/** Loader for the user's second (modified) B18 PNML scheme. */
public final class ModifiedModel {
    private ModifiedModel() {
    }

    public static LoadedPetriNet load() throws Exception {
        InputStream stream = ModifiedModel.class.getResourceAsStream(ModelParameters.MODIFIED_MODEL_RESOURCE);
        if (stream == null) {
            throw new IllegalStateException("Modified PNML resource was not found: " +
                    ModelParameters.MODIFIED_MODEL_RESOURCE);
        }
        try (InputStream input = stream) {
            return PnmlModelLoader.load(input, "B18 - модифікована модель");
        }
    }
}
