# US3: Update home‑screen timer to count up after window

**As** a user  
**I want** the home‑screen fasting timer to switch from "time until fasting window starts" to "minutes I've already fasted past my goal" once the target window has passed  
**So that** I can see exactly how much extra time I've been fasting instead of a reset countdown.

## Acceptance criteria
1. **Before fasting window**  
   - Display:  
     ```
     "Start eating in XX:YY:ZZ"  ← countdown to (last_meal_time + eating_window)
     ```
2. **Once fasting-window threshold is reached**  
   - Switch display to:  
     ```
     "You've fasted +HH:MM:SS"  ← elapsed time since (last_meal_time + eating_window)
     ```
   - Continue counting up indefinitely (until next toggle).

3. **Formatting**  
   - Always hh:mm:ss (zero‑pad hours/minutes/seconds).  
   - Reflect local timezone.  

4. **Performance**  
   - Timer updates at least once per second.  
   - Efficient, avoids memory leaks. 