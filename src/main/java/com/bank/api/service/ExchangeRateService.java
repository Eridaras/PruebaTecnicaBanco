package com.bank.api.service;

import com.bank.api.dto.response.ExchangeRateResponse;
import com.bank.api.exception.ExternalServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExchangeRateService {

    private final WebClient frankfurterWebClient;

    @Value("${exchange-rate.timeout-seconds:5}")
    private int timeoutSeconds;

    /**
     * Convierte un monto desde una moneda origen a USD usando la API de Frankfurter.
     * Si la moneda origen ya es USD, retorna el mismo monto sin llamar a la API externa.
     */
    public ExchangeRateResponse convert(String fromCurrency, BigDecimal amount) {
        String currency = fromCurrency.toUpperCase();

        if ("USD".equals(currency)) {
            return new ExchangeRateResponse(currency, "USD", amount, amount, BigDecimal.ONE);
        }

        FrankfurterResponse frankfurterResponse = fetchRate(currency);
        BigDecimal rate = extractUsdRate(frankfurterResponse, currency);
        BigDecimal converted = amount.multiply(rate).setScale(4, RoundingMode.HALF_UP);

        return new ExchangeRateResponse(currency, "USD", amount, converted, rate);
    }

    private FrankfurterResponse fetchRate(String fromCurrency) {
        try {
            FrankfurterResponse response = frankfurterWebClient.get()
                    .uri("/latest?from={currency}&to=USD", fromCurrency)
                    .retrieve()
                    .bodyToMono(FrankfurterResponse.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();

            if (response == null) {
                throw new ExternalServiceException("La API de tipo de cambio no retornó datos");
            }
            return response;

        } catch (WebClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                throw new ExternalServiceException("Moneda no soportada: " + fromCurrency);
            }
            throw new ExternalServiceException("Error consultando tipo de cambio: " + ex.getMessage(), ex);
        } catch (ExternalServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ExternalServiceException(
                    "Servicio de tipo de cambio no disponible. Intente nuevamente más tarde.", ex);
        }
    }

    private BigDecimal extractUsdRate(FrankfurterResponse response, String fromCurrency) {
        if (response.rates() == null || !response.rates().containsKey("USD")) {
            throw new ExternalServiceException(
                    "No se encontró tasa de conversión a USD para la moneda: " + fromCurrency);
        }
        return response.rates().get("USD");
    }

    private record FrankfurterResponse(
            String base,
            String date,
            Map<String, BigDecimal> rates
    ) {}
}
