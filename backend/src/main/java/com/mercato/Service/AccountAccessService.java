package com.mercato.Service;

import com.mercato.Entity.*;
import com.mercato.Repository.*;
import com.mercato.Mapper.EcommUserMapper;
import com.mercato.Payloads.Response.EcommUserResponseDTO;
import com.mercato.ExceptionHandler.*;
import com.mercato.Utils.AuthUtil;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Set;

@Service @RequiredArgsConstructor
public class AccountAccessService {
    private final UserRepository users;
    private final RoleRepository roles;
    private final AccountAccessEventRepository events;
    private final AuthUtil auth;
    private final com.mercato.Session.SessionRevocation sessions;
    public enum Action { DEACTIVATE, REACTIVATE, SUSPEND_SELLING, RESTORE_SELLING }
    public record Change(@NotNull Action action, @NotBlank @Size(min=10,max=500) String reason, @NotNull @PositiveOrZero Long expectedVersion) {}

    private EcommUser administrator() {
        var actor=auth.getLoggedInUser();
        if(actor==null || !actor.isAdmin()) throw new AccessDeniedException("Administrator access required");
        return actor;
    }
    private EcommUser subject(String id) {
        var subject=users.findByUserIdForUpdate(id).orElseThrow(()->new ResourceNotFoundException("User","userId",id));
        if(subject.isAdmin()) throw new AccessDeniedException("Administrator accounts are read-only in user management");
        return subject;
    }
    private String state(EcommUser user) {
        return (user.isEnabled()?"Account active":user.isSelfDeactivated()?"Account paused":"Account deactivated")+"; "+(user.isSeller()?(user.isSellerSuspended()?"Selling suspended":"Selling enabled"):"No selling access");
    }
    @Transactional
    public EcommUserResponseDTO change(String id, Change request) {
        var actor=administrator(); var user=subject(id);
        if(actor.getUserId().equals(id)) throw new AccessDeniedException("You cannot change your own access");
        if(request==null || request.action()==null || request.reason()==null || request.reason().strip().length()<10 || request.reason().strip().length()>500)
            throw new IllegalArgumentException("Give a reason between 10 and 500 characters");
        if(request.expectedVersion()==null || request.expectedVersion()!=user.getAccessVersion())
            throw new CustomConflictException("This account changed. Refresh its details before saving again");
        String before=state(user);
        switch(request.action()) {
            case DEACTIVATE -> {
                if(!user.isEnabled() && !user.isSelfDeactivated()) throw new CustomConflictException("Account is already deactivated");
                user.setEnabled(false); user.setSelfDeactivated(false); user.setDeactivatedAt(Instant.now());
            }
            case REACTIVATE -> {
                if(user.isEnabled()) throw new CustomConflictException("Account is already active");
                if(user.isAccountLocked()) throw new IllegalArgumentException("Resolve the account lock before restoring access");
                user.setEnabled(true); user.setSelfDeactivated(false); user.setDeactivatedAt(null);
            }
            case SUSPEND_SELLING -> {
                if(!user.isSeller()) throw new IllegalArgumentException("This account has no selling access");
                if(user.isSellerSuspended()) throw new CustomConflictException("Selling is already suspended");
                user.setSellerSuspended(true);
            }
            case RESTORE_SELLING -> {
                if(user.getSellerStatus()!=SellerStatus.APPROVED || !user.isEmailVerified() || !user.isEnabled() || user.isAccountLocked())
                    throw new IllegalArgumentException("Selling requires an approved application and an active, verified account");
                if(user.isSeller() && !user.isSellerSuspended()) throw new CustomConflictException("Selling is already enabled");
                user.getRoles().add(roles.findByRoleName(AppRole.ROLE_SELLER).orElseThrow()); user.setSellerSuspended(false);
            }
        }
        user.getRoles().add(roles.findByRoleName(AppRole.ROLE_USER).orElseThrow());
        user.setAccessVersion(user.getAccessVersion()+1); // Revoke every existing session, including after restoration.
        sessions.revokeAll(user.getUserId());
        events.save(new AccountAccessEvent(user,actor,request.action().name(),request.reason().strip(),before,state(user)));
        return EcommUserMapper.toDTO(users.saveAndFlush(user));
    }
    @Transactional(readOnly=true)
    public Page<AccountAccessEvent> history(String id,int page) {
        administrator();
        if(page<0) throw new IllegalArgumentException("Page must not be negative");
        if(users.findByUserId(id).isEmpty()) throw new ResourceNotFoundException("User","userId",id);
        return events.findBySubjectIdOrderByCreatedAtDescIdDesc(id,PageRequest.of(page,10));
    }
    /** The former unreasoned write routes cannot bypass the audited command. */
    @Transactional
    public void legacyChange(String id, Set<String> requested, boolean deleteNow) {
        administrator(); subject(id);
        if(deleteNow) throw new IllegalArgumentException("Accounts are retained. Use deactivation instead");
        if(requested!=null && !requested.contains("ROLE_USER")) throw new IllegalArgumentException("The base user role cannot be removed");
        throw new IllegalArgumentException("Use account access management with a reason and the current account version");
    }
}
