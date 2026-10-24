package com.mercato.commission;

import com.mercato.Configuration.CommissionInitializer;
import com.mercato.Entity.CommissionSettings;
import com.mercato.Repository.CommissionSettingsRepository;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommissionBaselineTest {
    @Test void migrationLowersOldBaselineOnceButNeverResetsLaterAdminRates() {
        var repository=mock(CommissionSettingsRepository.class); var settings=new CommissionSettings();
        settings.setRate(new BigDecimal("5")); settings.setPricingRevision(null);
        when(repository.findById(1L)).thenReturn(Optional.of(settings));
        var initializer=new CommissionInitializer(repository); initializer.run(null);
        assertEquals(new BigDecimal("0.50"),settings.getRate()); assertEquals(2,settings.getPricingRevision());
        settings.setRate(new BigDecimal("1")); initializer.run(null); assertEquals(new BigDecimal("1"),settings.getRate());
    }
    @Test void newSettingsStartAtHalfPercentAndLowerExistingRatesArePreserved() {
        assertEquals(new BigDecimal("0.50"),new CommissionSettings().getRate());
        var repository=mock(CommissionSettingsRepository.class); var settings=new CommissionSettings();
        settings.setRate(new BigDecimal("0.25")); settings.setPricingRevision(null);
        when(repository.findById(1L)).thenReturn(Optional.of(settings));new CommissionInitializer(repository).run(null);
        assertEquals(new BigDecimal("0.25"),settings.getRate());
    }
}
