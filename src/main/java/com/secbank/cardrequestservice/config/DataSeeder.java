package com.secbank.cardrequestservice.config;

import com.secbank.cardrequestservice.domain.Customer;
import com.secbank.cardrequestservice.domain.RiskTier;
import com.secbank.cardrequestservice.repository.CustomerRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataSeeder implements CommandLineRunner {

    private final CustomerRepository customerRepository;

    public DataSeeder(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Override
    public void run(String... args) {
        if (customerRepository.count() > 0) {
            return;
        }
        customerRepository.save(new Customer("CUST-1001", "Ada Okafor",
                RiskTier.STANDARD));
        customerRepository.save(new Customer("CUST-1002", "Bello Musa",
                RiskTier.HIGH_RISK));
    }
}
