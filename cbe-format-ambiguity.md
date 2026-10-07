# CBE - Conclusion on References

From samples:
- Style B has explicit "Ref No FT262079N0PS" and also id= contains FT token (possibly longer)
- Style A and C show mbreciept.cbe.com.et links with v2-<short token> but NO FT-style transaction reference visible in the SMS text
- No other obvious FT pattern in those blocks

This means:
1. Some CBE SMS do include the transaction reference (FT...) in text
2. Some CBE SMS (mbreciept style) may not include FT ref in the visible SMS body as shown - the "reference" might be the receipt token or the bank may format differently

Implication for parsing:
- We must be defensive. Only extract refs that match expected patterns.
- For CBE: try "Ref No <FT...>", then "id=<FT...>", then look for standalone FT[0-9A-Z]{10,} token. If none found after these, DO NOT ingest - either ignore as non-parsable or require operator to confirm sender format.
- Also filter for credit keywords (received/credited) before parsing.

But better to get actual live SMS from the operator SIM for all 3 banks (especially CBE variants they actually receive). The current samples show ambiguity for 2 of 3 CBE messages.

Action: Update sms-analysis.md and cbe-message-styles.md to note this. Don't change implementation plan yet - note "need confirmation of CBE SMS format for mbreciept style".
