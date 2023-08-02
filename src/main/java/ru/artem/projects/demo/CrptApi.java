package ru.artem.projects.demo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class CrptApi {
    private final int requestLimit;
    private final long timeInterval;
    private final AtomicInteger requestCount;
    private long lastRequestTime = System.currentTimeMillis();
    private final RestTemplate restTemplate;
    private final HttpHeaders headers;
    private final ObjectMapper objectMapper;
    private final Base64.Encoder encoder;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.timeInterval = timeUnit.toMillis(1);
        this.requestLimit = requestLimit;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.requestCount = new AtomicInteger(0);
        objectMapper.registerModule(new JavaTimeModule());
        this.encoder = Base64.getEncoder();

        headers = new HttpHeaders();
        headers.set("content-type", "application/json");
        headers.set("Authorization", "Bearer " /* + token */);
    }

    public void createDocument(@Valid Document document, String signature) throws InterruptedException {
        synchronized (this) {
            // проверка документа на правильность заполнения всех полей
            validateDocumentsField(document);

            long currentTime = System.currentTimeMillis();
            if (currentTime - lastRequestTime >= timeInterval) {
                // сбросить счетчик запросов, если интервал времени прошел
                requestCount.set(0);
                lastRequestTime = currentTime;
            }
            if (requestCount.get() >= requestLimit) {
                // ждем, пока количество запросов не станет ниже предела
                wait(timeInterval - (currentTime - lastRequestTime));
                // проверка документа снова после ожидания
                createDocument(document, signature);
            } else {
                // запрос к API (если задача, проверить, работает ли блокировка по времени и запросам -
                // стоит закомментить вызов метода запроса к API)
                makeApiCall(document, signature);
                requestCount.incrementAndGet();
                log.info("sent request");
            }
        }
    }

    // чтобы проверить работоспособность метода, следует изменить указать токен в конструкторе и пройти аунтефикацию
    private void makeApiCall(Document document, String signature) {
        String documentJson;
        try {
            documentJson = objectMapper.writeValueAsString(document);
        } catch (IOException e) {
            throw new RuntimeException(e); // сюда еще кастомное исключение можно добавить
        }

        log.info(documentJson);
        log.info(signature);
        documentJson = encoder.encodeToString(documentJson.getBytes()); // закодированный json
        signature = encoder.encodeToString(signature.getBytes()); // закодированная подпись
        log.info(documentJson);
        log.info(signature);

        Request request = new Request(documentJson, DocumentFormat.MANUAL, DocumentType.LP_INTRODUCE_GOODS, signature); // запрос для создания документа

        HttpEntity<Request> entity = new HttpEntity<>(request, headers); // запрос с хедерами
        log.info(entity.toString());
        restTemplate.exchange("https://ismp.crpt.ru/api/v3/lk/documents/create", HttpMethod.POST, entity, String.class); // тоже не помешал бы response-класс самопильный
        log.info("sent request");
    }

    // лучше перенести в отдельный класс, а то класс для работы с API дланью господней обладает, раз столько функций в нем
    private void validateDocumentsField(Document document) {
        for (Product product : document.getProducts()) {
            if (product.getProductionDate() != null && product.getProductionDate().isEqual(document.getProductionDate())) {
                throw new NullPointerException("The productionDate is present in the request if its value differs from the value"
                        + "of the production_date parameter in the document");
            }
            if (product.getTnvedCode() == null && document.getProductionDate() == null) {
                throw new NullPointerException("The production date must be mandatory if there is no data in CodeTNVED");
            }
            if (product.getUitCode() == null && product.getUituCode() == null) {
                throw new NullPointerException("Either uit code or uitu code must be specified");
            }
        }
    }

    @AllArgsConstructor @Data @Builder
    public static class Request {
        @JsonProperty("product_document")
        private final String document;

        @JsonProperty("document_format")
        private final DocumentFormat documentFormat;

        private final DocumentType type;

        private final String signature;
    }

    @AllArgsConstructor @Data @Builder
    public static class Document {
        private final Producer description;

        @NotEmpty
        @JsonProperty("doc_id")
        private final String docId;

        @NotEmpty
        @JsonProperty("doc_status")
        private final String docStatus;

        @NotEmpty
        @JsonProperty("doc_type")
        private final String docType;

        private final String importRequest;

        @NotEmpty
        @JsonProperty("owner_inn")
        private final String ownerInn;

        @NotEmpty
        @JsonProperty("participant_inn")
        private final String participantInn;

        @NotEmpty
        @JsonProperty("producer_inn")
        private final String producerInn;

        @JsonProperty("production_date")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd-MM-yyyy")
        private final LocalDateTime productionDate;

        @NotEmpty
        @JsonProperty("production_type")
        private final ProductionType productionType;

        private final Product[] products;

        @NotEmpty
        @JsonProperty("reg_date")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "YYYY-MM-DD'T'HH:mm:ss")
        private final LocalDateTime regDate;

        @JsonProperty("reg_number")
        private final String regNumber;
    }

    @AllArgsConstructor @Data
    public static class Producer {
        @NotEmpty
        private final String participantInn;
    }

    @AllArgsConstructor @Data @Builder
    public static class Product {
        @JsonProperty("certificate_document")
        private final CertificateType certificateDocument;

        @JsonProperty("certificate_document_date")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd-MM-yyyy")
        private final String certificateDocumentDate;

        @JsonProperty("certificate_document_number")
        private final String certificateDocumentNumber;

        @NotEmpty
        @JsonProperty("owner_inn")
        private final String ownerInn;

        @NotEmpty
        @JsonProperty("producer_inn")
        private final String producerInn;

        @NotEmpty
        @JsonProperty("production_date")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd-MM-yyyy")
        private final LocalDateTime productionDate;

        @NotEmpty
        @JsonProperty("tnved_code")
        private final String tnvedCode;

        @JsonProperty("uit_code")
        private final String uitCode;

        @JsonProperty("uitu_code")
        private final String uituCode;
    }

    public enum DocumentType {
        AGGREGATION_DOCUMENT, AGGREGATION_DOCUMENT_CSV, AGGREGATION_DOCUMENT_XML,
        DISAGGREGATION_DOCUMENT, DISAGGREGATION_DOCUMENT_CSV, DISAGGREGATION_DOCUMENT_XML,
        REAGGREGATION_DOCUMENT, REAGGREGATION_DOCUMENT_CSV, REAGGREGATION_DOCUMENT_XML,
        LP_INTRODUCE_GOODS, LP_INTRODUCE_GOODS_CSV, LP_INTRODUCE_GOODS_XML,
        LP_SHIP_GOODS, LP_SHIP_GOODS_CSV, LP_SHIP_GOODS_XML,
        LP_ACCEPT_GOODS, LP_ACCEPT_GOODS_XML,
        LK_REMARK, LK_REMARK_XML, LK_REMARK_CSV,
        LK_RECEIPT, LK_RECEIPT_CSV, LK_RECEIPT_XML,
        LP_GOODS_IMPORT, LP_GOODS_IMPORT_CSV, LP_GOODS_IMPORT_XML,
        LP_CANCEL_SHIPMENT, LP_CANCEL_SHIPMENT_CSV, LP_CANCEL_SHIPMENT_XML,
        LK_KM_CANCELLATION, LK_KM_CANCELLATION_CSV, LK_KM_CANCELLATION_XML,
        LK_APPLIED_KM_CANCELLATION, LK_APPLIED_KM_CANCELLATION_CSV, LK_APPLIED_KM_CANCELLATION_XML,
        LK_CONTRACT_COMMISSIONING, LK_CONTRACT_COMMISSIONING_CSV, LK_CONTRACT_COMMISSIONING_XML,
        LK_INDI_COMMISSIONING, LK_INDI_COMMISSIONING_CSV, LK_INDI_COMMISSIONING_XML,
        LP_SHIP_RECEIPT, LP_SHIP_RECEIPT_CSV, LP_SHIP_RECEIPT_XML,
        OST_DESCRIPTION, OST_DESCRIPTION_CSV, OST_DESCRIPTION_XML,
        CROSSBORDER, CROSSBORDER_CSV, CROSSBORDER_XML,
        LP_INTRODUCE_OST, LP_INTRODUCE_OST_CSV, LP_INTRODUCE_OST_XML,
        LP_RETURN, LP_RETURN_CSV, LP_RETURN_XML,
        LP_SHIP_GOODS_CROSSBORDER, LP_SHIP_GOODS_CROSSBORDER_CSV, LP_SHIP_GOODS_CROSSBORDER_XML,
        LP_CANCEL_SHIPMENT_CROSSBORDER
    }

    public enum DocumentFormat {
        MANUAL,
        XML,
        CSV
    }

    public enum ProductionType {
        OWN_PRODUCTION,
        CONTRACT_PRODUCTION
    }

    public enum CertificateType {
        CONFORMITY_CERTIFICATE,
        CONFORMITY_DECLARATION
    }
}
