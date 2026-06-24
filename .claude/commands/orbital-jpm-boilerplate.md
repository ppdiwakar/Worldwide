# Generate JPMC Commerce Platform Payment Client Boilerplate

Generate a ready-to-use JP Morgan Commerce Platform API client in the specified language, covering: OAuth token management, authorization, capture, refund, void, and tokenization.

## Usage

```
/orbital-jpm-boilerplate [language]
```

Supported languages: `python`, `javascript` (Node.js), `java`, `php`, `csharp`, `ruby`

Default is `python` if no language is specified.

---

## Instructions for Claude

When invoked, generate a complete, production-ready JPMC payment client in the requested language (`$ARGUMENTS`). Include all operations below. Use env vars for credentials. Add only essential inline comments.

---

## Python boilerplate

```python
import os
import uuid
import time
import requests

BASE_URL = os.environ.get("JPMC_BASE_URL", "https://api.uat.jpmorgan.com")
CLIENT_ID = os.environ["JPMC_CLIENT_ID"]
CLIENT_SECRET = os.environ["JPMC_CLIENT_SECRET"]
MERCHANT_ID = os.environ["JPMC_MERCHANT_ID"]

_token_cache = {"value": None, "expires_at": 0}

def get_access_token() -> str:
    if _token_cache["value"] and time.time() < _token_cache["expires_at"] - 60:
        return _token_cache["value"]
    r = requests.post(
        f"{BASE_URL}/oauth2/v1/token",
        data={
            "grant_type": "client_credentials",
            "client_id": CLIENT_ID,
            "client_secret": CLIENT_SECRET,
            "scope": "payments",
        },
    )
    r.raise_for_status()
    data = r.json()
    _token_cache["value"] = data["access_token"]
    _token_cache["expires_at"] = time.time() + data["expires_in"]
    return _token_cache["value"]

def _headers() -> dict:
    return {
        "Authorization": f"Bearer {get_access_token()}",
        "Content-Type": "application/json",
        "Request-ID": str(uuid.uuid4()),
    }

def authorize(amount_cents: int, currency_iso_alpha: str, card: dict, order_id: str, sale: bool = False) -> dict:
    """Authorize (or authorize+capture if sale=True)."""
    r = requests.post(
        f"{BASE_URL}/payments/v1/payments",
        headers=_headers(),
        json={
            "merchantId": MERCHANT_ID,
            "requestType": "PaymentCardSaleTransaction" if sale else "PaymentCardPreAuthTransaction",
            "transactionAmount": {
                "total": f"{amount_cents / 100:.2f}",
                "currency": currency_iso_alpha,
            },
            "paymentMethod": {"paymentCard": card},
            "order": {"orderId": order_id},
        },
    )
    r.raise_for_status()
    return r.json()

def capture(ipg_transaction_id: str, amount_cents: int, currency: str) -> dict:
    r = requests.post(
        f"{BASE_URL}/payments/v1/payments/{ipg_transaction_id}/postauth",
        headers=_headers(),
        json={
            "requestType": "PostAuthTransaction",
            "transactionAmount": {"total": f"{amount_cents / 100:.2f}", "currency": currency},
        },
    )
    r.raise_for_status()
    return r.json()

def refund(ipg_transaction_id: str, amount_cents: int, currency: str) -> dict:
    r = requests.post(
        f"{BASE_URL}/payments/v1/payments/{ipg_transaction_id}/return",
        headers=_headers(),
        json={
            "requestType": "ReturnTransaction",
            "transactionAmount": {"total": f"{amount_cents / 100:.2f}", "currency": currency},
        },
    )
    r.raise_for_status()
    return r.json()

def void(ipg_transaction_id: str) -> dict:
    r = requests.post(
        f"{BASE_URL}/payments/v1/payments/{ipg_transaction_id}/void",
        headers=_headers(),
        json={"requestType": "VoidTransaction"},
    )
    r.raise_for_status()
    return r.json()

def tokenize_card(card: dict, billing_address: dict | None = None) -> str:
    """Returns payment token value to store in place of Orbital CustomerRefNum."""
    payload = {"requestType": "PaymentCardPaymentMethod", "paymentCard": card}
    if billing_address:
        payload["billingAddress"] = billing_address
    r = requests.post(f"{BASE_URL}/payments/v1/payment-methods", headers=_headers(), json=payload)
    r.raise_for_status()
    return r.json()["paymentToken"]["value"]

def charge_token(token: str, amount_cents: int, currency: str, order_id: str) -> dict:
    r = requests.post(
        f"{BASE_URL}/payments/v1/payments",
        headers=_headers(),
        json={
            "merchantId": MERCHANT_ID,
            "requestType": "PaymentCardSaleTransaction",
            "transactionAmount": {"total": f"{amount_cents / 100:.2f}", "currency": currency},
            "paymentMethod": {"paymentToken": {"value": token, "reusable": True}},
            "order": {"orderId": order_id},
        },
    )
    r.raise_for_status()
    return r.json()
```

---

## JavaScript (Node.js) boilerplate

```javascript
import fetch from 'node-fetch';
import { randomUUID } from 'crypto';

const BASE_URL = process.env.JPMC_BASE_URL ?? 'https://api.uat.jpmorgan.com';
const CLIENT_ID = process.env.JPMC_CLIENT_ID;
const CLIENT_SECRET = process.env.JPMC_CLIENT_SECRET;
const MERCHANT_ID = process.env.JPMC_MERCHANT_ID;

let tokenCache = { value: null, expiresAt: 0 };

async function getAccessToken() {
  if (tokenCache.value && Date.now() < tokenCache.expiresAt - 60_000) return tokenCache.value;
  const res = await fetch(`${BASE_URL}/oauth2/v1/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ grant_type: 'client_credentials', client_id: CLIENT_ID, client_secret: CLIENT_SECRET, scope: 'payments' }),
  });
  const data = await res.json();
  tokenCache = { value: data.access_token, expiresAt: Date.now() + data.expires_in * 1000 };
  return tokenCache.value;
}

async function headers() {
  return { Authorization: `Bearer ${await getAccessToken()}`, 'Content-Type': 'application/json', 'Request-ID': randomUUID() };
}

export async function authorize(amountCents, currency, card, orderId, sale = false) {
  const res = await fetch(`${BASE_URL}/payments/v1/payments`, {
    method: 'POST', headers: await headers(),
    body: JSON.stringify({
      merchantId: MERCHANT_ID,
      requestType: sale ? 'PaymentCardSaleTransaction' : 'PaymentCardPreAuthTransaction',
      transactionAmount: { total: (amountCents / 100).toFixed(2), currency },
      paymentMethod: { paymentCard: card },
      order: { orderId },
    }),
  });
  if (!res.ok) throw await res.json();
  return res.json();
}

export async function capture(ipgTransactionId, amountCents, currency) {
  const res = await fetch(`${BASE_URL}/payments/v1/payments/${ipgTransactionId}/postauth`, {
    method: 'POST', headers: await headers(),
    body: JSON.stringify({ requestType: 'PostAuthTransaction', transactionAmount: { total: (amountCents / 100).toFixed(2), currency } }),
  });
  if (!res.ok) throw await res.json();
  return res.json();
}

export async function refund(ipgTransactionId, amountCents, currency) {
  const res = await fetch(`${BASE_URL}/payments/v1/payments/${ipgTransactionId}/return`, {
    method: 'POST', headers: await headers(),
    body: JSON.stringify({ requestType: 'ReturnTransaction', transactionAmount: { total: (amountCents / 100).toFixed(2), currency } }),
  });
  if (!res.ok) throw await res.json();
  return res.json();
}

export async function voidTransaction(ipgTransactionId) {
  const res = await fetch(`${BASE_URL}/payments/v1/payments/${ipgTransactionId}/void`, {
    method: 'POST', headers: await headers(),
    body: JSON.stringify({ requestType: 'VoidTransaction' }),
  });
  if (!res.ok) throw await res.json();
  return res.json();
}

export async function tokenizeCard(card, billingAddress) {
  const res = await fetch(`${BASE_URL}/payments/v1/payment-methods`, {
    method: 'POST', headers: await headers(),
    body: JSON.stringify({ requestType: 'PaymentCardPaymentMethod', paymentCard: card, billingAddress }),
  });
  if (!res.ok) throw await res.json();
  return (await res.json()).paymentToken.value;
}
```

---

## Environment variables required

Create a `.env` file (never commit to version control):

```bash
JPMC_CLIENT_ID=your-client-id
JPMC_CLIENT_SECRET=your-client-secret
JPMC_MERCHANT_ID=your-merchant-id
JPMC_BASE_URL=https://api.uat.jpmorgan.com   # switch to https://api.jpmorgan.com for production
```

---

After generating the boilerplate, remind the user to:
1. Register for JPMC Commerce Platform API credentials at https://developer.jpmorgan.com
2. Whitelist their server IPs in the JPMC developer portal
3. Run `/orbital-scan` to find all remaining Orbital patterns
4. Run `/orbital-recode-file <file>` on each file to apply the migration
