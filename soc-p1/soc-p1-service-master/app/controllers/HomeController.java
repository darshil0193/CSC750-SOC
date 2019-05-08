package controllers;

import play.mvc.*;
import com.google.inject.Inject;
import play.data.DynamicForm;
import play.data.FormFactory;
import play.Logger;
import play.libs.Json;
import com.fasterxml.jackson.databind.node.ObjectNode;
import play.db.Database;

import java.sql.*;
import java.text.SimpleDateFormat;


/**
 * This controller contains an action to handle HTTP requests
 * to the application's home page.
 */
public class HomeController extends Controller {
    @Inject Database db;
    @Inject FormFactory formFactory;

    private Connection conn = null;
    private Statement stmt = null;
    private ResultSet rs = null;
    private PreparedStatement pstmt = null;

    public Result handleUpdates() {
        DynamicForm dynamicForm = formFactory.form().bindFromRequest();
        ObjectNode result = Json.newObject();

        try {
            String username = dynamicForm.get("username");

            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Date parsedDate = new java.sql.Date(dateFormat.parse(dynamicForm.get("currentTimestamp")).getTime());
            Timestamp currentTimestamp = new java.sql.Timestamp(parsedDate.getTime());

            Double latitude = Double.parseDouble(dynamicForm.get("latitude"));
            Double longitude = Double.parseDouble(dynamicForm.get("longitude"));

            Logger.info(new Timestamp(System.currentTimeMillis()) + " Received Username: " + username);
            Logger.info(new Timestamp(System.currentTimeMillis()) + " Received Latitude: " + latitude);
            Logger.info(new Timestamp(System.currentTimeMillis()) + " Received Longitude: " + longitude);

            insertData(username, currentTimestamp, latitude, longitude, result);
            calculateUpdateInterval(username, result);

            Logger.info(new Timestamp(System.currentTimeMillis()) + " Total Distance: " + result.get("totalDistanceTillNow").asDouble() + " m");
            Logger.info(new Timestamp(System.currentTimeMillis()) + " Average Speed: " + result.get("averageSpeed").asDouble() + " m/s");
            Logger.info(new Timestamp(System.currentTimeMillis()) + " Sent Wait Time: " + result.get("updateInterval").asDouble() / 1000 + " s");

            return ok(result);
        } catch (Exception e) {
            result.put("status", "Error");
            result.put("message", e.toString());

            return internalServerError(result);
        }
    }

    private void calculateUpdateInterval(String username, ObjectNode result) throws SQLException {
        String selectQuery = "SELECT * FROM locationTracker WHERE username = '" + username + "' ORDER BY currentTimestamp DESC";

        try {
            conn = db.getConnection();
            stmt = conn.createStatement();
            rs = stmt.executeQuery(selectQuery);

            int i = 0;
            Double distanceTravelledInLastFiveUpdates = 0.0;
            double oldLatitude = 0.0;
            double oldLongitude = 0.0;

            java.util.Date date = new java.util.Date();
            Timestamp startTime = new Timestamp(date.getTime());
            Timestamp endTime = new Timestamp(date.getTime());

            while(i < 5 && rs.next()) {
                if(i == 0) {
                    endTime = rs.getTimestamp("currentTimestamp");
                    oldLatitude = rs.getDouble("latitude");
                    oldLongitude = rs.getDouble("longitude");
                }

                Double newDistance = calculateDistance(oldLatitude, rs.getDouble("latitude"), oldLongitude, rs.getDouble("longitude"));
                distanceTravelledInLastFiveUpdates += newDistance;
                startTime = rs.getTimestamp("currentTimestamp");

                oldLatitude = rs.getDouble("latitude");
                oldLongitude = rs.getDouble("longitude");
                ++i;
            }

            long seconds = (endTime.getTime() - startTime.getTime())/1000;
            double averageSpeed = 0.0;

            if(seconds != 0) {
                averageSpeed = distanceTravelledInLastFiveUpdates/seconds;
            }

            result.put("averageSpeed", averageSpeed);

            if(averageSpeed <= 1) {
                result.put("updateInterval", 5000);
            } else if(averageSpeed >= 20) {
                result.put("updateInterval", 1000);
            } else {
                result.put("updateInterval", Math.round(((99 - (4 * averageSpeed)) / 19) * 1000));
            }

        } finally {
            close(rs);
            close(stmt);
            close(conn);
        }
    }

    private static double calculateDistance(double lat1, double lat2, double lon1, double lon2) {

        final int R = 6371; // Radius of the earth

        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = R * c * 1000; // convert to meters

        distance = Math.pow(distance, 2) + Math.pow(0, 2);

        return Math.sqrt(distance);
    }

    private void insertData(String username, Timestamp currentTimestamp, Double latitude, Double longitude, ObjectNode result) throws Exception {
        try {
            conn = db.getConnection();
            String insertQuery = "INSERT INTO locationTracker(username, currentTimestamp, latitude, longitude, totalDistanceTillNow) VALUES(?, ?, ?, ?, ?)";
            String selectQuery = "SELECT * FROM locationTracker WHERE username = '" + username + "' ORDER BY currentTimestamp DESC LIMIT 1";

            stmt = conn.createStatement();
            rs = stmt.executeQuery(selectQuery);

            Double totalDistanceTillNow = 0.0;

            while(rs.next()) {
                totalDistanceTillNow = rs.getDouble("totalDistanceTillNow");

                Double newDistance = calculateDistance(rs.getDouble("latitude"), latitude, rs.getDouble("longitude"), longitude);
                totalDistanceTillNow += newDistance;
            }


            pstmt = conn.prepareStatement(insertQuery);

            pstmt.setString(1, username);
            pstmt.setTimestamp(2, currentTimestamp);
            pstmt.setDouble(3, latitude);
            pstmt.setDouble(4, longitude);
            pstmt.setDouble(5, totalDistanceTillNow);

            pstmt.executeUpdate();

            stmt = conn.createStatement();
            rs = stmt.executeQuery(selectQuery);

            rs.next();
            result.put("status", "Success");
//            result.put("username", rs.getString("username"));
//            result.put("currentTimestamp", rs.getTimestamp("currentTimestamp").toString());
//            result.put("latitude", rs.getDouble("latitude"));
//            result.put("longitude", rs.getDouble("longitude"));
            result.put("totalDistanceTillNow", rs.getDouble("totalDistanceTillNow"));
        } finally {
            close(rs);
            close(stmt);
            close(pstmt);
            close(conn);
        }
    }

    private void close(Connection conn) {
        if(conn != null) {
            try { conn.close(); } catch(Throwable whatever) {}
        }
    }

    private void close(Statement st) {
        if(st != null) {
            try { st.close(); } catch(Throwable whatever) {}
        }
    }

    private void close(ResultSet rs) {
        if(rs != null) {
            try { rs.close(); } catch(Throwable whatever) {}
        }
    }

    private void close(PreparedStatement pstmt) {
        if(pstmt != null) {
            try { pstmt.close(); } catch(Throwable whatever) {}
        }
    }


    /**
     * An action that renders an HTML page with a welcome message.
     * The configuration in the <code>routes</code> file means that
     * this method will be called when the application receives a
     * <code>GET</code> request with a path of <code>/</code>.
     */
    public Result index() {
        return ok(views.html.index.render());
    }
}
