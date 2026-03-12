# Arquitectura del Sistema

## Visión General

**Bank API** es una API REST para gestión de cuentas bancarias y movimientos financieros, construida con Java 21 y Spring Boot 3.5. Sigue una arquitectura en capas clásica con separación estricta de responsabilidades.

---

## Stack Tecnológico

| Componente | Tecnología | Versión | Justificación |
|---|---|---|---|
| Lenguaje | Java | 21 | LTS, Records nativos, pattern matching |
| Framework | Spring Boot | 3.5.0 | Autoconfiguración, ecosistema maduro |
| Persistencia | Spring Data JPA + Hibernate | 6.x | Abstracción ORM, soporte a locks |
| Base de datos | H2 (in-memory) | 2.x | Sin infraestructura externa; reemplazable por PostgreSQL solo cambiando `application.yml` |
| HTTP Client | Spring WebFlux WebClient | — | Sustituto moderno de RestTemplate (en mantenimiento) |
| Validación | Jakarta Bean Validation | 3.x | Validaciones declarativas en DTOs |
| Reducción boilerplate | Lombok | — | `@RequiredArgsConstructor`, `@Getter`, `@Setter`, `@Slf4j` |
| Build | Maven Wrapper | 3.8+ | Reproducible sin instalación local |

---

## Estructura de Capas

```
HTTP Request
     │
     ▼
┌─────────────────────────────────────────────┐
│  CONTROLLER  (@RestController)               │
│  Traduce HTTP ↔ objetos de dominio.          │
│  Solo valida (@Valid) y delega al Service.   │
└─────────────────────┬───────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────┐
│  SERVICE  (@Service, @Transactional)         │
│  Lógica de negocio, reglas, orquestación.    │
│  Maneja transacciones y locks.               │
└─────────────────────┬───────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────┐
│  REPOSITORY  (JpaRepository)                 │
│  Acceso a datos. Queries JPA + lock hints.   │
└─────────────────────┬───────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────┐
│  ENTITY  (@Entity)                           │
│  Modelo de dominio persistido en H2.         │
└─────────────────────────────────────────────┘
```

---

## Estructura de Paquetes

```
src/main/java/com/bank/api/
│
├── BankApiApplication.java              ← Punto de entrada @SpringBootApplication
│
├── config/
│   ├── DataInitializer.java             ← Datos precargados (CommandLineRunner, @Order(1))
│   └── WebClientConfig.java             ← Bean WebClient para Frankfurter API
│
├── domain/
│   ├── entity/
│   │   ├── Customer.java                ← Titular de cuentas
│   │   ├── Account.java                 ← Cuenta bancaria
│   │   └── Movement.java                ← Registro de operación
│   └── enums/
│       ├── AccountType.java             ← AHORRO | CORRIENTE
│       └── MovementType.java            ← DEPOSITO | RETIRO | TRANSFERENCIA
│
├── dto/
│   ├── request/
│   │   ├── DepositRequest.java          ← { amount, description }
│   │   ├── WithdrawalRequest.java       ← { amount, description }
│   │   └── TransferRequest.java         ← { sourceAccountNumber, targetAccountNumber, amount, description }
│   └── response/
│       ├── ApiResponse.java             ← Envoltura genérica { success, data, error, timestamp }
│       ├── MovementResponse.java        ← Datos de un movimiento
│       ├── AccountSummaryResponse.java  ← Datos resumidos de cuenta
│       ├── CustomerFinancialSummaryResponse.java ← Cliente + listado cuentas
│       ├── TransferResponse.java        ← Resultado de transferencia
│       └── ExchangeRateResponse.java    ← Resultado de conversión de moneda
│
├── repository/
│   ├── CustomerRepository.java          ← JpaRepository<Customer, Long>
│   ├── AccountRepository.java           ← + findByAccountNumber + findByCustomerId + findByAccountNumberWithLock
│   └── MovementRepository.java          ← + findByAccountIdOrderByCreatedAtDesc
│
├── service/
│   ├── AccountService.java              ← getCustomerFinancialSummary()
│   ├── MovementService.java             ← getMovements(), deposit(), withdraw()
│   ├── TransferService.java             ← transfer() — operación crítica con lock
│   └── ExchangeRateService.java         ← convert() — integración Frankfurter
│
├── controller/
│   ├── AccountController.java           ← GET /customers/{id}/financial-summary
│   ├── MovementController.java          ← GET+POST /accounts/{num}/movements|deposit|withdrawal
│   ├── TransferController.java          ← POST /transfers
│   └── ExchangeRateController.java      ← GET /exchange-rate
│
└── exception/
    ├── GlobalExceptionHandler.java      ← @RestControllerAdvice centralizado
    ├── AccountNotFoundException.java    ← → 404
    ├── CustomerNotFoundException.java   ← → 404
    ├── InsufficientFundsException.java  ← → 422
    ├── SameAccountTransferException.java← → 400
    └── ExternalServiceException.java    ← → 502
```

---

## Decisiones de Diseño

### 1. BigDecimal para montos monetarios

Toda operación monetaria usa `BigDecimal` con precisión 19, escala 4. `double` y `float` usan representación IEEE 754 binaria que no puede representar exactamente fracciones decimales (0.1 + 0.2 ≠ 0.3). En operaciones financieras, este error se acumula y produce resultados incorrectos.

### 2. Lock pesimista (PESSIMISTIC_WRITE) en retiros y transferencias

```
Escenario sin lock:
  Hilo A: lee balance=1000, valida 1000 >= 800 ✓
  Hilo B: lee balance=1000, valida 1000 >= 700 ✓
  Hilo A: actualiza balance=200
  Hilo B: actualiza balance=300   ← ¡Inconsistente! debería ser -500
```

`findByAccountNumberWithLock()` aplica `SELECT ... FOR UPDATE` a nivel de fila en H2/PostgreSQL. Serializa el acceso a la misma cuenta sin rechazar al cliente (como haría el lock optimista con `@Version`).

### 3. Trazabilidad bidireccional con `related_account_id`

En lugar de una tabla `TRANSFER` con dos foreign keys, cada movimiento de transferencia guarda la cuenta contraparte en `related_account_id`. Una transferencia de ACC-001 → ACC-003 genera:

```
MOVEMENT(type=TRANSFERENCIA, account=ACC-001, relatedAccount=ACC-003, amount=100)
MOVEMENT(type=TRANSFERENCIA, account=ACC-003, relatedAccount=ACC-001, amount=100)
```

Ventajas: historial completo por cuenta sin JOINs, simplicidad de modelo, trazabilidad sin tabla intermedia.

### 4. Java Records para DTOs

Inmutabilidad nativa, constructores canónicos, `equals/hashCode/toString` automáticos. Disponibles desde Java 16, plenamente soportados en Java 21.

### 5. H2 en memoria con CommandLineRunner

`DataInitializer` implementa `CommandLineRunner` con verificación de idempotencia (`if (customerRepository.count() > 0) return`). Permite reinicializar datos sin duplicados. Preferido sobre `data.sql` porque usa el repositorio JPA directamente, con tipos seguros.

### 6. Arquitectura en capas vs. Hexagonal

La arquitectura hexagonal (Ports & Adapters) añadiría interfaces de puerto, adaptadores de entrada/salida y mayor separación del dominio de la infraestructura. Para el scope de este proyecto (una sola aplicación, sin múltiples adaptadores de entrada), el overhead no aporta beneficio proporcional. Si el sistema creciera hacia microservicios o múltiples canales de entrada, la refactorización a hexagonal sería directa dado que el dominio ya está aislado.

---

## Flujo de una Transferencia (operación más crítica)

```
POST /api/v1/transfers
        │
        ▼
TransferController.transfer(@Valid request)
        │ delega
        ▼
TransferService.transfer(request)     ← @Transactional(isolation=REPEATABLE_READ)
  │
  ├─ 1. Validar source != target       → SameAccountTransferException (400)
  │
  ├─ 2. findByAccountNumberWithLock(source)  ← SELECT FOR UPDATE (bloquea fila)
  │     └─ no existe → AccountNotFoundException (404)
  │
  ├─ 3. findByAccountNumber(target)
  │     └─ no existe → AccountNotFoundException (404)
  │
  ├─ 4. Validar source.balance >= amount  → InsufficientFundsException (422)
  │
  ├─ 5. source.balance -= amount
  │     target.balance += amount
  │
  ├─ 6. save(Movement TRANSFERENCIA en source, relatedAccount=target)
  │     save(Movement TRANSFERENCIA en target, relatedAccount=source)
  │
  └─ 7. return TransferResponse (ambos saldos actualizados)
        │
        ▼ (commit automático al salir de @Transactional)
```

---

## Integración Externa: Frankfurter API

- **URL base**: `https://api.frankfurter.app`
- **Endpoint usado**: `GET /latest?from={CURRENCY}&to=USD`
- **Timeout**: 5 segundos (configurable via `exchange-rate.timeout-seconds`)
- **Optimización**: si `from=USD`, retorna el monto directamente sin llamada HTTP
- **Manejo de errores**:
  - HTTP 404 → moneda no soportada → `ExternalServiceException`
  - Timeout / red caída → `ExternalServiceException` → HTTP 502

---

## Configuración de la Aplicación

```yaml
# application.yml
spring:
  datasource:
    url: jdbc:h2:mem:bankdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
  jpa:
    hibernate.ddl-auto: create-drop     # esquema recreado en cada arranque
  h2.console.enabled: true              # disponible en /h2-console

server:
  port: 8080

exchange-rate:
  base-url: https://api.frankfurter.app
  timeout-seconds: 5
```

---

## Tests

| Clase | Tipo | Casos cubiertos |
|---|---|---|
| `TransferServiceTest` | Unitario (Mockito) | éxito, misma cuenta, fondos insuficientes, cuenta origen no encontrada, cuenta destino no encontrada |
| `MovementServiceTest` | Unitario (Mockito) | depósito actualiza saldo, cuenta no encontrada, retiro actualiza saldo, fondos insuficientes, retiro exacto |
| `MovementControllerIntegrationTest` | Integración (@SpringBootTest + MockMvc) | movimientos OK, cuenta desconocida 404, depósito 201, monto negativo 400, retiro insuficiente 422, transferencia 201, misma cuenta 400, resumen financiero 200 |

**Comando para ejecutar tests:**
```bash
./mvnw test
```
