package com.wcholmes.landscaper.common.item;

public enum NaturalizationMode {
    // Original replace-only modes
    GRASS_ONLY("Grass Only", false, false, false, StrategyType.REPLACE),
    GRASS_WITH_PLANTS("Grass + Plants", false, true, false, StrategyType.REPLACE),
    MESSY("Messy Natural", true, false, false, StrategyType.REPLACE),
    MESSY_WITH_PLANTS("Messy + Plants", true, true, false, StrategyType.REPLACE),
    PATH("Path Only", false, false, true, StrategyType.REPLACE),
    MESSY_PATH("Messy Path", true, false, true, StrategyType.REPLACE),

    // New modes with different strategies
    FILL("Fill Below", true, true, false, StrategyType.FILL),
    FLATTEN("Flatten", false, false, false, StrategyType.FLATTEN),
    FLOOD("Flood with Water", false, false, false, StrategyType.FLOOD),
    NATURALIZE("Natural Erosion", true, true, false, StrategyType.NATURALIZE);

    private final String displayName;
    private final boolean allowVariation;
    private final boolean addPlants;
    private final boolean pathOnly;
    private final StrategyType strategyType;

    NaturalizationMode(String displayName, boolean allowVariation, boolean addPlants, boolean pathOnly, StrategyType strategyType) {
        this.displayName = displayName;
        this.allowVariation = allowVariation;
        this.addPlants = addPlants;
        this.pathOnly = pathOnly;
        this.strategyType = strategyType;
    }

    public enum StrategyType {
        REPLACE,
        FILL,
        FLATTEN,
        FLOOD,
        NATURALIZE
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

    public StrategyType getStrategyType() {
        return strategyType;
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
