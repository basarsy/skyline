package com.basarsy.skyline.common.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PricingPropertiesTest {

    @Test
    void acceptsIso4217Codes() {
        assertThat(new PricingProperties("EUR").currency()).isEqualTo("EUR");
        assertThat(new PricingProperties("TRY").currency()).isEqualTo("TRY");
        assertThat(new PricingProperties("USD").currency()).isEqualTo("USD");
    }

    @Test
    void rejectsAnythingElseAtStartup() {
        for (String bad : new String[] {null, "", "eur", "EURO", "€", "XYZ", " EUR"}) {
            assertThatThrownBy(() -> new PricingProperties(bad))
                    .as("currency %s", bad)
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}
