# Recode a Single File: Orbital → JPMC Commerce Platform

Recode a given source file, replacing Orbital Gateway API calls with JP Morgan Commerce Platform REST API equivalents.

## Usage

```
/orbital-recode-file <file-path>
```

Example: `/orbital-recode-file src/payments/orbital_client.py`

## Instructions for Claude

When this skill is invoked with a file path argument (`$ARGUMENTS`), perform the following steps. If no file is given, ask the user for the path.

---

### Step 1 — Read and understand the file

Read the full file. Identify:
- Language / framework (Python, Java, Node.js, PHP, Ruby, C#, etc.)
- HTTP client library in use (requests, OkHttp, axios, Guzzle, RestSharp, etc.)
- How XML is built (string templates, DOM, JAXB, etc.)
- Where credentials are sourced (env vars, config, hardcoded)
- All Orbital operations present (NewOrder, MarkForCapture, Refund, Reversal, Profile)

---

### Step 2 — Plan the rewrite

Before editing, output a brief plan:

```
File: <path>
Language: <lang>
Operations found: <list>
HTTP client: <current> → <will use>
XML builder: <current> → removed (JSON)
Auth change: hardcoded/config creds → OAuth token fetch
```

Ask for confirmation if the file is >300 lines or if there are ambiguous patterns.

---

### Step 3 — Apply transformations

Apply ALL of the following transformations that are relevant:

#### A. Authentication
- Remove Orbital username/password from request payloads
- Add an `get_access_token()` / `fetchToken()` helper that calls:
  ```
  POST {BASE_URL}/oauth2/v1/token
  grant_type=client_credentials&client_id=…&client_secret=…&scope=payments
  ```
- Cache the token and refresh when `expires_in` is near
- Set env var names: `JPMC_CLIENT_ID`, `JPMC_CLIENT_SECRET`, `JPMC_MERCHANT_ID`

#### B. Endpoint URLs
Replace Orbital endpoint constants:
- `orbitalvar.chasepaymentech.com` → `api.uat.jpmorgan.com` (sandbox)
- `orbital1.chasepaymentech.com` / `orbital2.chasepaymentech.com` → `api.jpmorgan.com`

#### C. NewOrder → POST /payments/v1/payments

Map fields:
| Orbital field | JPMC field |
|---|---|
| `Amount` (integer cents) | `transactionAmount.total` (decimal string, divide by 100) |
| `CurrencyCode` (ISO numeric) | `transactionAmount.currency` (ISO alpha-3, e.g. 840→"USD") |
| `MessageType` = "A" | `requestType`: "PaymentCardPreAuthTransaction" |
| `MessageType` = "AC" | `requestType`: "PaymentCardSaleTransaction" |
| `CCAccountNum` | `paymentMethod.paymentCard.number` |
| `CCExpireDate` MMYY | `paymentMethod.paymentCard.expiryDate.month/year` |
| `CardSecVal` | `paymentMethod.paymentCard.securityCode` |
| `OrderID` | `order.orderId` |
| `AVSaddress1` | `billingAddress.address1` |
| `AVSzip` | `billingAddress.postalCode` |
| `CustomerRefNum` (profile token) | `paymentMethod.paymentToken.value` |
| `MerchantID` | `merchantId` |

Response field mapping:
| Orbital | JPMC |
|---|---|
| `TxRefNum` | `ipgTransactionId` |
| `ApprovalStatus` == "1" | HTTP 201, `transactionStatus` == "APPROVED" |
| `ProcStatus` | `error.code` (on 4xx/5xx) |
| `RespCode` | `approvalCode` |
| `AVSRespCode` | `avsResponse.streetMatch` + `avsResponse.postalCodeMatch` |
| `CVV2RespCode` | `securityCodeResponse` |

#### D. MarkForCapture → POST /payments/v1/payments/{ipgTransactionId}/postauth
- Use stored `ipgTransactionId` in URL path
- Body: `{ "requestType": "PostAuthTransaction", "transactionAmount": { "total": "…", "currency": "…" } }`

#### E. Refund → POST /payments/v1/payments/{ipgTransactionId}/return
- Body: `{ "requestType": "ReturnTransaction", "transactionAmount": { "total": "…", "currency": "…" } }`
- For unlinked refunds: `POST /payments/v1/payments` with `requestType: "CreditTransaction"`

#### F. Reversal (Void) → POST /payments/v1/payments/{ipgTransactionId}/void
- Body: `{ "requestType": "VoidTransaction" }`

#### G. Profile → POST /payments/v1/payment-methods
- Create: `POST /payments/v1/payment-methods` with `requestType: "PaymentCardPaymentMethod"`
- Response `paymentToken.value` replaces `CustomerRefNum`
- Delete: `DELETE /payments/v1/payment-methods/{paymentToken}`
- Read: `GET /payments/v1/payment-methods/{paymentToken}`

#### H. Error handling
- Replace XML `ProcStatus` checks with HTTP status code checks (4xx = error, 201 = success)
- Parse JSON `error.code` and `error.message` on failures
- Add retry logic on 429 (rate limit) and 5xx with exponential backoff

#### I. Request-ID header
- Generate a UUID v4 per request and send as `Request-ID` header (idempotency)

---

### Step 4 — Write the recoded file

Edit the file in-place with all transformations applied. Preserve the file's existing structure, function names (where logical), and comments. Add brief comments only where the mapping is non-obvious.

---

### Step 5 — Output a diff summary

After editing, list:
- Functions/methods changed
- Functions added (e.g. token fetch)
- Environment variables now required: `JPMC_CLIENT_ID`, `JPMC_CLIENT_SECRET`, `JPMC_MERCHANT_ID`, `JPMC_BASE_URL`
- Any Orbital logic that could not be automatically mapped (manual review needed)

Remind the user to run `/orbital-scan` to check if other files still reference Orbital patterns.
