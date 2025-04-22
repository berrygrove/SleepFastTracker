List of user stories

---

## US1: Add “delta” field to fasting records

**As** a user  
**I want** each completed fast record to store, in addition to the timestamp and fasting/eating flag, a signed number showing how many minutes I was over or under my target fasting window  
**So that** I can see immediately whether I exceeded or fell short of my goal, and later use that to adjust my streak.

### Acceptance criteria
1. **Database**  
   - Add a new column `delta_minutes` (integer, signed, precision up to ±99999) to the `fasting_records` table.  
   - Existing records default to `NULL` (or zero) until recalculated by a back‑fill task.

2. **Record creation**  
   - When the user toggles **from** fasting (`true`) **to** eating (`false`), before saving the “eating” record, fetch the immediately preceding “fasting” record’s timestamp.  
   - Compute:
     ```text
     fasting_window_minutes = (24h − user.eating_window_hours) × 60
     actual_fast_minutes = now_timestamp − previous_fast_start_timestamp  (in minutes)
     delta_minutes = actual_fast_minutes − fasting_window_minutes
     ```
   - Populate the new record’s `delta_minutes` field with that value.

3. **Data integrity**  
   - If no preceding `true` record exists (e.g. app reinstall or data loss), set `delta_minutes = NULL` and log a warning; do not crash.

4. **Back‑fill task (optional)**  
   - A one‑off script that iterates over all existing fasting records, recomputes `delta_minutes` using the same logic, and populates the new column.

---

## US2: Modify streak calculation to use “delta” compensation

**As** a user  
**I want** my “current streak” to count days where—even if I fasted slightly less than my goal—I had enough extra minutes on other days to cover the deficit  
**So that** small off‑days don’t break my long‑term motivation.

### Acceptance criteria
1. **Definition of a “day”**  
   - A logical day is a calendar date in the user’s local timezone (e.g. 2025‑04‑19). All fasts with an _end_ timestamp on that date are considered that day’s fast.

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
   - Multi‑day examples matching “18 h window” scenario from requirements  
   - Timezone boundary tests (fasts spanning midnight)

---

## US3: Update home‑screen timer to count up after window

**As** a user  
**I want** the home‑screen fasting timer to switch from “time until fasting window starts” to “minutes I’ve already fasted past my goal” once the target window has passed  
**So that** I can see exactly how much extra time I’ve been fasting instead of a reset countdown.

### Acceptance criteria
1. **Before fasting window**  
   - Display:  
     ```
     “Start eating in XX:YY:ZZ”  ← countdown to (last_meal_time + eating_window)
     ```
2. **Once fasting-window threshold is reached**  
   - Switch display to:  
     ```
     “You’ve fasted +HH:MM:SS”  ← elapsed time since (last_meal_time + eating_window)
     ```
   - Continue counting up indefinitely (until next toggle).

3. **Formatting**  
   - Always hh:mm:ss (zero‑pad hours/minutes/seconds).  
   - Reflect local timezone.  

4. **Performance**  
   - Timer updates at least once per second.  
   - Efficient, avoids memory leaks.  

---

## US4: Back‑fill and analytics on historical data

**As** a product owner  
**I want** a maintenance script to compute and populate `delta_minutes` for all past fasting records and verify streak correctness  
**So that** existing users see accurate streaks and deltas without manual replay.

### Acceptance criteria
1. **Script**  
   - Reads every “eating” record with `delta_minutes IS NULL`.  
   - Fetches its preceding “fasting” record and user’s eating‑window setting at that time.  
   - Populates `delta_minutes` as in US1.  
2. **Verification**  
   - After back‑fill, run streak calculation for a sample of users and compare to pre‑fill streak to ensure no regressions (allowing for change in logic).  
3. **Logging and roll‑back**  
   - Produce a CSV of records updated.  
   - Support a dry‑run mode.

---

## US5 (revised): Snapshot eating‑window on each fast record

**As** a developer  
**I want** each completed fast record to store the user’s eating‑window setting at the moment the fast ends  
**So that** our delta‑recalculation always uses the correct target, even if the user later changes their preference.

### Acceptance criteria

1. **Schema change**  
   - Add column `eating_window_minutes` (integer) to `fasting_records`.  
   - Back‑fill existing records with the correct snapshot if historical settings are stored; otherwise populate with user’s current window and log a warning.

2. **Record creation logic**  
   - On toggle **from** fasting → **to** eating:  
     1. Read user’s `eating_window_hours`, convert → `eating_window_minutes`.  
     2. Write that value into `fasting_records.eating_window_minutes`.  
     3. Compute  
        ```text
        fasting_window_minutes = 24*60 − eating_window_minutes
        actual_fast_minutes    = now − previous_fast_start
        delta_minutes          = actual_fast_minutes − fasting_window_minutes
        ```  
     4. Write `delta_minutes` to the same record.

3. **Back‑fill script**  
   - Extend US 4’s maintenance script to also populate `eating_window_minutes`.  
   - If no historical snapshot exists, use current user setting and flag for audit.

---

## US6 (revised): Enforce one completed fast per calendar day (with multi‑day support)

**As** a user  
**I want** the app to block creating more than one completed‑fast record whose **end date** falls on the same calendar day—unless it’s the natural end of a multi‑day fast  
**So that** daily streak logic remains consistent without accidentally double‑counting.

### Acceptance criteria

1. **Identify end date**  
   - Compute `end_date` as the date in user’s timezone of the fasting→eating toggle’s timestamp.

2. **Business rule**  
   - **If** there already exists a fast record with the same `end_date` **and** its `start_date` ≠ today’s start of a continuing multi‑day fast, **reject** the insert.  
   - **Else** allow.  

3. **User feedback**  
   - On rejection, show:  
     > “You’ve already ended a fast today. For a multi‑day fast, simply wait until you finish.”

4. **Multi‑day exception**  
   - If the current fasting record’s `start_date` < `end_date − 1 day`, treat it as a single multi‑day record—no extra enforcement on the intermediate days.

5. **Tests**  
   - Toggling to eating twice on the same calendar date → blocked.  
   - Single fast spanning D₁→D₂ (overnight or multi‑day) → allowed exactly once at D₂.  

---

## US7: Inject virtual deltas for multi‑day fasts

**As** a developer  
**I want** to populate, at streak‑calculation time, one “virtual” delta of 0 for each full fasting window covered on intermediate calendar days of a multi‑day fast  
**So that** streak logic doesn’t penalize days where I was still fasting.

### Acceptance criteria

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
     - Inject a “virtual” delta of **0** for date `D`.

3. **Streak algorithm update**  
   - As you iterate backwards over calendar dates:  
     1. Sum real deltas for that date (including the final-day delta on `end_date`).  
     2. Add any virtual 0’s for intermediate days.  
     3. If no real or virtual entries exist, subtract one full window ( `−fasting_window` ).  

4. **Edge conditions**  
   - Do **not** create virtual entries for the start day if the fast didn’t yet complete a full window before midnight.  
   - Virtuals only exist in‑memory for streak logic—they are not persisted.

---
