package com.mercato.Commerce;

import com.fasterxml.jackson.databind.JsonNode;
import com.mercato.Jobs.ManualInterventionJobException;
import java.math.BigDecimal;

/** Accept only one exact merchant refund and its proven primary payment attempt. */
public final class RefundEvidence {
    private RefundEvidence(){}
    public static JsonNode exact(JsonNode response,String id,String payment,String currency,BigDecimal amount){
        JsonNode match=null;
        if(response!=null && response.isArray()){
            for(var row:response)if(id.equals(row.path("refund_id").asText())){if(match!=null)throw attention();match=row;}
        }else if(response!=null && response.isObject())match=response;
        if(match==null || !id.equals(match.path("refund_id").asText()) || payment==null || payment.isBlank()
            || !payment.equals(match.path("cf_payment_id").asText()) || !currency.equals(match.path("refund_currency").asText()))throw attention();
        try{if(PaymentRules.decimal(match.path("refund_amount").asText()).compareTo(amount)!=0)throw attention();}catch(IllegalArgumentException invalid){throw attention();}
        return match;
    }
    private static ManualInterventionJobException attention(){return new ManualInterventionJobException("Refund identity, amount or primary payment attempt requires review");}
}
