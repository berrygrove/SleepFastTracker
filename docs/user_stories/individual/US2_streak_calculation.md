# US2: Modify streak calculation to use "delta" compensation

**As** a user  
**I want** my "current streak" to count days where—even if I fasted slightly less than my goal—I had enough extra minutes on other days to cover the deficit  
**So that** small off‑days don't break my long‑term motivation.

## Acceptance criteria
1. **Definition of a "day"**  
   - A logical day is a calendar date in the user's local timezone (e.g. 2025‑04‑19). All fasts with an _end_ timestamp on that date are considered that day's fast.

2. **Algorithm**  
   ```pseudo
   streak = 0
   cumulative_delta = 0

   for each date D from today backward:
     # sum all delta_minutes for fasts that ended on D (skip NULLs)
     day_delta = SUM(delta_minutes for records where end_date = D)
     cumulative_delta += day_delta

     if cumulative_delta >= 0:
       streak += 1
     else:
       break
   return streak
   ```

3. **Edge cases**  
   - If on date D the user had no completed fast, treat day_delta = −fasting_window_minutes (i.e. they missed entirely).  
   - If multiple fasts on the same day, sum their deltas.  
   - If cumulative_delta dips below zero, streak ends at the previous day.  

4. **Unit tests**  
   - Single‑day successes and failures  
   - Multi‑day examples matching "18 h window" scenario from requirements  
   - Timezone boundary tests (fasts spanning midnight) 