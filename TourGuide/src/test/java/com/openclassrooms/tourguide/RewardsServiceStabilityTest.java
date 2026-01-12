package com.openclassrooms.tourguide;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.VisitedLocation;
import rewardCentral.RewardCentral;
import com.openclassrooms.tourguide.helper.InternalTestHelper;
import com.openclassrooms.tourguide.service.RewardsService;
import com.openclassrooms.tourguide.service.TourGuideService;
import com.openclassrooms.tourguide.model.user.user.User;
import com.openclassrooms.tourguide.model.user.user.UserReward;

public class RewardsServiceStabilityTest {

    @Test
    public void calculateRewards_isStable_noDuplicate() {
        GpsUtil gpsUtil = new GpsUtil();
        RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());

        InternalTestHelper.setInternalUserNumber(0);
        TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService, false);

        User user = new User(UUID.randomUUID(), "stable-user", "000", "stable@tourGuide.com");
        Attraction attraction = gpsUtil.getAttractions().get(0);
        user.addToVisitedLocations(new VisitedLocation(user.getUserId(), attraction, new Date()));

        // First run
        rewardsService.calculateRewards(user).join();
        List<UserReward> firstRewards = user.getUserRewards();
        assertEquals(1, firstRewards.size(), "Expected one reward after first calculation");
        String rewardedName = firstRewards.get(0).attraction.attractionName;

        // Second run (idempotence, no duplicates)
        rewardsService.calculateRewards(user).join();
        List<UserReward> secondRewards = user.getUserRewards();
        assertEquals(1, secondRewards.size(), "No duplicate rewards after second calculation");
        assertEquals(rewardedName, secondRewards.get(0).attraction.attractionName,
                "Reward remains for the same attraction");

        // Third run to confirm stability
        rewardsService.calculateRewards(user).join();
        List<UserReward> thirdRewards = user.getUserRewards();
        assertEquals(1, thirdRewards.size(), "Still no duplicate after third calculation");
        assertEquals(rewardedName, thirdRewards.get(0).attraction.attractionName);

        tourGuideService.tracker.stopTracking();
    }
}
