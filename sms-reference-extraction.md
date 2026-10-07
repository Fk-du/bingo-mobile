# SMS Reference Extraction (from samples)

## Telebirr (4 messages)
1. amount=1,200.00 | ref=DJ50GRUXGI (transaction number is DJ50GRUXGI)
2. amount=5,000.00 | ref=DJ54GRTMKA
3. amount=100.00   | ref=DJ52GRNI5S
4. amount=120.00   | ref=DJ51GRFV1B

Yes - reference number is clearly "transaction number is <REF>".

## CBE (2 credit messages in sample)
1. amount=2,000.00 | ref=FT262079N0PS ("Ref No FT262079N0PS")
2. amount=500.00   | ref not shown as explicit "Ref No" in that exact line? But full block: has receipt URL with id=FT262079N0PS65372077 (contains longer token). The "Ref No" appears in first CBE message as FT262079N0PS.

But in the full block grouping: CBE credit shows "Ref No FT262079N0PS" in the message. Yes, reference is there.

## Abyssinia Bank (1 credit block)
1. amount=1,000.00 | ref=FT262760YTXX99168 (from receipt URL trx=...)
Reference is the transaction ID in trx parameter.

So all show reference numbers: Telebirr uses "transaction number is", CBE uses "Ref No", Abyssinia embeds ref in trx URL.
