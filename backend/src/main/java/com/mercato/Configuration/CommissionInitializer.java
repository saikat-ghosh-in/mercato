package com.mercato.Configuration;
import com.mercato.Entity.CommissionSettings;
import com.mercato.Repository.CommissionSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
@Component @RequiredArgsConstructor
public class CommissionInitializer implements ApplicationRunner {
    private final CommissionSettingsRepository repository;
    @Override @Transactional public void run(ApplicationArguments args) {
        CommissionSettings current = repository.findById(1L).orElseGet(CommissionSettings::new);
        if (current.getPricingRevision() == null || current.getPricingRevision() < 2) {
            current.setRate(current.getRate().min(new java.math.BigDecimal("0.50")));
            current.setPricingRevision(2);
        }
        repository.save(current);
    }
}
