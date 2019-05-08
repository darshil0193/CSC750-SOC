package actors;

import akka.actor.*;
import akka.japi.*;
import actors.*;
import play.libs.Json;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.JsonNode;
import scala.compat.java8.FutureConverters;
import actors.MarketActor.*;

import java.lang.reflect.Type;
import java.util.ArrayList;

import play.libs.Json;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static akka.pattern.Patterns.ask;
import static akka.pattern.PatternsCS.pipe;
import static controllers.HomeController.transactionHistory;

public class UserActor extends AbstractActor {

    private static Double currentUSDAmount = 0.0;
    private static Double possibleUSDAmount = 0.0;
    private static Double currentBTCRate = 0.0;
    private static Double possibleBTCRate = 0.0;
    private static ObjectMapper mapper = new ObjectMapper();
    private ActorRef marketActor;

    public UserActor(ActorRef marketActor) {
        this.marketActor = marketActor;
    }

    static public Props props(ActorRef marketActor) {
        return Props.create(UserActor.class, () -> new UserActor(marketActor));
    }

    static public class AddUSDAmount {
        public AddUSDAmount(Double amount) {
            currentUSDAmount += amount;
        }
    }

    static public class GetBalance {
        public GetBalance() {
        }
    }

    static public class CheckSellOffers {
        private Double maxRate = 0.0;
        private Double amount = 0.0;

        public CheckSellOffers(Double maxRate, Double amount) {
            this.maxRate = maxRate;
            this.amount = amount;
        }

        private ObjectNode checkOffer() {
            ObjectNode result = Json.newObject();
            ArrayNode transactionDetails = mapper.createArrayNode();
            ArrayNode localOrderBook = actors.MarketActor.orderBook.deepCopy();

            int i = 0;

            while (i < localOrderBook.size() && this.amount > 0) {
                ObjectNode transactionDetail = Json.newObject();
                if (localOrderBook.get(i).get("usdRate").asDouble() <= this.maxRate) {
                    transactionDetail.put("btcAmount", Math.min(localOrderBook.get(i).get("btcAmount").asDouble(), this.amount));
                    transactionDetail.put("usdRate", localOrderBook.get(i).get("usdRate").asDouble());
                    transactionDetail.put("offerID", localOrderBook.get(i).get("offerID").textValue());
//                    ((ObjectNode) localOrderBook.get(i)).put("btcAmount",
//                            localOrderBook.get(i).get("btcAmount").asDouble() - transactionDetail.get("btcAmount").asDouble());
                    this.amount -= transactionDetail.get("btcAmount").asDouble();
                    ++i;
                    if (transactionDetail.get("btcAmount").asDouble() != 0.0) {
                        transactionDetails.add(transactionDetail);
                    }
                } else {
//                    localOrderBook = actors.MarketActor.orderBook.deepCopy();
                    result.put("status", "error");
                    result.put("message", "The Maximum Rate provided is less than the minimum in the order book");
                    return result;
                }
            }

            if (this.amount == 0) {
                Double totalPrice = 0.0;
                Double totalBtc = 0.0;
//                actors.MarketActor.orderBook = localOrderBook.deepCopy();

                for (i = 0; i < transactionDetails.size(); ++i) {
                    Double btcAmount = transactionDetails.get(i).get("btcAmount").asDouble();
                    Double usdRate = transactionDetails.get(i).get("usdRate").asDouble();
                    totalBtc += btcAmount;
                    totalPrice += (btcAmount * usdRate);
                }
                result.put("status", "success");
                result.put("transactionDetails", transactionDetails);
                result.put("totalPrice", totalPrice);
                result.put("totalBtc", totalBtc);
            } else {
//                localOrderBook = actors.MarketActor.orderBook.deepCopy();
                result.put("status", "error");
                result.put("message", "The Maximum Rate provided is less than the minimum in the order book");
            }
            return result;
        }
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(AddUSDAmount.class, addUSDAmount -> {
                    ObjectNode result = Json.newObject();
                    result.put("status", "success");
                    sender().tell(result, self());
                })
                .match(GetBalance.class, getBalance -> {
                    ObjectNode result = Json.newObject();
                    result.put("status", "success");
                    result.put("usd", currentUSDAmount);
                    result.put("btc", currentBTCRate);
                    sender().tell(result, self());
                })
                .match(CheckSellOffers.class, checkSellOffersObj -> {
                    ObjectNode orderDetails = checkSellOffersObj.checkOffer();
                    ActorRef respondTo = sender();
                    List<CompletionStage<Object>> futures = new ArrayList<>();

                    if (Objects.equals(orderDetails.get("status").textValue(), "success") &&
                            orderDetails.get("totalPrice").asDouble() < currentUSDAmount) {
                        possibleUSDAmount = currentUSDAmount - orderDetails.get("totalPrice").asDouble();
                        possibleBTCRate = currentBTCRate + orderDetails.get("totalBtc").asDouble();
                        for (int i = 0; i < orderDetails.get("transactionDetails").size(); ++i) {
//                            possibleBTCRate += orderDetails.get("transactionDetails").get(i).get("btcAmount").asDouble();
                            futures.add(FutureConverters.toJava(ask(marketActor, new HoldRequest((ObjectNode) orderDetails.get("transactionDetails").get(i)), 5000)));
                        }

                        final CompletableFuture<Void> combinedFutures = CompletableFuture.allOf(
                                futures.toArray(new CompletableFuture[futures.size()]));

                        ArrayNode tempArray = mapper.createArrayNode();

                        combinedFutures.thenApply(future -> {
                            futures.stream()
                                    .map(f -> f.whenComplete((r, s) -> {
                                        tempArray.add((ObjectNode) mapper.convertValue(r, JsonNode.class));
                                    }))
                                    .collect(Collectors.toList());
                            ObjectNode result2 = Json.newObject();
                            for (int i = 0; i < tempArray.size(); ++i) {
                                if (!Objects.equals(tempArray.get(i).get("status").textValue(), "success")) {
                                    result2.put("status", "error");
                                    result2.put("holdResponse", tempArray);
                                    result2.put("message", "Unable to put hold");
                                    respondTo.tell(result2, self());
                                }
                            }

                            FutureConverters.toJava(ask(marketActor, new ConfirmRequest(), 5000))
                                    .whenComplete((result, failure) -> {
                                        ObjectNode resultObj = (ObjectNode)mapper.convertValue(result, JsonNode.class);
                                        if(Objects.equals(resultObj.get("status").textValue(), "success")) {
                                            resultObj.put("holdResponse", tempArray);
                                            currentUSDAmount = possibleUSDAmount;
                                            currentBTCRate = possibleBTCRate;
                                            ObjectNode transactionDetails = Json.newObject();
//                                            transactionDetails.put("transactionDetails", orderDetails.get("transactionDetails").deepCopy());
                                            transactionDetails.put("totalPrice", orderDetails.get("totalPrice").asDouble());
                                            transactionDetails.put("totalBtc", orderDetails.get("totalBtc").asDouble());
                                            transactionDetails.put("transactionID", resultObj.get("transactionID").asInt());
                                            transactionHistory.add(transactionDetails);
                                            respondTo.tell(resultObj, self());
                                        } else {
                                            respondTo.tell(resultObj, self());
                                        }
                                    });

                            return 1;
                        });
                    } else {
                        ObjectNode result = Json.newObject();
                        result.put("status", "error");
                        result.put("message", "Not enough USD");
                        respondTo.tell(result, self());
                    }
                })
                .build();
    }
}