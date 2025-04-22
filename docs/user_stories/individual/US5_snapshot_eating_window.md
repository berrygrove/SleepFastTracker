# US5: Snapshot eating‑window on each fast record

**As** a developer  
**I want** each completed fast record to store the user's eating‑window setting at the moment the fast ends  
**So that** our delta‑recalculation always uses the correct target, even if the user later changes their preference.

## Acceptance criteria

1. **Schema change**  
   - Add column `eating_window_minutes` (integer) to `fasting_records`.  
   - Back‑fill existing records with the correct snapshot if historical settings are stored; otherwise populate with user's current window and log a warning.

2. **Record creation logic**  
   - On toggle **from** fasting → **to** eating:  
     1. Read user's `eating_window_hours`, convert → `eating_window_minutes`.  
     2. Write that value into `fasting_records.eating_window_minutes`.  
     3. Compute  
        ```text
        fasting_window_minutes = 24*60 − eating_window_minutes
        actual_fast_minutes    = now − previous_fast_start
        delta_minutes          = actual_fast_minutes − fasting_window_minutes
        ```  
     4. Write `delta_minutes` to the same record.

3. **Back‑fill script**  
   - Extend US 4's maintenance script to also populate `eating_window_minutes`.  
   - If no historical snapshot exists, use current user setting and flag for audit. 