package com.basarsy.skyline.reservation.service;

import com.basarsy.skyline.common.exception.ResourceNotFoundException;
import com.basarsy.skyline.common.pricing.PricingProperties;
import com.basarsy.skyline.reservation.dto.SeatPriceRuleRequest;
import com.basarsy.skyline.reservation.dto.SeatPriceRuleResponse;
import com.basarsy.skyline.reservation.entity.SeatPriceRule;
import com.basarsy.skyline.reservation.repository.SeatPriceRuleRepository;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin edits of the seat price table. A change applies to seats bought from then on, never to paid ones. */
@Service
@RequiredArgsConstructor
public class SeatPriceRuleService {

    private final SeatPriceRuleRepository rules;
    private final PricingProperties pricing;

    @Transactional(readOnly = true)
    public List<SeatPriceRuleResponse> findAll() {
        return rules.findAllByOrderByCabinClassAscFareTypeAscSeatCategoryAsc().stream()
                .map(rule -> SeatPriceRuleResponse.of(rule, pricing.currency()))
                .toList();
    }

    @Transactional
    public SeatPriceRuleResponse update(UUID id, SeatPriceRuleRequest request) {
        SeatPriceRule rule = rules.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Seat price rule not found: " + id));
        rule.setPrice(request.price().setScale(2, RoundingMode.HALF_UP));
        rule.setAutoAssign(request.autoAssign());
        return SeatPriceRuleResponse.of(rules.save(rule), pricing.currency());
    }
}
