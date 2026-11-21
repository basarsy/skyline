package com.basarsy.skyline.reservation.service;

import com.basarsy.skyline.common.exception.ResourceNotFoundException;
import com.basarsy.skyline.common.exception.SkylineException;
import com.basarsy.skyline.common.pricing.PricingProperties;
import com.basarsy.skyline.reservation.dto.AncillaryPriceRequest;
import com.basarsy.skyline.reservation.dto.AncillaryProductRequest;
import com.basarsy.skyline.reservation.dto.AncillaryProductResponse;
import com.basarsy.skyline.reservation.entity.AncillaryPrice;
import com.basarsy.skyline.reservation.entity.AncillaryProduct;
import com.basarsy.skyline.reservation.entity.RouteScope;
import com.basarsy.skyline.reservation.repository.AncillaryPriceRepository;
import com.basarsy.skyline.reservation.repository.AncillaryProductRepository;
import com.basarsy.skyline.reservation.repository.FareAllowanceRepository;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The add-on catalog as admins see and edit it. Edits apply to purchases from then on, never to past ones. */
@Service
@RequiredArgsConstructor
public class AncillaryCatalogService {

    private final AncillaryProductRepository products;
    private final AncillaryPriceRepository prices;
    private final FareAllowanceRepository allowances;
    private final PricingProperties pricing;

    @Transactional(readOnly = true)
    public List<AncillaryProductResponse> findAll() {
        return products.findAllByOrderBySortOrderAsc().stream().map(this::view).toList();
    }

    @Transactional
    public AncillaryProductResponse update(String code, AncillaryProductRequest request) {
        AncillaryProduct product = find(code);
        product.setName(request.name().trim());
        product.setDescription(request.description().trim());
        product.setMaxQuantity(request.maxQuantity());
        product.setCutoffHours(request.cutoffHours());
        product.setAlwaysRefundable(request.alwaysRefundable());
        product.setActive(request.active());
        return view(products.save(product));
    }

    @Transactional
    public AncillaryProductResponse updatePrice(String code, RouteScope scope, AncillaryPriceRequest request) {
        AncillaryProduct product = find(code);
        if (request.airportPrice().compareTo(request.onlinePrice()) < 0) {
            throw new SkylineException("The airport price can't be lower than the online price", HttpStatus.BAD_REQUEST);
        }
        AncillaryPrice price = prices.findByProductCodeAndRouteScope(code, scope).orElseGet(() -> {
            AncillaryPrice created = new AncillaryPrice();
            created.setProductCode(code);
            created.setRouteScope(scope);
            return created;
        });
        price.setOnlinePrice(request.onlinePrice().setScale(2, RoundingMode.HALF_UP));
        price.setAirportPrice(request.airportPrice().setScale(2, RoundingMode.HALF_UP));
        prices.save(price);
        return view(product);
    }

    private AncillaryProduct find(String code) {
        return products.findById(code).orElseThrow(() -> new ResourceNotFoundException("Add-on not found: " + code));
    }

    private AncillaryProductResponse view(AncillaryProduct product) {
        List<AncillaryProductResponse.Price> priceViews = prices.findByProductCode(product.getCode()).stream()
                .sorted(Comparator.comparing(AncillaryPrice::getRouteScope))
                .map(p -> new AncillaryProductResponse.Price(p.getRouteScope(), p.getOnlinePrice(), p.getAirportPrice()))
                .toList();
        List<AncillaryProductResponse.Allowance> included = allowances.findAll().stream()
                .filter(a -> a.getProductCode().equals(product.getCode()))
                .sorted(Comparator.comparing((com.basarsy.skyline.reservation.entity.FareAllowance a) -> a.getCabinClass())
                        .thenComparing(a -> a.getFareType()))
                .map(a -> new AncillaryProductResponse.Allowance(a.getCabinClass(), a.getFareType(), a.getQuantity()))
                .toList();
        return new AncillaryProductResponse(product.getCode(), product.getType(), product.getName(),
                product.getDescription(), product.getWeightKg(), product.getMaxQuantity(), product.getCutoffHours(),
                product.isAlwaysRefundable(), product.isActive(), priceViews, included, pricing.currency());
    }
}
