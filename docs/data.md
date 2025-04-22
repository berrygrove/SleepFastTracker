Data structure

//The app only has one user, so make a database structure to store the following user settings:

[User settings]
- id (fixed = 1)
- Name
- Age
- Height (cm)
- Eating start time (HH:MM)
- Eating window hours
- Bed time (HH:MM)
- Wake up time (HH:MM)
- Setup completed (boolean)
- Notifications enabled (master switch)
- Bedtime check notification enabled
- Bedtime reminder notification enabled
- Weight update notification enabled
- Fasting end notification enabled
- Eating end notification enabled

[Fasting]
- id
- State (boolean: true = fasting, false = eating)
- Timestamp
- Delta minutes (minutes over/under target fasting window)
- Eating window minutes (snapshot of user's eating window setting)

[Sleep]
- id
- On time (boolean: true = went to bed on time, false = did not)
- Timestamp

[Weight]
- id
- Weight (kg)
- Timestamp

[Achievement]
- id
- Name
- Points
- Achieved (boolean)
- Category (sleep, fasting, or weight)
- Description
- Emoticon
- Threshold (value needed to achieve this)