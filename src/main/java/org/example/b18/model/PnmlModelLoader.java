package org.example.b18.model;

import ua.stetsenkoinna.petriobj.ArcIn;
import ua.stetsenkoinna.petriobj.ArcOut;
import ua.stetsenkoinna.petriobj.PetriNet;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PnmlModelLoader {
    private static final Pattern PLACE_CODE_AT_END = Pattern.compile("([A-Za-z][A-Za-z0-9_]*|E[0-9]+)$");

    private PnmlModelLoader() {
    }

    public static LoadedPetriNet load(InputStream input, String netName) throws Exception {
        if (input == null) {
            throw new IllegalArgumentException("PNML input stream is null");
        }

        Document document = parseDocument(input);

        List<Element> placeElements = elements(document, "place");
        List<Element> transitionElements = elements(document, "transition");
        List<Element> arcElements = elements(document, "arc");

        if (placeElements.isEmpty() || transitionElements.isEmpty()) {
            throw new IllegalArgumentException("PNML must contain at least one place and one transition");
        }

        ArrayList<PetriP> places = new ArrayList<>();
        ArrayList<PetriT> transitions = new ArrayList<>();
        ArrayList<ArcIn> inputArcs = new ArrayList<>();
        ArrayList<ArcOut> outputArcs = new ArrayList<>();

        Map<String, PetriP> placeById = new HashMap<>();
        Map<String, PetriT> transitionById = new HashMap<>();
        Map<String, Integer> placeIndexes = new LinkedHashMap<>();
        Map<String, Integer> ordinaryInputCountByTransition = new HashMap<>();

        // Places
        for (Element element : placeElements) {
            String id = requiredAttribute(element, "id");
            String label = childText(element, "name");
            String code = extractPlaceCode(label);
            int initialMarking = parseInt(childText(element, "initialMarking"), 0, "initial marking of " + code);
            if (initialMarking < 0) {
                throw new IllegalArgumentException("Negative initial marking for place " + code);
            }
            if (placeIndexes.containsKey(code)) {
                throw new IllegalArgumentException("Duplicate place code in PNML: " + code);
            }

            PetriP place = new PetriP(code, initialMarking);
            placeIndexes.put(code, places.size());
            places.add(place);
            placeById.put(id, place);
        }

        // Transitions.
        for (Element element : transitionElements) {
            String id = requiredAttribute(element, "id");
            String label = childText(element, "name");
            String name = label.isBlank() ? id : label.trim();

            String delayText = toolValue(element, "delayMeanValue");
            if (delayText.isBlank()) {
                delayText = toolValue(element, "timeDelay");
            }
            double meanDelay = parseDouble(delayText, 0.0, "time delay of " + name);
            if (meanDelay < 0.0) {
                throw new IllegalArgumentException("Negative time delay for transition " + name);
            }

            PetriT transition = new PetriT(name, meanDelay);
            String distribution = toolValue(element, "distribution").trim().toLowerCase(Locale.ROOT);
            String deviationText = toolValue(element, "standardDeviation");
            double deviation = parseDouble(deviationText, 0.0, "standard deviation of " + name);

            if (!distribution.isEmpty() && !"null".equals(distribution)) {
                transition.setDistribution(distribution, meanDelay);
                if (deviation > 0.0) {
                    transition.setParamDeviation(deviation);
                }
            }

            String priorityText = toolValue(element, "priority");
            if (!priorityText.isBlank()) {
                transition.setPriority(parseInt(priorityText, 0, "priority of " + name));
            }

            String probabilityText = toolValue(element, "probability");
            if (!probabilityText.isBlank()) {
                double probability = parseDouble(probabilityText, 1.0, "probability of " + name);
                if (probability < 0.0 || probability > 1.0) {
                    throw new IllegalArgumentException("Activation probability must be in [0,1] for " + name);
                }
                transition.setProbability(probability);
            }

            transitions.add(transition);
            transitionById.put(id, transition);
            ordinaryInputCountByTransition.put(id, 0);
        }

        // Arcs
        for (Element element : arcElements) {
            String source = requiredAttribute(element, "source");
            String target = requiredAttribute(element, "target");
            int weight = parseInt(childText(element, "inscription"), 1, "arc weight");
            if (weight < 1) {
                throw new IllegalArgumentException("Arc weight must be a positive integer: " + element.getAttribute("id"));
            }

            PetriP sourcePlace = placeById.get(source);
            PetriP targetPlace = placeById.get(target);
            PetriT sourceTransition = transitionById.get(source);
            PetriT targetTransition = transitionById.get(target);

            if (sourcePlace != null && targetTransition != null) {
                ArcIn arc = new ArcIn(sourcePlace, targetTransition, weight);
                boolean informational = "true".equalsIgnoreCase(toolValue(element, "informational").trim());
                if (informational) {
                    arc.setInf(true);
                } else {
                    ordinaryInputCountByTransition.put(target, ordinaryInputCountByTransition.get(target) + 1);
                }
                inputArcs.add(arc);
            } else if (sourceTransition != null && targetPlace != null) {
                outputArcs.add(new ArcOut(sourceTransition, targetPlace, weight));
            } else {
                throw new IllegalArgumentException("Arc endpoint does not reference a place/transition pair: " +
                        source + " -> " + target);
            }
        }

        for (Element element : transitionElements) {
            String transitionId = element.getAttribute("id");
            boolean hasInformationalInput = false;
            for (Element arc : arcElements) {
                if (transitionId.equals(arc.getAttribute("target")) &&
                        "true".equalsIgnoreCase(toolValue(arc, "informational").trim())) {
                    hasInformationalInput = true;
                    break;
                }
            }
            if (hasInformationalInput && ordinaryInputCountByTransition.get(transitionId) == 0) {
                throw new IllegalArgumentException("Transition " + childText(element, "name") +
                        " has an informational input but no ordinary input");
            }
        }

        PetriNet net = new PetriNet(netName, places, transitions, inputArcs, outputArcs);

        PetriP.initNext();
        PetriT.initNext();
        ArcIn.initNext();
        ArcOut.initNext();

        return new LoadedPetriNet(net, placeIndexes, netName);
    }

    private static Document parseDocument(InputStream input) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(input);
    }

    private static List<Element> elements(Document document, String localName) {
        NodeList nodes = document.getElementsByTagNameNS("*", localName);
        List<Element> result = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element) {
                result.add((Element) node);
            }
        }
        return result;
    }

    private static String requiredAttribute(Element element, String attribute) {
        String value = element.getAttribute(attribute);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing '" + attribute + "' attribute on " + element.getTagName());
        }
        return value;
    }

    private static String childText(Element parent, String childName) {
        Element child = firstDirectChild(parent, childName);
        if (child == null) {
            return "";
        }
        Element text = firstDescendant(child, "text");
        if (text == null || text.getTextContent() == null) {
            return "";
        }
        return text.getTextContent().trim();
    }

    private static Element firstDirectChild(Element parent, String localName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element && localName.equals(node.getLocalName())) {
                return (Element) node;
            }
        }
        return null;
    }

    private static Element firstDescendant(Element parent, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS("*", localName);
        if (nodes.getLength() == 0 || !(nodes.item(0) instanceof Element)) {
            return null;
        }
        return (Element) nodes.item(0);
    }

    private static String toolValue(Element parent, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS("*", localName);
        if (nodes.getLength() == 0) {
            return "";
        }
        return nodes.item(0).getTextContent() == null ? "" : nodes.item(0).getTextContent().trim();
    }

    private static String extractPlaceCode(String label) {
        String normalized = label == null ? "" : label.trim().replace('Е', 'E').replace('е', 'e');
        Matcher matcher = PLACE_CODE_AT_END.matcher(normalized);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Could not extract a short place code from PNML place name: " + label);
        }
        return matcher.group(1);
    }

    private static int parseInt(String value, int defaultValue, String description) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid integer for " + description + ": " + value, ex);
        }
    }

    private static double parseDouble(String value, double defaultValue, String description) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            double result = Double.parseDouble(value.trim());
            if (!Double.isFinite(result)) {
                throw new IllegalArgumentException("Non-finite number for " + description + ": " + value);
            }
            return result;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid number for " + description + ": " + value, ex);
        }
    }
}
