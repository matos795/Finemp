package com.finemp.assets;

public enum AssetCategory {
    IT(60, true),
    VEHICLE(60, true),
    MACHINERY(120, true),
    FURNITURE(120, true),
    INSTALLATION(120, true),
    BUILDING(300, true),
    LAND(0, false),
    INTANGIBLE(60, true),
    OTHER(120, true);

    private final int defaultUsefulLifeMonths;
    private final boolean depreciableByDefault;

    AssetCategory(int defaultUsefulLifeMonths, boolean depreciableByDefault) {
        this.defaultUsefulLifeMonths = defaultUsefulLifeMonths;
        this.depreciableByDefault = depreciableByDefault;
    }

    public int defaultUsefulLifeMonths() {
        return defaultUsefulLifeMonths;
    }

    public boolean depreciableByDefault() {
        return depreciableByDefault;
    }
}
