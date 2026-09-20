# GymPro v1.0 — Targeted Fix Package

This package is based on the latest GymPro BranchWise/FeeReview stable project and includes the targeted v1.0 fixes:

- Profile camera capture uses a real output URI instead of TakePicturePreview/manual rotation, reducing camera orientation issues.
- Add Member creates the Firestore member before uploading the profile photo, so Storage branch/member security rules can authorize the upload.
- If photo upload fails after member creation, the member is retained and the photo can be added later instead of creating a duplicate.
- Editing Last Fees Date / membership plan / custom duration recalculates the due/expiry date.
- Ordinary member edits preserve the existing due/expiry date.
- Visible membership date label is `Last Fees Date`.
- Gradle AndroidX support is enabled.
- Gradle JVM heap is set to 4096 MB to avoid Java heap-space build failures.

## Important

`gradle.properties` contains placeholders for the release keystore password fields. Replace those placeholders with the existing local signing values before building a signed release APK. Do not commit real signing passwords.

`google-services.json` is intentionally not included; use the existing Firebase config file for the GymPro Firebase project.
