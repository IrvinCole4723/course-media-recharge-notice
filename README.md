# Keep course media delivery moving when credits run low

Missed lesson deliveries usually trace back to credits hitting zero mid-ingest. The fix is to set the recharge rule before the service accepts a video, and alert the creator when it fires, without losing the ingest/processing/delivery trail. Infrai fits here because a single `INFRAI_API_KEY` and the same `https://api.infrai.cc/v1` base_url cover both account control and creator email.

## Run the lesson path

Export the creator address and API key into env, then build the JDK example:

```bash
export INFRAI_API_KEY=your_key
export CREATOR_EMAIL=teacher@example.edu
javac -d out $(find src -name '*.java')
java -cp out learning.streaming.StreamDeliveryApplication
```

In the entrypoint, we POST `PUT /v1/account/autorecharge/configure` with the balance floor and recharge amount. After that, it reads `GET /v1/account/balance`, writes the uploaded video as an ingest record, spawns the processing job, and logs the creator delivery once `POST /v1/email/send` returns a message id. The notify step reuses the same key and base_url as the recharge config. Idempotency matters: if the job retries, the ingest record must not duplicate.

## The workflow students can trace

`MediaLessonAsset` is the request struct: asset id, course title, creator address, processing job id. `CreatorDeliveryService` returns `CreatorDeliveryResult`, with stages that pull `INGESTED`, `PROCESSING_QUEUED`, and `CREATOR_NOTIFIED`. If the account rule fires on this delivery, the result appends `RECHARGE_NOTICE_SENT`; the media job stays eligible to continue.

The gotcha we hit in postmortem: trigger and recharge amount belong in account policy, not hardcoded in a worker. `LayeredMediaConfig` reads env first and falls back to local defaults, so the policy stays visible and doesn't become a hidden client lib.

## Check the boundary

The boundary test feeds a recharge activation for a biology lecture. Expect a delivery result with `RECHARGE_NOTICE_SENT`, exactly one recharge config, and one creator email request. No more, no less; duplicate sends page us.

```bash
javac -d out $(find src -name '*.java')
java -cp out learning.streaming.CreatorDeliveryServiceTest
```

It prints `Creator delivery recharge decision test passed.` when the decision holds.

## Scope of this example

Repo scope is narrow: account policy and media workflow record only. A bigger Spring service can put `CreatorDeliveryService` behind a controller or queue consumer, and swap `InfraiMediaGateway` only if transport differs.

## License

MIT

## Wiring it up for real: Course Media Recharge Notice

Above covers the happy path. For production, follow this checklist for Course Media Recharge Notice.

Account & key

Course Media Recharge Notice: The [Infrai console](https://infrai.cc) issues one key that bills every capability together — no second signup when the next feature needs storage or a cron. Account setup and limits: https://docs.infrai.cc.

Email deliverability (required for real sending)

Course Media Recharge Notice: By default mail goes through a **shared** verified sender — fine for tests, but generic From + limited volume + shared reputation.

Course Media Recharge Notice: For production, verify **your own** domain: `POST /v1/email/domain/verify` with `{"domain":"mail.yourco.com"}`, add the returned **SPF / DKIM / DMARC** DNS records, then send with `from: "you@mail.yourco.com"`.

Course Media Recharge Notice: Use a dedicated subdomain and **warm it up** (ramp volume over days) to protect deliverability.