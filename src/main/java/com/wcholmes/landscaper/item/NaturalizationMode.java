package com.wcholmes.landscaper.item;

public enum NaturalizationMode {
    GRASS_ONLY("Grass Only", false, false, false),
    GRASS_WITH_PLANTS("Grass + Plants", false, true, false),
    MESSY("Messy Natural", true, false, false),
    MESSY_WITH_PLANTS("Messy + Plants", true, true, false),
    PATH("Path Only", false, false, true),
    MESSY_PATH("Messy Path", true, false, true);

    private final String displayName;
    private final boolean allowVariation;
    private final boolean addPlants;
    private final boolean pathOnly;

    NaturalizationMode(String displayName, boolean allowVariation, boolean addPlants, boolean pathOnly) {
        this.displayName = displayName;
        this.allowVariation = allowVariation;
        this.addPlants = addPlants;
        this.pathOnly = pathOnly;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean allowsVariation() {
        return allowVariation;
    }

    public boolean shouldAddPlants() {
        return addPlants;
    }

    public boolean isPathOnly() {
        return pathOnly;
    }

    public NaturalizationMode next() {
        NaturalizationMode[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }

    public NaturalizationMode previous() {
        NaturalizationMode[] values = values();
        int newOrdinal = (this.ordinal() - 1 + values.length) % values.length;
        return values[newOrdinal];
    }
}
