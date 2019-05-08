package controllers;

import akka.actor.*;
import play.mvc.*;

import java.io.IOException;

import scala.compat.java8.FutureConverters;

import javax.inject.*;
import java.util.Objects;
import java.util.TimeZone;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import play.libs.Json;
import actors.*;
import actors.UserActor.*;
import actors.MarketActor.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import play.db.Database;

import java.sql.*;

import static akka.pattern.Patterns.ask;

/**
 * This controller contains an action to handle HTTP requests
 * to the application's home page.
 */
public class HomeController extends Controller {
    @Inject Database db;
    private Connection conn = null;
    private PreparedStatement pstmt = null;

    private ActorRef userActor;
    private ActorRef marketActor;
    private static ObjectMapper mapper = new ObjectMapper();
    public static ArrayNode transactionHistory = mapper.createArrayNode();

    @Inject
    public HomeController(ActorSystem system) throws SQLException {
        marketActor = system.actorOf(MarketActor.props(), "marketActor");
        userActor = system.actorOf(UserActor.props(marketActor), "userActor");
    }

    public CompletionStage<Result> addUSDAmount(String amount) {
        return FutureConverters.toJava(ask(userActor, new AddUSDAmount(Double.parseDouble(amount)), 5000))
                .thenApply(result -> ok(mapper.convertValue(result, JsonNode.class)));
    }

    public CompletionStage<Result> getBalance() {
        return FutureConverters.toJava(ask(userActor, new GetBalance(), 5000))
                .thenApply(result -> ok(mapper.convertValue(result, JsonNode.class)));
    }

    public CompletionStage<Result> getTransactions() {
        return FutureConverters.toJava(ask(marketActor, new GetTransactions(db.getConnection()), 5000))
                .thenApply(result -> ok(mapper.convertValue(result, JsonNode.class)));
    }

    public CompletionStage<Result> getTransactionDetails(String transactionID) {
        return FutureConverters.toJava(ask(marketActor, new GetTransactionDetails(Integer.parseInt(transactionID)), 5000))
                .thenApply(result -> {
                    ObjectNode resultObj = (ObjectNode) mapper.convertValue(result, JsonNode.class);
                    if(Objects.equals(resultObj.get("status").textValue(), "success")) {
                        return ok(mapper.convertValue(result, JsonNode.class));
                    } else {
                        return notFound(mapper.convertValue(result, JsonNode.class));
                    }
                });
    }

    public CompletionStage<Result> getSellOffers() {
        return FutureConverters.toJava(ask(marketActor, new GetSellOffers(), 5000))
                .thenApply(result -> ok(mapper.convertValue(result, JsonNode.class)))
                .exceptionally(result -> {
                    ObjectNode result2 = Json.newObject();
                    result2.put("status", "error");
                    result2.put("message", "Unable to read the order Book JSON File");
                    return notFound(result2);
                });
    }

    public CompletionStage<Result> getSellOfferDetails(String offerID) {
        return FutureConverters.toJava(ask(marketActor, new GetSellOfferDetails(offerID), 5000))
                .thenApply(result -> {
                    String status = mapper.convertValue(result, JsonNode.class).get("status").textValue();
                    if (status.equals("success")) {
                        return ok(mapper.convertValue(result, JsonNode.class));
                    } else {
                        return notFound(mapper.convertValue(result, JsonNode.class));
                    }
                }).exceptionally(result -> {
                    ObjectNode result2 = Json.newObject();
                    result2.put("status", "error");
                    result2.put("message", "Unable to read the order Book JSON File");
                    return notFound(result2);
                });
    }

    public CompletionStage<Result> buyTransaction(String maxRate, String amount) {
        final long startTime = System.currentTimeMillis();
        return FutureConverters.toJava(ask(userActor,
                new CheckSellOffers(Double.parseDouble(maxRate), Double.parseDouble(amount)), 5000))
                .thenApply(result -> {
                    ObjectNode resultObj = (ObjectNode)mapper.convertValue(result, JsonNode.class);
                    if(Objects.equals(resultObj.get("status").textValue(), "success")) {
                        try {
                            final long endTime = System.currentTimeMillis();
                            String insertQuery = "INSERT INTO Logs (timeStamp, transactionID, holdResponse, expirationTime, confirmResponse) VALUES(?,?,?,?,?)";
                            conn = db.getConnection();
                            pstmt = conn.prepareStatement(insertQuery);
                            pstmt.setLong(1, endTime);
                            pstmt.setInt(2, resultObj.get("transactionID").asInt());
                            pstmt.setString(3, resultObj.get("holdResponse").toString());
                            pstmt.setLong(4, (endTime - startTime)/1000);
                            ObjectNode confirmResponse = Json.newObject();
                            confirmResponse.put("status", resultObj.get("status"));
                            confirmResponse.put("transactionID", resultObj.get("transactionID"));
                            pstmt.setString(5, confirmResponse.toString());
                            pstmt.executeUpdate();
                        } catch(SQLException e) {
                            ObjectNode errorObj = Json.newObject();
                            errorObj.put("status", "error");
                            errorObj.put("message", e.toString());
                            return 	internalServerError(errorObj);
                        } finally {
                            close(pstmt);
                            close(conn);
                        }

                        return ok((ObjectNode)mapper.convertValue(result, JsonNode.class));
                    } else {
                        return internalServerError((ObjectNode)mapper.convertValue(result, JsonNode.class));
                    }
                });

    }

    private void close(Connection conn) {
        if(conn != null) {
            try { conn.close(); } catch(Throwable ignored) {}
        }
    }

    private void close(PreparedStatement pstmt) {
        if(pstmt != null) {
            try { pstmt.close(); } catch(Throwable ignored) {}
        }
    }

    public CompletionStage<Result> debugConfirmFail() {
        return FutureConverters.toJava(ask(marketActor, new DebugConfirmFail(), 5000))
                .thenApply(result -> ok(mapper.convertValue(result, JsonNode.class)))
                .exceptionally(result -> {
                    ObjectNode result2 = Json.newObject();
                    result2.put("status", "error");
                    result2.put("message", "Unable to read the orderBook JSON File");
                    return notFound(result2);
                });
    }

    public CompletionStage<Result> debugConfirmNoResponse() {
        return FutureConverters.toJava(ask(marketActor, new DebugConfirmNoResponse(), 5000))
                .thenApply(result -> ok(mapper.convertValue(result, JsonNode.class)))
                .exceptionally(result -> {
                    ObjectNode result2 = Json.newObject();
                    result2.put("status", "error");
                    result2.put("message", "Unable to read the order Book JSON File");
                    return notFound(result2);
                });
    }

    public CompletionStage<Result> debugReset() {
        return FutureConverters.toJava(ask(marketActor, new DebugReset(), 5000))
                .thenApply(result -> ok(mapper.convertValue(result, JsonNode.class)))
                .exceptionally(result -> {
                    ObjectNode result2 = Json.newObject();
                    result2.put("status", "error");
                    result2.put("message", "Unable to read the order Book JSON File");
                    return notFound(result2);
                });
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
