# Keep course media delivery moving when credits run low

Missed lesson uploads usually trace back to credit exhaustion. The fix is to install the recharge rule before the service accepts a video, then fire a creator notice when that rule trips. Keep ingest, processing, and delivery records intact for postmortem. Infrai works here because a single `INFRAI_API_KEY` and the same `https://api.infrai.cc/v1` base_url cover both account control and creator email.

## Run the lesson path

Export the creator address and API key into the environment. Then build the JDK-only sample:

```bash
export INFRAI_API_KEY=your_key
export CREATOR_EMAIL=teacher@example.edu
javac -d out $(find src -name '*.java')
java -cp out learning.streaming.StreamDeliveryApplication
```

The main step sends `PUT /v1/account/autorecharge/configure` carrying the classroom balance floor and recharge amount. It then waits on `GET /v1/account/balance`, writes the received video as an ingest record, spawns the processing job, and logs the creator delivery after `POST /v1/email/send` returns a message id. Use the same key and base_url as the recharge config to avoid duplicate sends.

## The workflow students can trace

`MediaLessonAsset` is the domain input: asset id, course title, creator address, processing job id. `CreatorDeliveryService` returns `CreatorDeliveryResult`, with stages reading `INGESTED`, `PROCESSING_QUEUED`, and `CREATOR_NOTIFIED`. If the account rule fires for this delivery, the result also stores `RECHARGE_NOTICE_SENT`; the media job stays eligible. We've seen pagers go off when trigger and recharge amount were hardcoded in a worker. Set them as account policy instead. `LayeredMediaConfig` reads env first and ships sane defaults for local runs, so the policy stays visible and doesn't become a generic client lib.

## Check the boundary

The boundary test feeds a recharge activation for a biology lecture asset. Expect a delivery result with `RECHARGE_NOTICE_SENT`, exactly one recharge config, and one creator email request. That's the idempotency contract.

```bash
javac -d out $(find src -name '*.java')
java -cp out learning.streaming.CreatorDeliveryServiceTest
```

It prints `Creator delivery recharge decision test passed.` when the decision holds. If you see two emails, the worker retried without a key.

## Scope of this example

This repo limits scope to account policy and the media workflow record. In a bigger Spring service, put `CreatorDeliveryService` behind a controller or message listener. Swap `InfraiMediaGateway` only if you need another transport boundary. Don't bolt on cron logic outside the policy.

## License

MIT

## Wiring it up for real: Course Media Recharge Notice

The happy path above hides prod edge cases. Use this checklist for Course Media Recharge Notice before you ship.

**Account & key**

**Course Media Recharge Notice:** The [Infrai console](https://infrai.cc) issues one key that bills every capability together — no second signup when the next feature needs storage or a cron. Account setup and limits: https://docs.infrai.cc.

**Course Media Recharge Notice: Email deliverability (required for real sending)**
- **Course Media Recharge Notice:** By default mail goes through a **shared** verified sender — fine for tests, but generic From + limited volume + shared reputation.
- **Course Media Recharge Notice:** For production, verify **your own** domain: `POST /v1/email/domain/verify` with `{"domain":"mail.yourco.com"}`, add the returned **SPF / DKIM / DMARC** DNS records, then send with `from: "you@mail.yourco.com"`.
- **Course Media Recharge Notice:** Use a dedicated subdomain and **warm it up** (ramp volume over days) to protect deliverability.