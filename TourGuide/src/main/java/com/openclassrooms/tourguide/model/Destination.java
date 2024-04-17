package com.openclassrooms.tourguide.model;

import gpsUtil.location.Location;

public class Destination {
    public final String name;
    public final gpsUtil.location.Location location;
    public final double distanceToTravel;
    public final int rewardPoints;

    public Destination(String name, Location location, double distanceToTravel, int rewardPoints) {
        this.name = name;
        this.location = location;
        this.distanceToTravel = distanceToTravel;
        this.rewardPoints = rewardPoints;
    }
}
