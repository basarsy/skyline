package com.basarsy.skyline.reservation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** An add-on in the catalog (a bag size, priority boarding, a meal, ...), identified by a stable code. */
@Getter
@Setter
@Entity
@Table(name = "ancillary_products")
@EntityListeners(AuditingEntityListener.class)
public class AncillaryProduct {

    @Id
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AncillaryType type;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    @Column(name = "weight_kg")
    private Integer weightKg;

    /** The most of this product one passenger can have on one flight, included ones counted. */
    @Column(name = "max_quantity", nullable = false)
    private int maxQuantity;

    /** Online sales close this many hours before departure. */
    @Column(name = "cutoff_hours", nullable = false)
    private int cutoffHours;

    /** Refunded on cancellation on every fare (otherwise only on FLEX). */
    @Column(name = "always_refundable", nullable = false)
    private boolean alwaysRefundable;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
