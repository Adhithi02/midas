package com.jpmc.midascore.component;

import com.jpmc.midascore.foundation.Incentive;
import com.jpmc.midascore.foundation.Transaction;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class IncentiveApiClient {
    private final RestTemplate restTemplate;

    public IncentiveApiClient(RestTemplateBuilder builder) {
        this.restTemplate = builder.build();
    }

    public float fetchIncentive(Transaction transaction) {
        Incentive incentive = restTemplate.postForObject(
                "http://localhost:8080/incentive", transaction, Incentive.class);
        return incentive != null ? incentive.getAmount() : 0f;
    }
}
