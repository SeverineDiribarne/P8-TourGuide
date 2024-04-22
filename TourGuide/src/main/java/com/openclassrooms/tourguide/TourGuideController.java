package com.openclassrooms.tourguide;

import java.util.List;

import com.openclassrooms.tourguide.model.Destination;
import com.openclassrooms.tourguide.model.RecommendedDestinationsBasedOnTheUserLastLocation;
import com.openclassrooms.tourguide.service.RewardsService;
import gpsUtil.GpsUtil;
import gpsUtil.location.Location;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import gpsUtil.location.Attraction;
import gpsUtil.location.VisitedLocation;

import com.openclassrooms.tourguide.service.TourGuideService;
import com.openclassrooms.tourguide.model.user.user.User;
import com.openclassrooms.tourguide.model.user.user.UserReward;

import rewardCentral.RewardCentral;
import tripPricer.Provider;

@RestController
public class TourGuideController {

	@Autowired
	TourGuideService tourGuideService;
	
    @RequestMapping("/")
    public String index() {
        return "Greetings from TourGuide!";
    }
    
    @RequestMapping("/getlocation")
    public VisitedLocation getLocation(@RequestParam String userName) {
    	return tourGuideService.getUserLocation(getUser(userName));
    }
    
    //  Change this method to no longer return a List of Attractions.
 	//  Instead: Get the closest five tourist attractions to the user - no matter how far away they are.
 	//  Return a new JSON object that contains:
    	// Name of Tourist attraction, 
        // Tourist attractions lat/long, 
        // The user's location lat/long, 
        // The distance in miles between the user's location and each of the attractions.
        // The reward points for visiting each Attraction.
        //    Note: Attraction reward points can be gathered from RewardsCentral
    @RequestMapping("/getnearbyattractions")
    public RecommendedDestinationsBasedOnTheUserLastLocation getNearbyAttractions(@RequestParam String userName) {
        VisitedLocation visitedLocation = tourGuideService.getUserLocation(getUser(userName));
        List<Attraction> nearestAttractionsFromUser = tourGuideService.getNearByAttractions(visitedLocation);
        RewardsService rewardsService = new RewardsService(new GpsUtil(), new RewardCentral());
        RewardCentral rewardCentral = new RewardCentral();
        List<Destination> destinations = nearestAttractionsFromUser.stream()
                .map(
                        attraction -> {
                            Location attractionLocation = new Location(
                                    attraction.latitude,
                                    attraction.longitude);
                            return new Destination(
                                    attraction.attractionName,
                                    attractionLocation,
                                    rewardsService.getDistance(
                                            attractionLocation,
                                            visitedLocation.location),
                                    rewardCentral.getAttractionRewardPoints(attraction.attractionId,visitedLocation.userId));
                        })
                .toList();
        RecommendedDestinationsBasedOnTheUserLastLocation recommendedDestinationsBasedOnTheUserLastLocation = new RecommendedDestinationsBasedOnTheUserLastLocation(visitedLocation.location, destinations);
        return recommendedDestinationsBasedOnTheUserLastLocation;
    }
    
    @RequestMapping("/getrewards")
    public List<UserReward> getRewards(@RequestParam String userName) {
    	return tourGuideService.getUserRewards(getUser(userName));
    }
       
    @RequestMapping("/gettripdeals")
    public List<Provider> getTripDeals(@RequestParam String userName) {
    	return tourGuideService.getTripDeals(getUser(userName));
    }
    
    private User getUser(String userName) {
    	return tourGuideService.getUser(userName);
    }
   

}