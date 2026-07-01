package com.pedrodalben.bigbangworld.domain;

public class BorderConfig {
    private boolean enabled;
    private double diameter;

    public BorderConfig() {}

    public BorderConfig(boolean enabled, double diameter) {
        this.enabled = enabled;
        this.diameter = diameter;
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public double getDiameter() { return diameter; }
    public void setDiameter(double diameter) { this.diameter = diameter; }
}
