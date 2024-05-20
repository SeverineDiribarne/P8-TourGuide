package com.openclassrooms.tourguide.service;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import rewardCentral.RewardCentral;
import com.openclassrooms.tourguide.model.user.user.User;
import com.openclassrooms.tourguide.model.user.user.UserReward;

@Service
public class RewardsService {
    private final Map<String, CompletableFuture<User>> calculateRewardsFutures = new HashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private static final double STATUTE_MILES_PER_NAUTICAL_MILE = 1.15077945;

    // proximity in miles
    private int defaultProximityBuffer = 10;
    private int proximityBuffer = defaultProximityBuffer;
    private int attractionProximityRange = 200;
    private final GpsUtil gpsUtil;
    private final RewardCentral rewardsCentral;
    private final Executor rewardExecutor = Executors.newFixedThreadPool(100);

    public RewardsService(GpsUtil gpsUtil, RewardCentral rewardCentral) {
        this.gpsUtil = gpsUtil;
        this.rewardsCentral = rewardCentral;
    }

    public void setProximityBuffer(int proximityBuffer) {
        this.proximityBuffer = proximityBuffer;
    }

    public void setDefaultProximityBuffer() {
        proximityBuffer = defaultProximityBuffer;
    }

    public CompletableFuture<User> calculateRewards(User user) {
        CompletableFuture<User> producerFuture = null;

        lock.readLock().lock();
        try {
            producerFuture = calculateRewardsFutures.get(user.getUserName());
        } finally {
            lock.readLock().unlock();
        }
        if (producerFuture != null) {
            return producerFuture;
        }

        CompletableFuture<List<VisitedLocation>> userLocationFuture = CompletableFuture.supplyAsync(user::getVisitedLocations, rewardExecutor);
        CompletableFuture<List<Attraction>> attractionsFuture = CompletableFuture.supplyAsync(gpsUtil::getAttractions, rewardExecutor);

        producerFuture = userLocationFuture.thenCombineAsync(attractionsFuture,
                (userLocations, attractions) -> {
                    userLocations
                            .parallelStream()
                            .forEach(visitedLocation ->
                                    {
                                        attractions
                                                .parallelStream().
                                                forEach(attraction ->
                                                        {
                                                            if (user.getUserRewards()
                                                                    .parallelStream()
                                                                    .noneMatch(r -> r.attraction.attractionName.equals(attraction.attractionName))) {
                                                                if (nearAttraction(visitedLocation, attraction)) {
                                                                    int rewardPoints = getRewardPoints(attraction, user);
                                                                    user.addUserReward(new UserReward(visitedLocation, attraction, rewardPoints));
                                                                }
                                                            }
                                                        }
                                                );
                                    }
                            );
                    return user;
                }, rewardExecutor);

        lock.writeLock().lock();
        try {
            calculateRewardsFutures.put(user.getUserName(), producerFuture);
        } finally {
            lock.writeLock().unlock();
        }
        return producerFuture;

//        CompletableFuture<List<VisitedLocation>> userLocationFuture = CompletableFuture.supplyAsync(user::getVisitedLocations);
//        CompletableFuture<List<Attraction>> attractionsFuture = CompletableFuture.supplyAsync(gpsUtil::getAttractions);
//
//    //    CompletableFuture<Void> allOf = CompletableFuture.allOf(userLocationFuture, attractionsFuture);
//        CompletableFuture<Void> rewardsFuture = CompletableFuture.runAsync(() -> {
//            List<VisitedLocation> userLocations = userLocationFuture.join();
//            List<Attraction> attractions = attractionsFuture.join();
//
//            for (VisitedLocation visitedLocation : userLocations) {
//                for (Attraction attraction : attractions) {
//                    if (user.getUserRewards().stream().noneMatch(r -> r.attraction.attractionName.equals(attraction.attractionName))) {
//                        if (nearAttraction(visitedLocation, attraction)) {
//                            int rewardPoints = getRewardPoints(attraction, user);
//                            user.addUserReward(new UserReward(visitedLocation, attraction, rewardPoints));
//                        }
//                    }
//                }
//            }
//        }, rewardExecutor
//        );
        // }
    }

    public Stream<User> usersWithUserRewardsStream() throws InterruptedException {
        return calculateRewardsFutures
                .values()
                .stream()
                .map(CompletableFuture::join)
                .parallel();
    }

    public boolean isWithinAttractionProximity(Attraction attraction, Location location) {
        return getDistance(attraction, location) > attractionProximityRange ? false : true;
    }

    private boolean nearAttraction(VisitedLocation visitedLocation, Attraction attraction) {
        return getDistance(attraction, visitedLocation.location) > proximityBuffer ? false : true;
    }

    private int getRewardPoints(Attraction attraction, User user) {
        return rewardsCentral.getAttractionRewardPoints(attraction.attractionId, user.getUserId());
    }

    public double getDistance(Location loc1, Location loc2) {
        double lat1 = Math.toRadians(loc1.latitude);
        double lon1 = Math.toRadians(loc1.longitude);
        double lat2 = Math.toRadians(loc2.latitude);
        double lon2 = Math.toRadians(loc2.longitude);

        double angle = Math.acos(Math.sin(lat1) * Math.sin(lat2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.cos(lon1 - lon2));

        double nauticalMiles = 60 * Math.toDegrees(angle);
        double statuteMiles = STATUTE_MILES_PER_NAUTICAL_MILE * nauticalMiles;
        return statuteMiles;
    }
}
