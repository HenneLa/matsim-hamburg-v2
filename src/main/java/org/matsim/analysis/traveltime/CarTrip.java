package org.matsim.analysis.traveltime;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;

/**
 * Local replacement for org.matsim.contrib.analysis.vsp.traveltimedistance.CarTrip
 * which was removed in MATSim 2026.
 */
public class CarTrip {

    private final Id<Person> personId;
    private double departureTime;
    private Coord departureLocation;
    private Coord arrivalLocation;
    private double actualTravelTime;
    private double travelledDistance;
    private Double validatedTravelTime;
    private Double validatedTravelDistance;

    public CarTrip(Id<Person> personId) {
        this.personId = personId;
    }

    public Id<Person> getPersonId() {
        return personId;
    }

    public double getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(double departureTime) {
        this.departureTime = departureTime;
    }

    public Coord getDepartureLocation() {
        return departureLocation;
    }

    public void setDepartureLocation(Coord departureLocation) {
        this.departureLocation = departureLocation;
    }

    public Coord getArrivalLocation() {
        return arrivalLocation;
    }

    public void setArrivalLocation(Coord arrivalLocation) {
        this.arrivalLocation = arrivalLocation;
    }

    public double getActualTravelTime() {
        return actualTravelTime;
    }

    public void setActualTravelTime(double actualTravelTime) {
        this.actualTravelTime = actualTravelTime;
    }

    public double getTravelledDistance() {
        return travelledDistance;
    }

    public void setTravelledDistance(double travelledDistance) {
        this.travelledDistance = travelledDistance;
    }

    public Double getValidatedTravelTime() {
        return validatedTravelTime;
    }

    public void setValidatedTravelTime(Double validatedTravelTime) {
        this.validatedTravelTime = validatedTravelTime;
    }

    public Double getValidatedTravelDistance() {
        return validatedTravelDistance;
    }

    public void setValidatedTravelDistance(Double validatedTravelDistance) {
        this.validatedTravelDistance = validatedTravelDistance;
    }

    @Override
    public String toString() {
        return personId + ";" +
                departureTime + ";" +
                departureLocation.getX() + ";" +
                departureLocation.getY() + ";" +
                arrivalLocation.getX() + ";" +
                arrivalLocation.getY() + ";" +
                actualTravelTime + ";" +
                (validatedTravelTime != null ? validatedTravelTime : "") + ";" +
                travelledDistance + ";" +
                (validatedTravelDistance != null ? validatedTravelDistance : "");
    }
}
