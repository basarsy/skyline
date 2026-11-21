package com.basarsy.skyline.reservation.repository;

import com.basarsy.skyline.reservation.entity.AncillaryProduct;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AncillaryProductRepository extends JpaRepository<AncillaryProduct, String> {

    List<AncillaryProduct> findAllByOrderBySortOrderAsc();
}
