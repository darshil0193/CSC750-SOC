package actors;

import akka.actor.*;
import akka.japi.*;
import play.libs.Json;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.Random;

import static controllers.HomeController.transactionHistory;

public class MarketActor extends AbstractActor {
    private static ObjectMapper mapper = new ObjectMapper();
    public static ArrayNode orderBook = mapper.createArrayNode();
    public static ArrayNode heldOrderDetails = mapper.createArrayNode();
    private static int currentTransactionId = -1;
    private static boolean debugConfirmFail = false;
    private static boolean debugConfirmNoResponse = false;

    public MarketActor() throws IOException {
            File from = new File("app/data/orderBook.json");
            orderBook = (ArrayNode) mapper.readTree(from);
            sortOrderBook(orderBook);
            currentTransactionId = new Random().nextInt(99999999) + 1;
    }

    private void sortOrderBook(ArrayNode orderBook) {
        for (int i = 0; i < orderBook.size() - 1; ++i) {
            for (int j = i + 1; j < orderBook.size(); ++j) {
                if (orderBook.get(i).get("usdRate").asDouble() > orderBook.get(j).get("usdRate").asDouble()) {
                    swap(i, j);
                }
            }
        }
    }

    private void swap(int i, int j) {
        JsonNode temp = orderBook.get(i);
        orderBook.set(i, orderBook.get(j));
        orderBook.set(j, (JsonNode) temp);
    }

    static public Props props() {
        return Props.create(MarketActor.class, () -> new MarketActor());
    }

    static public class GetSellOffers {
        public GetSellOffers() {
        }

        private ObjectNode getOfferIDs() {
            ArrayNode offerIDs = mapper.createArrayNode();
            for (int i = 0; i < orderBook.size(); ++i) {
                offerIDs.add(orderBook.get(i).get("offerID"));
            }
            ObjectNode result = Json.newObject();
            result.put("status", "success");
            result.put("offers", offerIDs);
            return result;
        }
    }

    static public class GetSellOfferDetails {
        private String offerID;

        public GetSellOfferDetails(String offerID) {
            this.offerID = offerID;
        }

        private ObjectNode getOfferDetails() {
            ObjectNode result = Json.newObject();
            int offerIndex = -1;
            for (int i = 0; i < orderBook.size(); ++i) {
                if (Objects.equals(orderBook.get(i).get("offerID").textValue(), this.offerID)) {
                    offerIndex = i;
                }
            }

            if (offerIndex < 0) {
                result.put("status", "error");
                result.put("message", "Offer ID (" + this.offerID + ") not found");
            } else {
                ObjectNode offer = Json.newObject();
                offer = (ObjectNode) orderBook.get(offerIndex);
                result.put("status", "success");
                result.put("rate", offer.get("usdRate"));
                result.put("amount", offer.get("btcAmount"));
            }

            return result;
        }
    }

    static public class ConfirmRequest {
        private ObjectNode confirmOrder() {
            ObjectNode result = Json.newObject();
            if(!debugConfirmFail) {
                heldOrderDetails.removeAll();
                currentTransactionId = new Random().nextInt(99999999) + 1;
                result.put("status", "success");
                result.put("transactionID", currentTransactionId);
            } else {
                revertOrder();
                heldOrderDetails.removeAll();
                result.put("status", "error");
                result.put("message", "Debug Confirm Fail was hit");
            }
            return result;
        }

        private static void revertOrder() {
            for(int i=0; i<heldOrderDetails.size(); ++i) {
                for(int j=0;j<orderBook.size(); ++j) {
                    if(Objects.equals(heldOrderDetails.get(i).get("offerID").textValue(), orderBook.get(j).get("offerID").textValue())) {
                        Double currentBtcAmount = orderBook.get(j).get("btcAmount").asDouble();
                        Double newBtcAmount = currentBtcAmount + heldOrderDetails.get(i).get("btcAmount").asDouble();
                        ((ObjectNode)orderBook.get(j)).put("btcAmount", newBtcAmount);
                    }
                }
            }
        }

    }

    static public class GetTransactions {
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        public GetTransactions(Connection conn) {
            this.conn = conn;
        }

        public ObjectNode getAllTransactions() throws SQLException {
            String selectQuery = "SELECT * FROM Logs";
            this.stmt = this.conn.createStatement();
            this.rs = stmt.executeQuery(selectQuery);

            ObjectNode result = Json.newObject();
            ArrayNode transactionIDs = mapper.createArrayNode();

            while(rs.next()) {
                transactionIDs.add(rs.getInt("transactionID"));
            }
            result.put("status", "success");
            result.put("transactions", transactionIDs);
            return result;
        }
    }

    static public class DebugConfirmFail {

    }

    static public class DebugConfirmNoResponse {

    }

    static public class DebugReset {

    }

    static public class GetTransactionDetails {
        int transactionID;
        public GetTransactionDetails(int transactionID) {
            this.transactionID = transactionID;
        }

        public ObjectNode getTransactionDetails() {
            ObjectNode result = Json.newObject();
            Boolean found = false;
            Double totalPrice = 0.0;
            Double totalBtc = 0.0;
            for(int i=0; i<transactionHistory.size(); ++i) {
                if(!found && transactionHistory.get(i).get("transactionID").asInt() == this.transactionID) {
                    totalPrice = transactionHistory.get(i).get("totalPrice").asDouble();
                    totalBtc = transactionHistory.get(i).get("totalBtc").asDouble();
                    found = true;
                    break;
                }
            }
            if(!found) {
                result.put("status", "error");
                result.put("message", "Transaction ID (" + this.transactionID + ") not found");
            } else {
                result.put("status", "success");
                result.put("effectiveRate", Math.round((totalPrice / totalBtc)*100.0)/100.0);
                result.put("transactionID", this.transactionID);
            }
            return result;
        }
    }

    static public class HoldRequest {
        private String offerID;
        private Double quantity;

        public HoldRequest(ObjectNode orderDetails) {
            this.offerID = orderDetails.get("offerID").textValue();
            this.quantity = orderDetails.get("btcAmount").asDouble();
        }

        public ObjectNode checkOffer() {
            ObjectNode result = Json.newObject();
            ObjectNode currentOfferDetails = Json.newObject();
            Integer orderBookPosition = -1;

            for(int i=0; i<orderBook.size(); ++i) {
                if(Objects.equals(orderBook.get(i).get("offerID").textValue(), this.offerID)) {
                    orderBookPosition = i;
                    currentOfferDetails = orderBook.get(i).deepCopy();
                    break;
                }
            }

            if(currentOfferDetails.get("btcAmount").asDouble() >= this.quantity) {
                Double currentBtcAmount = orderBook.get(orderBookPosition).get("btcAmount").asDouble();
                Double newBtcAmount = currentBtcAmount - this.quantity;
                ((ObjectNode)orderBook.get(orderBookPosition)).put("btcAmount", newBtcAmount);
            }

            currentOfferDetails.put("btcAmount", this.quantity);
            heldOrderDetails.add(currentOfferDetails);
            result.put("status", "success");
            return result;
        }
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(GetSellOffers.class, getSellOffersObj -> {
                    sender().tell(getSellOffersObj.getOfferIDs(), self());
                })
                .match(GetSellOfferDetails.class, getSellOfferDetailsObj -> {
                    sender().tell(getSellOfferDetailsObj.getOfferDetails(), self());
                })
                .match(HoldRequest.class, holdRequestObj -> {
                    ObjectNode t = holdRequestObj.checkOffer();
                    sender().tell(t, self());
                    new java.util.Timer().schedule(
                            new java.util.TimerTask() {
                                @Override
                                public void run() {
                                    ConfirmRequest.revertOrder();
                                    heldOrderDetails.removeAll();
                                }
                            }, 5000);
                })
                .match(ConfirmRequest.class, confirmRequestObj -> {
                    if(!debugConfirmNoResponse) {
                        sender().tell(confirmRequestObj.confirmOrder(), self());
                    }
                })
                .match(GetTransactions.class, getTransactionsObj -> {
                    sender().tell(getTransactionsObj.getAllTransactions(), self());
                })
                .match(GetTransactionDetails.class, getTransactionDetailsObj -> {
                    sender().tell(getTransactionDetailsObj.getTransactionDetails(), self());
                })
                .match(DebugConfirmFail.class, debugConfirmFailObj -> {
                    debugConfirmFail = true;
                    ObjectNode result = Json.newObject();
                    result.put("status", "success");
                    sender().tell(result, self());
                })
                .match(DebugConfirmNoResponse.class, debugConfirmNoResponseObj -> {
                    debugConfirmNoResponse = true;
                    ObjectNode result = Json.newObject();
                    result.put("status", "success");
                    sender().tell(result, self());
                })
                .match(DebugReset.class, debugResetObj -> {
                    debugConfirmFail = false;
                    debugConfirmNoResponse = false;
                    ObjectNode result = Json.newObject();
                    result.put("status", "success");
                    sender().tell(result, self());
                })
                .build();
    }
}