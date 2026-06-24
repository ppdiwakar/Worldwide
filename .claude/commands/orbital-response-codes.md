# Orbital → JPMC Response Code Mapping

Display the response/error code mapping between Orbital Gateway and JP Morgan Commerce Platform APIs, or look up a specific code.

## Usage

```
/orbital-response-codes [code]
```

- `/orbital-response-codes` — show complete mapping tables
- `/orbital-response-codes 100` — look up Orbital ProcStatus 100
- `/orbital-response-codes DECLINED` — find JPMC equivalent for a keyword

---

## Response Code Reference

### Transaction Status

| Orbital `ApprovalStatus` | Orbital meaning | JPMC HTTP status | JPMC `transactionStatus` |
|---|---|---|---|
| 1 | Approved | 201 Created | `APPROVED` |
| 2 | Declined | 201 Created | `DECLINED` |
| 3 | Referral | 201 Created | `WAITING` |
| 4 | Error | 400 / 500 | `FAILED` |

### ProcStatus Codes (Orbital) → JPMC error.code

| Orbital `ProcStatus` | Orbital meaning | JPMC equivalent |
|---|---|---|
| 0 | Success | HTTP 201, no error |
| 100 | Approval | HTTP 201, `transactionStatus: APPROVED` |
| 101 | Decline | HTTP 201, `transactionStatus: DECLINED` |
| 102 | Referral | HTTP 201, `transactionStatus: WAITING` |
| 481 | Soft decline | HTTP 201, `transactionStatus: DECLINED` |
| 9001 | General processing error | HTTP 500, `error.code: INTERNAL_ERROR` |
| 9100 | Invalid merchant | HTTP 400, `error.code: INVALID_MERCHANT` |
| 9583 | Duplicate order | HTTP 409, `error.code: DUPLICATE_TRANSACTION` |
| 9620 | Invalid card number | HTTP 400, `error.code: INVALID_CARD_NUMBER` |
| 9625 | Expired card | HTTP 400, `error.code: EXPIRED_CARD` |
| 9630 | Invalid CVV | HTTP 400, `error.code: INVALID_SECURITY_CODE` |
| 9740 | Velocity limit exceeded | HTTP 429, `error.code: RATE_LIMIT_EXCEEDED` |
| 9813 | Transaction not found | HTTP 404, `error.code: NOT_FOUND` |

### AVS Response Codes

| Orbital `AVSRespCode` | Orbital meaning | JPMC `avsResponse.streetMatch` | JPMC `avsResponse.postalCodeMatch` |
|---|---|---|---|
| A | Address matches, ZIP does not | `EXACT` | `NO_MATCH` |
| W | ZIP matches, address does not | `NO_MATCH` | `EXACT` |
| X | Exact match (address + ZIP) | `EXACT` | `EXACT` |
| Y | Exact match (address + ZIP) | `EXACT` | `EXACT` |
| Z | ZIP matches only | `NO_MATCH` | `EXACT` |
| N | No match | `NO_MATCH` | `NO_MATCH` |
| U | Unavailable | `NOT_CHECKED` | `NOT_CHECKED` |
| S | Service not supported | `NOT_CHECKED` | `NOT_CHECKED` |

### CVV/Security Code Response

| Orbital `CVV2RespCode` | Orbital meaning | JPMC `securityCodeResponse` |
|---|---|---|
| M | CVV2 match | `MATCHED` |
| N | CVV2 no match | `NOT_MATCHED` |
| P | Not processed | `NOT_PROCESSED` |
| S | Issuer indicates no CVV | `NOT_PRESENT` |
| U | Issuer not certified | `NOT_CHECKED` |
| X | No response | `NOT_CHECKED` |

### Card Type Codes

| Orbital `CardBrand` | Card type | JPMC `paymentCard.cardFunction` / detect by BIN |
|---|---|---|
| VI | Visa | Auto-detected from PAN |
| MC | Mastercard | Auto-detected from PAN |
| AX | American Express | Auto-detected from PAN |
| DS | Discover | Auto-detected from PAN |
| DI | Diners Club | Auto-detected from PAN |
| JC | JCB | Auto-detected from PAN |

Note: JPMC auto-detects card brand from the PAN — no explicit brand field required.

### Currency Code Conversion

| ISO 4217 Numeric (Orbital) | ISO 4217 Alpha (JPMC) | Currency |
|---|---|---|
| 840 | USD | US Dollar |
| 124 | CAD | Canadian Dollar |
| 826 | GBP | British Pound |
| 978 | EUR | Euro |
| 036 | AUD | Australian Dollar |
| 392 | JPY | Japanese Yen |
| 756 | CHF | Swiss Franc |
| 344 | HKD | Hong Kong Dollar |

---

## Code Snippet — JPMC Error Handler

```javascript
async function handleJpmcResponse(response) {
  if (response.status === 201) {
    const body = await response.json();
    if (body.transactionStatus === 'APPROVED') {
      return { success: true, transactionId: body.ipgTransactionId, approvalCode: body.approvalCode };
    }
    // Declined / Waiting
    return { success: false, status: body.transactionStatus, processor: body.processorResponseCode };
  }

  if (response.status === 409) {
    throw new Error('DUPLICATE_TRANSACTION');
  }
  if (response.status === 429) {
    throw new Error('RATE_LIMIT_EXCEEDED — implement exponential backoff');
  }

  const err = await response.json();
  throw new Error(`JPMC error ${err.error.code}: ${err.error.message}`);
}
```

```python
def handle_jpmc_response(response):
    if response.status_code == 201:
        body = response.json()
        if body['transactionStatus'] == 'APPROVED':
            return {'success': True, 'transaction_id': body['ipgTransactionId']}
        return {'success': False, 'status': body['transactionStatus']}
    if response.status_code == 409:
        raise ValueError('DUPLICATE_TRANSACTION')
    if response.status_code == 429:
        raise IOError('RATE_LIMIT_EXCEEDED')
    err = response.json()
    raise RuntimeError(f"JPMC {err['error']['code']}: {err['error']['message']}")
```
