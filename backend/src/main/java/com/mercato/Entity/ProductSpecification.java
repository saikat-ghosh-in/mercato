package com.mercato.Entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductSpecification {
    @NotBlank @Size(max = 100)
    @Column(name = "spec_label", nullable = false, length = 100)
    private String label;

    @NotBlank @Size(max = 500)
    @Column(name = "spec_value", nullable = false, length = 500)
    private String value;
}
