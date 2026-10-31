package com.mercato.order;
import com.mercato.ExceptionHandler.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
class WorkflowHttpConflictTest {
    @Test void clientOperationsCannotOccupyDeadlineOrBatchCommandNamespaces(){
        for(String reserved:java.util.List.of("accept-timeout:1","scope:test:1","whole:test"))
            org.junit.jupiter.api.Assertions.assertThrows(ResponseStatusException.class,()->com.mercato.Service.WorkflowCommands.requireExternalOperation(reserved));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(()->com.mercato.Service.WorkflowCommands.requireExternalOperation(java.util.UUID.randomUUID().toString()));
    }
    @RestController static class Example {
        @GetMapping("/conflict") void conflict(){throw new ResponseStatusException(HttpStatus.CONFLICT,"Stale workflow version");}
    }
    @Test void staleWorkflowErrorsRetainTheirHttpStatus()throws Exception{
        standaloneSetup(new Example()).setControllerAdvice(new GlobalExceptionHandler()).build().perform(get("/conflict")).andExpect(status().isConflict());
    }
}
