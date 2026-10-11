# Privacy

WhatsWeb does not run an account system, analytics, advertising, or a crash-reporting service. Nothing is sent to a WhatsWeb server, because there isn't one.

The companion page is the official WhatsApp Web site, loaded in the system WebView. Whatever that site stores (the linked-device session, cookies, and page data) stays in this app's private storage on the phone. Android backup and device-to-device transfer of that storage are turned off.

## Delete your data

WhatsWeb keeps the linked session only on this phone. To delete it, open the app, open settings, and choose **Clear this link**. That signs the companion out and deletes the cookies and the page data stored by the app.

Files already saved into the system Downloads collection stay there until you delete them yourself.

There is no WhatsWeb account and no copy of your chats on a WhatsWeb server. Deleting the WhatsApp account, or chats stored by WhatsApp, is done in the WhatsApp app.

Optional app lock uses Android's biometric prompt. WhatsWeb never receives a fingerprint, face, or device PIN. It only learns whether the device accepted the unlock.

"Hide from recents" sets Android's secure-window flag, which blanks the recent-apps preview and blocks screenshots.

Files you save, and files shared into the app, are written to the system Downloads collection when the phone allows it. On older Android versions, if that folder is not writable, the file is kept in the app's own folder instead.

Opening the companion means the chat connection goes to WhatsApp. That service has its own privacy policy, and it is not controlled by this app. The app refuses three calls the page makes for itself: deidentified telemetry (`dit.whatsapp.net`), the metrics beacon (`/ajax/bz`), and crash-log uploads (`crashlogs.whatsapp.net`).
