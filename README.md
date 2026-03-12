# Bank API

API REST para gestión de cuentas bancarias y movimientos financieros.

## Documentación

| Documento | Descripción |
|---|---|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Arquitectura, decisiones de diseño, flujos detallados |
| [docs/API_REFERENCE.md](docs/API_REFERENCE.md) | Referencia completa de endpoints, schemas y ejemplos |
| [docs/DOMAIN_MODEL.md](docs/DOMAIN_MODEL.md) | Entidades JPA, schema SQL, relaciones e invariantes |
| [docs/BUSINESS_RULES.md](docs/BUSINESS_RULES.md) | Reglas de negocio, validaciones y manejo de concurrencia |
| [AI_CONTEXT.md](AI_CONTEXT.md) | Contexto compacto del sistema para revisión por IA |

## Requisitos

- Java 21+
- Maven 3.8+ (o usar el wrapper incluido `./mvnw`)

## Ejecución

```bash
cd bank-api
./mvnw spring-boot:run
```

La aplicación estará disponible en `http://localhost:8080`.

### Con Docker

```bash
docker-compose up --build
```

### Consola H2 (base de datos en memoria)

Disponible en `http://localhost:8080/h2-console`
- JDBC URL: `jdbc:h2:mem:bankdb`
- Usuario: `sa`
- Contraseña: *(vacía)*

---

## Datos precargados

La aplicación inicia con los siguientes datos listos para probar:

| Cuenta  | Titular     | Tipo      | Saldo    |
|---------|-------------|-----------|----------|
| ACC-001 | Ana García  | AHORRO    | $2500.00 |
| ACC-002 | Ana García  | CORRIENTE | $1000.00 |
| ACC-003 | Carlos Ruiz | AHORRO    | $5000.00 |

- Cliente ID 1: Ana García (DNI: 12345678)
- Cliente ID 2: Carlos Ruiz (DNI: 87654321)

---

## Endpoints

### 1. Consulta de movimientos de una cuenta

```
GET /api/v1/accounts/{accountNumber}/movements
```

```bash
curl http://localhost:8080/api/v1/accounts/ACC-001/movements
```

---

### 2. Depósito

```
POST /api/v1/accounts/{accountNumber}/deposit
```

```bash
curl -X POST http://localhost:8080/api/v1/accounts/ACC-001/deposit \
  -H "Content-Type: application/json" \
  -d '{"amount": 500.00, "description": "Depósito en efectivo"}'
```

---

### 3. Retiro

```
POST /api/v1/accounts/{accountNumber}/withdrawal
```

```bash
curl -X POST http://localhost:8080/api/v1/accounts/ACC-001/withdrawal \
  -H "Content-Type: application/json" \
  -d '{"amount": 200.00, "description": "Retiro cajero"}'
```

**Error — fondos insuficientes (422):**
```bash
curl -X POST http://localhost:8080/api/v1/accounts/ACC-001/withdrawal \
  -H "Content-Type: application/json" \
  -d '{"amount": 99999.00, "description": "Excede saldo"}'
```

---

### 4. Transferencia entre cuentas

```
POST /api/v1/transfers
```

```bash
curl -X POST http://localhost:8080/api/v1/transfers \
  -H "Content-Type: application/json" \
  -d '{
    "sourceAccountNumber": "ACC-001",
    "targetAccountNumber": "ACC-003",
    "amount": 150.00,
    "description": "Pago deuda"
  }'
```

**Reglas de negocio aplicadas:**
- Origen y destino no pueden ser la misma cuenta → `400 SAME_ACCOUNT_TRANSFER`
- La cuenta origen debe tener fondos suficientes → `422 INSUFFICIENT_FUNDS`
- El monto debe ser mayor a cero → `400 VALIDATION_ERROR`

---

### 5. Resumen financiero de un cliente

```
GET /api/v1/customers/{customerId}/financial-summary
```

```bash
# Ana García (ID: 1)
curl http://localhost:8080/api/v1/customers/1/financial-summary

# Carlos Ruiz (ID: 2)
curl http://localhost:8080/api/v1/customers/2/financial-summary
```

---

### 6. Consulta de tipo de cambio (→ USD)

```
GET /api/v1/exchange-rate?from={moneda}&amount={monto}
```

```bash
# 100 EUR a USD
curl "http://localhost:8080/api/v1/exchange-rate?from=EUR&amount=100"

# 50 GBP a USD
curl "http://localhost:8080/api/v1/exchange-rate?from=GBP&amount=50"

# 1000 JPY a USD
curl "http://localhost:8080/api/v1/exchange-rate?from=JPY&amount=1000"
```

Integra con [Frankfurter API](https://www.frankfurter.app/) para tipos de cambio en tiempo real.

---

## Estructura de respuestas

**Éxito:**
```json
{
  "success": true,
  "data": { ... },
  "timestamp": "2026-03-11T10:00:00"
}
```

**Error:**
```json
{
  "success": false,
  "error": {
    "code": "INSUFFICIENT_FUNDS",
    "message": "Fondos insuficientes en la cuenta ACC-001. Saldo disponible: 100.00, Monto solicitado: 500.00"
  },
  "timestamp": "2026-03-11T10:00:00"
}
```

### Códigos de error

| Código | HTTP | Descripción |
|--------|------|-------------|
| `ACCOUNT_NOT_FOUND` | 404 | Cuenta no existe |
| `CUSTOMER_NOT_FOUND` | 404 | Cliente no existe |
| `INSUFFICIENT_FUNDS` | 422 | Saldo insuficiente para retiro/transferencia |
| `SAME_ACCOUNT_TRANSFER` | 400 | Origen y destino son la misma cuenta |
| `VALIDATION_ERROR` | 400 | Campos inválidos (monto ≤ 0, campos vacíos) |
| `EXTERNAL_SERVICE_ERROR` | 502 | Frankfurter API no disponible |
| `INTERNAL_ERROR` | 500 | Error interno del servidor |

---

## Decisiones de diseño

### BigDecimal para montos
Toda operación monetaria usa `BigDecimal` (precisión 19, escala 4). Evita los errores de representación de punto flotante de `double`/`float`, críticos en cálculos financieros.

### Lock pesimista en retiros y transferencias
`AccountRepository.findByAccountNumberWithLock()` aplica `PESSIMISTIC_WRITE` a nivel de fila. Esto serializa operaciones concurrentes sobre la misma cuenta, eliminando la posibilidad de llevar el saldo a negativo bajo carga concurrente, sin necesidad de reintentos por parte del cliente (como requeriría el lock optimista).

### Trazabilidad bidireccional con related_account_id
En lugar de una tabla separada `TRANSFER`, cada movimiento de transferencia guarda la cuenta contraparte en `related_account_id`. Resultado: ambas cuentas reflejan el movimiento con referencia cruzada, sin JOINs adicionales ni complejidad de modelo.

### H2 en memoria
Elimina cualquier dependencia de infraestructura externa. La aplicación levanta con `./mvnw spring-boot:run` sin configuración adicional. Los datos se precargan via `DataInitializer` (CommandLineRunner) que verifica idempotencia antes de insertar.

### WebClient para tipo de cambio
`RestTemplate` está en modo mantenimiento desde Spring 5. `WebClient` es la API moderna recomendada para llamadas HTTP, incluido en `spring-boot-starter-webflux` sin convertir la aplicación a reactiva.

### Arquitectura en capas
Controller → Service → Repository. Pragmática para el scope del proyecto. La arquitectura hexagonal añadiría ports/adapters sin beneficio proporcional en este tamaño.

---

## Supuestos y limitaciones

- **Moneda base:** todas las cuentas operan en USD. No se hace conversión automática en depósitos/retiros.
- **Autenticación:** no implementada. Los endpoints son públicos. Para producción se recomendaría JWT via Spring Security.
- **Paginación:** el historial de movimientos retorna todos los registros. En producción se agregaría `Pageable`.
- **Base de datos:** H2 en memoria — los datos se pierden al reiniciar. Para producción se reemplazaría por PostgreSQL cambiando solo el `application.yml` y el driver.
- **Frankfurter API:** si el servicio externo no está disponible, se retorna `502 Bad Gateway` con mensaje descriptivo.
