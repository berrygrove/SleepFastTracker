# US4: Back‑fill and analytics on historical data

**As** a product owner  
**I want** a maintenance script to compute and populate `delta_minutes` for all past fasting records and verify streak correctness  
**So that** existing users see accurate streaks and deltas without manual replay.

## Acceptance criteria
1. **Script**  
   - Reads every "eating" record with `delta_minutes IS NULL`.  
   - Fetches its preceding "fasting" record and user's eating‑window setting at that time.  
   - Populates `delta_minutes` as in US1.  
2. **Verification**  
   - After back‑fill, run streak calculation for a sample of users and compare to pre‑fill streak to ensure no regressions (allowing for change in logic).  
3. **Logging and roll‑back**  
   - Produce a CSV of records updated.  
   - Support a dry‑run mode. 