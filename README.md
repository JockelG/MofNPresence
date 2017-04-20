# MofN Presence

Require M of N sensors from a defined group for truth

Two SmartApps -- 'Presence' and 'Assured Presence'

Presence
Will provide a virtual presence sensor with proper state, based upon M of N presence sensor state.  For instance, you can require 3 out of 6 presense sensors are showing 'present'.

Assured Presence
Will provide a virtual presence sensor with proper state, based upon M of N presens sonsor state.  This requires the 'arrival' of M sensors to happen in a specified timeframe.  For instance, 2 out of 3 presence sensors show a state change to 'present' within a 3 minute timeframe, otherwise the virtual sensor will show as 'not present'.
