# US7: Inject virtual deltas for multi‑day fasts

**As** a developer  
**I want** to populate, at streak‑calculation time, one "virtual" delta of 0 for each full fasting window covered on intermediate calendar days of a multi‑day fast  
**So that** streak logic doesn't penalize days where I was still fasting.

## Acceptance criteria

1. **When to run**  
   - During streak computation (not in DB), for each multi‑day record where  
     ```text
     total_minutes   = end_ts − start_ts  
     fasting_window = 24*60 − eating_window_minutes  
     full_windows    = ⌊ total_minutes / fasting_window ⌋  
     ```
   - And where `full_windows ≥ 2`.  

2. **Virtual deltas**  
   - Let  
     ```text
     virtual_days = full_windows − 1
     end_date     = date(end_ts)
     ```
   - For `i = 1 … virtual_days`:  
     - Compute `D = end_date − i days`.  
     - Inject a "virtual" delta of **0** for date `D`.

3. **Streak algorithm update**  
   - As you iterate backwards over calendar dates:  
     1. Sum real deltas for that date (including the final-day delta on `end_date`).  
     2. Add any virtual 0's for intermediate days.  
     3. If no real or virtual entries exist, subtract one full window ( `−fasting_window` ).  

4. **Edge conditions**  
   - Do **not** create virtual entries for the start day if the fast didn't yet complete a full window before midnight.  
   - Virtuals only exist in‑memory for streak logic—they are not persisted. 