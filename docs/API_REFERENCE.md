# API Reference

**Base URL:** `http://localhost:8080/api/v1`
**Content-Type:** `application/json`
**Todas las respuestas siguen el envelope `ApiResponse<T>`**

---

## Envelope de respuesta

### Éxito
```json
{
  "success": true,
  "data": { ... },
  "timestamp": "2026-03-11T10:30:00"
}
```

### Error
```json
{
  "success": false,
  "error": {
    "code": "ERROR_CODE",
    "message": "Descripción legible del error"
  },
  "timestamp": "2026-03-11T10:30:00"
}
```

> `data` es `null` en errores. `error` es `null` en éxitos. `@JsonInclude(NON_NULL)` omite el campo nulo.

---

## Tabla de Códigos de Error

| Código | HTTP | Condición |
|---|---|---|
| `ACCOUNT_NOT_FOUND` | 404 | Número de cuenta no existe |
| `CUSTOMER_NOT_FOUND` | 404 | ID de cliente no existe |
| `INSUFFICIENT_FUNDS` | 422 | Saldo < monto solicitado en retiro/transferencia |
| `SAME_ACCOUNT_TRANSFER` | 400 | `sourceAccountNumber == targetAccountNumber` |
| `VALIDATION_ERROR` | 400 | Fallo Bean Validation (`@NotNull`, `@DecimalMin`, etc.) |
| `EXTERNAL_SERVICE_ERROR` | 502 | Frankfurter API no disponible o moneda no soportada |
| `INTERNAL_ERROR` | 500 | Error no controlado |

---

## Endpoints

---

### 1. Consultar movimientos de una cuenta

```
GET /accounts/{accountNumber}/movements
```

**Path params:**

| Param | Tipo | Descripción |
|---|---|---|
| `accountNumber` | String | Número de cuenta (ej. `ACC-001`) |

**Respuesta exitosa — 200 OK:**
```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "type": "DEPOSITO",
      "amount": 3000.0000,
      "description": "Depósito inicial",
      "createdAt": "2026-03-11T10:00:00",
      "relatedAccountNumber": null
    },
    {
      "id": 5,
      "type": "TRANSFERENCIA",
      "amount": 200.0000,
      "description": "Transferencia a Carlos → ACC-003",
      "createdAt": "2026-03-11T10:01:00",
      "relatedAccountNumber": "ACC-003"
    }
  ],
  "timestamp": "2026-03-11T10:30:00"
}
```

> Ordenados por `createdAt` descendente (más reciente primero).
> `relatedAccountNumber` es `null` para depósitos y retiros. Para transferencias, contiene la cuenta contraparte.

**Error — 404:**
```json
{
  "success": false,
  "error": { "code": "ACCOUNT_NOT_FOUND", "message": "Cuenta no encontrada: ACC-999" },
  "timestamp": "..."
}
```

**Ejemplo:**
```bash
curl http://localhost:8080/api/v1/accounts/ACC-001/movements
```

---

### 2. Depósito

```
POST /accounts/{accountNumber}/deposit
```

**Path params:**

| Param | Tipo | Descripción |
|---|---|---|
| `accountNumber` | String | Número de cuenta destino |

**Body:**
```json
{
  "amount": 500.00,
  "description": "Depósito en efectivo"
}
```

| Campo | Tipo | Requerido | Validación |
|---|---|---|---|
| `amount` | BigDecimal | Sí | `> 0` |
| `description` | String | No | Max 255 chars |

**Respuesta exitosa — 201 Created:**
```json
{
  "success": true,
  "data": {
    "id": 7,
    "type": "DEPOSITO",
    "amount": 500.0000,
    "description": "Depósito en efectivo",
    "createdAt": "2026-03-11T10:30:00",
    "relatedAccountNumber": null
  },
  "timestamp": "2026-03-11T10:30:00"
}
```

**Errores posibles:**

| HTTP | Código | Condición |
|---|---|---|
| 400 | `VALIDATION_ERROR` | `amount` ≤ 0 o `null` |
| 404 | `ACCOUNT_NOT_FOUND` | Cuenta no existe |

**Ejemplo:**
```bash
curl -X POST http://localhost:8080/api/v1/accounts/ACC-001/deposit \
  -H "Content-Type: application/json" \
  -d '{"amount": 500.00, "description": "Depósito en efectivo"}'
```

---

### 3. Retiro

```
POST /accounts/{accountNumber}/withdrawal
```

**Path params:**

| Param | Tipo | Descripción |
|---|---|---|
| `accountNumber` | String | Número de cuenta a debitar |

**Body:**
```json
{
  "amount": 200.00,
  "description": "Retiro cajero"
}
```

| Campo | Tipo | Requerido | Validación |
|---|---|---|---|
| `amount` | BigDecimal | Sí | `> 0` |
| `description` | String | No | Max 255 chars |

**Respuesta exitosa — 201 Created:**
```json
{
  "success": true,
  "data": {
    "id": 8,
    "type": "RETIRO",
    "amount": 200.0000,
    "description": "Retiro cajero",
    "createdAt": "2026-03-11T10:31:00",
    "relatedAccountNumber": null
  },
  "timestamp": "2026-03-11T10:31:00"
}
```

**Errores posibles:**

| HTTP | Código | Condición |
|---|---|---|
| 400 | `VALIDATION_ERROR` | `amount` ≤ 0 o `null` |
| 404 | `ACCOUNT_NOT_FOUND` | Cuenta no existe |
| 422 | `INSUFFICIENT_FUNDS` | `balance < amount` |

**Ejemplo de error 422:**
```bash
curl -X POST http://localhost:8080/api/v1/accounts/ACC-001/withdrawal \
  -H "Content-Type: application/json" \
  -d '{"amount": 99999.00, "description": "Excede saldo"}'
```
```json
{
  "success": false,
  "error": {
    "code": "INSUFFICIENT_FUNDS",
    "message": "Fondos insuficientes en la cuenta ACC-001. Saldo disponible: 2500.00, Monto solicitado: 99999.00"
  }
}
```

**Ejemplo:**
```bash
curl -X POST http://localhost:8080/api/v1/accounts/ACC-001/withdrawal \
  -H "Content-Type: application/json" \
  -d '{"amount": 200.00, "description": "Retiro cajero"}'
```

---

### 4. Transferencia entre cuentas

```
POST /transfers
```

**Body:**
```json
{
  "sourceAccountNumber": "ACC-001",
  "targetAccountNumber": "ACC-003",
  "amount": 150.00,
  "description": "Pago deuda"
}
```

| Campo | Tipo | Requerido | Validación |
|---|---|---|---|
| `sourceAccountNumber` | String | Sí | No vacío |
| `targetAccountNumber` | String | Sí | No vacío |
| `amount` | BigDecimal | Sí | `> 0` |
| `description` | String | No | Max 255 chars |

**Respuesta exitosa — 201 Created:**
```json
{
  "success": true,
  "data": {
    "sourceAccountNumber": "ACC-001",
    "sourceNewBalance": 2350.0000,
    "targetAccountNumber": "ACC-003",
    "targetNewBalance": 5150.0000,
    "amount": 150.0000,
    "description": "Pago deuda"
  },
  "timestamp": "2026-03-11T10:32:00"
}
```

**Errores posibles:**

| HTTP | Código | Condición |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Campos vacíos o `amount` ≤ 0 |
| 400 | `SAME_ACCOUNT_TRANSFER` | `sourceAccountNumber == targetAccountNumber` |
| 404 | `ACCOUNT_NOT_FOUND` | Cuenta origen o destino no existe |
| 422 | `INSUFFICIENT_FUNDS` | `source.balance < amount` |

**Trazabilidad:** La transferencia genera **dos movimientos**:
- En la cuenta origen: `TRANSFERENCIA`, `description = "Pago deuda → ACC-003"`, `relatedAccountNumber = "ACC-003"`
- En la cuenta destino: `TRANSFERENCIA`, `description = "Pago deuda ← ACC-001"`, `relatedAccountNumber = "ACC-001"`

**Ejemplo:**
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

---

### 5. Resumen financiero de un cliente

```
GET /customers/{customerId}/financial-summary
```

**Path params:**

| Param | Tipo | Descripción |
|---|---|---|
| `customerId` | Long | ID interno del cliente |

**Respuesta exitosa — 200 OK:**
```json
{
  "success": true,
  "data": {
    "customerId": 1,
    "name": "Ana García",
    "documentId": "12345678",
    "accounts": [
      {
        "id": 1,
        "accountNumber": "ACC-001",
        "type": "AHORRO",
        "balance": 2500.0000
      },
      {
        "id": 2,
        "accountNumber": "ACC-002",
        "type": "CORRIENTE",
        "balance": 1000.0000
      }
    ]
  },
  "timestamp": "2026-03-11T10:33:00"
}
```

**Errores posibles:**

| HTTP | Código | Condición |
|---|---|---|
| 404 | `CUSTOMER_NOT_FOUND` | Cliente con ese ID no existe |

**Clientes precargados:**
- ID `1` → Ana García (DNI: 12345678)
- ID `2` → Carlos Ruiz (DNI: 87654321)

**Ejemplo:**
```bash
curl http://localhost:8080/api/v1/customers/1/financial-summary
```

---

### 6. Tipo de cambio (conversión a USD)

```
GET /exchange-rate?from={moneda}&amount={monto}
```

**Query params:**

| Param | Tipo | Requerido | Descripción |
|---|---|---|---|
| `from` | String | Sí | Código ISO 4217 (ej. `EUR`, `GBP`, `JPY`) |
| `amount` | BigDecimal | Sí | Monto a convertir, `> 0` |

**Respuesta exitosa — 200 OK:**
```json
{
  "success": true,
  "data": {
    "fromCurrency": "EUR",
    "toCurrency": "USD",
    "originalAmount": 100.00,
    "convertedAmount": 108.4200,
    "rate": 1.0842
  },
  "timestamp": "2026-03-11T10:34:00"
}
```

**Optimización:** si `from=USD`, retorna el mismo monto sin llamar a Frankfurter.

**Errores posibles:**

| HTTP | Código | Condición |
|---|---|---|
| 400 | `VALIDATION_ERROR` | `from` vacío o `amount` ≤ 0 |
| 502 | `EXTERNAL_SERVICE_ERROR` | Moneda no soportada, timeout (5s), o Frankfurter no disponible |

**Monedas soportadas:** todas las disponibles en [Frankfurter API](https://api.frankfurter.app/currencies). Ejemplos: `EUR`, `GBP`, `JPY`, `BRL`, `ARS`, `CLP`, `MXN`, `CAD`, `CHF`, `AUD`.

**Ejemplos:**
```bash
# EUR a USD
curl "http://localhost:8080/api/v1/exchange-rate?from=EUR&amount=100"

# GBP a USD
curl "http://localhost:8080/api/v1/exchange-rate?from=GBP&amount=50"

# JPY a USD
curl "http://localhost:8080/api/v1/exchange-rate?from=JPY&amount=10000"

# USD (sin llamada externa)
curl "http://localhost:8080/api/v1/exchange-rate?from=USD&amount=250"
```

---

## Datos Precargados

Al iniciar la aplicación, se crean automáticamente:

### Clientes
| ID | Nombre | DNI |
|---|---|---|
| 1 | Ana García | 12345678 |
| 2 | Carlos Ruiz | 87654321 |

### Cuentas
| Número | Titular | Tipo | Saldo inicial |
|---|---|---|---|
| ACC-001 | Ana García | AHORRO | $2,500.00 |
| ACC-002 | Ana García | CORRIENTE | $1,000.00 |
| ACC-003 | Carlos Ruiz | AHORRO | $5,000.00 |

### Movimientos históricos (6 registros)
| Cuenta | Tipo | Monto | Descripción |
|---|---|---|---|
| ACC-001 | DEPOSITO | $3,000.00 | Depósito inicial |
| ACC-001 | RETIRO | $500.00 | Retiro cajero |
| ACC-002 | DEPOSITO | $1,000.00 | Depósito inicial |
| ACC-003 | DEPOSITO | $5,000.00 | Depósito inicial |
| ACC-001 | TRANSFERENCIA | $200.00 | Transferencia a Carlos (relatedAccount: ACC-003) |
| ACC-003 | TRANSFERENCIA | $200.00 | Transferencia recibida de Ana (relatedAccount: ACC-001) |

---

## Consola H2

Disponible en `http://localhost:8080/h2-console`

| Campo | Valor |
|---|---|
| JDBC URL | `jdbc:h2:mem:bankdb` |
| Username | `sa` |
| Password | *(vacía)* |

Queries útiles:
```sql
SELECT * FROM customers;
SELECT * FROM accounts;
SELECT m.*, a.account_number, r.account_number as related
FROM movements m
JOIN accounts a ON m.account_id = a.id
LEFT JOIN accounts r ON m.related_account_id = r.id
ORDER BY m.created_at DESC;
```
