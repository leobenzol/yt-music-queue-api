# Tasker project

[YT_Music_Queue.prj.xml](YT_Music_Queue.prj.xml) turns messages in one WhatsApp group into song
requests during a scheduled party window. Tasker reads the sender and the song from each
notification and sends a `REQUEST` to the patched app, which searches the song, applies the limits
and keeps the request order. No Tasker plugins are needed.

## Import

1. Patch YouTube Music with the **Queue API** patch and choose a token (the `token` patch option).
2. Copy `YT_Music_Queue.prj.xml` to the phone, for example into `Tasker/projects/`.
3. In Tasker, long-press the project bar at the bottom (the house icon) → **Import Project** → pick the file.
4. Open the **YTQ Setup** task and edit its values:
   - `%YTQ_TOKEN`: the token you chose when patching.
   - `%YTQ_GROUP`: the WhatsApp group name as it appears in notifications.
   - `%YTQ_PACKAGE`: `app.morphe.android.apps.youtube.music` (non-root install with GmsCore),
     or `com.google.android.apps.youtube.music` (root install).
5. Run **YTQ Setup** once (▶), then **YTQ Test Request** while YouTube Music is playing. A popup
   like `Tasker test: "Never Gonna Give You Up" by Rick Astley plays next` confirms the setup.
6. Give Tasker notification access: Android settings → Notifications → Device & app notifications.

## What's inside

| Profile | Trigger | Task |
|---|---|---|
| YTQ Party Start | Saturday at 21:00 | **YTQ Open Requests**: sets `%YTQ_ACTIVE` to 1 |
| YTQ Party End | Every day at 01:00 | **YTQ Close Requests**: sets `%YTQ_ACTIVE` to 0 |
| YTQ WhatsApp Requests | WhatsApp notification, while `%YTQ_ACTIVE` is 1 | **YTQ Request From Message**: sends a `REQUEST` if the title contains `%YTQ_GROUP` |
| YTQ Results | Queue API result | **YTQ Show Result**: popup with who requested what, or the queue |
| YTQ Now Playing | Song changed, while `%YTQ_ACTIVE` is 1 | **YTQ Announce Song**: popup when a requested song starts |

Manual tasks: **YTQ Open Requests** / **YTQ Close Requests** (start or stop right now), **YTQ Show Queue**.

## How a message becomes a request

Each message is one song request: the whole message text is the search query, or a YouTube /
YouTube Music link. **YTQ Request From Message** finds the sender in the notification title:

| Title | Sender |
|---|---|
| `Party: Alice` (WhatsApp group message, as Tasker 6.6 reports it) | Alice |
| `Alice @ Party` (older Android versions) | Alice |
| anything else | `unknown` |

Summary notifications such as "5 new messages" or "12 messages from 3 chats" are skipped. The app
ignores the same song from the same person within 2 minutes, since WhatsApp sometimes posts a
notification twice.

To use another messenger, change the app in the **YTQ WhatsApp Requests** profile, and the title
formats in **YTQ Request From Message** if its notifications look different. To accept only
messages that start with a prefix such as `!song`, add a condition like `%evtprm3 ~ !song*` to the
first **If** of that task. The prefix can stay in the message: the search finds "!song toto africa"
just like "toto africa".

## Changing the schedule

- **Start:** edit the Day and Time of *YTQ Party Start*.
- **Length:** set the time of *YTQ Party End* to the start plus *x* hours.

These are two single points in time on purpose. A single Tasker Time context such as 21:00–01:00
together with a Day context does **not** cover the night into the next day. Tasker applies both to
the same calendar day.

## Replying in the WhatsApp group (optional)

Tasker cannot reply to a notification natively. With the AutoNotification plugin, add an
**AutoNotification Reply** action to *YTQ Show Result*: reply to the `%YTQ_GROUP` notification with
`%requester: %message`. Consider replying only when `%status` is not `QUEUED`.

## If requests do not arrive

- **The group is muted in WhatsApp.** Muted chats post no notifications. Silence the group's
  notification channel in Android settings instead.
- **You are looking at the group on the phone,** or **WhatsApp Web / Desktop is active.** WhatsApp
  then posts no notification.
- **YouTube Music is paused for a long time.** Android freezes it and requests wait until playback
  resumes. While music plays, everything works with the screen locked.
- **YouTube Music was not opened since the phone started.** The app listens for requests once its
  main screen has opened.
- **Battery optimization** is on for Tasker or YouTube Music. Set both to *Unrestricted*, and see
  dontkillmyapp.com for your phone brand.
