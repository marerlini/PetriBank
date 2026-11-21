package org.example.b18.model;

import ua.stetsenkoinna.petriobj.PetriNet;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** A PetriObj network plus stable place-code -> listP index mapping. */
public final class LoadedPetriNet {
    private final PetriNet net;
    private final Map<String, Integer> placeIndexes;
    private final String sourceName;

    public LoadedPetriNet(PetriNet net, Map<String, Integer> placeIndexes, String sourceName) {
        if (net == null) {
            throw new IllegalArgumentException("PetriNet must not be null");
        }
        this.net = net;
        this.placeIndexes = Collections.unmodifiableMap(new LinkedHashMap<>(placeIndexes));
        this.sourceName = sourceName == null ? "B18" : sourceName;
    }

    public PetriNet getNet() {
        return net;
    }

    public Map<String, Integer> getPlaceIndexes() {
        return placeIndexes;
    }

    public String getSourceName() {
        return sourceName;
    }

    public int getPlaceIndex(String code) {
        Integer index = placeIndexes.get(code);
        if (index == null) {
            throw new IllegalArgumentException("Place code '" + code + "' is absent in " + sourceName);
        }
        return index;
    }
}
