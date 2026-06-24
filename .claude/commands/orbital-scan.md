# Scan Codebase for Orbital API Patterns

Search the codebase for all Orbital Gateway API usage and produce a prioritized list of files/lines that need migration to JP Morgan Commerce Platform APIs.

## Usage

```
/orbital-scan [path]
```

- `/orbital-scan` — scan entire working directory
- `/orbital-scan src/` — scan a specific directory

## Instructions for Claude

When this skill is invoked, perform the following steps:

### Step 1 — Locate Orbital API patterns

Search for these patterns (use Grep tool, glob `**/*`):

**XML request elements**
- `NewOrder` — authorization or sale
- `MarkForCapture` — capture
- `Refund` — refund / follow-on refund
- `Reversal` — void
- `Profile` — customer profile / tokenization
- `OrbitalConnectionUsername`
- `OrbitalConnectionPassword`
- `CustomerRefNum` — profile token reference
- `TxRefNum` — transaction reference number
- `ProcStatus` — processing status response code
- `ApprovalStatus` — approval response
- `RespCode` — response code

**Endpoint URLs**
- `orbitalvar.chasepaymentech.com`
- `orbital1.chasepaymentech.com`
- `orbital2.chasepaymentech.com`
- `paymentech.net`

**Config keys / constants (case-insensitive)**
- `ORBITAL_USERNAME` / `OrbitalUsername`
- `ORBITAL_PASSWORD` / `OrbitalPassword`
- `ORBITAL_MERCHANT_ID` / `OrbitalMerchantID`

### Step 2 — Categorize findings

Group matches into:
1. **Auth/Config** — credentials, merchant IDs, endpoint URLs
2. **Transaction creation** — NewOrder (auth, sale)
3. **Capture** — MarkForCapture
4. **Refund** — Refund, FollowOnRefund
5. **Void** — Reversal
6. **Profile/Token** — Profile, CustomerRefNum
7. **Response handling** — ProcStatus, ApprovalStatus, RespCode, error parsing

### Step 3 — Output report

For each category, list:
- File path and line numbers
- The Orbital pattern found
- The JPMC equivalent to replace it with (brief note)
- Estimated effort: Low / Medium / High

### Step 4 — Summary

Print a table:

| Category | Files affected | Lines | Effort |
|----------|---------------|-------|--------|
| Auth/Config | … | … | Low |
| Transaction creation | … | … | High |
| Capture | … | … | Medium |
| Refund | … | … | Medium |
| Void | … | … | Low |
| Profile/Token | … | … | Medium |
| Response handling | … | … | High |

Conclude with the recommended migration order (highest-risk items first).

Remind the user to run `/orbital-to-jpm` for the complete API mapping reference, or `/orbital-recode-file <file>` to auto-recode a single file.
