package com.basarsy.skyline.reservation.dto;

import com.basarsy.skyline.reservation.entity.SalesChannel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Add-ons to buy for one reservation. Prices are never sent: the server prices every item.
 *
 * @param channel ONLINE (default) or AIRPORT (staff at the desk, airport prices)
 */
public record AncillaryPurchaseRequest(@NotEmpty @Size(max = 10) @Valid List<Item> items, SalesChannel channel) {

    public record Item(@NotBlank String productCode, @Min(1) @Max(3) int quantity) {}
}
