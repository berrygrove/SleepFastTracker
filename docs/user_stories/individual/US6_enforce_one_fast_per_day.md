# US6: Enforce one completed fast per calendar day (with multi‑day support)

**As** a user  
**I want** the app to block creating more than one completed‑fast record whose **end date** falls on the same calendar day—unless it's the natural end of a multi‑day fast  
**So that** daily streak logic remains consistent without accidentally double‑counting.

## Acceptance criteria

1. **Identify end date**  
   - Compute `end_date` as the date in user's timezone of the fasting→eating toggle's timestamp.

2. **Business rule**  
   - **If** there already exists a fast record with the same `end_date` **and** its `start_date` ≠ today's start of a continuing multi‑day fast, **reject** the insert.  
   - **Else** allow.  

3. **User feedback**  
   - On rejection, show:  
     > "You've already ended a fast today. For a multi‑day fast, simply wait until you finish."

4. **Multi‑day exception**  
   - If the current fasting record's `start_date` < `end_date − 1 day`, treat it as a single multi‑day record—no extra enforcement on the intermediate days.

5. **Tests**  
   - Toggling to eating twice on the same calendar date → blocked.  
   - Single fast spanning D₁→D₂ (overnight or multi‑day) → allowed exactly once at D₂. 