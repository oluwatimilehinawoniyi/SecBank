package com.secbank.cardrequestservice.repository;

import com.secbank.cardrequestservice.domain.CardRequest;
import com.secbank.cardrequestservice.domain.CardRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CardRequestRepository extends JpaRepository<CardRequest, Long> {

    Optional<CardRequest> findByReference(String reference);

    List<CardRequest> findByStatus(CardRequestStatus status);
}
