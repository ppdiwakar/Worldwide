# Orbital → JP Morgan Commerce Platform Migration Guide

Analyze the current file or directory for Orbital Gateway API usage and produce a migration plan to JP Morgan Commerce Platform (JPMC) Payment APIs.

## How to use

Run `/orbital-to-jpm` in any file or with a path argument:
- `/orbital-to-jpm` — scan the current working directory
- `/orbital-to-jpm src/payments/` — scan a specific directory

## What this skill does

1. Searches for Orbital XML request patterns (`NewOrder`, `MarkForCapture`, `Refund`, `Void`, `Profile`)
2. Maps each operation to its JPMC REST equivalent
3. Outputs a prioritized migration checklist with code snippets

---

## API Mapping Reference

### Authentication

| Orbital | JPMC Commerce Platform |
|---------|----------------------|
| `OrbitalConnectionUsername` / `OrbitalConnectionPassword` in XML headers | OAuth 2.0 Bearer token via `POST /oauth2/v1/token` (client_credentials grant) |
| Per-request credentials in every XML envelope | One token (TTL 3600 s), refresh before expiry |

**Orbital (XML header)**
```xml
<OrbitalConnectionUsername>myuser</OrbitalConnectionUsername>
<OrbitalConnectionPassword>mypass</OrbitalConnectionPassword>
<MerchantID>123456</MerchantID>
```

**JPMC (HTTP header on every call)**
```
Authorization: Bearer {access_token}
Content-Type: application/json
Request-ID: {uuid-v4}       # idempotency key
```

**Token fetch**
```http
POST https://api.jpmorgan.com/oauth2/v1/token
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials
&client_id={CLIENT_ID}
&client_secret={CLIENT_SECRET}
&scope=payments
```

---

### 1  Authorization / Sale (NewOrder)

**Orbital XML**
```xml
<Request>
  <NewOrder>
    <OrbitalConnectionUsername>…</OrbitalConnectionUsername>
    <OrbitalConnectionPassword>…</OrbitalConnectionPassword>
    <IndustryType>EC</IndustryType>
    <MessageType>A</MessageType>          <!-- A=Auth, AC=Auth+Capture -->
    <MerchantID>123456</MerchantID>
    <Amount>1099</Amount>                 <!-- in cents -->
    <CurrencyCode>840</CurrencyCode>      <!-- ISO 4217 numeric -->
    <CardBrand>VI</CardBrand>
    <CCAccountNum>4111111111111111</CCAccountNum>
    <CCExpireDate>1225</CCExpireDate>
    <CardSecVal>123</CardSecVal>
    <OrderID>ORD-001</OrderID>
    <AVSzip>10001</AVSzip>
    <AVSaddress1>123 Main St</AVSaddress1>
  </NewOrder>
</Request>
```

**JPMC REST**
```http
POST https://api.jpmorgan.com/payments/v1/payments
Authorization: Bearer {token}
Request-ID: {uuid}
Content-Type: application/json

{
  "merchantId": "123456",
  "requestType": "PaymentCardSaleTransaction",   // or PaymentCardPreAuthTransaction for auth-only
  "transactionAmount": {
    "total": "10.99",
    "currency": "USD"
  },
  "paymentMethod": {
    "paymentCard": {
      "number": "4111111111111111",
      "expiryDate": { "month": "12", "year": "2025" },
      "securityCode": "123"
    }
  },
  "order": {
    "orderId": "ORD-001"
  },
  "billingAddress": {
    "address1": "123 Main St",
    "postalCode": "10001"
  }
}
```

**Key differences**
- Amounts: Orbital uses integer cents (`1099`); JPMC uses decimal string (`"10.99"`)
- Currency: Orbital uses ISO numeric (`840`); JPMC uses ISO alpha (`"USD"`)
- Auth-only vs sale: Orbital `MessageType` A/AC → JPMC `requestType` `PaymentCardPreAuthTransaction` / `PaymentCardSaleTransaction`
- Response: JPMC returns JSON with `ipgTransactionId`, `approvalCode`, `avsResponse`, `securityCodeResponse`

---

### 2  Capture (MarkForCapture)

**Orbital XML**
```xml
<Request>
  <MarkForCapture>
    <OrbitalConnectionUsername>…</OrbitalConnectionUsername>
    <OrbitalConnectionPassword>…</OrbitalConnectionPassword>
    <MerchantID>123456</MerchantID>
    <Amount>1099</Amount>
    <TxRefNum>ABC123</TxRefNum>           <!-- original auth transaction ref -->
  </MarkForCapture>
</Request>
```

**JPMC REST**
```http
POST https://api.jpmorgan.com/payments/v1/payments/{ipgTransactionId}/postauth
Authorization: Bearer {token}
Request-ID: {uuid}
Content-Type: application/json

{
  "requestType": "PostAuthTransaction",
  "transactionAmount": {
    "total": "10.99",
    "currency": "USD"
  }
}
```

**Key differences**
- `TxRefNum` → `ipgTransactionId` in the URL path
- Partial capture: just set `total` to the partial amount

---

### 3  Refund (Refund / FollowOnRefund)

**Orbital XML**
```xml
<Request>
  <Refund>
    <OrbitalConnectionUsername>…</OrbitalConnectionUsername>
    <OrbitalConnectionPassword>…</OrbitalConnectionPassword>
    <MerchantID>123456</MerchantID>
    <Amount>1099</Amount>
    <TxRefNum>ABC123</TxRefNum>
  </Refund>
</Request>
```

**JPMC REST**
```http
POST https://api.jpmorgan.com/payments/v1/payments/{ipgTransactionId}/return
Authorization: Bearer {token}
Request-ID: {uuid}
Content-Type: application/json

{
  "requestType": "ReturnTransaction",
  "transactionAmount": {
    "total": "10.99",
    "currency": "USD"
  }
}
```

For standalone (unlinked) refunds use `POST /payments/v1/payments` with `requestType: CreditTransaction`.

---

### 4  Void

**Orbital XML**
```xml
<Request>
  <Reversal>
    <OrbitalConnectionUsername>…</OrbitalConnectionUsername>
    <OrbitalConnectionPassword>…</OrbitalConnectionPassword>
    <MerchantID>123456</MerchantID>
    <TxRefNum>ABC123</TxRefNum>
  </Reversal>
</Request>
```

**JPMC REST**
```http
POST https://api.jpmorgan.com/payments/v1/payments/{ipgTransactionId}/void
Authorization: Bearer {token}
Request-ID: {uuid}
Content-Type: application/json

{
  "requestType": "VoidTransaction"
}
```

---

### 5  Customer Profile / Tokenization

**Orbital — create profile**
```xml
<Request>
  <Profile>
    <CustomerName>Jane Doe</CustomerName>
    <CustomerAddress1>123 Main St</CustomerAddress1>
    <CCAccountNum>4111111111111111</CCAccountNum>
    <CCExpireDate>1225</CCExpireDate>
    <Action>C</Action>          <!-- C=Create, U=Update, D=Delete, R=Read -->
    <CustomerProfileFromOrderInd>A</CustomerProfileFromOrderInd>
  </Profile>
</Request>
```

**JPMC — tokenize card (Payment Methods API)**
```http
POST https://api.jpmorgan.com/payments/v1/payment-methods
Authorization: Bearer {token}
Request-ID: {uuid}
Content-Type: application/json

{
  "requestType": "PaymentCardPaymentMethod",
  "paymentCard": {
    "number": "4111111111111111",
    "expiryDate": { "month": "12", "year": "2025" }
  },
  "billingAddress": {
    "name": "Jane Doe",
    "address1": "123 Main St"
  }
}
```

Response includes `paymentToken.value` — store this instead of the Orbital `CustomerRefNum`.

**Use token in a payment**
```json
{
  "requestType": "PaymentCardSaleTransaction",
  "paymentMethod": {
    "paymentToken": {
      "value": "{token}",
      "reusable": true,
      "declineDuplicates": false
    }
  }
}
```

---

## Migration Checklist

- [ ] Replace XML HTTP client with JSON REST client
- [ ] Implement OAuth 2.0 token fetch + refresh logic
- [ ] Add `Request-ID` (UUID v4) generation per request for idempotency
- [ ] Convert amount from integer cents → decimal string
- [ ] Convert currency from ISO numeric → ISO alpha-3
- [ ] Map `MessageType` A/AC → `requestType` Pre-auth/Sale
- [ ] Replace `TxRefNum` storage with `ipgTransactionId`
- [ ] Replace `CustomerRefNum` (Orbital profile) with JPMC `paymentToken.value`
- [ ] Update AVS/CVV response code mappings (JPMC uses its own codes)
- [ ] Update error handling: Orbital `ProcStatus`/`ApprovalStatus` → JPMC HTTP 4xx/5xx + `error.code`
- [ ] Update webhook/notification endpoints if using Orbital's Orbital Spectrum notifications
- [ ] Test in JPMC Sandbox (`https://api.uat.jpmorgan.com/`) before production

---

## Environment URLs

| Environment | Base URL |
|-------------|----------|
| Sandbox (UAT) | `https://api.uat.jpmorgan.com` |
| Production | `https://api.jpmorgan.com` |

## JPMC Documentation References

- Commerce Platform API Docs: https://developer.jpmorgan.com/products/commerce-platform
- Payments API reference: https://developer.jpmorgan.com/apis/commerce-platform/payments
- OAuth 2.0 setup: https://developer.jpmorgan.com/guides/authentication
