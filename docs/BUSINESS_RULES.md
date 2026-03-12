# Reglas de Negocio y Validaciones

## Validaciones de Entrada (Bean Validation)

Aplicadas en DTOs via Jakarta Validation. Capturadas por `GlobalExceptionHandler` → `400 VALIDATION_ERROR`.

### DepositRequest / WithdrawalRequest

| Campo | Regla | Anotación | Mensaje |
|---|---|---|---|
| `amount` | No nulo | `@NotNull` | "El monto es obligatorio" |
| `amount` | Mayor a cero | `@DecimalMin("0.01")` | "El monto debe ser mayor a cero" |
| `description` | Opcional | — | — |

### TransferRequest

| Campo | Regla | Anotación | Mensaje |
|---|---|---|---|
| `sourceAccountNumber` | No vacío | `@NotBlank` | "La cuenta origen es obligatoria" |
| `targetAccountNumber` | No vacío | `@NotBlank` | "La cuenta destino es obligatoria" |
| `amount` | No nulo | `@NotNull` | "El monto es obligatorio" |
| `amount` | Mayor a cero | `@DecimalMin("0.01")` | "El monto debe ser mayor a cero" |
| `description` | Opcional | — | — |

### ExchangeRateController (query params)

| Param | Regla | Anotación | Resultado |
|---|---|---|---|
| `from` | No vacío | `@NotBlank` | 400 VALIDATION_ERROR |
| `amount` | Mayor a cero | `@DecimalMin("0.01")` | 400 VALIDATION_ERROR |

---

## Reglas de Negocio por Operación

### Depósito

| Regla | Implementación | Error |
|---|---|---|
| El monto debe ser > 0 | `@DecimalMin("0.01")` en DTO | 400 VALIDATION_ERROR |
| La cuenta debe existir | `findByAccountNumber` → Optional | 404 ACCOUNT_NOT_FOUND |
| El saldo se incrementa exactamente en `amount` | `balance = balance + amount` con BigDecimal | — |

**Efecto:** `account.balance += amount`

---

### Retiro

| Regla | Implementación | Error |
|---|---|---|
| El monto debe ser > 0 | `@DecimalMin("0.01")` en DTO | 400 VALIDATION_ERROR |
| La cuenta debe existir | `findByAccountNumberWithLock` → Optional | 404 ACCOUNT_NOT_FOUND |
| El saldo disponible debe cubrir el monto | `balance.compareTo(amount) >= 0` | 422 INSUFFICIENT_FUNDS |
| Operación atómica bajo lock pesimista | `PESSIMISTIC_WRITE` en query | — |

**Efecto:** `account.balance -= amount`

**Mensaje de error INSUFFICIENT_FUNDS:**
```
"Fondos insuficientes en la cuenta {accountNumber}.
 Saldo disponible: {balance}, Monto solicitado: {amount}"
```

---

### Transferencia

| Regla | Implementación | Error |
|---|---|---|
| El monto debe ser > 0 | `@DecimalMin("0.01")` en DTO | 400 VALIDATION_ERROR |
| Origen y destino no pueden ser iguales | `source.equals(target)` al inicio | 400 SAME_ACCOUNT_TRANSFER |
| La cuenta origen debe existir | `findByAccountNumberWithLock` → Optional | 404 ACCOUNT_NOT_FOUND |
| La cuenta destino debe existir | `findByAccountNumber` → Optional | 404 ACCOUNT_NOT_FOUND |
| La cuenta origen debe tener fondos suficientes | `source.balance.compareTo(amount) >= 0` | 422 INSUFFICIENT_FUNDS |
| Ambas cuentas deben reflejar el resultado | Actualización dentro de `@Transactional` | — |
| Debe quedar trazabilidad en ambas cuentas | Dos movimientos `TRANSFERENCIA` con `relatedAccount` | — |

**Efectos:**
- `source.balance -= amount`
- `target.balance += amount`
- Movimiento en source: `TRANSFERENCIA, relatedAccount=target, description="{desc} → {target.number}"`
- Movimiento en target: `TRANSFERENCIA, relatedAccount=source, description="{desc} ← {source.number}"`

**Garantía de atomicidad:** `@Transactional(isolation = Isolation.REPEATABLE_READ)` con lock pesimista en cuenta origen. Si cualquier paso falla, toda la transacción hace rollback.

---

### Resumen Financiero

| Regla | Implementación | Error |
|---|---|---|
| El cliente debe existir | `findById` → Optional | 404 CUSTOMER_NOT_FOUND |
| Se retornan todas las cuentas del cliente | `findByCustomerId(customerId)` | — |

---

### Tipo de Cambio

| Regla | Implementación | Error |
|---|---|---|
| La moneda origen no puede estar vacía | `@NotBlank` en controller | 400 VALIDATION_ERROR |
| El monto debe ser > 0 | `@DecimalMin("0.01")` en controller | 400 VALIDATION_ERROR |
| Si la moneda ya es USD, retornar sin llamada HTTP | `if "USD".equals(currency)` | — |
| Timeout de 5 segundos en llamada externa | `.timeout(Duration.ofSeconds(5))` | 502 EXTERNAL_SERVICE_ERROR |
| Si la moneda no existe en Frankfurter | HTTP 404 de Frankfurter | 502 EXTERNAL_SERVICE_ERROR |

---

## Manejo de Errores — Mapa Completo

```
GlobalExceptionHandler (@RestControllerAdvice)
│
├── AccountNotFoundException        → 404  { code: "ACCOUNT_NOT_FOUND" }
├── CustomerNotFoundException       → 404  { code: "CUSTOMER_NOT_FOUND" }
├── InsufficientFundsException      → 422  { code: "INSUFFICIENT_FUNDS" }
├── SameAccountTransferException    → 400  { code: "SAME_ACCOUNT_TRANSFER" }
├── ExternalServiceException        → 502  { code: "EXTERNAL_SERVICE_ERROR" }
├── MethodArgumentNotValidException → 400  { code: "VALIDATION_ERROR" }
│     └── mensajes de todos los campos fallidos, concatenados con ", "
└── Exception (fallback)            → 500  { code: "INTERNAL_ERROR" }
                                           message: "Error interno del servidor"
                                           (el mensaje real NO se expone al cliente)
```

---

## Concurrencia y Consistencia

### Problema

Sin control de concurrencia, dos hilos ejecutando retiros/transferencias simultáneos en la misma cuenta pueden superar el saldo:

```
Tiempo | Hilo A                         | Hilo B
-------|--------------------------------|--------------------------------
T1     | lee balance=1000               | lee balance=1000
T2     | valida 1000 >= 800 ✓           | valida 1000 >= 700 ✓
T3     | balance = 200                  |
T4     |                                | balance = 300  ← Error: debería ser -500
```

### Solución

`AccountRepository.findByAccountNumberWithLock()` emite `SELECT ... FOR UPDATE`:

```sql
SELECT a.* FROM accounts a WHERE a.account_number = ? FOR UPDATE
```

H2 (y PostgreSQL en producción) bloquea la fila. El Hilo B espera hasta que el Hilo A haga commit, luego lee el saldo actualizado y aplica su validación correctamente.

### Alcance del Lock

- **Retiro:** lock en la única cuenta involucrada.
- **Transferencia:** lock en la cuenta origen (la que puede quedar sin fondos). La cuenta destino no necesita lock porque solo recibe fondos.

---

## Supuestos y Limitaciones

| Supuesto/Limitación | Descripción |
|---|---|
| Moneda única (USD) | Todas las cuentas operan en USD. No hay conversión implícita en depósitos/retiros/transferencias. |
| Sin autenticación | Los endpoints son públicos. Para producción: JWT via Spring Security. |
| Sin paginación | `getMovements` retorna todos los movimientos. En producción se agregaría `Pageable`. |
| Base de datos volátil | H2 in-memory: datos se pierden al reiniciar. Reemplazable por PostgreSQL solo cambiando `application.yml` y el driver en `pom.xml`. |
| Balance nunca negativo | Garantizado por lógica de servicio, no por constraint de base de datos. |
| Número de cuenta como String | No se valida formato. Permite cualquier cadena hasta 20 caracteres. |
