# Orbital XML Gateway → JP Morgan Commerce Platform REST API Mapping

**Audience:** Developers migrating payment integrations from Chase Paymentech Orbital Gateway to the JP Morgan Commerce Platform (JPMC) API.

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Connectivity & Environments](#2-connectivity--environments)
3. [Authentication & Session Management](#3-authentication--session-management)
4. [Request Structure](#4-request-structure)
5. [Authorization (Auth-Only)](#5-authorization-auth-only)
6. [Sale (Auth + Capture)](#6-sale-auth--capture)
7. [Capture (Mark for Capture)](#7-capture-mark-for-capture)
8. [Refund / Return](#8-refund--return)
9. [Void / Reversal](#9-void--reversal)
10. [Customer Profile & Tokenization](#10-customer-profile--tokenization)
11. [3-D Secure (3DS)](#11-3-d-secure-3ds)
12. [Field Reference: NewOrder](#12-field-reference-neworder)
13. [Response Field Mapping](#13-response-field-mapping)
14. [Error & Status Code Mapping](#14-error--status-code-mapping)
15. [AVS Response Code Mapping](#15-avs-response-code-mapping)
16. [CVV Response Code Mapping](#16-cvv-response-code-mapping)
17. [Currency Code Conversion](#17-currency-code-conversion)
18. [Card Brand Mapping](#18-card-brand-mapping)
19. [Migration Checklist](#19-migration-checklist)

---

## 1. Architecture Overview

| Dimension | Orbital Gateway | JPMC Commerce Platform |
|---|---|---|
| Protocol | HTTPS POST with XML body | HTTPS REST with JSON body |
| Message format | XML (Chase DTD) | JSON (OpenAPI 3.0 schema) |
| API style | RPC via action elements (`<NewOrder>`, `<Refund>`, …) | Resource-oriented REST (`/payments`, `/payments/{id}/return`, …) |
| Versioning | `<IndustryType>` + DTD version header | URL path versioning (`/v1/`) |
| Idempotency | `<OrderID>` uniqueness enforced by gateway | `Request-ID` UUID header per request |
| Credentials | Per-request username/password in XML body | OAuth 2.0 Bearer token in `Authorization` header |
| Transaction reference | `TxRefNum` (alphanumeric) | `ipgTransactionId` (numeric string) |
| Customer token | `CustomerRefNum` (profile-based) | `paymentToken.value` (tokenization service) |

---

## 2. Connectivity & Environments

### Orbital Endpoints

| Environment | Host | Port |
|---|---|---|
| Certification (test) | `orbitalvar.chasepaymentech.com` | 443 |
| Production (primary) | `orbital1.chasepaymentech.com` | 443 |
| Production (failover) | `orbital2.chasepaymentech.com` | 443 |
| Path | `/authorize` | |

**Full Orbital URL:** `https://orbitalvar.chasepaymentech.com/authorize`

### JPMC Commerce Platform Endpoints

| Environment | Base URL |
|---|---|
| Sandbox (UAT) | `https://api.uat.jpmorgan.com` |
| Production | `https://api.jpmorgan.com` |

**Key paths:**

| Resource | Path |
|---|---|
| OAuth token | `POST /oauth2/v1/token` |
| Payments | `POST /payments/v1/payments` |
| Capture | `POST /payments/v1/payments/{id}/postauth` |
| Refund | `POST /payments/v1/payments/{id}/return` |
| Void | `POST /payments/v1/payments/{id}/void` |
| Payment methods (tokens) | `POST /payments/v1/payment-methods` |
| Get transaction | `GET /payments/v1/payments/{id}` |

---

## 3. Authentication & Session Management

### Orbital — Per-Request Credentials

Every Orbital request embeds credentials directly in the XML body:

```xml
<Request>
  <NewOrder>
    <OrbitalConnectionUsername>myuser</OrbitalConnectionUsername>
    <OrbitalConnectionPassword>mypassword</OrbitalConnectionPassword>
    <MerchantID>123456</MerchantID>
    <BIN>000001</BIN>
    <!-- ... rest of request ... -->
  </NewOrder>
</Request>
```

Required HTTP header:
```
POST /authorize HTTP/1.1
Host: orbitalvar.chasepaymentech.com
Content-Type: application/PTI78
MIME-Version: 1.0
Content-transfer-encoding: text
Request-number: 1
Document-type: Request
Interface-Version: 2.5
```

### JPMC — OAuth 2.0 Client Credentials

**Step 1 — Fetch token (once; cache for `expires_in` seconds):**

```http
POST https://api.uat.jpmorgan.com/oauth2/v1/token
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials
&client_id=YOUR_CLIENT_ID
&client_secret=YOUR_CLIENT_SECRET
&scope=payments
```

**Token response:**
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "scope": "payments"
}
```

**Step 2 — Attach token to every API call:**

```http
POST https://api.uat.jpmorgan.com/payments/v1/payments
Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
Content-Type: application/json
Request-ID: 550e8400-e29b-41d4-a716-446655440000
```

### Authentication Field Mapping

| Orbital XML element | JPMC equivalent |
|---|---|
| `<OrbitalConnectionUsername>` | `client_id` in token request body (one-time) |
| `<OrbitalConnectionPassword>` | `client_secret` in token request body (one-time) |
| `<MerchantID>` | `merchantId` in JSON payment body |
| `<BIN>` | Not required — derived from merchant credentials |
| Per-request credentials | Bearer token in `Authorization` header |

---

## 4. Request Structure

### Orbital XML Envelope

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE Request SYSTEM "https://orbital.chasepaymentech.com/dtd/orbital.dtd">
<Request>
  <{ActionElement}>
    <!-- fields -->
  </{ActionElement}>
</Request>
```

Action elements: `NewOrder`, `MarkForCapture`, `Refund`, `Reversal`, `Profile`, `Inquiry`

### JPMC REST Request

```
{HTTP_METHOD} {BASE_URL}/{resource-path}
Authorization: Bearer {token}
Content-Type: application/json
Request-ID: {uuid-v4}

{JSON body}
```

The operation is determined by the HTTP method + URL path, not a wrapper element.

---

## 5. Authorization (Auth-Only)

Reserves funds without capturing. Orbital `MessageType = A`.

### Orbital XML Request

```xml
<Request>
  <NewOrder>
    <OrbitalConnectionUsername>myuser</OrbitalConnectionUsername>
    <OrbitalConnectionPassword>mypass</OrbitalConnectionPassword>
    <IndustryType>EC</IndustryType>
    <MessageType>A</MessageType>
    <BIN>000001</BIN>
    <MerchantID>123456</MerchantID>
    <TerminalID>001</TerminalID>
    <CardBrand>VI</CardBrand>
    <CCAccountNum>4111111111111111</CCAccountNum>
    <CCExpireDate>1225</CCExpireDate>
    <CardSecVal>123</CardSecVal>
    <CardSecValInd>1</CardSecValInd>
    <AVSzip>10001</AVSzip>
    <AVSaddress1>123 Main St</AVSaddress1>
    <AVScity>New York</AVScity>
    <AVSstate>NY</AVSstate>
    <AVScountryCode>US</AVScountryCode>
    <OrderID>ORD-20240101-001</OrderID>
    <Amount>2599</Amount>
    <CurrencyCode>840</CurrencyCode>
    <CustomerEmail>jane@example.com</CustomerEmail>
    <Comments>Auth for order ORD-20240101-001</Comments>
  </NewOrder>
</Request>
```

### Orbital XML Response

```xml
<Response>
  <NewOrderResp>
    <IndustryType/>
    <MessageType>A</MessageType>
    <MerchantID>123456</MerchantID>
    <TerminalID>001</TerminalID>
    <CardBrand>VI</CardBrand>
    <AccountNum>411111XXXXXX1111</AccountNum>
    <OrderID>ORD-20240101-001</OrderID>
    <TxRefNum>5B3D920A04E0A7E8BA8F85E29AC4D7F1D92C</TxRefNum>
    <TxRefIdx>0</TxRefIdx>
    <ProcStatus>0</ProcStatus>
    <ApprovalStatus>1</ApprovalStatus>
    <RespCode>00</RespCode>
    <AVSRespCode>X</AVSRespCode>
    <CVV2RespCode>M</CVV2RespCode>
    <AuthCode>tst554</AuthCode>
    <RecurringAdviceCd/>
    <CAVVResultCode/>
    <StatusMsg>Approved</StatusMsg>
    <RespMsg/>
  </NewOrderResp>
</Response>
```

### JPMC REST Request

```http
POST https://api.uat.jpmorgan.com/payments/v1/payments
Authorization: Bearer {token}
Content-Type: application/json
Request-ID: 550e8400-e29b-41d4-a716-446655440000

{
  "requestType": "PaymentCardPreAuthTransaction",
  "merchantId": "123456",
  "transactionAmount": {
    "total": "25.99",
    "currency": "USD"
  },
  "order": {
    "orderId": "ORD-20240101-001",
    "customerEmail": "jane@example.com"
  },
  "paymentMethod": {
    "paymentCard": {
      "number": "4111111111111111",
      "expiryDate": {
        "month": "12",
        "year": "2025"
      },
      "securityCode": "123"
    }
  },
  "billingAddress": {
    "address1": "123 Main St",
    "city": "New York",
    "stateProvince": "NY",
    "postalCode": "10001",
    "country": "USA"
  }
}
```

### JPMC REST Response

```json
{
  "ipgTransactionId": "84568394892",
  "orderId": "ORD-20240101-001",
  "transactionType": "PREAUTH",
  "transactionStatus": "APPROVED",
  "approvalCode": "tst554",
  "transactionAmount": {
    "total": "25.99",
    "currency": "USD"
  },
  "paymentMethod": {
    "paymentCard": {
      "brand": "VISA",
      "last4": "1111",
      "expiryDate": { "month": "12", "year": "2025" }
    }
  },
  "avsResponse": {
    "streetMatch": "EXACT",
    "postalCodeMatch": "EXACT"
  },
  "securityCodeResponse": "MATCHED",
  "processor": {
    "referenceNumber": "5B3D920A04E0",
    "responseCode": "00",
    "responseMessage": "Approved"
  }
}
```

---

## 6. Sale (Auth + Capture)

Authorizes and immediately captures funds. Orbital `MessageType = AC`.

### Orbital XML Request

```xml
<Request>
  <NewOrder>
    <OrbitalConnectionUsername>myuser</OrbitalConnectionUsername>
    <OrbitalConnectionPassword>mypass</OrbitalConnectionPassword>
    <IndustryType>EC</IndustryType>
    <MessageType>AC</MessageType>
    <BIN>000001</BIN>
    <MerchantID>123456</MerchantID>
    <TerminalID>001</TerminalID>
    <CardBrand>VI</CardBrand>
    <CCAccountNum>4111111111111111</CCAccountNum>
    <CCExpireDate>1225</CCExpireDate>
    <CardSecVal>123</CardSecVal>
    <CardSecValInd>1</CardSecValInd>
    <AVSzip>10001</AVSzip>
    <AVSaddress1>123 Main St</AVSaddress1>
    <OrderID>ORD-20240101-002</OrderID>
    <Amount>4999</Amount>
    <CurrencyCode>840</CurrencyCode>
  </NewOrder>
</Request>
```

### JPMC REST Request

```http
POST https://api.uat.jpmorgan.com/payments/v1/payments
Authorization: Bearer {token}
Content-Type: application/json
Request-ID: 660f9511-f30c-52e5-b827-557766551111

{
  "requestType": "PaymentCardSaleTransaction",
  "merchantId": "123456",
  "transactionAmount": {
    "total": "49.99",
    "currency": "USD"
  },
  "order": {
    "orderId": "ORD-20240101-002"
  },
  "paymentMethod": {
    "paymentCard": {
      "number": "4111111111111111",
      "expiryDate": { "month": "12", "year": "2025" },
      "securityCode": "123"
    }
  },
  "billingAddress": {
    "address1": "123 Main St",
    "postalCode": "10001"
  }
}
```

### MessageType to requestType Mapping

| Orbital `MessageType` | Orbital meaning | JPMC `requestType` |
|---|---|---|
| `A` | Auth only | `PaymentCardPreAuthTransaction` |
| `AC` | Auth + Capture (sale) | `PaymentCardSaleTransaction` |
| `FC` | Force capture (offline) | `PaymentCardForcedTicketTransaction` |
| `R` | Refund (unlinked) | `CreditTransaction` |

---

## 7. Capture (Mark for Capture)

Completes a prior authorization. Must reference the original `TxRefNum` / `ipgTransactionId`.

### Orbital XML Request

```xml
<Request>
  <MarkForCapture>
    <OrbitalConnectionUsername>myuser</OrbitalConnectionUsername>
    <OrbitalConnectionPassword>mypass</OrbitalConnectionPassword>
    <BIN>000001</BIN>
    <MerchantID>123456</MerchantID>
    <TerminalID>001</TerminalID>
    <TxRefNum>5B3D920A04E0A7E8BA8F85E29AC4D7F1D92C</TxRefNum>
    <Amount>2599</Amount>
    <TaxInd>0</TaxInd>
  </MarkForCapture>
</Request>
```

### Orbital XML Response

```xml
<Response>
  <MarkForCaptureResp>
    <MerchantID>123456</MerchantID>
    <TerminalID>001</TerminalID>
    <TxRefNum>5B3D920A04E0A7E8BA8F85E29AC4D7F1D92C</TxRefNum>
    <TxRefIdx>1</TxRefIdx>
    <ProcStatus>0</ProcStatus>
    <StatusMsg>Approved</StatusMsg>
  </MarkForCaptureResp>
</Response>
```

### JPMC REST Request

```http
POST https://api.uat.jpmorgan.com/payments/v1/payments/84568394892/postauth
Authorization: Bearer {token}
Content-Type: application/json
Request-ID: 770a0622-041d-63f6-c938-668877662222

{
  "requestType": "PostAuthTransaction",
  "transactionAmount": {
    "total": "25.99",
    "currency": "USD"
  }
}
```

### JPMC REST Response

```json
{
  "ipgTransactionId": "84568394893",
  "parentTransactionId": "84568394892",
  "transactionType": "POSTAUTH",
  "transactionStatus": "APPROVED",
  "transactionAmount": {
    "total": "25.99",
    "currency": "USD"
  }
}
```

### Capture Field Mapping

| Orbital field | JPMC equivalent | Notes |
|---|---|---|
| `<TxRefNum>` | `{ipgTransactionId}` in URL path | Stored from original auth response |
| `<Amount>` (cents) | `transactionAmount.total` (decimal) | `2599` → `"25.99"` |
| `<TaxInd>` | Not required | JPMC handles tax via order line items |
| Partial capture | Set `total` < original auth amount | Both support partial capture |

---

## 8. Refund / Return

Returns funds to the cardholder. Linked to the original transaction.

### Orbital XML Request (Follow-On Refund)

```xml
<Request>
  <Refund>
    <OrbitalConnectionUsername>myuser</OrbitalConnectionUsername>
    <OrbitalConnectionPassword>mypass</OrbitalConnectionPassword>
    <BIN>000001</BIN>
    <MerchantID>123456</MerchantID>
    <TerminalID>001</TerminalID>
    <TxRefNum>5B3D920A04E0A7E8BA8F85E29AC4D7F1D92C</TxRefNum>
    <Amount>2599</Amount>
    <CurrencyCode>840</CurrencyCode>
    <OrderID>ORD-20240101-001</OrderID>
  </Refund>
</Request>
```

### Orbital XML Response

```xml
<Response>
  <NewOrderResp>
    <MerchantID>123456</MerchantID>
    <TxRefNum>6C4E031B05F1B8F9CB9G96F30BD5E8G2E03D</TxRefNum>
    <ProcStatus>0</ProcStatus>
    <ApprovalStatus>1</ApprovalStatus>
    <RespCode>00</RespCode>
    <StatusMsg>Approved</StatusMsg>
  </NewOrderResp>
</Response>
```

### JPMC REST Request (Linked Return)

```http
POST https://api.uat.jpmorgan.com/payments/v1/payments/84568394892/return
Authorization: Bearer {token}
Content-Type: application/json
Request-ID: 881b1733-152e-74g7-da49-779988773333

{
  "requestType": "ReturnTransaction",
  "transactionAmount": {
    "total": "25.99",
    "currency": "USD"
  }
}
```

### JPMC REST Request (Unlinked / Standalone Refund)

For Orbital `MessageType = R` (unlinked credit):

```http
POST https://api.uat.jpmorgan.com/payments/v1/payments
Authorization: Bearer {token}
Content-Type: application/json
Request-ID: 992c2844-263f-85h8-eb50-880099884444

{
  "requestType": "CreditTransaction",
  "merchantId": "123456",
  "transactionAmount": {
    "total": "25.99",
    "currency": "USD"
  },
  "order": {
    "orderId": "ORD-20240101-001"
  },
  "paymentMethod": {
    "paymentCard": {
      "number": "4111111111111111",
      "expiryDate": { "month": "12", "year": "2025" }
    }
  }
}
```

### Refund Field Mapping

| Orbital field | JPMC equivalent | Notes |
|---|---|---|
| `<TxRefNum>` | `{ipgTransactionId}` in URL path | Linked refund |
| `<Amount>` (cents) | `transactionAmount.total` (decimal) | Partial refund supported |
| `<CurrencyCode>` (numeric) | `transactionAmount.currency` (alpha) | `840` → `"USD"` |
| `<OrderID>` | `order.orderId` | Optional on linked refunds |
| Unlinked credit | `POST /payments` with `requestType: CreditTransaction` | Include card details |

---

## 9. Void / Reversal

Cancels a transaction before settlement.

### Orbital XML Request

```xml
<Request>
  <Reversal>
    <OrbitalConnectionUsername>myuser</OrbitalConnectionUsername>
    <OrbitalConnectionPassword>mypass</OrbitalConnectionPassword>
    <BIN>000001</BIN>
    <MerchantID>123456</MerchantID>
    <TerminalID>001</TerminalID>
    <TxRefNum>5B3D920A04E0A7E8BA8F85E29AC4D7F1D92C</TxRefNum>
    <TxRefIdx>0</TxRefIdx>
    <AdjustedAmt>0</AdjustedAmt>
    <OrderID>ORD-20240101-001</OrderID>
  </Reversal>
</Request>
```

### Orbital XML Response

```xml
<Response>
  <ReversalResp>
    <MerchantID>123456</MerchantID>
    <TerminalID>001</TerminalID>
    <TxRefNum>5B3D920A04E0A7E8BA8F85E29AC4D7F1D92C</TxRefNum>
    <TxRefIdx>0</TxRefIdx>
    <ProcStatus>0</ProcStatus>
    <StatusMsg>Approved</StatusMsg>
  </ReversalResp>
</Response>
```

### JPMC REST Request

```http
POST https://api.uat.jpmorgan.com/payments/v1/payments/84568394892/void
Authorization: Bearer {token}
Content-Type: application/json
Request-ID: aa3d3955-374g-96i9-fc61-991100995555

{
  "requestType": "VoidTransaction"
}
```

### JPMC REST Response

```json
{
  "ipgTransactionId": "84568394894",
  "parentTransactionId": "84568394892",
  "transactionType": "VOID",
  "transactionStatus": "APPROVED"
}
```

### Void Field Mapping

| Orbital field | JPMC equivalent |
|---|---|
| `<TxRefNum>` | `{ipgTransactionId}` in URL path |
| `<TxRefIdx>` | Not required |
| `<AdjustedAmt>` | Not required for full void |
| `<OrderID>` | Not required |

---

## 10. Customer Profile & Tokenization

Stores card data securely so subsequent transactions don't require the raw PAN.

### Orbital — Create Profile

```xml
<Request>
  <Profile>
    <OrbitalConnectionUsername>myuser</OrbitalConnectionUsername>
    <OrbitalConnectionPassword>mypass</OrbitalConnectionPassword>
    <BIN>000001</BIN>
    <MerchantID>123456</MerchantID>
    <CustomerName>Jane Doe</CustomerName>
    <CustomerAddress1>123 Main St</CustomerAddress1>
    <CustomerCity>New York</CustomerCity>
    <CustomerState>NY</CustomerState>
    <CustomerZIP>10001</CustomerZIP>
    <CustomerCountryCode>US</CustomerCountryCode>
    <CustomerEmail>jane@example.com</CustomerEmail>
    <CCAccountNum>4111111111111111</CCAccountNum>
    <CCExpireDate>1225</CCExpireDate>
    <Action>C</Action>
    <CustomerProfileFromOrderInd>A</CustomerProfileFromOrderInd>
    <CustomerProfileOrderOverrideInd>NO</CustomerProfileOrderOverrideInd>
  </Profile>
</Request>
```

### Orbital — Create Profile Response

```xml
<Response>
  <ProfileResp>
    <MerchantID>123456</MerchantID>
    <CustomerRefNum>1234567890</CustomerRefNum>
    <ProcStatus>0</ProcStatus>
    <StatusMsg>Profile Create Successful</StatusMsg>
  </ProfileResp>
</Response>
```

### JPMC — Tokenize Card

```http
POST https://api.uat.jpmorgan.com/payments/v1/payment-methods
Authorization: Bearer {token}
Content-Type: application/json
Request-ID: bb4e4066-485h-07j0-gd72-aa2211aa6666

{
  "requestType": "PaymentCardPaymentMethod",
  "paymentCard": {
    "number": "4111111111111111",
    "expiryDate": {
      "month": "12",
      "year": "2025"
    }
  },
  "billingAddress": {
    "name": "Jane Doe",
    "address1": "123 Main St",
    "city": "New York",
    "stateProvince": "NY",
    "postalCode": "10001",
    "country": "USA",
    "email": "jane@example.com"
  }
}
```

### JPMC — Tokenize Response

```json
{
  "type": "PaymentCardPaymentMethod",
  "paymentToken": {
    "value": "1751905h1f9s7f91c851s3141s841s31s4",
    "reusable": true,
    "declineDuplicates": false,
    "last4": "1111",
    "brand": "VISA"
  }
}
```

### Orbital — Use Profile in Payment

```xml
<Request>
  <NewOrder>
    <OrbitalConnectionUsername>myuser</OrbitalConnectionUsername>
    <OrbitalConnectionPassword>mypass</OrbitalConnectionPassword>
    <IndustryType>EC</IndustryType>
    <MessageType>AC</MessageType>
    <BIN>000001</BIN>
    <MerchantID>123456</MerchantID>
    <CustomerRefNum>1234567890</CustomerRefNum>
    <OrderID>ORD-20240101-003</OrderID>
    <Amount>1500</Amount>
    <CurrencyCode>840</CurrencyCode>
  </NewOrder>
</Request>
```

### JPMC — Use Token in Payment

```http
POST https://api.uat.jpmorgan.com/payments/v1/payments
Authorization: Bearer {token}
Content-Type: application/json
Request-ID: cc5f5177-596i-18k1-he83-bb3322bb7777

{
  "requestType": "PaymentCardSaleTransaction",
  "merchantId": "123456",
  "transactionAmount": {
    "total": "15.00",
    "currency": "USD"
  },
  "order": {
    "orderId": "ORD-20240101-003"
  },
  "paymentMethod": {
    "paymentToken": {
      "value": "1751905h1f9s7f91c851s3141s841s31s4",
      "reusable": true,
      "declineDuplicates": false
    }
  }
}
```

### Profile Action Mapping

| Orbital `Action` | Orbital operation | JPMC equivalent |
|---|---|---|
| `C` | Create profile | `POST /payments/v1/payment-methods` |
| `U` | Update profile | `PATCH /payments/v1/payment-methods/{token}` |
| `D` | Delete profile | `DELETE /payments/v1/payment-methods/{token}` |
| `R` | Read profile | `GET /payments/v1/payment-methods/{token}` |

### Profile Field Mapping

| Orbital field | JPMC field | Notes |
|---|---|---|
| `<CustomerRefNum>` | `paymentToken.value` | Store this; replaces raw PAN reference |
| `<CustomerName>` | `billingAddress.name` | |
| `<CustomerAddress1>` | `billingAddress.address1` | |
| `<CustomerCity>` | `billingAddress.city` | |
| `<CustomerState>` | `billingAddress.stateProvince` | |
| `<CustomerZIP>` | `billingAddress.postalCode` | |
| `<CustomerCountryCode>` | `billingAddress.country` | ISO 2-char → ISO 3-char (`US` → `USA`) |
| `<CustomerEmail>` | `billingAddress.email` | |
| `<CCAccountNum>` | `paymentCard.number` | |
| `<CCExpireDate>` MMYY | `paymentCard.expiryDate.month` / `.year` | Split `1225` → `"12"` / `"2025"` |
| `<CustomerProfileFromOrderInd>` | Not applicable | JPMC tokenization is standalone |

---

## 11. 3-D Secure (3DS)

### Orbital — 3DS Fields in NewOrder

```xml
<CardSecValInd>1</CardSecValInd>
<AuthenticationECIInd>5</AuthenticationECIInd>
<DPANInd/>
<DigitalTokenCryptogram/>
<CAVV>AAABBJg0VhI0VniQEjRWAAAAAAA=</CAVV>
<XID>MDAwMDAwMDAwMDAwMDAwMDAwMDE=</XID>
```

### JPMC — 3DS Fields

```json
{
  "paymentMethod": {
    "paymentCard": {
      "number": "4111111111111111",
      "expiryDate": { "month": "12", "year": "2025" }
    }
  },
  "authenticationRequest": {
    "authenticationType": "Secure3D21AuthenticationRequest",
    "termURL": "https://your-site.com/3ds-callback",
    "methodNotificationURL": "https://your-site.com/3ds-method",
    "challengeIndicator": "01",
    "challengeWindowSize": "01"
  }
}
```

Or, when passing results of an external 3DS authentication:

```json
{
  "authenticationResult": {
    "authenticationType": "Secure3D21AuthenticationResult",
    "cavv": "AAABBJg0VhI0VniQEjRWAAAAAAA=",
    "xid": "MDAwMDAwMDAwMDAwMDAwMDAwMDE=",
    "eci": "05",
    "status": "AUTHENTICATED"
  }
}
```

### 3DS Field Mapping

| Orbital field | JPMC field |
|---|---|
| `<CAVV>` | `authenticationResult.cavv` |
| `<XID>` | `authenticationResult.xid` |
| `<AuthenticationECIInd>` | `authenticationResult.eci` |
| `<CardSecValInd>` = 1 (CVV present) | `paymentCard.securityCode` present |

---

## 12. Field Reference: NewOrder

Complete mapping of every Orbital `<NewOrder>` field to its JPMC equivalent.

### Core Transaction Fields

| Orbital Field | Type | JPMC Field | Type | Transformation |
|---|---|---|---|---|
| `OrbitalConnectionUsername` | String | _(token request)_ `client_id` | String | One-time OAuth credential |
| `OrbitalConnectionPassword` | String | _(token request)_ `client_secret` | String | One-time OAuth credential |
| `IndustryType` | String (`EC`, `RC`, `MO`) | Not required | — | JPMC infers from merchant setup |
| `MessageType` | String (`A`, `AC`, `R`, `FC`) | `requestType` | String | See §6 mapping table |
| `BIN` | String | Not required | — | Derived from credentials |
| `MerchantID` | String | `merchantId` | String | Direct map |
| `TerminalID` | String | Not required | — | JPMC uses merchant-level routing |
| `CardBrand` | String (`VI`, `MC`, `AX`, …) | Not required | — | Auto-detected from PAN |
| `CCAccountNum` | String | `paymentMethod.paymentCard.number` | String | Direct map |
| `CCExpireDate` | String MMYY | `paymentMethod.paymentCard.expiryDate` | Object | Split: `"1225"` → `{month:"12",year:"2025"}` |
| `CardSecVal` | String | `paymentMethod.paymentCard.securityCode` | String | Direct map |
| `CardSecValInd` | Integer | Implicit | — | Omit; JPMC infers from presence of `securityCode` |
| `Amount` | Integer (cents) | `transactionAmount.total` | String (decimal) | `2599` → `"25.99"` |
| `CurrencyCode` | Integer (ISO numeric) | `transactionAmount.currency` | String (ISO alpha-3) | `840` → `"USD"` |
| `OrderID` | String | `order.orderId` | String | Direct map |
| `CustomerRefNum` | String | `paymentMethod.paymentToken.value` | String | JPMC payment token |

### Billing / AVS Fields

| Orbital Field | JPMC Field | Notes |
|---|---|---|
| `AVSaddress1` | `billingAddress.address1` | |
| `AVSaddress2` | `billingAddress.address2` | |
| `AVScity` | `billingAddress.city` | |
| `AVSstate` | `billingAddress.stateProvince` | |
| `AVSzip` | `billingAddress.postalCode` | |
| `AVScountryCode` | `billingAddress.country` | `US` → `USA` (ISO 2 → ISO 3) |

### Customer Information Fields

| Orbital Field | JPMC Field |
|---|---|
| `CustomerEmail` | `order.customerEmail` |
| `CustomerProfileFromOrderInd` | Not applicable |
| `CustomerProfileOrderOverrideInd` | Not applicable |
| `Comments` | `order.notes` |
| `TaxInd` | `order.taxAmount` |
| `PCOrderNum` | `order.purchaseOrderNumber` |

### Level 2 / Level 3 Data

| Orbital Field | JPMC Field |
|---|---|
| `TaxAmount` | `order.taxAmount` |
| `PC3FreightAmt` | `shipToAddress.shippingAmount` |
| `PC3DutyAmt` | `order.dutyAmount` |
| `PC3LineItemCount` | `order.lineItems` (array length) |
| `PC3Desc1` | `order.lineItems[0].description` |
| `PC3Qty1` | `order.lineItems[0].quantity` |
| `PC3UnitCost1` | `order.lineItems[0].unitPrice` |

---

## 13. Response Field Mapping

### NewOrder / Authorization Response

| Orbital Field | JPMC Field | Notes |
|---|---|---|
| `TxRefNum` | `ipgTransactionId` | **Store this** — needed for capture/refund/void |
| `TxRefIdx` | Not present | JPMC uses `ipgTransactionId` only |
| `ProcStatus` | HTTP status code + `error.code` | `0` = success → HTTP 201 |
| `ApprovalStatus` | `transactionStatus` | See §14 |
| `RespCode` | `processor.responseCode` | Issuer response code |
| `StatusMsg` | `processor.responseMessage` | |
| `RespMsg` | `error.message` | On failures |
| `AuthCode` | `approvalCode` | Authorization approval code |
| `AVSRespCode` | `avsResponse.streetMatch` + `avsResponse.postalCodeMatch` | See §15 |
| `CVV2RespCode` | `securityCodeResponse` | See §16 |
| `AccountNum` | `paymentMethod.paymentCard.last4` | JPMC returns masked last 4 only |
| `CardBrand` | `paymentMethod.paymentCard.brand` | String (`VISA`, `MASTERCARD`, …) |
| `OrderID` | `orderId` | |
| `CAVVResultCode` | `authenticationResult.status` | |

---

## 14. Error & Status Code Mapping

### Transaction Approval Status

| Orbital `ApprovalStatus` | Orbital meaning | JPMC HTTP status | JPMC `transactionStatus` |
|---|---|---|---|
| `1` | Approved | 201 | `APPROVED` |
| `2` | Declined | 201 | `DECLINED` |
| `3` | Referral / call issuer | 201 | `WAITING` |
| `4` | Error / processing failure | 400 or 500 | `FAILED` |

### ProcStatus Codes

| Orbital `ProcStatus` | Meaning | JPMC HTTP Status | JPMC `error.code` |
|---|---|---|---|
| `0` | Success | 201 | _(no error)_ |
| `100` | Approved | 201 | _(no error)_ |
| `101` | Declined | 201 | _(transactionStatus: DECLINED)_ |
| `102` | Referral | 201 | _(transactionStatus: WAITING)_ |
| `475` | End of day processing | 202 | `PROCESSING` |
| `481` | Soft decline | 201 | `DECLINED` |
| `9001` | General error | 500 | `INTERNAL_ERROR` |
| `9004` | Invalid request | 400 | `VALIDATION_FAILED` |
| `9100` | Invalid merchant ID | 400 | `INVALID_MERCHANT` |
| `9583` | Duplicate order ID | 409 | `DUPLICATE_TRANSACTION` |
| `9620` | Invalid card number (Luhn fail) | 400 | `INVALID_CARD_NUMBER` |
| `9621` | Card type not accepted | 400 | `CARD_TYPE_NOT_ACCEPTED` |
| `9625` | Expired card | 400 | `EXPIRED_CARD` |
| `9630` | Invalid CVV / security code | 400 | `INVALID_SECURITY_CODE` |
| `9640` | Invalid expiry date | 400 | `INVALID_EXPIRY_DATE` |
| `9740` | Velocity / rate limit | 429 | `RATE_LIMIT_EXCEEDED` |
| `9813` | Transaction not found | 404 | `NOT_FOUND` |
| `9815` | Invalid transaction state | 409 | `INVALID_STATE` |
| `9822` | Amount exceeds auth | 400 | `AMOUNT_EXCEEDS_AUTHORIZATION` |

### JPMC Error Response Shape

```json
{
  "type": "https://api.jpmorgan.com/errors/validation-failed",
  "title": "Validation Failed",
  "status": 400,
  "error": {
    "code": "VALIDATION_FAILED",
    "message": "The request body contains invalid fields.",
    "details": [
      {
        "field": "transactionAmount.total",
        "message": "Must be a positive decimal value."
      }
    ]
  }
}
```

---

## 15. AVS Response Code Mapping

| Orbital `AVSRespCode` | Orbital Meaning | JPMC `avsResponse.streetMatch` | JPMC `avsResponse.postalCodeMatch` |
|---|---|---|---|
| `A` | Address matches, ZIP does not | `EXACT` | `NO_MATCH` |
| `B` | Street address match, postal code not verified | `EXACT` | `NOT_CHECKED` |
| `C` | Street and postal code not verified | `NOT_CHECKED` | `NOT_CHECKED` |
| `D` | Street and postal code match (international) | `EXACT` | `EXACT` |
| `E` | AVS error | `NOT_CHECKED` | `NOT_CHECKED` |
| `G` | Non-US issuer, AVS not supported | `NOT_CHECKED` | `NOT_CHECKED` |
| `I` | International address, not verified | `NOT_CHECKED` | `NOT_CHECKED` |
| `M` | Street and postal code match (international) | `EXACT` | `EXACT` |
| `N` | No match on address or ZIP | `NO_MATCH` | `NO_MATCH` |
| `O` | No response from issuer | `NOT_CHECKED` | `NOT_CHECKED` |
| `P` | Postal code match, address not verified | `NOT_CHECKED` | `EXACT` |
| `R` | Retry — issuer unavailable | `NOT_CHECKED` | `NOT_CHECKED` |
| `S` | AVS not supported by issuer | `NOT_CHECKED` | `NOT_CHECKED` |
| `T` | Nine-digit ZIP match, address not verified | `NOT_CHECKED` | `EXACT` |
| `U` | Address unavailable | `NOT_CHECKED` | `NOT_CHECKED` |
| `W` | Nine-digit ZIP matches, address does not | `NO_MATCH` | `EXACT` |
| `X` | Exact match: address + 9-digit ZIP | `EXACT` | `EXACT` |
| `Y` | Exact match: address + 5-digit ZIP | `EXACT` | `EXACT` |
| `Z` | Five-digit ZIP matches, address does not | `NO_MATCH` | `EXACT` |

---

## 16. CVV Response Code Mapping

| Orbital `CVV2RespCode` | Orbital Meaning | JPMC `securityCodeResponse` |
|---|---|---|
| `M` | CVV2 match | `MATCHED` |
| `N` | CVV2 does not match | `NOT_MATCHED` |
| `P` | CVV2 not processed | `NOT_PROCESSED` |
| `S` | Issuer indicates no CVV2 on card | `NOT_PRESENT` |
| `U` | Issuer not CVV2 certified | `NOT_CHECKED` |
| `X` | No response from issuer | `NOT_CHECKED` |
| _(blank)_ | CVV2 not submitted | `NOT_CHECKED` |

---

## 17. Currency Code Conversion

Orbital uses ISO 4217 **numeric** codes; JPMC uses ISO 4217 **alpha-3** codes.

| Orbital (numeric) | JPMC (alpha-3) | Currency |
|---|---|---|
| `036` | `AUD` | Australian Dollar |
| `124` | `CAD` | Canadian Dollar |
| `156` | `CNY` | Chinese Yuan |
| `208` | `DKK` | Danish Krone |
| `344` | `HKD` | Hong Kong Dollar |
| `356` | `INR` | Indian Rupee |
| `392` | `JPY` | Japanese Yen |
| `410` | `KRW` | South Korean Won |
| `484` | `MXN` | Mexican Peso |
| `554` | `NZD` | New Zealand Dollar |
| `578` | `NOK` | Norwegian Krone |
| `702` | `SGD` | Singapore Dollar |
| `710` | `ZAR` | South African Rand |
| `752` | `SEK` | Swedish Krona |
| `756` | `CHF` | Swiss Franc |
| `764` | `THB` | Thai Baht |
| `826` | `GBP` | British Pound |
| `840` | `USD` | US Dollar |
| `978` | `EUR` | Euro |
| `986` | `BRL` | Brazilian Real |

---

## 18. Card Brand Mapping

| Orbital `CardBrand` | Card Network | JPMC `paymentCard.brand` |
|---|---|---|
| `VI` | Visa | `VISA` (auto-detected) |
| `MC` | Mastercard | `MASTERCARD` (auto-detected) |
| `AX` | American Express | `AMEX` (auto-detected) |
| `DS` | Discover | `DISCOVER` (auto-detected) |
| `DI` | Diners Club | `DINERS` (auto-detected) |
| `JC` | JCB | `JCB` (auto-detected) |
| `CU` | China UnionPay | `UNIONPAY` (auto-detected) |

> **Note:** JPMC auto-detects the card brand from the PAN using BIN lookup. You do not need to send a card brand field.

---

## 19. Migration Checklist

### Infrastructure

- [ ] Obtain JPMC Commerce Platform API credentials (Client ID, Client Secret, Merchant ID) from the JPMC Developer Portal
- [ ] Whitelist server IPs in the JPMC merchant portal
- [ ] Update firewall rules: allow outbound HTTPS to `api.uat.jpmorgan.com` and `api.jpmorgan.com`
- [ ] Remove Orbital failover hostname logic (`orbital1` / `orbital2`) — JPMC handles HA internally
- [ ] Update environment configuration to use `JPMC_CLIENT_ID`, `JPMC_CLIENT_SECRET`, `JPMC_MERCHANT_ID`, `JPMC_BASE_URL`

### Authentication

- [ ] Implement OAuth 2.0 client credentials token fetch
- [ ] Implement token caching (cache `access_token` until `expires_in - 60` seconds)
- [ ] Implement token refresh on 401 responses
- [ ] Remove all Orbital username/password from request payloads and config

### Request Construction

- [ ] Replace XML HTTP client with JSON REST client
- [ ] Replace XML envelope with JSON body per operation
- [ ] Add `Request-ID: {uuid-v4}` header to every request for idempotency
- [ ] Convert amount from integer cents to decimal string (`2599` → `"25.99"`)
- [ ] Convert currency from ISO numeric to ISO alpha-3 (`840` → `"USD"`)
- [ ] Map `MessageType` A/AC to `requestType` PreAuth/Sale
- [ ] Remove `BIN`, `TerminalID`, `IndustryType`, `CardBrand` fields
- [ ] Split `CCExpireDate` MMYY into `expiryDate.month` / `expiryDate.year`

### Transaction References

- [ ] Replace `TxRefNum` storage with `ipgTransactionId` (returned in auth/sale response)
- [ ] Update capture, refund, and void flows to use `ipgTransactionId` in URL path
- [ ] Replace `CustomerRefNum` with `paymentToken.value` from JPMC tokenization service

### Response Handling

- [ ] Replace `ProcStatus` / `ApprovalStatus` checks with HTTP status + `transactionStatus`
- [ ] Replace `RespCode` with `processor.responseCode`
- [ ] Replace `AVSRespCode` with `avsResponse.streetMatch` + `avsResponse.postalCodeMatch`
- [ ] Replace `CVV2RespCode` with `securityCodeResponse`
- [ ] Replace `AuthCode` reference with `approvalCode`
- [ ] Handle HTTP 409 for duplicate `Request-ID` (idempotent retry is safe)
- [ ] Handle HTTP 429 with exponential backoff

### Profile / Tokenization

- [ ] Replace Orbital Profile `Action=C` with `POST /payments/v1/payment-methods`
- [ ] Replace Orbital Profile `Action=U` with `PATCH /payments/v1/payment-methods/{token}`
- [ ] Replace Orbital Profile `Action=D` with `DELETE /payments/v1/payment-methods/{token}`
- [ ] Replace Orbital Profile `Action=R` with `GET /payments/v1/payment-methods/{token}`
- [ ] Migrate `CustomerRefNum` values: re-tokenize cards or use JPMC's token migration service

### Testing

- [ ] Validate auth-only flow against JPMC sandbox
- [ ] Validate sale (auth+capture) flow
- [ ] Validate capture against prior auth
- [ ] Validate partial capture
- [ ] Validate full refund
- [ ] Validate partial refund
- [ ] Validate void
- [ ] Validate tokenization create/use/delete
- [ ] Validate AVS decline scenarios
- [ ] Validate CVV decline scenarios
- [ ] Validate duplicate `Request-ID` behavior (expect 409 on retry)
- [ ] Load test against JPMC UAT before production cutover

---

## Quick Reference Card

```
Orbital                              JPMC Commerce Platform
──────────────────────────────────── ────────────────────────────────────
POST /authorize (XML)                POST /payments/v1/payments (JSON)
  <NewOrder> MessageType=A       →     requestType: PaymentCardPreAuthTransaction
  <NewOrder> MessageType=AC      →     requestType: PaymentCardSaleTransaction
  <MarkForCapture>               →   POST /payments/v1/payments/{id}/postauth
  <Refund>                       →   POST /payments/v1/payments/{id}/return
  <Reversal>                     →   POST /payments/v1/payments/{id}/void
  <Profile> Action=C             →   POST /payments/v1/payment-methods
  <Profile> Action=U             →   PATCH /payments/v1/payment-methods/{token}
  <Profile> Action=D             →   DELETE /payments/v1/payment-methods/{token}
  <Profile> Action=R             →   GET /payments/v1/payment-methods/{token}

Credentials per request (XML)    →   OAuth 2.0 Bearer token (header)
TxRefNum                         →   ipgTransactionId
CustomerRefNum                   →   paymentToken.value
Amount in cents (2599)           →   Amount as decimal string ("25.99")
CurrencyCode numeric (840)       →   Currency alpha-3 ("USD")
CCExpireDate MMYY (1225)         →   expiryDate {month:"12", year:"2025"}
ProcStatus + ApprovalStatus      →   HTTP status + transactionStatus
AVSRespCode (single char)        →   avsResponse {streetMatch, postalCodeMatch}
CVV2RespCode (single char)       →   securityCodeResponse
```

---

*Generated for ppdiwakar/Worldwide. Use `/orbital-to-jpm` in Claude Code for interactive guidance, `/orbital-scan` to find Orbital patterns in your codebase, and `/orbital-recode-file <file>` to auto-migrate individual files.*
