package org.matsim.analysis.traveltime;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.*;
import org.matsim.api.core.v01.events.handler.*;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Person;

import java.util.*;

/**
 * Local replacement for org.matsim.contrib.analysis.vsp.traveltimedistance.CarTripsExtractor
 * which was removed in MATSim 2026.
 *
 * This handler extracts car trips from events.
 */
public class CarTripsExtractor implements PersonDepartureEventHandler, PersonArrivalEventHandler,
        LinkEnterEventHandler, LinkLeaveEventHandler, ActivityStartEventHandler {

    private final Set<Id<Person>> personsToAnalyze;
    private final Network network;
    private final Map<Id<Person>, CarTrip> ongoingTrips = new HashMap<>();
    private final Map<Id<Person>, Double> currentTripDistances = new HashMap<>();
    private final List<CarTrip> completedTrips = new ArrayList<>();

    public CarTripsExtractor(Set<Id<Person>> personsToAnalyze, Network network) {
        this.personsToAnalyze = personsToAnalyze;
        this.network = network;
    }

    @Override
    public void handleEvent(PersonDepartureEvent event) {
        if (!personsToAnalyze.contains(event.getPersonId())) {
            return;
        }
        if (!"car".equals(event.getLegMode())) {
            return;
        }

        CarTrip trip = new CarTrip(event.getPersonId());
        trip.setDepartureTime(event.getTime());

        Link departureLink = network.getLinks().get(event.getLinkId());
        if (departureLink != null) {
            trip.setDepartureLocation(departureLink.getCoord());
        }

        ongoingTrips.put(event.getPersonId(), trip);
        currentTripDistances.put(event.getPersonId(), 0.0);
    }

    @Override
    public void handleEvent(LinkLeaveEvent event) {
        // Add link length to trip distance when leaving a link
        Id<Person> driverId = getDriverId(event.getVehicleId());
        if (driverId == null || !ongoingTrips.containsKey(driverId)) {
            return;
        }

        Link link = network.getLinks().get(event.getLinkId());
        if (link != null) {
            double currentDistance = currentTripDistances.getOrDefault(driverId, 0.0);
            currentTripDistances.put(driverId, currentDistance + link.getLength());
        }
    }

    @Override
    public void handleEvent(LinkEnterEvent event) {
        // Nothing specific needed here for basic implementation
    }

    @Override
    public void handleEvent(PersonArrivalEvent event) {
        if (!personsToAnalyze.contains(event.getPersonId())) {
            return;
        }
        if (!"car".equals(event.getLegMode())) {
            return;
        }

        CarTrip trip = ongoingTrips.remove(event.getPersonId());
        if (trip == null) {
            return;
        }

        Link arrivalLink = network.getLinks().get(event.getLinkId());
        if (arrivalLink != null) {
            trip.setArrivalLocation(arrivalLink.getCoord());
        }

        trip.setActualTravelTime(event.getTime() - trip.getDepartureTime());
        trip.setTravelledDistance(currentTripDistances.getOrDefault(event.getPersonId(), 0.0));
        currentTripDistances.remove(event.getPersonId());

        completedTrips.add(trip);
    }

    @Override
    public void handleEvent(ActivityStartEvent event) {
        // Can be used to finalize trips if needed
    }

    private Id<Person> getDriverId(Id<?> vehicleId) {
        // Simple assumption: vehicle ID equals person ID for private cars
        String vehicleIdStr = vehicleId.toString();
        return Id.createPersonId(vehicleIdStr);
    }

    public List<CarTrip> getTrips() {
        return Collections.unmodifiableList(completedTrips);
    }

    @Override
    public void reset(int iteration) {
        ongoingTrips.clear();
        currentTripDistances.clear();
        completedTrips.clear();
    }
}
