package com.mercato.payment;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercato.Commerce.RefundEvidence;
import com.mercato.Jobs.ManualInterventionJobException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
class RefundEvidenceTest {
    final ObjectMapper json=new ObjectMapper();
    String body(String payment){return "{\"refund_id\":\"R\",\"cf_payment_id\":\""+payment+"\",\"refund_amount\":111,\"refund_currency\":\"INR\",\"refund_status\":\"SUCCESS\"}";}
    @Test void acceptsObjectOrOneExactIdentityInArray()throws Exception{assertEquals("SUCCESS",RefundEvidence.exact(json.readTree(body("primary")),"R","primary","INR",new BigDecimal("111")).path("refund_status").asText());assertEquals("R",RefundEvidence.exact(json.readTree("["+body("primary")+"]"),"R","primary","INR",new BigDecimal("111")).path("refund_id").asText());}
    @Test void ambiguousDuplicateOrWrongAttemptCannotClaimSuccess()throws Exception{assertThrows(ManualInterventionJobException.class,()->RefundEvidence.exact(json.readTree("["+body("primary")+","+body("primary")+"]"),"R","primary","INR",new BigDecimal("111")));assertThrows(ManualInterventionJobException.class,()->RefundEvidence.exact(json.readTree(body("secondary")),"R","primary","INR",new BigDecimal("111")));assertThrows(ManualInterventionJobException.class,()->RefundEvidence.exact(json.readTree(body("primary")),"R","primary","INR",new BigDecimal("112")));}
    @Test void missingAttemptCurrencyOrExpectedOwnerRequiresReview()throws Exception{assertThrows(ManualInterventionJobException.class,()->RefundEvidence.exact(json.readTree("{\"refund_id\":\"R\",\"refund_amount\":111}"),"R","primary","INR",new BigDecimal("111")));assertThrows(ManualInterventionJobException.class,()->RefundEvidence.exact(json.readTree(body("primary")),"R",null,"INR",new BigDecimal("111")));}
}
