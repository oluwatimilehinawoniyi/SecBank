package com.secbank.cardrequestservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "customers")
public class Customer {
    
    protected Customer() {
    }

    public Customer(String customerId, String name, RiskTier riskTier) {
        this.customerId = customerId;
        this.name = name;
        this.riskTier = riskTier;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false, unique = true,
            updatable = false)
    private String customerId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_tier", nullable = false)
    private RiskTier riskTier;

    public Long getId() {
        return id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getName() {
        return name;
    }

    public RiskTier getRiskTier() {
        return riskTier;
    }
}
