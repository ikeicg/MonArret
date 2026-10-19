# Design notes

A short overview of how MonArret works and the main choices behind it.

## Two data flows

1. **The static schedule, once a day.** STM publishes its schedule as a GTFS zip. The loader streams it into a staging schema in PostgreSQL, checks row counts and references, then swaps the staging schema in. Reads never see a half-loaded schedule.
2. **The live feeds, every 20 seconds.** Two STM feeds, vehicle positions and trip updates, are polled into an in-memory store that holds one state per running trip. A request for a trip's health reads that store, runs a handful of small checks over a copy of the state, and returns the result. No database query is involved.

## Main choices

- **Live state lives in memory.** Around 1,500 trips run at a time and each state is small. A state holds the trip's route geometry, its stop schedule, a two-minute window of positions and the latest facts from the feed. A bus that leaves the feed is dropped within five minutes.
- **Each health item is a pure function of a snapshot.** The checks do not touch the store, the clock or the database, which keeps them small and easy to test.
- **Searches are cache-first.** The picker's route, direction and departure lookups are answered from Redis for an hour and fall back to PostgreSQL on a miss. The day's departures are cached per route and direction, and the next-hour filter is applied on each request.
- **Responses are cached per feed version.** A new feed message produces a new cache key, so an old answer is never served for new data. Responses carry an `ETag`, so a page that has not changed answers "not modified".
- **Polling respects STM's quota.** STM allows 10,000 requests a day per user. Two feeds every 20 seconds use 8,640. The interval is a setting, and the application refuses to start below 20 seconds.
- **Each feed has its own loop.** One failing feed cannot hold up the other, and a loop that errors restarts itself. If processing falls behind, only the newest message is kept.
- **Only trip updates state a service date.** After midnight STM's two feeds name a late-night trip with dates a day apart. A position therefore never changes a trip's date. It joins the trip already held, or, when it has to create one, takes its day from the static schedule.
- **No circuit breaker or tracing stack.** STM is an external service polled by one sequential loop, so there is nothing for them to protect. Health and metrics are served on a private port.
- **Facts, not scores.** Each item states what is known and says nothing when nothing matters.

## Known limits

- Predicted times are STM's. For some buses STM's prediction is just the timetable, and the page shows it as STM gave it.
- A bus on a detour may serve stops differently from the schedule. The page says when a bus is off its route, but its stop count follows the normal route.
- The after-midnight date rule rests on how STM's feeds behave, as observed. A trip update always overrides it.
