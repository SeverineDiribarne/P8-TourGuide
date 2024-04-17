package com.openclassrooms.tourguide.model;

import gpsUtil.location.Location;

import java.util.List;

public class RecommendedDestinationsBasedOnTheUserLastLocation {

    public final Location userLocation;
    public final List<Destination> recommendedDestinations;

    public RecommendedDestinationsBasedOnTheUserLastLocation(Location userLocation, List<Destination> recommendedDestinations) {
        this.userLocation = userLocation;
        this.recommendedDestinations = recommendedDestinations;

    }
}

