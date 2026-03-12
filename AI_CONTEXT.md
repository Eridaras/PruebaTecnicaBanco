# AI_CONTEXT — Bank API

> Documento de contexto compacto para consumo por LLMs.
> Cubre el 100% del sistema en mínimos tokens.
> Leer este archivo antes de revisar cualquier código fuente.

---

## IDENTIDAD DEL PROYECTO

```
Nombre:       Bank API
Propósito:    API REST para gestión de cuentas bancarias y movimientos financieros
Stack:        Java 21 + Spring Boot 3.5 + Spring Data JPA + H2 + WebFlux (WebClient)
Build:        Maven Wrapper (./mvnw spring-boot:run)
Puerto:       8080
Base URL:     http://localhost:8080/api/v1
DB:           H2 in-memory (bankdb) — recreada en cada arranque, datos precargados
Repo:         https://github.com/Eridaras/PruebaTecnicaBanco
```

---

## MAPA DE ARCHIVOS

```
src/main/java/com/bank/api/
  BankApiApplication.java                  → @SpringBootApplication, main()

  config/
    DataInitializer.java                   → CommandLineRunner @Order(1), carga 2 clientes + 3 cuentas + 6 movimientos
    WebClientConfig.java                   → @Bean WebClient para https://api.frankfurter.app

  domain/entity/
    Customer.java                          → id, name, documentId(UNIQUE), List<Account>
    Account.java                           → id, accountNumber(UNIQUE), AccountType, BigDecimal balance, Customer, List<Movement>
    Movement.java                          → id, MovementType, BigDecimal amount, description, createdAt(@CreationTimestamp), Account, Account relatedAccount(nullable)

  domain/enums/
    AccountType.java                       → AHORRO | CORRIENTE
    MovementType.java                      → DEPOSITO | RETIRO | TRANSFERENCIA

  dto/request/
    DepositRequest.java                    → record { @NotNull @DecimalMin("0.01") amount, description }
    WithdrawalRequest.java                 → record { @NotNull @DecimalMin("0.01") amount, description }
    TransferRequest.java                   → record { @NotBlank source, @NotBlank target, @NotNull @DecimalMin("0.01") amount, description }

  dto/response/
    ApiResponse.java                       → record<T> { success, data, error{code,message}, timestamp } @JsonInclude(NON_NULL)
    MovementResponse.java                  → record { id, type, amount, description, createdAt, relatedAccountNumber }
    AccountSummaryResponse.java            → record { id, accountNumber, type, balance }
    CustomerFinancialSummaryResponse.java  → record { customerId, name, documentId, List<AccountSummaryResponse> }
    TransferResponse.java                  → record { sourceAccountNumber, sourceNewBalance, targetAccountNumber, targetNewBalance, amount, description }
    ExchangeRateResponse.java              → record { fromCurrency, toCurrency, originalAmount, convertedAmount, rate }

  repository/
    CustomerRepository.java                → JpaRepository<Customer, Long>
    AccountRepository.java                 → + findByAccountNumber(String) : Optional<Account>
                                             + findByCustomerId(Long) : List<Account>
                                             + @Lock(PESSIMISTIC_WRITE) findByAccountNumberWithLock(String) : Optional<Account>
    MovementRepository.java                → + findByAccountIdOrderByCreatedAtDesc(Long) : List<Movement>

  service/
    AccountService.java                    → getCustomerFinancialSummary(Long) : @Transactional(readOnly)
    MovementService.java                   → getMovements(String), deposit(String, DepositRequest), withdraw(String, WithdrawalRequest)
    TransferService.java                   → transfer(TransferRequest) : @Transactional(isolation=REPEATABLE_READ)
    ExchangeRateService.java               → convert(String, BigDecimal) : llama Frankfurter, timeout 5s

  controller/
    AccountController.java                 → GET  /customers/{id}/financial-summary
    MovementController.java                → GET  /accounts/{num}/movements
                                             POST /accounts/{num}/deposit
                                             POST /accounts/{num}/withdrawal
    TransferController.java                → POST /transfers
    ExchangeRateController.java            → GET  /exchange-rate?from=&amount=

  exception/
    GlobalExceptionHandler.java            → @RestControllerAdvice — centraliza todos los errores
    AccountNotFoundException.java          → RuntimeException → 404
    CustomerNotFoundException.java         → RuntimeException → 404
    InsufficientFundsException.java        → RuntimeException → 422
    SameAccountTransferException.java      → RuntimeException → 400
    ExternalServiceException.java          → RuntimeException → 502

src/main/resources/
  application.yml                          → H2, JPA, puerto 8080, frankfurter URL+timeout

src/test/java/com/bank/api/
  service/TransferServiceTest.java         → 5 casos Mockito (éxito, misma cuenta, fondos insuficientes, origen/destino no encontrado)
  service/MovementServiceTest.java         → 5 casos Mockito (depósito, retiro, cuenta no encontrada, fondos insuficientes, retiro exacto)
  controller/MovementControllerIntegrationTest.java → 8 casos @SpringBootTest + MockMvc

Dockerfile                                 → Multi-stage: eclipse-temurin:21-jdk-alpine build → 21-jre-alpine run
docker-compose.yml                         → Servicio bank-api en puerto 8080
docs/ARCHITECTURE.md                       → Arquitectura detallada, decisiones de diseño, flujos
docs/API_REFERENCE.md                      → Referencia completa de endpoints, schemas, ejemplos curl
docs/DOMAIN_MODEL.md                       → Entidades JPA, schema SQL, invariantes
docs/BUSINESS_RULES.md                     → Todas las reglas de negocio, validaciones, concurrencia
AI_CONTEXT.md                              → Este archivo
```

---

## ENDPOINTS — REFERENCIA RÁPIDA

```
GET  /accounts/{accountNumber}/movements
     → 200 List<MovementResponse> | 404 ACCOUNT_NOT_FOUND

POST /accounts/{accountNumber}/deposit
     body: { amount: BigDecimal, description?: String }
     → 201 MovementResponse | 400 VALIDATION_ERROR | 404 ACCOUNT_NOT_FOUND

POST /accounts/{accountNumber}/withdrawal
     body: { amount: BigDecimal, description?: String }
     → 201 MovementResponse | 400 VALIDATION_ERROR | 404 ACCOUNT_NOT_FOUND | 422 INSUFFICIENT_FUNDS

POST /transfers
     body: { sourceAccountNumber, targetAccountNumber, amount, description? }
     → 201 TransferResponse | 400 VALIDATION_ERROR | 400 SAME_ACCOUNT_TRANSFER | 404 ACCOUNT_NOT_FOUND | 422 INSUFFICIENT_FUNDS

GET  /customers/{customerId}/financial-summary
     → 200 CustomerFinancialSummaryResponse | 404 CUSTOMER_NOT_FOUND

GET  /exchange-rate?from=EUR&amount=100
     → 200 ExchangeRateResponse | 400 VALIDATION_ERROR | 502 EXTERNAL_SERVICE_ERROR
```

---

## DATOS PRECARGADOS

```
Customer(id=1, name="Ana García",  documentId="12345678")
Customer(id=2, name="Carlos Ruiz", documentId="87654321")

Account(num="ACC-001", type=AHORRO,    balance=2500.00, customer=1)
Account(num="ACC-002", type=CORRIENTE, balance=1000.00, customer=1)
Account(num="ACC-003", type=AHORRO,    balance=5000.00, customer=2)

Movement(type=DEPOSITO,      amount=3000, account=ACC-001, related=null)
Movement(type=RETIRO,        amount=500,  account=ACC-001, related=null)
Movement(type=DEPOSITO,      amount=1000, account=ACC-002, related=null)
Movement(type=DEPOSITO,      amount=5000, account=ACC-003, related=null)
Movement(type=TRANSFERENCIA, amount=200,  account=ACC-001, related=ACC-003)
Movement(type=TRANSFERENCIA, amount=200,  account=ACC-003, related=ACC-001)
```

---

## LÓGICA CRÍTICA — TRANSFERENCIA

```java
// TransferService.transfer() — @Transactional(isolation=REPEATABLE_READ)

1. if source == target → throw SameAccountTransferException          // 400
2. source = findByAccountNumberWithLock(src)                         // SELECT FOR UPDATE
   target = findByAccountNumber(tgt)
3. if source.balance < amount → throw InsufficientFundsException     // 422
4. source.balance -= amount
   target.balance += amount
5. save Movement(TRANSFERENCIA, source, relatedAccount=target)
   save Movement(TRANSFERENCIA, target, relatedAccount=source)       // trazabilidad bidireccional
6. return TransferResponse(source.number, source.balance, target.number, target.balance, amount, desc)
```

---

## LÓGICA CRÍTICA — EXCHANGE RATE

```java
// ExchangeRateService.convert(from, amount)

if from == "USD" → return { rate=1, converted=amount }              // sin llamada HTTP
else:
  GET https://api.frankfurter.app/latest?from={from}&to=USD         // timeout 5s
  if HTTP 404 → throw ExternalServiceException("Moneda no soportada")
  if timeout  → throw ExternalServiceException("Servicio no disponible")
  rate = response.rates["USD"]
  return { from, "USD", amount, amount*rate, rate }
```

---

## MANEJO DE ERRORES

```
Excepción                         HTTP   Código JSON
─────────────────────────────────────────────────────────────
AccountNotFoundException          404    ACCOUNT_NOT_FOUND
CustomerNotFoundException         404    CUSTOMER_NOT_FOUND
InsufficientFundsException        422    INSUFFICIENT_FUNDS
SameAccountTransferException      400    SAME_ACCOUNT_TRANSFER
ExternalServiceException          502    EXTERNAL_SERVICE_ERROR
MethodArgumentNotValidException   400    VALIDATION_ERROR
Exception (fallback)              500    INTERNAL_ERROR
```

Formato de error:
```json
{ "success": false, "error": { "code": "...", "message": "..." }, "timestamp": "..." }
```

---

## DECISIONES DE DISEÑO — RESUMEN

| Decisión | Por qué |
|---|---|
| `BigDecimal` para montos | IEEE 754 introduce errores de punto flotante inadmisibles en finanzas |
| `PESSIMISTIC_WRITE` en retiro y transferencia | Previene race condition que llevaría saldo a negativo bajo carga concurrente |
| `REPEATABLE_READ` en transferencia | El saldo leído para validar no cambia antes de la actualización en la misma transacción |
| `related_account_id` en Movement | Trazabilidad bidireccional sin tabla TRANSFER separada; historial completo por cuenta sin JOINs extras |
| Java Records para DTOs | Inmutabilidad nativa, menos boilerplate, soportados plenamente en Java 21 |
| H2 in-memory | Sin infraestructura externa; intercambiable por PostgreSQL solo con cambios en `application.yml` |
| `CommandLineRunner` para datos | Idempotente (`if count > 0 return`), usa tipos JPA seguros, más flexible que `data.sql` |
| `WebClient` en vez de `RestTemplate` | `RestTemplate` en modo mantenimiento desde Spring 5; WebClient es la API moderna |
| Arquitectura en capas | Pragmática para el scope; hexagonal añadiría complejidad sin beneficio proporcional |
| Respuesta envuelta en `ApiResponse<T>` | Contrato uniforme: cliente siempre puede verificar `success` sin parsing condicional |

---

## CÓMO EXTENDER EL SISTEMA

### Cambiar a PostgreSQL
```yaml
# application.yml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/bankdb
    username: postgres
    password: secret
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate.ddl-auto: validate
```
Agregar en pom.xml: `org.postgresql:postgresql`

### Agregar autenticación JWT
Agregar `spring-boot-starter-security` + `jjwt`. Implementar `SecurityFilterChain` en un nuevo `SecurityConfig.java`. Los endpoints de solo lectura pueden ser públicos; los de escritura requieren `ROLE_USER`.

### Agregar paginación a movimientos
```java
// MovementRepository
Page<Movement> findByAccountIdOrderByCreatedAtDesc(Long accountId, Pageable pageable);
// MovementController
@GetMapping("/movements")
public ApiResponse<Page<MovementResponse>> getMovements(
    @PathVariable String accountNumber,
    @PageableDefault(size = 20, sort = "createdAt", direction = DESC) Pageable pageable)
```

### Agregar migraciones de base de datos
Agregar `flyway-core`. Cambiar `ddl-auto: validate`. Crear `src/main/resources/db/migration/V1__init.sql`.
