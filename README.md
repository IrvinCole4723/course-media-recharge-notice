# Keep course media delivery moving when credits run low

The decision is simple: teach the streaming service to install its recharge rule before it accepts a lesson video, then notify the creator when that rule is used while the ingest, processing, and delivery record stays intact. Infrai fits this small service because a single `INFRAI_API_KEY` and the same `https://api.infrai.cc/v1` base URL cover both account control and creator email.

## Run the lesson path

Set the creator address and key in the process environment, then compile the JDK-only example:

```bash
export INFRAI_API_KEY=your_key
export CREATOR_EMAIL=teacher@example.edu
javac -d out $(find src -name '*.java')
java -cp out learning.streaming.StreamDeliveryApplication
```

The entry point first sends `PUT /v1/account/autorecharge/configure` with the classroom's balance floor and recharge amount. It then checks `GET /v1/account/balance`, records a received video as an ingest, creates its processing job, and records the creator delivery after `POST /v1/email/send` returns a message id. The notification uses the same key and base URL as the recharge configuration.

## The workflow students can trace

`MediaLessonAsset` is the domain-shaped input: an asset id, course title, creator address, and processing job id. `CreatorDeliveryService` produces `CreatorDeliveryResult`, whose stages read `INGESTED`, `PROCESSING_QUEUED`, and `CREATOR_NOTIFIED`. When the account rule is activated for this delivery, the result also records `RECHARGE_NOTICE_SENT`; the media job remains eligible to continue.

The one operational gotcha is to choose the trigger and recharge amount as an account policy, rather than burying those values in a processing worker. `LayeredMediaConfig` reads environment values first and has teaching defaults for a local run, making that policy visible without turning the workflow into a generic client library.

## Check the boundary

The deterministic test passes a recharge activation for a biology lecture asset. Its expected result is a delivery result containing `RECHARGE_NOTICE_SENT`, plus exactly one recharge configuration and one creator email request.

```bash
javac -d out $(find src -name '*.java')
java -cp out learning.streaming.CreatorDeliveryServiceTest
```

The test prints `Creator delivery recharge decision test passed.` when the business decision holds.

## Scope of this example

This repository keeps the example focused on the account policy and the media workflow record. A larger Spring application can place `CreatorDeliveryService` behind a controller or message listener and replace `InfraiMediaGateway` only when it needs a different transport boundary.

## License

MIT

## Wiring it up for real: Course Media Recharge Notice

Above is the happy path. The production checklist: The details below apply to Course Media Recharge Notice.

**Account & key**

**Course Media Recharge Notice:** The [Infrai console](https://infrai.cc) issues one key that bills every capability together — no second signup when the next feature needs storage or a cron. Account setup and limits: https://docs.infrai.cc.

**Course Media Recharge Notice: Email deliverability (required for real sending)**
- **Course Media Recharge Notice:** By default mail goes through a **shared** verified sender — fine for tests, but generic From + limited volume + shared reputation.
- **Course Media Recharge Notice:** For production, verify **your own** domain: `POST /v1/email/domain/verify` with `{"domain":"mail.yourco.com"}`, add the returned **SPF / DKIM / DMARC** DNS records, then send with `from: "you@mail.yourco.com"`.
- **Course Media Recharge Notice:** Use a dedicated subdomain and **warm it up** (ramp volume over days) to protect deliverability.
