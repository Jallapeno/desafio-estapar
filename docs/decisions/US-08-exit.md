# US-08: exit processing

- ENTRY, PARKED and EXIT acquire the same ordered sector locks before reading sessions.
- Closing the active session and releasing its spot are one transaction. Invalid time or failed persistence leaves both unchanged.
- Pricing uses the session's saved entry multiplier and its sector's current base price.
- An EXIT without an active session is acknowledged with HTTP 200 and changes nothing. A replay never reopens a closed session or releases its former spot.
- An EXIT older than a new active visit's entry is rejected with HTTP 400, preserving the new visit.
- For a session still ENTERED, US-08 adopts the first sector ordered by code as the fallback, records it on the closed session, and applies the same 30-minute tolerance. This is a project assumption: the challenge does not define the default sector or tariff for a vehicle without PARKED.
- Revenue aggregation and its API remain outside US-08.
