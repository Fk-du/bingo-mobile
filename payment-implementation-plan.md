# Implementation Plan: SMS-Based Deposit (Reference + Bank Type)

## Overview
Replace/augment screenshot-based top-up with SMS-based auto-claim: agent configures deposit accounts (Telebirr 127, CBE, Abyssinia), incoming SMS parsed via webhook and stored as unclaimed receipts, players claim by selecting bank type + entering reference only.

## 1) Requirements
- Support 3 providers: TELEBIRR (127), CBE, ABYSSINIA
- Agent can configure which accounts are active (per provider)
- Player sees active agent accounts in wallet
- Claim with provider + referenceNo only (auto-credit exact amount)
- Prevent double-claim (atomic), deduplicate by reference
- Multi-tenant safe, backward compatible (keep screenshot flow initially)

## 2) Data Model

### A) Migration V20: received_payments
Table in tenant schema:
- id BIGSERIAL PK
- amount DECIMAL(19,2) NOT NULL
- reference_no VARCHAR(50) NOT NULL
- provider VARCHAR(20) NOT NULL (TELEBIRR|CBE|ABYSSINIA)
- sender VARCHAR(100)
- raw_sms TEXT
- status VARCHAR(20) NOT NULL DEFAULT 'UNCLAIMED' (UNCLAIMED|CLAIMED|IGNORED)
- claimed_by_user_id BIGINT
- claimed_at TIMESTAMP
- created_at TIMESTAMP NOT NULL DEFAULT NOW()
Indexes:
- UNIQUE (reference_no) - prevents reuse globally
- INDEX (status, provider, reference_no) for fast lookup

### B) Agent deposit accounts (User entity)
Add JSON field to master User (agents/admins): deposit_accounts
Structure:
```json
[
  {"provider":"TELEBIRR","accountNumber":"0911...","label":"Telebirr","active":true},
  {"provider":"CBE","accountNumber":"1000...","label":"CBE","active":false},
  {"provider":"ABYSSINIA","accountNumber":"...","label":"Abyssinia","active":false}
]
```
Enum: DepositProvider { TELEBIRR, CBE, ABYSSINIA }

## 3) Backend - Entities/Enums/Repo

- Enum PaymentIngestStatus { UNCLAIMED, CLAIMED, IGNORED }
- Enum DepositProvider { TELEBIRR, CBE, ABYSSINIA }
- Entity ReceivedPayment (tenant) with fields above
- Repository ReceivedPaymentRepository:
  - Optional<ReceivedPayment> findByReferenceNoAndStatus(String ref, PaymentIngestStatus status)
  - Optional<ReceivedPayment> findByProviderAndReferenceNoAndStatus(DepositProvider p, String ref, PaymentIngestStatus status)
  - boolean existsByReferenceNo(String ref)
  - long countUnclaimed()
- DTOs: ClaimDepositRequest (provider, referenceNo), ClaimDepositResponse (creditedAmount, balance)

## 4) SMS Parsing & Detection
Service SmsParserService:
- Normalize: trim, uppercase, collapse spaces, strip dashes/spaces in ref
- Provider detection from sender + body (priority: sender match):
  - Sender contains "127" or equals "127" -> TELEBIRR
  - Sender contains "CBE" (case-insensitive) -> CBE
  - Sender contains "ABY" or "ABYSSINIA" or "BOA" -> ABYSSINIA
- Amount regex: extract ETB/Birr number, strip commas, parse BigDecimal
- Ref regex priority:
  1. TELEBIRR: Transaction ID|Txn ID|Trans ID -> capture token (allow 127TB...)
  2. Generic: Ref|Reference|Ref No -> capture
  3. Fallback: [A-Z]{2,5}[0-9]{6,25}
- Keyword filter for credits: must contain credited|received|deposited
- Return ParsedSms(provider, amount, referenceNo, sender, rawSms, isValid)

## 5) Webhook API
Controller: PaymentsWebhookController (tenant-aware)
- POST /api/v1/payments/sms-webhook
- Auth: X-API-KEY header (shared secret) or IP allowlist
- Body: {sender, text, receivedAt?}
- Logic:
  - Parse SMS
  - If not valid credit -> save as IGNORED or just log, return 200
  - If existsByReferenceNo -> ignore (duplicate), return 200 (idempotent)
  - Save ReceivedPayment UNCLAIMED with provider/sender/rawSms
  - Return 201/200

## 6) Claim Deposit API
Wallet/Payments:
- POST /api/v1/wallet/claim-deposit
- Auth: authenticated player
- Body: ClaimDepositRequest(provider, referenceNo)
- Service.claimDeposit(userId, provider, ref):
  - Normalize ref
  - Find ReceivedPayment by (provider, normRef, UNCLAIMED)
  - If not found -> throw 404/400 "No matching unclaimed deposit found..."
  - Atomic: update status to CLAIMED with claimedByUserId, claimedAt WHERE id=? AND status='UNCLAIMED' (row update check)
  - Credit player.balance += amount (Player/Wallet service)
  - Create transaction record (type DEPOSIT, ref to received_payment id, description include provider+ref)
  - Return credited amount + new balance
- @Transactional, use version/rowcount check to avoid races

## 7) Agent Config API
Extend profile/settings:
- Add depositAccounts to User update (admin/agent only)
- Validate providers, dedupe, max 3
- GET/PUT /api/v1/users/me or existing profile update includes depositAccounts

## 8) Frontend (Mobile)

### A) Admin Profile (agent)
- UI to add/edit up to 3 providers: TELEBIRR/CBE/ABYSSINIA
- Fields: accountNumber, label, active toggle
- Save to profile.depositAccounts

### B) Player Wallet
- Show "Send deposit to" section: list active agent accounts grouped by provider (with account numbers)
- Add "Claim deposit" card:
  - Provider selector (segmented): show only enabled by agent; disable if none
  - Reference input with provider-specific placeholder (Telebirr: "Transaction ID", CBE/Abyssinia: "Ref No")
  - Submit button
- Call POST /wallet/claim-deposit
- On success: show amount credited, refresh wallet/balance
- Keep screenshot top-up section as fallback (toggleable later)

## 9) Testing Plan
- Parser unit tests with real samples (need from operator):
  - Telebirr x3: TB..., 127TB..., different formats
  - CBE x3: FT..., TRF..., variations
  - Abyssinia x3: TRF..., ABY..., variations
- Webhook: valid SMS creates UNCLAIMED; duplicate ref ignored; non-credit ignored
- Claim: correct provider+ref credits once; second claim fails; wrong provider fails; wrong ref fails
- Race: two concurrent claims -> only one succeeds
- Multi-tenant: SMS ingested in correct tenant context

## 10) Deployment/Setup
- Add Flyway V20
- Configure SMS forwarder app on operator phone: allowlist senders (127, CBE IDs, Abyssinia IDs), POST to webhook with X-API-KEY, filter keywords
- Expose webhook endpoint (HTTPS), document shared secret
- Backward compatible: no breaking changes to existing coin_requests flow
