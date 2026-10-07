# SMS Analysis from Sample

## Telebirr (127)
Samples show:
- "You have received ETB X,XXX.XX from ... Your transaction number is <TOKEN>"
- Tokens like DJ50GRUXGI, DJ54GRTMKA (8–10 alphanum)
- Sender in practice is short code 127 (from real SMS). In sample text shows "EFB 2" as chat context but actual SMS sender will be 127.

**Parser:**
- Amount: received ETB\s*([0-9,]+\.?\d*)
- Ref: transaction number is\s+([A-Z0-9]{6,20})

## CBE
Two forms:
1. "Ref No FT262079N0PS" - clean
2. Receipt URL has id=FT262079N0PS65372077 (may include suffix)
Prefer "Ref No" if present. Also "id=" can be fallback. 
Note "credited with ETB" and "received ETB" both appear.

**Parser:**
- Amount: (?:credited with|received) ETB\s*([0-9,]+\.?\d*)
- Ref priority: Ref No\s+([A-Z0-9]+) > id=([A-Z0-9]+)

## Abyssinia Bank
Credit lines: "your account ... was credited with ETB X,XXX.XX"
Reference in receipt: trx=FT262760YTXX99168
Ignore debit lines.

**Parser:**
- Amount: credited with ETB\s*([0-9,]+\.?\d*)
- Ref: trx=(FT[A-Z0-9]{10,25}) (from slip URL)
- Only process if not debit

## Provider detection (from sender)
In webhook, sender comes from SMS metadata:
- Telebirr: sender == "127" or "+251127..." 
- CBE: alphanumeric sender ID (varies by SIM/network) - e.g. "CBE", "CBE-ALERT"
- Abyssinia: bank sender ID

Also can infer from body if sender ambiguous, but sender is reliable.

## Note on CBE formats
See cbe-message-styles.md and cbe-format-ambiguity.md - some CBE SMS (mbreciept style) don't show FT ref in text; need confirmation from operator SIM.

## CBE reference extraction preference
- Prefer explicit "Ref No <REF>" (shorter base ref) over URL id/trx tokens that may include account suffix
