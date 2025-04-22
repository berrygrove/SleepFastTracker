# US11: Home Screen Widget Support

**As** a user  
**I want** to have a home screen widget showing my current fasting status and timer  
**So that** I can quickly check my progress without opening the full app.

## Acceptance criteria

1. **Widget Sizes**  
   - Small (1x1): Basic status indicator and time remaining
   - Medium (2x1): Status, timer, and streak information
   - Large (4x1): Complete dashboard with status, timer, streak, and quick actions

2. **Visual Design**  
   - Clear, high-contrast display for readability in various lighting conditions
   - Consistent with app's visual identity
   - Color-coded status indicators (fasting/eating)
   - Dynamic theme support (light/dark mode)

3. **Content**  
   - Current status (fasting/eating)
   - Timer counting down or up (matching home screen behavior in US3)
   - Current streak (on medium and large sizes)
   - Today's progress toward goal (on large size)

4. **Functionality**  
   - Auto-refresh at least every minute
   - Manual refresh on tap
   - Toggle fasting state directly from widget (with confirmation)
   - Launch app when widget background is tapped

5. **Performance**  
   - Minimal battery impact
   - Efficient updates
   - Survive device restarts 