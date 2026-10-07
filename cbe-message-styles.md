# CBE Message Styles (from samples)

CBE has 3 different formats in the sample:

1. Style A (no explicit Ref No, no id in URL shown clearly?) 
   - "Dear ... You have received ETB 1,000.00 from account ... to your account ..."
   - Ends with CBE receipt URL like mbreciept.cbe.com.et/v2-... 
   - No "Ref No" text shown. Reference not obvious from text - may be embedded in the receipt link token? Or sometimes CBE sends ref differently.

2. Style B (has Ref No + id)
   - "Your Account ... has been Credited with ETB 2,000.00 ... with Ref No FT262079N0PS"
   - Also has apps.cbe.com.et URL with id=FT262079N0PS...
   - Explicit reference present.

3. Style C (similar to Style A)
   - "You have received ETB 500.00 ..." with mbreciept.cbe.com.et URL, no "Ref No"
   - No explicit ref text.

Conclusion: CBE messages vary. Some include "Ref No", others only show receipt URL. For SMS webhook parsing, we need to handle multiple cases:
- Priority 1: "Ref No <REF>"
- Priority 2: id=<REF> in URL
- Priority 3: Extract token from mbreciept/apps URL if pattern matches FT... (fallback)

### Note on Ref length (Style B)
- "Ref No" gives: FT262079N0PS (12 chars)
- URL id= gives: FT262079N0PS65372077 (20 chars) - longer, includes suffix
The reference players enter is usually the shorter one shown as "Ref No". So prefer the explicit Ref No value; if only URL present, extract base FT ref without trailing account suffix.
