# DingTalk Card Template Contract

OrbisOps uses one DingTalk Advanced Interactive Card template for three presentation concerns: ordinary Channel replies, durable run progress updates, and interactive approval cards. The DingTalk template is presentation only; it must never reconstruct approval authority from card fields.

## Required card parameters

The template should bind these parameters from `cardData.cardParamMap`:

| Parameter | Meaning |
| --- | --- |
| `markdown` | Main OrbisOps presentation text. |
| `resolved` | `true` after an interactive decision becomes terminal. |
| `hasActions` | Whether action controls should be shown. |
| `primaryLabel` | Label for the first action. |
| `primaryActionToken` | Opaque value for the first action. |
| `secondaryLabel` | Label for the second action. |
| `secondaryActionToken` | Opaque value for the second action. |

A normal progress card can omit the action-specific values. The template should hide the action area when `hasActions` is not `true`, and should treat `resolved=true` as terminal presentation.

## Action callback contract

Each approval button must submit only an opaque action value in the callback private parameters. The recommended callback parameter name is:

```text
actionToken
```

Bind the primary button's `actionToken` to `primaryActionToken`, and the secondary button's `actionToken` to `secondaryActionToken`.

For compatibility, OrbisOps also accepts the callback parameter names `opaqueActionToken`, `actionKey`, or `action`, but new templates should use `actionToken`.

Do not add ChangePackage IDs, package hashes, approved hashes, workflow authority data, tool arguments, or production credentials to the card callback. OrbisOps resolves the opaque token against its server-side approval ledger and re-runs identity, project membership, RBAC, expiry, idempotency, and current-state checks before accepting a decision.

## Callback and update mode

The card instance must use Stream callbacks. OrbisOps creates the card with `callbackType=STREAM` and maintains the returned `outTrackId` as the provider message ID. Run progress updates reuse the same `outTrackId`; terminal approval callbacks return a card-data update that hides actions and replaces the presentation with the resolved outcome.

## Setup checklist

1. Create or import an Advanced Interactive Card template in the same DingTalk application used by the OrbisOps Channel.
2. Bind the required parameters above.
3. Configure both buttons to return `actionToken` from the corresponding opaque token parameter.
4. Configure terminal/resolved state so action controls are hidden.
5. Copy the resulting Card Template ID into the OrbisOps DingTalk Channel setup wizard.
6. Keep the DingTalk Stream client and card API credentials under the same application identity.

A configured template is a prerequisite for native approval cards and in-place progress updates. Real provider readiness still requires a live DingTalk application, permissions, credentials, Stream connection, and successful external delivery/callback verification.
