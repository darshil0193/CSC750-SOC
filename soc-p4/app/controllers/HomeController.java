package controllers;

import play.mvc.*;
import openllet.jena.PelletReasonerFactory;

import org.apache.jena.ontology.*;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.util.FileManager;
import org.apache.jena.reasoner.*;
import org.apache.jena.shared.JenaException;
import org.apache.jena.*;

import play.libs.Json;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.*;
/**
 * This controller contains an action to handle HTTP requests
 * to the application's home page.
 */
public class HomeController extends Controller {

    String source_file = "owl/csc750.owl"; // This is your file on the disk
    String source_url = "http://www.semanticweb.org/darsh/ontologies/csc750.owl"; // Remember that IRI from before?
    String NS = source_url + "#";
    OntModel ontReasoned;

    HomeController() {
        loadOntology();
    }

    private void loadOntology() {
        // Read the ontology. No reasoner yet.
        OntModel baseOntology = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM);
        try {
            InputStream in = FileManager.get().open(source_file);
            try {
                baseOntology.read(in, null);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } catch (JenaException je) {
            System.err.println("ERROR" + je.getMessage());
            je.printStackTrace();
            System.exit(0);
        }

        baseOntology.setNsPrefix("csc750", NS); // Just for compact printing; doesn't really matter


        // This will create an ontology that has a reasoner attached.
        // This means that it will automatically infer classes an individual belongs to, according to restrictions, etc.
        ontReasoned = ModelFactory.createOntologyModel(PelletReasonerFactory.THE_SPEC, baseOntology);
    }

    public Result addMerchant(String uniqueID) {
        OntClass merchant = ontReasoned.getOntClass(NS + "Merchant");
        Individual individualMerchant1 = ontReasoned.createIndividual(NS + uniqueID, merchant);

        ObjectNode result = Json.newObject();
        if(individualMerchant1.hasOntClass(merchant)) {
            result.put("status", "success");
        } else {
            result.put("status", "error");
            result.put("message", "Unable to create the individual");
        }
        return ok(result);
    }

    public Result addConsumer(String uniqueID) {
        OntClass consumer = ontReasoned.getOntClass(NS + "Consumer");
        Individual individualConsumer1 = ontReasoned.createIndividual(NS + uniqueID, consumer);

        ObjectNode result = Json.newObject();
        if(individualConsumer1.hasOntClass(consumer)) {
            result.put("status", "success");
        } else {
            result.put("status", "error");
            result.put("message", "Unable to create the individual");
        }

        return ok(result);
    }

    public Result addTransaction(String senderID, String receiverID, String transactionID) {
//        Getting required
        OntClass transaction = ontReasoned.getOntClass(NS + "Transaction");

//        Getting properties
        OntProperty hasSender = ontReasoned.getObjectProperty(NS + "hasSender");
        OntProperty hasReceiver = ontReasoned.getObjectProperty(NS + "hasReceiver");
        OntProperty isReceiverOf = ontReasoned.getObjectProperty(NS + "isReceiverOf");

//        Getting individuals
        Individual sender = ontReasoned.getIndividual(NS + senderID);
        Individual receiver = ontReasoned.getIndividual(NS + receiverID);

//        Creating individuals
        Individual individualTransaction1 = ontReasoned.createIndividual(NS + transactionID, transaction);

//        Adding Properties
        individualTransaction1.addProperty(hasSender, sender);
        individualTransaction1.addProperty(hasReceiver, receiver);
        receiver.addProperty(isReceiverOf, individualTransaction1);

        ObjectNode result = Json.newObject();
        if(individualTransaction1.hasOntClass(transaction)) {
            result.put("status", "success");
        } else {
            result.put("status", "error");
            result.put("message", "Unable to create the individual");
        }
        return ok(result);
    }

    public Result isCommercial(String transactionID) {
        OntClass commercial = ontReasoned.getOntClass(NS + "CommercialTransaction");
        Individual transaction = ontReasoned.getIndividual(NS + transactionID);

        ObjectNode result = Json.newObject();
        result.put("result", transaction.hasOntClass(commercial));
        return ok(result);
    }

    public Result isPersonal(String transactionID) {
        OntClass personal = ontReasoned.getOntClass(NS + "PersonalTransaction");
        Individual transaction = ontReasoned.getIndividual(NS + transactionID);

        ObjectNode result = Json.newObject();
        result.put("result", transaction.hasOntClass(personal));
        return ok(result);
    }

    public Result isPurchase(String transactionID) {
        OntClass purchase = ontReasoned.getOntClass(NS + "PurchaseTransaction");
        Individual transaction = ontReasoned.getIndividual(NS + transactionID);

        ObjectNode result = Json.newObject();
        result.put("result", transaction.hasOntClass(purchase));
        return ok(result);
    }

    public Result isRefund(String transactionID) {
        OntClass refund = ontReasoned.getOntClass(NS + "RefundTransaction");
        Individual transaction = ontReasoned.getIndividual(NS + transactionID);

        ObjectNode result = Json.newObject();
        result.put("result", transaction.hasOntClass(refund));
        return ok(result);
    }

    public Result isTrusted(String merchantID) {
        OntClass trustedMerchant = ontReasoned.getOntClass(NS + "TrustedMerchant");
        OntClass merchantClass = ontReasoned.getOntClass(NS + "Merchant");
        Individual merchant = ontReasoned.getIndividual(NS + merchantID);

        ObjectNode result = Json.newObject();
        if(merchant != null && merchant.hasOntClass(merchantClass)) {
            result.put("result", merchant.hasOntClass(trustedMerchant));
            return ok(result);
        } else {
            result.put("error", "not a merchant");
            return notFound(result);
        }

    }

    public Result reset() {
        loadOntology();
        ObjectNode result = Json.newObject();
        result.put("status", "success");
        return ok(result);
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
