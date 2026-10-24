package com.mercato.Controller;

import com.mercato.Payloads.Request.ForgotPasswordRequestDTO;
import com.mercato.Payloads.Request.ResetPasswordRequestDTO;
import com.mercato.Payloads.Response.EcommUserResponseDTO;
import com.mercato.Payloads.Response.MessageResponse;
import com.mercato.Security.payloads.LoginRequest;
import com.mercato.Security.payloads.RegisterUserRequest;
import com.mercato.Security.services.AuthService;
import com.mercato.Service.EmailVerificationService;
import com.mercato.Service.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;
    private final PasswordResetService passwordResetService;
    private final com.mercato.Session.SessionService sessions;
    private final com.mercato.Auth.MfaService mfa;
    private final com.mercato.Auth.AbuseCounters abuse;
    private final com.mercato.Auth.CustomerStepUpService stepUp;
    private final com.mercato.Auth.LegacyGuestMigrationService legacy;

    @GetMapping("/session")
    public Object session(HttpServletRequest request) { return sessions.bootstrap(request); }
    @GetMapping("/csrf")
    public Object csrf(org.springframework.security.web.csrf.CsrfToken token) { return java.util.Map.of("token",token.getToken(),"headerName",token.getHeaderName()); }
    public record Proof(String code) {}
    public record LegacyGuest(String guestToken) {}
    public record OperatorRecovery(String userId,String reason,Long expectedVersion) {}
    @PostMapping("/mfa/operator-recovery") public Object operatorRecovery(HttpServletRequest request,@RequestBody OperatorRecovery recovery){mfa.operatorRecovery(request,recovery.userId(),recovery.reason(),recovery.expectedVersion());return java.util.Map.of("message","Authenticator recovery recorded. The account must enroll again.");}
    private void emailRecoveryBudget(HttpServletRequest request,String email){
        int delay=abuse.consume("recovery-email",email.strip().toLowerCase(java.util.Locale.ROOT),3,3600,false);
        delay=Math.max(delay,abuse.consume("recovery-email-ip",request.getRemoteAddr(),20,3600,false));
        if(delay>0)throw new RetryLater(delay);
    }
    private void proofBudget(HttpServletRequest request, boolean recovery) {
        var id=sessions.identity(request); if(id==null)throw new com.mercato.Auth.AuthProofException();
        int delay=recovery?abuse.consume("recovery-account",id.userId(),3,3600,false):abuse.retryAfter("mfa-account",id.userId());
        delay=Math.max(delay,abuse.consume(recovery?"recovery-ip":"mfa-ip",request.getRemoteAddr(),recovery?20:100,3600,false));
        if(delay>0)throw new RetryLater(delay);
    }
    @PostMapping("/mfa/enroll") public Object enroll(HttpServletRequest request) {proofBudget(request,false);return mfa.enroll(request,null,false);}
    @PostMapping("/mfa/replace") public Object replace(HttpServletRequest request,@RequestBody Proof proof) {proofBudget(request,false);return mfa.enroll(request,proof.code(),true);}
    @PostMapping("/mfa/verify") public Object verifyMfa(HttpServletRequest request,@RequestBody Proof proof) {proofBudget(request,false);return mfa.verify(request,proof.code(),false);}
    @PostMapping("/mfa/recovery") public Object recovery(HttpServletRequest request,@RequestBody Proof proof) {proofBudget(request,true);return mfa.verify(request,proof.code(),true);}
    @PostMapping("/step-up/send") public Object sendStepUp(HttpServletRequest request) {proofBudget(request,true);stepUp.send(request);return java.util.Map.of("message","A verification code has been sent.");}
    @PostMapping("/step-up/verify") public Object verifyStepUp(HttpServletRequest request,@RequestBody Proof proof) {proofBudget(request,false);return stepUp.verify(request,proof.code());}
    @PostMapping("/legacy-guest-cart") public Object migrateGuest(HttpServletRequest request,@RequestBody LegacyGuest body) {legacy.migrate(request,body.guestToken());return sessions.bootstrap(request);}
    @ExceptionHandler(com.mercato.Auth.AuthProofException.class) public ResponseEntity<?> proofFailure(com.mercato.Auth.AuthProofException e,HttpServletRequest request){var id=sessions.identity(request);if(id!=null&&!request.getRequestURI().endsWith("/mfa/recovery"))abuse.consume("mfa-account",id.userId(),5,3600,true);return ResponseEntity.status(401).body(java.util.Map.of("code","AUTH_PROOF_INVALID","message",e.getMessage()));}
    private static class RetryLater extends RuntimeException { final int seconds;RetryLater(int seconds){this.seconds=seconds;} }
    @ExceptionHandler(RetryLater.class) public ResponseEntity<?> retry(RetryLater e){return ResponseEntity.status(429).header("Retry-After",Integer.toString(e.seconds)).body(java.util.Map.of("code","AUTH_RETRY_LATER","message","Please wait before trying again."));}

    @PostMapping("/signin")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest,
                                              HttpServletRequest request,
                                              HttpServletResponse response) {
        return authService.authenticateUser(loginRequest, request, response);
    }

    @PostMapping("/register")
    public ResponseEntity<MessageResponse> registerNewUser(@Valid @RequestBody RegisterUserRequest registerUserRequest) {
        String response = authService.registerNewUser(registerUserRequest);
        return ResponseEntity.ok(new MessageResponse(response));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<MessageResponse> verifyEmail(@RequestParam String token) {
        emailVerificationService.verifyEmail(token);
        return ResponseEntity.ok(new MessageResponse("Email verified successfully! Welcome to Mercato."));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<MessageResponse> resendVerification(@RequestParam @Email String email,HttpServletRequest servlet) {
        emailRecoveryBudget(servlet,email);
        emailVerificationService.resendVerificationEmail(email);
        return ResponseEntity.ok(new MessageResponse("Verification email sent! Please check your inbox."));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequestDTO request,HttpServletRequest servlet) {
        emailRecoveryBudget(servlet,request.getEmail());
        passwordResetService.requestPasswordReset(request.getEmail());
        return ResponseEntity.ok(new MessageResponse(
                "If that email is registered, you will receive a password reset link shortly."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequestDTO request) {
        passwordResetService.resetPassword(request.getToken(), request.getPassword());
        return ResponseEntity.ok(new MessageResponse("Password updated successfully. You can sign in now."));
    }

    @GetMapping("/user/username")
    public ResponseEntity<String> getUsernameFromAuthentication(Authentication authentication) {
        String username = authService.getCurrentUsernameFromAuthentication();
        return ResponseEntity.ok(username);
    }

    @GetMapping("/user")
    public ResponseEntity<EcommUserResponseDTO> getCurrentUserFromAuthentication() {
        EcommUserResponseDTO user = authService.getCurrentUserFromAuthentication();
        return ResponseEntity.ok(user);
    }

    @PostMapping("/signout")
    public ResponseEntity<?> signOutCurrentUser(HttpServletRequest request) {
        if(request.getSession(false)!=null) request.getSession(false).invalidate();
        authService.signOutCurrentUser();
        return ResponseEntity.ok(sessions.bootstrap(request));
    }
}
