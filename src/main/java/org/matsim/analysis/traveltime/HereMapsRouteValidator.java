package org.matsim.analysis.traveltime;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.utils.geometry.CoordinateTransformation;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Local replacement for org.matsim.contrib.analysis.vsp.traveltimedistance.HereMapsRouteValidator
 * which may have been removed or moved in MATSim 2026.
 *
 * This class validates simulated travel times against HERE Maps API.
 */
public class HereMapsRouteValidator {

    private static final Logger log = LogManager.getLogger(HereMapsRouteValidator.class);

    private final String outputDirectory;
    private final String apiKey;
    private final String date;
    private final CoordinateTransformation transformation;
    private final boolean useDetailedOutput;
    private boolean writeDetailedFiles = true;

    public HereMapsRouteValidator(String outputDirectory, String apiKey, String date,
                                   CoordinateTransformation transformation, boolean useDetailedOutput) {
        this.outputDirectory = outputDirectory;
        this.apiKey = apiKey;
        this.date = date;
        this.transformation = transformation;
        this.useDetailedOutput = useDetailedOutput;

        // Create output directory if it doesn't exist
        File dir = new File(outputDirectory);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    public void setWriteDetailedFiles(boolean writeDetailedFiles) {
        this.writeDetailedFiles = writeDetailedFiles;
    }

    /**
     * Get travel time and distance from HERE Maps API for a given trip.
     *
     * @param trip The car trip to validate
     * @return Tuple of (travel time in seconds, distance in meters) or null if API call fails
     */
    public Tuple<Double, Double> getTravelTime(CarTrip trip) {
        if (trip.getDepartureLocation() == null || trip.getArrivalLocation() == null) {
            log.warn("Trip has null departure or arrival location, skipping validation");
            return null;
        }

        try {
            // Transform coordinates to WGS84
            double originLat = trip.getDepartureLocation().getY();
            double originLon = trip.getDepartureLocation().getX();
            double destLat = trip.getArrivalLocation().getY();
            double destLon = trip.getArrivalLocation().getX();

            if (transformation != null) {
                org.matsim.api.core.v01.Coord originWgs84 = transformation.transform(trip.getDepartureLocation());
                org.matsim.api.core.v01.Coord destWgs84 = transformation.transform(trip.getArrivalLocation());
                originLat = originWgs84.getY();
                originLon = originWgs84.getX();
                destLat = destWgs84.getY();
                destLon = destWgs84.getX();
            }

            // Build HERE Maps Routing API v8 URL
            String urlString = String.format(
                    "https://router.hereapi.com/v8/routes?transportMode=car&origin=%f,%f&destination=%f,%f&return=summary&apiKey=%s",
                    originLat, originLon, destLat, destLon, apiKey);

            // Add departure time if date is specified
            if (date != null && !date.isEmpty()) {
                // Convert departure time to ISO format
                int hours = (int) (trip.getDepartureTime() / 3600);
                int minutes = (int) ((trip.getDepartureTime() % 3600) / 60);
                int seconds = (int) (trip.getDepartureTime() % 60);
                String departureTime = String.format("%sT%02d:%02d:%02d", date, hours, minutes, seconds);
                urlString += "&departureTime=" + departureTime;
            }

            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder response = new StringBuilder();
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                // Parse JSON response (simple parsing without external library)
                String jsonResponse = response.toString();
                Double travelTime = parseJsonValue(jsonResponse, "duration");
                Double distance = parseJsonValue(jsonResponse, "length");

                if (travelTime != null && distance != null) {
                    if (writeDetailedFiles) {
                        writeDetailedOutput(trip, travelTime, distance);
                    }
                    return new Tuple<>(travelTime, distance);
                }
            } else {
                log.warn("HERE API returned response code: " + responseCode);
            }

            conn.disconnect();

        } catch (Exception e) {
            log.warn("Error calling HERE API: " + e.getMessage());
        }

        return null;
    }

    private Double parseJsonValue(String json, String key) {
        try {
            String searchKey = "\"" + key + "\":";
            int startIndex = json.indexOf(searchKey);
            if (startIndex == -1) return null;

            startIndex += searchKey.length();
            int endIndex = startIndex;

            // Skip whitespace
            while (endIndex < json.length() && Character.isWhitespace(json.charAt(endIndex))) {
                endIndex++;
            }
            startIndex = endIndex;

            // Find end of number
            while (endIndex < json.length() &&
                    (Character.isDigit(json.charAt(endIndex)) || json.charAt(endIndex) == '.' || json.charAt(endIndex) == '-')) {
                endIndex++;
            }

            String valueStr = json.substring(startIndex, endIndex);
            return Double.parseDouble(valueStr);
        } catch (Exception e) {
            return null;
        }
    }

    private void writeDetailedOutput(CarTrip trip, double travelTime, double distance) {
        try {
            File outputFile = new File(outputDirectory + "trip_" + trip.getPersonId() + "_" + (int) trip.getDepartureTime() + ".txt");
            try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
                writer.println("PersonId: " + trip.getPersonId());
                writer.println("DepartureTime: " + trip.getDepartureTime());
                writer.println("SimulatedTravelTime: " + trip.getActualTravelTime());
                writer.println("ValidatedTravelTime: " + travelTime);
                writer.println("SimulatedDistance: " + trip.getTravelledDistance());
                writer.println("ValidatedDistance: " + distance);
            }
        } catch (IOException e) {
            log.warn("Error writing detailed output: " + e.getMessage());
        }
    }
}
