package com.basarsy.skyline.common.pricing;

import java.util.Currency;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Pricing settings shared by the services that return prices (schedule, reservation). Every price is a
 * bare amount in this currency, so it travels with each price-bearing response. Startup fails on anything
 * that is not an ISO 4217 code.
 *
 * @param currency ISO 4217 code, e.g. {@code EUR}; set with {@code SKYLINE_CURRENCY}
 */
@ConfigurationProperties(prefix = "skyline.pricing")
public record PricingProperties(String currency) {

    public PricingProperties {
        if (currency == null || !currency.matches("[A-Z]{3}")) {
            throw new IllegalStateException(
                    "skyline.pricing.currency must be an ISO 4217 code such as EUR (SKYLINE_CURRENCY), got: " + currency);
        }
        try {
            Currency.getInstance(currency);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("skyline.pricing.currency is not a known ISO 4217 code: " + currency, e);
        }
    }
}
