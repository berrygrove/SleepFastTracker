# US1: Add "delta" field to fasting records

**As** a user  
**I want** each completed fast record to store, in addition to the timestamp and fasting/eating flag, a signed number showing how many minutes I was over or under my target fasting window  
**So that** I can see immediately whether I exceeded or fell short of my goal, and later use that to adjust my streak.

## Acceptance criteria
1. **Database**  
   - Add a new column `delta_minutes` (integer, signed, precision up to ±99999) to the `fasting_records` table.  
   - Existing records default to `NULL` (or zero) until recalculated by a back‑fill task.

2. **Record creation**  
   - When the user toggles **from** fasting (`true`) **to** eating (`false`), before saving the "eating" record, fetch the immediately preceding "fasting" record's timestamp.  
   - Compute:
     ```text
     fasting_window_minutes = (24h − user.eating_window_hours) × 60
     actual_fast_minutes = now_timestamp − previous_fast_start_timestamp  (in minutes)
     delta_minutes = actual_fast_minutes − fasting_window_minutes
     ```
   - Populate the new record's `delta_minutes` field with that value.

3. **Data integrity**  
   - If no preceding `true` record exists (e.g. app reinstall or data loss), set `delta_minutes = NULL` and log a warning; do not crash.

4. **Back‑fill task (optional)**  
   - A one‑off script that iterates over all existing fasting records, recomputes `delta_minutes` using the same logic, and populates the new column. 