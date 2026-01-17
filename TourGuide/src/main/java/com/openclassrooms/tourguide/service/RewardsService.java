package com.openclassrooms.tourguide.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.openclassrooms.tourguide.model.user.user.User;
import com.openclassrooms.tourguide.model.user.user.UserReward;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import rewardCentral.RewardCentral;

@Service
public class RewardsService {
	private final ConcurrentHashMap<String, CompletableFuture<User>> calculateRewardsFutures = new ConcurrentHashMap<>();

	private static final double STATUTE_MILES_PER_NAUTICAL_MILE = 1.15077945;

	// proximity in miles
	private int defaultProximityBuffer = 10;
	private int proximityBuffer = defaultProximityBuffer;
	private int attractionProximityRange = 200;
	private final GpsUtil gpsUtil;
	private final RewardCentral rewardsCentral;
	private final Executor rewardExecutor = Executors.newFixedThreadPool(100);
	private volatile List<Attraction> attractionsCache;
	private final Object attractionsLock = new Object();

	public RewardsService(GpsUtil gpsUtil, RewardCentral rewardCentral) {
		this.gpsUtil = gpsUtil;
		this.rewardsCentral = rewardCentral;
		// Preloading the attractions cache to avoid the cost of the first request
		try {
			this.attractionsCache = gpsUtil.getAttractions();
		} catch (Exception ignored) {
			// if this fails, we will fall back to lazy loading on the first calculation
		}
	}

	private List<Attraction> getAttractionsCached() {
		List<Attraction> local = attractionsCache;
		if (local == null) {
			synchronized (attractionsLock) {
				local = attractionsCache;
				if (local == null) {
					local = gpsUtil.getAttractions();
					attractionsCache = local;
				}
			}
		}
		return local;
	}

	public void setProximityBuffer(int proximityBuffer) {
		this.proximityBuffer = proximityBuffer;
	}

	public void setDefaultProximityBuffer() {
		proximityBuffer = defaultProximityBuffer;
	}

	public CompletableFuture<User> calculateRewards(User user) {
		return calculateRewardsFutures.computeIfAbsent(user.getUserName(), k ->
				CompletableFuture.supplyAsync(() -> computeRewards(user), rewardExecutor));
	}

	private User computeRewards(User user) {
		List<Attraction> attractions = getAttractionsCached();

		final java.util.Set<String> rewardedAttractions = user.getUserRewards()
				.stream()
				.map(r -> r.attraction.attractionName)
				.collect(java.util.stream.Collectors.toSet());

		// Goes through all visits to assign the corresponding rewards
		for (VisitedLocation visitedLocation : user.getVisitedLocations()) {
			attractions.stream()
					.filter(attraction -> !rewardedAttractions.contains(attraction.attractionName))
					.filter(attraction -> nearAttraction(visitedLocation, attraction))
					.map(attraction -> new UserReward(visitedLocation, attraction, getRewardPoints(attraction, user)))
					.forEach(user::addUserReward);
		}
		return user;
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
		// Fast filter by bounding box in miles to avoid costly trigonometry
		final double buffer = proximityBuffer;
		final double dLat = Math.abs(visitedLocation.location.latitude - attraction.latitude);
		final double dLon = Math.abs(visitedLocation.location.longitude - attraction.longitude);
		// ~69 miles per degree of latitude; longitude depends on current latitude
		final double milesPerDegLat = 69.0;
		final double milesPerDegLon = 69.0 * Math.cos(Math.toRadians(visitedLocation.location.latitude));
		if (dLat * milesPerDegLat > buffer) return false;
		if (dLon * milesPerDegLon > buffer) return false;
		return getDistance(attraction, visitedLocation.location) > buffer ? false : true;
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
