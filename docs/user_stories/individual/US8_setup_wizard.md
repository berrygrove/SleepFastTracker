# US8: Setup Wizard for New Users

**As** a new user  
**I want** to be guided through initial setup with a step-by-step wizard  
**So that** I can configure my preferred eating window, notifications, and other personal settings without feeling overwhelmed.

## Acceptance criteria

1. **Wizard Flow**  
   - Display setup wizard on first app launch after installation.
   - Include the following steps:
     1. Welcome introduction to the app
     2. Setting intermittent fasting window (default 16:8)
     3. Notification preferences
     4. Time zone confirmation
     5. Optional account creation

2. **User Experience**  
   - Clear navigation buttons (Back, Next, Skip).
   - Progress indicator showing current step in the flow.
   - Ability to revisit setup from settings later.
   - Responsive and accessible design.

3. **Data Storage**  
   - Save user preferences to local storage.
   - Apply settings immediately after completion.
   - Default values for skipped steps.

4. **Validation**  
   - Ensure eating window is between 1-23 hours.
   - Confirm time zone matches device settings.
   - Validate any entered data before proceeding. 