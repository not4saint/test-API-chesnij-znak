package ru.artem.projects.demo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

class CrptApiTest {
    private CrptApi crptApi;
    @BeforeEach
    void setUp() {
        this.crptApi = new CrptApi(TimeUnit.MINUTES, 10);
    }

    @Test
    void createDoc_RandomExample() {
        CrptApi.Product product = new CrptApi.Product(CrptApi.CertificateType.CONFORMITY_CERTIFICATE, "10",
                "1", "1", "1", LocalDateTime.now(), "123",
                "13", "14");
        CrptApi.Producer producer = new CrptApi.Producer("123431");
        CrptApi.Document doc = new CrptApi.Document(producer, "1", "true", "supertype",
                "true", "123", "12", "12", LocalDateTime.now(),
                CrptApi.ProductionType.OWN_PRODUCTION, new CrptApi.Product[]{product}, LocalDateTime.now(), "123");
        for (int i = 0; i < 11; i++) {
            try {
                crptApi.createDocument(doc, "123");
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            };
        }
//        try {
//            sleep(20000);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }
    }
}